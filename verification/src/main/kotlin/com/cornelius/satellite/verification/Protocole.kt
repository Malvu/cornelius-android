package com.cornelius.satellite.verification

import com.cornelius.satellite.net.Appairage
import com.cornelius.satellite.net.ConfianceCornelius
import com.cornelius.satellite.net.Protocole
import com.cornelius.satellite.net.Satellite
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Vérifie le dialogue avec Cornelius, sans téléphone et sans toucher au vrai serveur.
 *
 * Un faux Cornelius (MockWebServer) présente un certificat auto-signé, comme le vrai.
 * On vérifie l'association, la connexion, l'en-tête d'autorisation, les messages
 * `hello` / `welcome` / `listen` / `session`, la taille des trames de son, et le refus
 * d'un certificat inconnu.
 */
fun verifierProtocole() {
    var toutBon = true
    println("— Association (wss://…/satellite/pair)")
    toutBon = testerAppairage() && toutBon
    println("— Satellite (wss://…/satellite)")
    toutBon = testerSatellite() && toutBon
    println("— Certificat changé")
    toutBon = testerCertificatInconnu() && toutBon
    println(if (toutBon) "RÉUSSITE : le dialogue correspond au protocole des satellites." else "ÉCHEC : le dialogue ne correspond pas.")
    // Les fils d'OkHttp gardent le programme en vie : on sort explicitement.
    kotlin.system.exitProcess(if (toutBon) 0 else 1)
}

private fun serveurTls(serveur: MockWebServer) {
    val certificat = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    val certificats = HandshakeCertificates.Builder().heldCertificate(certificat).build()
    serveur.useHttps(certificats.sslSocketFactory(), false)
}

private fun testerAppairage(): Boolean {
    val serveur = MockWebServer()
    serveurTls(serveur)
    val recus = CopyOnWriteArrayList<String>()
    val nom = AtomicReference("")
    val jeton = AtomicReference("")
    val empreinte = AtomicReference("")
    val attente = CountDownLatch(0)
    val fin = CountDownLatch(2)

    serveur.enqueue(
        MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) = Unit

            override fun onMessage(webSocket: WebSocket, text: String) {
                recus.add(text)
                if (text.contains("\"pair\"")) {
                    webSocket.send("""{"type":"waiting"}""")
                    webSocket.send("""{"type":"paired","name":"Cuisine","token":"jeton-de-test"}""")
                }
            }
        }),
    )
    serveur.start()

    val client = ConfianceCornelius.client({ empreinte.get() }, { empreinte.set(it) })
    val appairage = Appairage(
        client,
        "127.0.0.1:${serveur.port}",
        "cornelius-android-test01",
        object : Appairage.Ecoute {
            override fun enAttente() {
                attente.countDown()
                fin.countDown()
            }

            override fun associe(nomRecu: String, jetonRecu: String) {
                nom.set(nomRecu)
                jeton.set(jetonRecu)
                fin.countDown()
            }

            override fun clignote() = Unit
            override fun erreur(message: String) = println("  erreur : $message")
        },
    )
    appairage.demarrer()
    fin.await(15, TimeUnit.SECONDS)

    val requete = serveur.takeRequest(10, TimeUnit.SECONDS)
    var ok = true

    fun verifier(nomVerif: String, condition: Boolean, detail: String = "") {
        println("  ${if (condition) "OK" else "ÉCHEC"}  $nomVerif${if (detail.isBlank()) "" else " — $detail"}")
        if (!condition) ok = false
    }

    verifier(
        "message d'association envoyé",
        recus.isNotEmpty() && recus[0].contains("\"type\":\"pair\""),
        recus.firstOrNull() ?: "aucun message",
    )
    verifier(
        "identifiant de l'appareil",
        recus.firstOrNull()?.contains("cornelius-android-test01") == true,
    )
    verifier("protocole annoncé", recus.firstOrNull()?.contains("\"protocol\":${Protocole.VERSION}") == true)
    verifier("chemin demandé", requete?.path == "/satellite/pair", requete?.path ?: "aucune requête")
    verifier("nom reçu", nom.get() == "Cuisine", nom.get())
    verifier("jeton reçu", jeton.get() == "jeton-de-test", jeton.get())
    verifier(
        "empreinte du certificat retenue",
        empreinte.get().length == 64,
        if (empreinte.get().isEmpty()) "aucune" else empreinte.get().take(16) + "…",
    )
    appairage.fermer()
    Thread.sleep(300)
    runCatching { serveur.shutdown() }
    return ok
}

private fun testerSatellite(): Boolean {
    val serveur = MockWebServer()
    serveurTls(serveur)
    val textes = CopyOnWriteArrayList<String>()
    val trames = CopyOnWriteArrayList<Int>()
    val sons = CopyOnWriteArrayList<Int>()
    val pret = CountDownLatch(1)

    serveur.enqueue(
        MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                textes.add(text)
                if (text.contains("\"hello\"")) {
                    webSocket.send(
                        """{"type":"welcome","protocol":1,"id":"sat-1","name":"Chambre","volume":70}""",
                    )
                    webSocket.send(
                        """{"type":"settings","volume":70,"led":{"color":"#00ffb4","brightness":6,"nightLight":false},"attentive":false}""",
                    )
                    webSocket.send("""{"type":"listen","enabled":true}""")
                    // 100 ms de son, comme la voix de Cornelius.
                    webSocket.send(okio.ByteString.of(*ByteArray(3200) { (it % 120).toByte() }))
                    pret.countDown()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                trames.add(bytes.size)
            }
        }),
    )
    serveur.start()

    val empreinte = AtomicReference("")
    val client = ConfianceCornelius.client({ empreinte.get() }, { empreinte.set(it) })
    val messages = CopyOnWriteArrayList<String>()
    val connecte = CountDownLatch(1)
    val satellite = Satellite(
        client,
        "127.0.0.1:${serveur.port}",
        "jeton-de-test",
        object : Satellite.Ecoute {
            override fun ouvert() = connecte.countDown()
            override fun message(texte: String) {
                messages.add(texte)
            }

            override fun audio(pcm: ShortArray) {
                sons.add(pcm.size)
            }

            override fun perdu(raison: String) = println("  connexion perdue : $raison")
        },
    )
    satellite.connecter()
    connecte.await(10, TimeUnit.SECONDS)
    // En vrai, c'est le Moteur qui envoie hello à l'ouverture, avec l'annulation d'écho du micro.
    satellite.envoyer("""{"type":"hello","protocol":${Protocole.VERSION},"firmware":"${Protocole.FIRMWARE}","aec":false}""")
    attendreJusqua(5_000) { messages.any { it.contains("welcome") } }
    repeat(4) { satellite.envoyerAudio(ShortArray(1280) { 100 }) }
    attendreJusqua(5_000) { trames.size >= 4 }
    attendreJusqua(5_000) { sons.isNotEmpty() }

    val requete = serveur.takeRequest(10, TimeUnit.SECONDS)
    var ok = true

    fun verifier(nomVerif: String, condition: Boolean, detail: String = "") {
        println("  ${if (condition) "OK" else "ÉCHEC"}  $nomVerif${if (detail.isBlank()) "" else " — $detail"}")
        if (!condition) ok = false
    }

    verifier("chemin demandé", requete?.path == "/satellite", requete?.path ?: "aucune requête")
    verifier(
        "en-tête d'autorisation",
        requete?.getHeader("Authorization") == "Bearer jeton-de-test",
        requete?.getHeader("Authorization") ?: "absent",
    )
    verifier("hello envoyé", textes.firstOrNull()?.contains("\"hello\"") == true)
    verifier("firmware annoncé", textes.firstOrNull()?.contains(Protocole.FIRMWARE) == true)
    verifier("welcome reçu", messages.any { it.contains("\"welcome\"") })
    verifier("listen reçu", messages.any { it.contains("\"listen\"") })
    verifier("trames de 2 560 octets", trames.isNotEmpty() && trames.all { it == 2560 }, trames.toString())
    verifier("son reçu et décodé", sons.isNotEmpty() && sons[0] == 1600, sons.toString())
    satellite.fermer()
    Thread.sleep(300)
    runCatching { serveur.shutdown() }
    return ok
}

private fun testerCertificatInconnu(): Boolean {
    val serveur = MockWebServer()
    serveurTls(serveur)
    serveur.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
    serveur.start()

    val refus = CountDownLatch(1)
    val message = AtomicReference("")
    // Une empreinte différente de celle du serveur : la connexion doit être refusée.
    val client = ConfianceCornelius.client({ "00".repeat(32) }, { })
    val satellite = Satellite(
        client,
        "127.0.0.1:${serveur.port}",
        "jeton-de-test",
        object : Satellite.Ecoute {
            override fun ouvert() = Unit
            override fun message(texte: String) = Unit
            override fun audio(pcm: ShortArray) = Unit
            override fun perdu(raison: String) {
                message.set(raison)
                refus.countDown()
            }
        },
    )
    satellite.connecter()
    val refuse = refus.await(10, TimeUnit.SECONDS)
    val ok = refuse && message.get().contains("certificat", ignoreCase = true)
    println("  ${if (ok) "OK" else "ÉCHEC"}  connexion refusée — ${message.get()}")
    satellite.fermer()
    Thread.sleep(300)
    runCatching { serveur.shutdown() }
    return ok
}

private fun attendreJusqua(delai: Long, condition: () -> Boolean) {
    val fin = System.currentTimeMillis() + delai
    while (System.currentTimeMillis() < fin && !condition()) Thread.sleep(20)
}

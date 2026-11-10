package com.cornelius.satellite.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Connexion au satellite de Cornelius (`wss://<adresse>/satellite`).
 *
 * Le dialogue est celui décrit dans `docs/developpement/satellites-protocole.md` :
 * des messages JSON pour l'état, et du son en binaire — du PCM 16 bits signé,
 * mono, 16 kHz, par trames de 2 560 octets (80 ms).
 */
class Satellite(
    private val client: OkHttpClient,
    private val adresse: String,
    private val jeton: String,
    private val ecoute: Ecoute,
) {

    interface Ecoute {
        /** Le WebSocket est ouvert : il faut envoyer `hello` tout de suite. */
        fun ouvert()

        /** Message JSON reçu du serveur. */
        fun message(texte: String)

        /** Son de Cornelius à jouer. */
        fun audio(pcm: ShortArray)

        /** Connexion perdue : code et raison donnés par le serveur. */
        fun perdu(raison: String)
    }

    private var socket: WebSocket? = null
    private var ouvert = false

    /** Tampon réutilisé pour convertir le son en octets, sans allocation à chaque trame. */
    private val tampon = ByteArray(TRAME * 2)

    fun connecter() {
        val requete = Request.Builder()
            .url("wss://$adresse/satellite")
            .header("Authorization", "Bearer $jeton")
            .build()
        socket = client.newWebSocket(requete, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                ouvert = true
                ecoute.ouvert()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                ecoute.message(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                ecoute.audio(enTableau(bytes))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ouvert = false
                ecoute.perdu(t.message ?: "Connexion perdue.")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ouvert = false
                ecoute.perdu(reason.ifBlank { "Fermé par Cornelius ($code)." })
            }
        })
    }

    fun envoyer(texte: String) {
        if (ouvert) socket?.send(texte)
    }

    /**
     * Envoie une trame de 1 280 échantillons ; toute autre taille ferait fermer la connexion.
     * Synchronisé : le son peut venir du fil du micro et de celui du mot d'appel, qui rejoue
     * les deux secondes précédant une détection.
     */
    @Synchronized
    fun envoyerAudio(pcm: ShortArray) {
        if (!ouvert || pcm.size != TRAME) return
        val vue = ByteBuffer.wrap(tampon).order(ByteOrder.LITTLE_ENDIAN)
        for (valeur in pcm) vue.putShort(valeur)
        socket?.send(tampon.toByteString())
    }

    fun fermer() {
        ouvert = false
        socket?.close(1000, "Arrêt de l'application")
        socket = null
    }

    private fun enTableau(bytes: ByteString): ShortArray {
        val vue = ByteBuffer.wrap(bytes.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        val pcm = ShortArray(bytes.size / 2)
        for (i in pcm.indices) pcm[i] = vue.short
        return pcm
    }

    companion object {
        /** Une trame montante : 1 280 échantillons, soit 80 ms à 16 kHz. */
        const val TRAME = 1280
    }
}

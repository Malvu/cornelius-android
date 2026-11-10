package com.cornelius.satellite.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * Association d'un téléphone neuf avec Cornelius (`wss://<adresse>/satellite/pair`).
 *
 * Le téléphone se présente ; il apparaît dans **Administration → Satellites audio →
 * Nouveaux satellites**. Quelqu'un doit cliquer « Associer » : rien n'est accordé
 * sans ce clic. Le serveur renvoie alors un nom et un jeton, que l'on garde.
 */
class Appairage(
    private val client: OkHttpClient,
    private val adresse: String,
    private val identifiant: String,
    private val ecoute: Ecoute,
) {

    interface Ecoute {
        /** Le serveur nous a vus : il attend le clic dans l'administration. */
        fun enAttente()

        /** Association acceptée : nom et jeton à enregistrer. */
        fun associe(nom: String, jeton: String)

        /** Le serveur demande de clignoter pour être reconnu (sans effet ici). */
        fun clignote()

        fun erreur(message: String)
    }

    private var socket: WebSocket? = null
    @Volatile private var abandonne = false

    fun demarrer() {
        val requete = Request.Builder()
            .url("wss://$adresse/satellite/pair")
            .build()
        socket = client.newWebSocket(requete, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val presentation = JSONObject()
                    .put("type", "pair")
                    .put("protocol", Protocole.VERSION)
                    .put("device", identifiant)
                    .put("firmware", Protocole.FIRMWARE)
                webSocket.send(presentation.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (message.optString("type")) {
                    "waiting" -> ecoute.enAttente()
                    "identify" -> ecoute.clignote()
                    "paired" -> {
                        ecoute.associe(
                            message.optString("name", "Téléphone"),
                            message.optString("token"),
                        )
                        webSocket.close(1000, "Associé.")
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!abandonne) ecoute.erreur(t.message ?: "Connexion impossible à $adresse.")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (code != 1000) ecoute.erreur(reason.ifBlank { "Association interrompue." })
            }
        })
    }

    fun fermer() {
        abandonne = true
        socket?.close(1000, "Abandon")
        socket = null
    }
}

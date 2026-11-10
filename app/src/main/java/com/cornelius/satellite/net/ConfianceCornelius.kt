package com.cornelius.satellite.net

import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Connexion chiffrée à Cornelius, dont le certificat est auto-signé.
 *
 * Cornelius crée lui-même son certificat : aucun organisme de confiance ne peut le
 * valider. On fait donc confiance au premier contact, en retenant l'empreinte SHA-256
 * de la clé publique (celle que l'administration affiche). Si le certificat change,
 * la connexion est refusée jusqu'à ce que la personne accepte le nouveau.
 */
object ConfianceCornelius {

    /** Empreinte SHA-256 de la clé publique d'un certificat, en hexadécimal minuscule. */
    fun empreinte(certificat: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val valeurs = digest.digest(certificat.publicKey.encoded)
        return valeurs.joinToString("") { "%02x".format(it) }
    }

    /**
     * Construit un client HTTP qui accepte le certificat auto-signé de Cornelius.
     *
     * @param empreinteConnue empreinte retenue lors d'une connexion précédente, ou vide.
     * @param retenir appelé avec l'empreinte retenue (première connexion, ou changement accepté).
     */
    fun client(empreinteConnue: () -> String, retenir: (String) -> Unit): OkHttpClient {
        val gestionnaire = object : X509TrustManager {
            override fun checkClientTrusted(chaine: Array<out X509Certificate>?, authType: String?) = Unit

            override fun checkServerTrusted(chaine: Array<out X509Certificate>?, authType: String?) {
                val certificat = chaine?.firstOrNull()
                    ?: throw CertificateException("Cornelius n'a pas présenté de certificat.")
                val valeur = empreinte(certificat)
                val connue = empreinteConnue()
                when {
                    connue.isEmpty() -> retenir(valeur)
                    connue.equals(valeur, ignoreCase = true) -> Unit
                    else -> throw CertificateException(
                        "Le certificat de Cornelius a changé. Empreinte reçue : $valeur",
                    )
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val contexte = SSLContext.getInstance("TLS")
        contexte.init(null, arrayOf(gestionnaire), null)

        return OkHttpClient.Builder()
            .sslSocketFactory(contexte.socketFactory, gestionnaire)
            // L'identité du serveur est vérifiée par l'empreinte de sa clé, pas par son nom :
            // Cornelius s'appelle tour à tour une adresse IP, un nom `.local`, etc.
            .hostnameVerifier { _, _ -> true }
            .pingInterval(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}

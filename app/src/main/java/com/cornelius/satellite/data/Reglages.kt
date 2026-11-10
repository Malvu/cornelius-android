package com.cornelius.satellite.data

import android.content.Context
import java.util.Locale
import java.util.UUID

/** Comment le téléphone écoute « Cornelius ». */
enum class ModeEcoute {
    /** Le son part en continu vers Cornelius, qui cherche le mot d'appel, comme les satellites ESP32. */
    DIRECT,

    /** Le téléphone cherche d'abord le mot d'appel, puis n'envoie le son que s'il l'a entendu. */
    ECONOMIE,
}

/**
 * Réglages de l'application, gardés sur le téléphone.
 *
 * Le jeton d'association est un secret : il permet de parler à Cornelius en tant que
 * satellite. Il ne quitte pas l'appareil.
 */
class Reglages(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    /** Adresse du serveur Cornelius, sans schéma : `192.168.50.3:3443`. */
    var adresse: String
        get() = prefs.getString(CLE_ADRESSE, DEFAULT_ADRESSE) ?: DEFAULT_ADRESSE
        set(valeur) = prefs.edit().putString(CLE_ADRESSE, valeur.trim()).apply()

    /** Jeton remis par l'administration lors de l'association ; vide tant que le satellite n'est pas associé. */
    var jeton: String
        get() = prefs.getString(CLE_JETON, "") ?: ""
        set(valeur) = prefs.edit().putString(CLE_JETON, valeur).apply()

    /** Nom donné au satellite dans l'administration. */
    var nom: String
        get() = prefs.getString(CLE_NOM, "") ?: ""
        set(valeur) = prefs.edit().putString(CLE_NOM, valeur).apply()

    var mode: ModeEcoute
        get() = runCatching { ModeEcoute.valueOf(prefs.getString(CLE_MODE, ModeEcoute.ECONOMIE.name)!!) }
            .getOrDefault(ModeEcoute.ECONOMIE)
        set(valeur) = prefs.edit().putString(CLE_MODE, valeur.name).apply()

    /** Score à partir duquel le mot d'appel est considéré entendu, en mode économie. */
    var seuil: Float
        get() = prefs.getFloat(CLE_SEUIL, DEFAULT_SEUIL)
        set(valeur) = prefs.edit().putFloat(CLE_SEUIL, valeur).apply()

    /** Empreinte SHA-256 de la clé publique du certificat de Cornelius (confiance au premier contact). */
    var empreinte: String
        get() = prefs.getString(CLE_EMPREINTE, "") ?: ""
        set(valeur) = prefs.edit().putString(CLE_EMPREINTE, valeur).apply()

    var microCoupe: Boolean
        get() = prefs.getBoolean(CLE_MICRO_COUPE, false)
        set(valeur) = prefs.edit().putBoolean(CLE_MICRO_COUPE, valeur).apply()

    /** Identifiant stable du téléphone, au format attendu par Cornelius : `cornelius-android-xxxxxx`. */
    val identifiantAppareil: String
        get() {
            val existant = prefs.getString(CLE_IDENTIFIANT, null)
            if (existant != null) return existant
            val nouveau = "cornelius-android-" + UUID.randomUUID().toString().replace("-", "")
                .lowercase(Locale.ROOT).take(6)
            prefs.edit().putString(CLE_IDENTIFIANT, nouveau).apply()
            return nouveau
        }

    fun oublierAssociation() {
        prefs.edit().remove(CLE_JETON).remove(CLE_NOM).apply()
    }

    fun oublierCertificat() {
        prefs.edit().remove(CLE_EMPREINTE).apply()
    }

    companion object {
        private const val FICHIER = "cornelius-satellite"
        private const val CLE_ADRESSE = "adresse"
        private const val CLE_JETON = "jeton"
        private const val CLE_NOM = "nom"
        private const val CLE_MODE = "mode"
        private const val CLE_SEUIL = "seuil"
        private const val CLE_EMPREINTE = "empreinte"
        private const val CLE_MICRO_COUPE = "micro-coupe"
        private const val CLE_IDENTIFIANT = "identifiant"

        const val DEFAULT_ADRESSE = "192.168.50.3:3443"
        const val DEFAULT_SEUIL = 0.5f
    }
}

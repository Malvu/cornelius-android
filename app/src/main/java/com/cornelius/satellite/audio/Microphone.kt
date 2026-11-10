package com.cornelius.satellite.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log

/**
 * Micro du téléphone, au format attendu par Cornelius : 16 kHz, mono, 16 bits signé.
 *
 * La source `VOICE_COMMUNICATION` laisse Android appliquer son annulation d'écho :
 * Cornelius peut parler pendant qu'on l'écoute, sans se réentendre lui-même.
 * Quand elle est disponible, on l'annonce au serveur (`"aec": true`), ce qui autorise
 * l'écoute attentive — on peut couper la parole à Cornelius.
 */
class Microphone {

    private var enregistreur: AudioRecord? = null
    private var annulationEcho: AcousticEchoCanceler? = null

    val annuleEcho: Boolean get() = annulationEcho?.enabled == true

    @SuppressLint("MissingPermission")
    fun demarrer(): Boolean {
        arreter()
        val tampon = maxOf(
            AudioRecord.getMinBufferSize(ECHANTILLONS, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
            TRAME * 2 * 8,
        )
        val nouveau = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            ECHANTILLONS,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            tampon,
        )
        if (nouveau.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "Micro indisponible (état ${nouveau.state}).")
            nouveau.release()
            return false
        }
        if (AcousticEchoCanceler.isAvailable()) {
            runCatching {
                annulationEcho = AcousticEchoCanceler.create(nouveau.audioSessionId)?.apply { enabled = true }
            }.onFailure { Log.w(TAG, "Annulation d'écho refusée par le système.", it) }
        }
        return try {
            nouveau.startRecording()
            enregistreur = nouveau
            true
        } catch (erreur: IllegalStateException) {
            Log.w(TAG, "Démarrage du micro impossible.", erreur)
            nouveau.release()
            false
        }
    }

    /** Lit exactement une trame de 80 ms et rend son nombre d'échantillons. */
    fun lire(destination: ShortArray): Int {
        val enregistreur = this.enregistreur ?: return 0
        var total = 0
        while (total < destination.size) {
            val lus = enregistreur.read(destination, total, destination.size - total)
            if (lus <= 0) return if (total > 0) total else lus
            total += lus
        }
        return total
    }

    fun arreter() {
        annulationEcho?.let {
            runCatching { it.enabled = false }
            it.release()
        }
        annulationEcho = null
        enregistreur?.let {
            runCatching { it.stop() }
            it.release()
        }
        enregistreur = null
    }

    companion object {
        private const val TAG = "CorneliusMicro"
        const val ECHANTILLONS = 16000
        const val TRAME = 1280
    }
}

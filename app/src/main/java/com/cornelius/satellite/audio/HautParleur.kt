package com.cornelius.satellite.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

/**
 * Haut-parleur du téléphone, au format du son envoyé par Cornelius : 16 kHz, mono, 16 bits.
 *
 * Le tampon de lecture est volontairement court (environ 200 ms) : c'est ce qui donne
 * une réponse qui arrive vite ; au-delà, Cornelius semblerait en retard.
 */
class HautParleur {

    private var piste: AudioTrack? = null

    fun demarrer() {
        if (piste != null) return
        val taille = maxOf(
            AudioTrack.getMinBufferSize(ECHANTILLONS, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
            ECHANTILLONS / 5 * 2,
        )
        val nouvelle = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ECHANTILLONS)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(taille)
            .build()
        if (nouvelle.state != AudioTrack.STATE_INITIALIZED) {
            Log.w(TAG, "Sortie audio indisponible (état ${nouvelle.state}).")
            nouvelle.release()
            return
        }
        nouvelle.play()
        piste = nouvelle
    }

    fun ecrire(pcm: ShortArray) {
        val piste = this.piste ?: return
        if (pcm.isEmpty()) return
        runCatching { piste.write(pcm, 0, pcm.size) }
    }

    fun arreter() {
        piste?.let {
            runCatching { it.stop() }
            it.release()
        }
        piste = null
    }

    companion object {
        private const val TAG = "CorneliusSortie"
        const val ECHANTILLONS = 16000
    }
}

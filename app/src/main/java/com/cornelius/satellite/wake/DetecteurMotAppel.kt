package com.cornelius.satellite.wake

/**
 * Décide quand le mot d'appel est entendu, à partir des scores du pipeline.
 *
 * Deux garde-fous : le seuil, et un délai après une détection, pour éviter qu'un
 * même « Cornelius » n'ouvre deux conversations.
 */
class DetecteurMotAppel(
    private val pipeline: WakePipeline,
    private var seuil: Float = 0.5f,
    private val delai: Long = 2_000,
) {

    private var derniere: Long = 0L

    fun reglerSeuil(valeur: Float) {
        seuil = valeur.coerceIn(0.1f, 0.9f)
    }

    /** Calcule le score du bloc ; doit être appelé depuis un seul fil. */
    fun analyser(pcm: ShortArray): Float = synchronized(this) {
        pipeline.pousser(pcm).score
    }

    fun motEntendu(score: Float): Boolean = synchronized(this) {
        val maintenant = System.nanoTime() / 1_000_000
        if (score >= seuil && maintenant - derniere >= delai) {
            derniere = maintenant
            true
        } else {
            false
        }
    }

    /** Repart d'une écoute neuve : les tampons du pipeline sont vidés. */
    fun reinitialiser() = synchronized(this) {
        pipeline.reinitialiser()
    }
}

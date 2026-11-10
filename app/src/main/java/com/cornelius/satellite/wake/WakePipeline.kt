package com.cornelius.satellite.wake

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.util.ArrayDeque

/**
 * Calcule localement ce que le serveur Cornelius calcule pour entendre « Cornelius ».
 *
 * C'est le même enchaînement que sur l'ordinateur du miroir (openWakeWord 0.5.1) :
 *   1. `melspectrogram.onnx` transforme 80 ms de son en trames de mel ;
 *   2. `embedding_model.onnx` résume 76 trames de mel en un vecteur de 96 valeurs ;
 *   3. `cornelius.onnx` donne un score entre 0 et 1 sur les 16 derniers vecteurs.
 *
 * Le calcul est volontairement identique à celui du serveur : les nombres doivent
 * tomber pareil, sinon les deux détections ne sont pas comparables.
 *
 * Cette classe ne dépend d'aucune classe Android : elle est aussi compilée et
 * vérifiée côté ordinateur (module `verification`).
 */
class WakePipeline(
    melModele: ByteArray,
    embeddingModele: ByteArray,
    motModele: ByteArray,
) : AutoCloseable {

    /** Ce que le pipeline rend pour un bloc de 80 ms. */
    class ResultatBloc(
        /** Trames de mel ajoutées par ce bloc (8 trames de 32 valeurs en général). */
        val mel: List<FloatArray>,
        /** Vecteurs d'embedding ajoutés par ce bloc (un seul en général). */
        val embeddings: List<FloatArray>,
        /** Score du mot d'appel, de 0 à 1. */
        val score: Float,
    )

    private val environnement: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val melSession: OrtSession
    private val embeddingSession: OrtSession
    private val motSession: OrtSession

    private val nomEntreeMel: String
    private val nomEntreeEmbedding: String
    private val nomEntreeMot: String

    /** Derniers échantillons reçus, en anneau : 20 ms de plus que la fenêtre nécessaire. */
    private val brut = IntArray(RAW_CAPACITE)
    private var brutEcrit = 0
    private var brutRempli = 0

    private val tramesMel = ArrayDeque<FloatArray>()
    private val vecteurs = ArrayDeque<FloatArray>()

    private var predictions = 0

    init {
        val options = OrtSession.SessionOptions()
        options.setIntraOpNumThreads(1)
        options.setInterOpNumThreads(1)
        melSession = environnement.createSession(melModele, options)
        embeddingSession = environnement.createSession(embeddingModele, options)
        motSession = environnement.createSession(motModele, options)
        nomEntreeMel = melSession.inputNames.first()
        nomEntreeEmbedding = embeddingSession.inputNames.first()
        nomEntreeMot = motSession.inputNames.first()
        amorcer()
    }

    /**
     * Ajoute un bloc de son (16 kHz, 16 bits signé, mono) et rend les valeurs calculées.
     *
     * Le bloc doit faire 1280 échantillons, comme les trames du protocole satellite.
     */
    fun pousser(pcm: ShortArray): ResultatBloc {
        ajouterBrut(pcm)

        val taille = minOf(pcm.size + CONTEXTE, brutRempli)
        val fenetre = FloatArray(taille)
        dernieresEchantillons(taille, fenetre)

        val mel = calculerMel(fenetre)
        for (trame in mel) tramesMel.addLast(trame)
        while (tramesMel.size > MEL_MAX) tramesMel.removeFirst()

        val nouveauxEmbeddings = mutableListOf<FloatArray>()
        val fenetreMel = derniereFenetreMel()
        if (fenetreMel != null) {
            val vecteur = calculerEmbedding(fenetreMel)
            vecteurs.addLast(vecteur)
            nouveauxEmbeddings.add(vecteur)
            while (vecteurs.size > VECTEURS_MAX) vecteurs.removeFirst()
        }

        val score = calculerScore()
        predictions++
        return ResultatBloc(mel, nouveauxEmbeddings, score)
    }

    /** Remet le pipeline à zéro : nouveau flux d'écoute, sans mémoire du précédent. */
    fun reinitialiser() {
        brutEcrit = 0
        brutRempli = 0
        tramesMel.clear()
        vecteurs.clear()
        predictions = 0
        amorcer()
    }

    /**
     * État de départ du serveur Cornelius : 76 trames de mel à 1, aucun vecteur.
     * Sans ces trames, les premiers dixièmes de seconde ne donneraient pas les mêmes
     * scores que le serveur.
     */
    private fun amorcer() {
        repeat(FENETRE_MEL) { tramesMel.addLast(FloatArray(32) { 1f }) }
        repeat(VECTEURS_MAX) { vecteurs.addLast(FloatArray(96)) }
    }

    override fun close() {
        motSession.close()
        embeddingSession.close()
        melSession.close()
    }

    private fun ajouterBrut(pcm: ShortArray) {
        for (valeur in pcm) {
            brut[brutEcrit] = valeur.toInt()
            brutEcrit = (brutEcrit + 1) % brut.size
            if (brutRempli < brut.size) brutRempli++
        }
    }

    private fun dernieresEchantillons(nombre: Int, sortie: FloatArray) {
        val dispo = minOf(nombre, brutRempli)
        val debut = (brutEcrit - dispo + brut.size) % brut.size
        for (i in 0 until dispo) sortie[i] = brut[(debut + i) % brut.size].toFloat()
    }

    private fun calculerMel(fenetre: FloatArray): List<FloatArray> {
        OnnxTensor.createTensor(
            environnement,
            FloatBuffer.wrap(fenetre),
            longArrayOf(1, fenetre.size.toLong()),
        ).use { entree ->
            melSession.run(mapOf(nomEntreeMel to entree)).use { sortie ->
                val valeurs = lireFlottants(sortie.get(0) as OnnxTensor)
                val trames = valeurs.size / 32
                return (0 until trames).map { trame ->
                    FloatArray(32) { colonne -> valeurs[trame * 32 + colonne] / 10f + 2f }
                }
            }
        }
    }

    private fun derniereFenetreMel(): FloatArray? {
        if (tramesMel.size < FENETRE_MEL) return null
        val tableau = FloatArray(FENETRE_MEL * 32)
        var index = tramesMel.size - FENETRE_MEL
        var position = 0
        for (trame in tramesMel) {
            if (index-- > 0) continue
            System.arraycopy(trame, 0, tableau, position, 32)
            position += 32
        }
        return tableau
    }

    private fun calculerEmbedding(fenetre: FloatArray): FloatArray {
        OnnxTensor.createTensor(
            environnement,
            FloatBuffer.wrap(fenetre),
            longArrayOf(1, FENETRE_MEL.toLong(), 32, 1),
        ).use { entree ->
            embeddingSession.run(mapOf(nomEntreeEmbedding to entree)).use { sortie ->
                return lireFlottants(sortie.get(0) as OnnxTensor)
            }
        }
    }

    private fun calculerScore(): Float {
        // openWakeWord ignore les cinq premières prédictions, le temps que les tampons se remplissent.
        if (predictions < 5 || vecteurs.size < TRAMES_FEATURES) return 0f
        val entree = FloatArray(TRAMES_FEATURES * 96)
        var index = vecteurs.size - TRAMES_FEATURES
        var position = 0
        for (vecteur in vecteurs) {
            if (index-- > 0) continue
            System.arraycopy(vecteur, 0, entree, position, 96)
            position += 96
        }
        OnnxTensor.createTensor(
            environnement,
            FloatBuffer.wrap(entree),
            longArrayOf(1, TRAMES_FEATURES.toLong(), 96),
        ).use { tensor ->
            motSession.run(mapOf(nomEntreeMot to tensor)).use { sortie ->
                return lireFlottants(sortie.get(0) as OnnxTensor).firstOrNull() ?: 0f
            }
        }
    }

    private fun lireFlottants(tensor: OnnxTensor): FloatArray {
        val tampon: FloatBuffer = tensor.floatBuffer
            ?: throw IllegalStateException("Sortie ONNX inattendue : ce n'est pas un tableau de flottants.")
        tampon.rewind()
        val valeurs = FloatArray(tampon.remaining())
        tampon.get(valeurs)
        return valeurs
    }

    companion object {
        /** Une trame audio du protocole satellite : 1280 échantillons, soit 80 ms à 16 kHz. */
        const val BLOC = 1280

        /** Échantillons de contexte demandés par le modèle de mel (30 ms). */
        const val CONTEXTE = 480

        /** Trames de mel dans une fenêtre d'embedding. */
        const val FENETRE_MEL = 76

        /** Trames d'embedding lues par le modèle du mot d'appel. */
        const val TRAMES_FEATURES = 16

        private const val MEL_MAX = 10 * 97
        private const val VECTEURS_MAX = 120
        private const val RAW_CAPACITE = BLOC + CONTEXTE + 512
    }
}

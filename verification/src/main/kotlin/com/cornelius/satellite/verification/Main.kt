package com.cornelius.satellite.verification

import com.cornelius.satellite.wake.WakePipeline
import java.io.File
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.abs

/**
 * Vérifications faites sur l'ordinateur, sans téléphone.
 *
 * Utilisation, depuis le dossier `verification` (aucun chemin avec espace à saisir) :
 *   gradle run                     -> pipeline du mot d'appel, jeu « pipeline »
 *   gradle run --args="tts"        -> pipeline du mot d'appel, jeu « tts »
 *   gradle run --args="protocole"  -> dialogue avec un faux Cornelius (WebSocket + TLS)
 *
 * Les fichiers de vecteurs sont produits par outils/reference.py.
 */
fun main(args: Array<String>) {
    when (args.firstOrNull() ?: "pipeline") {
        "protocole" -> verifierProtocole()
        else -> verifierPipeline(args.firstOrNull() ?: "pipeline")
    }
}

fun verifierPipeline(jeu: String) {
    val dossierModeles = File("../app/src/main/assets/models")
    val wav = File("../outils/audio/" + if (jeu == "tts") "tts-cornelius.wav" else "test-pipeline.wav")
    val dossierVecteurs = File("../outils/vecteurs/$jeu")

    val pipeline = WakePipeline(
        File(dossierModeles, "melspectrogram.onnx").readBytes(),
        File(dossierModeles, "embedding_model.onnx").readBytes(),
        File(dossierModeles, "cornelius.onnx").readBytes(),
    )

    val audio = lireWav(wav)
    println("${wav.name} : ${audio.size} échantillons (${"%.1f".format(audio.size / 16000.0)} s)")

    val mel = mutableListOf<FloatArray>()
    val embeddings = mutableListOf<FloatArray>()
    val scores = mutableListOf<Float>()
    val durees = mutableListOf<Long>()

    var debut = 0
    while (debut + WakePipeline.BLOC <= audio.size) {
        val bloc = audio.copyOfRange(debut, debut + WakePipeline.BLOC)
        val chrono = System.nanoTime()
        val resultat = pipeline.pousser(bloc)
        durees.add((System.nanoTime() - chrono) / 1000)
        mel.addAll(resultat.mel)
        embeddings.addAll(resultat.embeddings)
        scores.add(resultat.score)
        debut += WakePipeline.BLOC
    }
    pipeline.close()

    val melAttendu = lireLignes(File(dossierVecteurs, "mel.txt"))
    val embeddingAttendu = lireLignes(File(dossierVecteurs, "embedding.txt"))
    val scoreAttendu = File(File(dossierVecteurs, "score.txt").path).readLines()
        .filter { it.isNotBlank() }
        .map { it.toFloat() }

    var echec = false
    echec = comparer("mel", mel, melAttendu, 1e-4f) || echec
    echec = comparer("embedding", embeddings, embeddingAttendu, 1e-4f) || echec
    echec = comparerScores(scores, scoreAttendu, 1e-5f) || echec

    val moyennes = durees.sorted()
    println(
        "Temps par bloc de 80 ms : médiane ${moyennes[moyennes.size / 2]} µs, " +
            "maximum ${moyennes.last()} µs",
    )
    println(if (echec) "ÉCHEC : le portage ne correspond pas à la référence." else "RÉUSSITE : le portage correspond à la référence.")
}

private fun lireLignes(fichier: File): List<FloatArray> =
    fichier.readLines().filter { it.isNotBlank() }.map { ligne ->
        ligne.trim().split(" ").map { it.toFloat() }.toFloatArray()
    }

private fun comparer(
    nom: String,
    obtenu: List<FloatArray>,
    attendu: List<FloatArray>,
    tolerance: Float,
): Boolean {
    if (obtenu.size != attendu.size) {
        println("$nom : ${obtenu.size} valeurs calculées, ${attendu.size} attendues -> ÉCHEC")
        return true
    }
    var maximum = 0f
    var ou = -1
    for (i in obtenu.indices) {
        val a = obtenu[i]
        val b = attendu[i]
        if (a.size != b.size) {
            println("$nom : taille différente à l'indice $i (${a.size} contre ${b.size}) -> ÉCHEC")
            return true
        }
        for (j in a.indices) {
            val ecart = abs(a[j] - b[j])
            if (ecart > maximum) {
                maximum = ecart
                ou = i
            }
        }
    }
    val ok = maximum <= tolerance
    println(
        "$nom : ${obtenu.size} lignes, écart maximum ${"%.2e".format(maximum)}" +
            (if (ou >= 0) " (ligne $ou)" else "") + " -> " + if (ok) "OK" else "ÉCHEC",
    )
    return !ok
}

private fun comparerScores(obtenus: List<Float>, attendus: List<Float>, tolerance: Float): Boolean {
    if (obtenus.size != attendus.size) {
        println("score : ${obtenus.size} calculés, ${attendus.size} attendus -> ÉCHEC")
        return true
    }
    var maximum = 0f
    var ou = -1
    for (i in obtenus.indices) {
        val ecart = abs(obtenus[i] - attendus[i])
        if (ecart > maximum) {
            maximum = ecart
            ou = i
        }
    }
    val ok = maximum <= tolerance
    println(
        "score : ${obtenus.size} blocs, écart maximum ${"%.2e".format(maximum)} (bloc $ou) -> " +
            if (ok) "OK" else "ÉCHEC",
    )
    return !ok
}

private fun lireWav(fichier: File): ShortArray {
    AudioSystem.getAudioInputStream(fichier).use { flux: AudioInputStream ->
        val format = flux.format
        require(format.sampleSizeInBits == 16) { "WAV 16 bits attendu" }
        require(format.channels == 1) { "WAV mono attendu" }
        require(format.sampleRate == 16000f) { "WAV 16 kHz attendu" }
        val octets = flux.readAllBytes()
        val echantillons = ShortArray(octets.size / 2)
        for (i in echantillons.indices) {
            val bas = octets[2 * i].toInt() and 0xFF
            val haut = octets[2 * i + 1].toInt()
            echantillons[i] = ((haut shl 8) or bas).toShort()
        }
        return echantillons
    }
}

"""Référence Python du pipeline du mot d'appel Cornelius.

Ce script est la référence : il reproduit exactement ce que fait le serveur
Cornelius (openWakeWord 0.5.1 : melspectrogram.onnx, embedding_model.onnx,
puis cornelius.onnx). Il sert à vérifier que le portage Kotlin de l'application
Android donne les mêmes nombres.

Usage :
    python outils/reference.py --wav test.wav --sortie outils/vecteurs

Produit dans le dossier de sortie :
    mel.txt         8 trames de mel par bloc de 80 ms
    embedding.txt   96 valeurs par bloc
    score.txt       1 score par bloc
    entete.txt      nombre de blocs, taille des fenêtres
"""

import argparse
import wave
from pathlib import Path

import numpy as np
import onnxruntime as ort

RACINE = Path(__file__).resolve().parent.parent
MODELES = RACINE / "app" / "src" / "main" / "assets" / "models"

CHUNK = 1280          # 80 ms à 16 kHz (une trame audio du protocole satellite)
CONTEXTE = 160 * 3    # 480 échantillons de contexte demandés par le modèle de mel
FENETRE_MEL = 76      # trames de mel dans une fenêtre d'embedding
PAS = 8               # pas entre deux fenêtres d'embedding
TRAMES_FEATURES = 16  # trames d'embedding lues par le modèle du mot d'appel


class Caracteristiques:
    """Portage fidèle de openwakeword.utils.AudioFeatures (mode ONNX)."""

    def __init__(self, mel_path, embedding_path):
        self.mel = ort.InferenceSession(str(mel_path), providers=["CPUExecutionProvider"])
        self.embedding = ort.InferenceSession(str(embedding_path), providers=["CPUExecutionProvider"])
        self.raw = np.zeros(0, dtype=np.int16)
        self.mel_buffer = np.ones((76, 32), dtype=np.float32)
        self.mel_max = 10 * 97
        self.accumulated = 0
        self.features = np.zeros((120, 96), dtype=np.float32)

    def _melspectrogram(self, x):
        x = np.asarray(x, dtype=np.int16).astype(np.float32)[None, :]
        spec = np.squeeze(self.mel.run(None, {"input": x})[0])
        return spec / 10.0 + 2.0

    def _streaming_mel(self, n_samples):
        fenetre = self.raw[-n_samples - CONTEXTE:]
        ajout = self._melspectrogram(fenetre)
        self.mel_buffer = np.vstack((self.mel_buffer, ajout))
        if self.mel_buffer.shape[0] > self.mel_max:
            self.mel_buffer = self.mel_buffer[-self.mel_max:, :]
        return ajout

    def _embedding(self, batch):
        return self.embedding.run(None, {"input_1": batch.astype(np.float32)})[0].squeeze()

    def push(self, pcm):
        """Ajoute un bloc de 1280 échantillons et rend (trames de mel, embedding, prêt)."""
        self.raw = np.concatenate((self.raw, pcm))[-self.mel_max * 160:]
        self.accumulated += len(pcm)
        if self.accumulated % CHUNK != 0:
            return None
        avant = self.mel_buffer.shape[0]
        mel_ajoute = self._streaming_mel(self.accumulated)
        embeddings = []
        for i in range(self.accumulated // CHUNK - 1, -1, -1):
            ndx = -PAS * i
            ndx = ndx if ndx != 0 else len(self.mel_buffer)
            fenetre = self.mel_buffer[-FENETRE_MEL + ndx:ndx].astype(np.float32)[None, :, :, None]
            if fenetre.shape[1] == FENETRE_MEL:
                embeddings.append(self._embedding(fenetre))
        self.accumulated = 0
        self.features = np.vstack((self.features, np.array(embeddings)))
        if self.features.shape[0] > 120:
            self.features = self.features[-120:, :]
        return mel_ajoute, np.array(embeddings)

    def dernieres_features(self):
        return self.features[-TRAMES_FEATURES:, :][None, :, :].astype(np.float32)


def lire_wav(chemin):
    with wave.open(str(chemin), "rb") as fichier:
        assert fichier.getnchannels() == 1, "mono attendu"
        assert fichier.getframerate() == 16000, "16 kHz attendu"
        assert fichier.getsampwidth() == 2, "16 bits attendu"
        return np.frombuffer(fichier.readframes(fichier.getnframes()), dtype="<i2")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--wav", required=True)
    parser.add_argument("--sortie", required=True)
    args = parser.parse_args()

    sortie = Path(args.sortie)
    sortie.mkdir(parents=True, exist_ok=True)

    caracteristiques = Caracteristiques(MODELES / "melspectrogram.onnx", MODELES / "embedding_model.onnx")
    mot = ort.InferenceSession(str(MODELES / "cornelius.onnx"), providers=["CPUExecutionProvider"])
    entree = mot.get_inputs()[0].name

    audio = lire_wav(args.wav)
    mel_lignes, embedding_lignes, scores = [], [], []
    for bloc, debut in enumerate(range(0, len(audio) - CHUNK + 1, CHUNK)):
        resultat = caracteristiques.push(audio[debut:debut + CHUNK])
        if resultat is None:
            continue
        mel, embeddings = resultat
        for ligne in mel:
            mel_lignes.append(" ".join(f"{v:.8f}" for v in ligne))
        for vecteur in embeddings:
            embedding_lignes.append(" ".join(f"{v:.8f}" for v in vecteur))
        entree_mot = caracteristiques.dernieres_features()
        score = float(np.ravel(mot.run(None, {entree: entree_mot})[0])[0])
        # openWakeWord rend 0 pendant les cinq premiers blocs, le temps que ses tampons se remplissent.
        if bloc < 5:
            score = 0.0
        scores.append(f"{score:.8f}")

    (sortie / "mel.txt").write_text("\n".join(mel_lignes) + "\n", encoding="utf-8")
    (sortie / "embedding.txt").write_text("\n".join(embedding_lignes) + "\n", encoding="utf-8")
    (sortie / "score.txt").write_text("\n".join(scores) + "\n", encoding="utf-8")
    (sortie / "entete.txt").write_text(
        f"blocs {len(scores)}\nmel_par_bloc {len(mel_lignes) // max(1, len(scores))}\n"
        f"embedding_dim {len(embedding_lignes[0].split()) if embedding_lignes else 0}\n",
        encoding="utf-8",
    )
    print(f"{len(scores)} blocs, {len(mel_lignes)} trames de mel")
    print("score maximum : " + (max(scores) if scores else "aucun"))


if __name__ == "__main__":
    main()

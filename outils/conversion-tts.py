"""Convertit le WAV produit par la voix Windows en 16 kHz mono 16 bits.

Sert seulement à fabriquer un fichier de test : la synthèse vocale de Windows
peut produire d'autres fréquences ou deux canaux selon la machine.
"""

import wave
from pathlib import Path

import numpy as np

DOSSIER = Path(__file__).resolve().parent / "audio"
SR_CIBLE = 16000


def lire(chemin):
    with wave.open(str(chemin), "rb") as f:
        canaux = f.getnchannels()
        largeur = f.getsampwidth()
        sr = f.getframerate()
        brut = f.readframes(f.getnframes())
    if largeur != 2:
        raise SystemExit(f"Largeur d'échantillon non gérée : {largeur * 8} bits")
    donnees = np.frombuffer(brut, dtype="<i2").astype(np.float32)
    if canaux > 1:
        donnees = donnees.reshape(-1, canaux).mean(axis=1)
    return donnees, sr


def main():
    source = DOSSIER / "tts-raw.wav"
    cible = DOSSIER / "tts-cornelius.wav"
    donnees, sr = lire(source)
    if sr != SR_CIBLE:
        duree = len(donnees) / sr
        positions = np.linspace(0, len(donnees) - 1, int(duree * SR_CIBLE))
        donnees = np.interp(positions, np.arange(len(donnees)), donnees)
    pcm = np.clip(donnees, -32768, 32767).astype("<i2")
    with wave.open(str(cible), "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SR_CIBLE)
        f.writeframes(pcm.tobytes())
    print(f"{cible} : {sr} Hz -> {SR_CIBLE} Hz, {len(pcm) / SR_CIBLE:.2f} s")


if __name__ == "__main__":
    main()

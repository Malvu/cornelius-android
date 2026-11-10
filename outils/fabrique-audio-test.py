"""Fabrique un WAV de test varié (16 kHz, mono, 16 bits) pour comparer Python et Kotlin.

Le contenu n'a pas besoin de contenir « Cornelius » : il sert à vérifier que le
portage Kotlin calcule exactement les mêmes nombres que la référence Python.
On y met donc de tout : silence, sinus, bruit, impulsions, de quoi faire varier
les valeurs du mel et des embeddings.
"""

import wave
from pathlib import Path

import numpy as np

SORTIE = Path(__file__).resolve().parent / "audio" / "test-pipeline.wav"
DUREE = 20.0
SR = 16000


def main():
    rng = np.random.default_rng(20261101)
    n = int(DUREE * SR)
    t = np.arange(n) / SR
    signal = np.zeros(n, dtype=np.float32)

    # Une seconde de silence, puis des sinusoïdes à des hauteurs différentes.
    for debut, duree, frequence, amplitude in [
        (1.0, 1.5, 220.0, 0.25),
        (3.0, 1.0, 440.0, 0.35),
        (4.5, 0.7, 880.0, 0.15),
        (6.0, 2.0, 150.0, 0.30),
    ]:
        a, b = int(debut * SR), int((debut + duree) * SR)
        signal[a:b] += amplitude * np.sin(2 * np.pi * frequence * t[a:b])

    # Du bruit doux, puis des impulsions.
    a, b = int(9.0 * SR), int(11.0 * SR)
    signal[a:b] += 0.05 * rng.standard_normal(b - a).astype(np.float32)
    for instant in (12.0, 12.5, 13.2):
        i = int(instant * SR)
        signal[i:i + 160] += 0.6 * np.exp(-np.arange(160) / 40.0).astype(np.float32)

    # Un accord de trois notes, puis un retour au silence.
    a, b = int(14.0 * SR), int(17.0 * SR)
    for frequence in (196.0, 246.94, 293.66):
        signal[a:b] += 0.12 * np.sin(2 * np.pi * frequence * t[a:b])

    # Une « voix » grossière : porteuse modulée, pour ressembler à du signal parlé.
    a, b = int(17.0 * SR), int(19.5 * SR)
    porteuse = np.sin(2 * np.pi * 130.0 * t[a:b])
    modulation = 0.5 + 0.5 * np.sin(2 * np.pi * 3.5 * t[a:b])
    signal[a:b] += 0.22 * porteuse * modulation

    signal = np.clip(signal, -1.0, 1.0)
    pcm = (signal * 32767.0).astype("<i2")

    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(SORTIE), "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SR)
        f.writeframes(pcm.tobytes())
    print(f"{SORTIE} : {len(pcm)} échantillons, {len(pcm) / SR:.1f} s")


if __name__ == "__main__":
    main()

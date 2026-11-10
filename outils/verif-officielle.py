"""Compare notre référence Python à openWakeWord officiel.

C'est la preuve que le pipeline décrit dans reference.py est bien celui du
serveur Cornelius : si les scores coïncident, le portage Kotlin peut être
vérifié contre cette référence.
"""

import importlib.metadata
import wave
from pathlib import Path

import numpy as np
from openwakeword.model import Model

RACINE = Path(__file__).resolve().parent.parent
MODELES = RACINE / "app" / "src" / "main" / "assets" / "models"


def lire_wav(chemin):
    with wave.open(str(chemin), "rb") as f:
        return np.frombuffer(f.readframes(f.getnframes()), dtype="<i2")


def main():
    officiel = Model(
        wakeword_models=[str(MODELES / "cornelius.onnx")],
        inference_framework="onnx",
        melspec_model_path=str(MODELES / "melspectrogram.onnx"),
        embedding_model_path=str(MODELES / "embedding_model.onnx"),
    )
    print("openWakeWord " + importlib.metadata.version("openwakeword"))

    for nom in ("test-pipeline", "tts-cornelius"):
        audio = lire_wav(RACINE / "outils" / "audio" / f"{nom}.wav")
        officiel.reset()
        scores = []
        for debut in range(0, len(audio) - 1280 + 1, 1280):
            prediction = officiel.predict(audio[debut:debut + 1280])
            scores.append(float(next(iter(prediction.values()))))
        reference = np.loadtxt(RACINE / "outils" / "vecteurs" / _dossier(nom) / "score.txt")
        n = min(len(scores), len(reference))
        ecart = float(np.max(np.abs(np.array(scores[:n]) - reference[:n])))
        print(f"{nom} : {n} blocs, écart maximum {ecart:.3e}, "
              f"score max officiel {max(scores):.6f}, référence {float(reference.max()):.6f}")


def _dossier(nom):
    return "pipeline" if nom == "test-pipeline" else "tts"


if __name__ == "__main__":
    main()

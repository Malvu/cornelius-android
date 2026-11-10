"""Montre où se situe l'écart entre la référence et openWakeWord officiel."""

import wave
from pathlib import Path

import numpy as np
from openwakeword.model import Model

RACINE = Path(__file__).resolve().parent.parent
MODELES = RACINE / "app" / "src" / "main" / "assets" / "models"


def main():
    officiel = Model(
        wakeword_models=[str(MODELES / "cornelius.onnx")],
        inference_framework="onnx",
        melspec_model_path=str(MODELES / "melspectrogram.onnx"),
        embedding_model_path=str(MODELES / "embedding_model.onnx"),
    )
    with wave.open(str(RACINE / "outils" / "audio" / "test-pipeline.wav"), "rb") as f:
        audio = np.frombuffer(f.readframes(f.getnframes()), dtype="<i2")
    officiel.reset()
    scores = []
    for debut in range(0, len(audio) - 1280 + 1, 1280):
        scores.append(float(next(iter(officiel.predict(audio[debut:debut + 1280]).values()))))
    reference = np.loadtxt(RACINE / "outils" / "vecteurs" / "pipeline" / "score.txt")
    ecarts = np.abs(np.array(scores) - reference)
    for debut in (0, 5, 20, 60, 120, 121, 130, 200, 249):
        print(f"bloc {debut:3d} : officiel {scores[debut]:.8f}  référence {reference[debut]:.8f}  écart {ecarts[debut]:.3e}")
    print(f"écart maximum après le bloc 130 : {ecarts[130:].max():.3e}")


if __name__ == "__main__":
    main()

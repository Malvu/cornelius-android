"""Vérifie la forme exacte des sorties ONNX utilisées par l'application."""

from pathlib import Path

import numpy as np
import onnxruntime as ort

RACINE = Path(__file__).resolve().parent.parent
MODELES = RACINE / "app" / "src" / "main" / "assets" / "models"

mel = ort.InferenceSession(str(MODELES / "melspectrogram.onnx"), providers=["CPUExecutionProvider"])
embedding = ort.InferenceSession(str(MODELES / "embedding_model.onnx"), providers=["CPUExecutionProvider"])
mot = ort.InferenceSession(str(MODELES / "cornelius.onnx"), providers=["CPUExecutionProvider"])

for n in (1280, 1760, 2560):
    entree = np.zeros((1, n), dtype=np.float32)
    sortie = mel.run(None, {"input": entree})[0]
    print(f"mel {n} échantillons -> forme {sortie.shape}, dtype {sortie.dtype}")

sortie = embedding.run(None, {"input_1": np.zeros((1, 76, 32, 1), dtype=np.float32)})[0]
print(f"embedding [1,76,32,1] -> forme {sortie.shape}, dtype {sortie.dtype}")

sortie = mot.run(None, {mot.get_inputs()[0].name: np.zeros((1, 16, 96), dtype=np.float32)})[0]
print(f"mot [1,16,96] -> forme {sortie.shape}, dtype {sortie.dtype}")

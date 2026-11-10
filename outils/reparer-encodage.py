"""Répare un fichier UTF-8 qui a été relu en Windows-1252 puis réécrit.

Utilisé une seule fois pour Moteur.kt, corrompu par un passage PowerShell.
Le fichier actuel contient « Ã© » là où il faut « é » : on refait le trajet
texte -> octets Windows-1252 -> texte UTF-8.
"""

import sys
from pathlib import Path

CIBLE = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "java" / "com" / "cornelius" / "satellite" / "Moteur.kt"

avant = CIBLE.read_text(encoding="utf-8-sig")
apres = avant.encode("cp1252", errors="strict").decode("utf-8", errors="strict")
CIBLE.write_text(apres, encoding="utf-8", newline="\n")
print(f"{CIBLE.name} : {len(avant)} -> {len(apres)} caractères")
for ligne in apres.splitlines():
    if "entendu" in ligne or "Cornelius »" in ligne:
        print("  " + ligne.strip()[:110])

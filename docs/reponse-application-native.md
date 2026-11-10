# Réponse courte — l'APK

Version condensée, à poster telle quelle.

---

L'APK existe : `Cornelius-satellite.apk`, 83 Mo, Android 8 et plus, Kotlin et ONNX
Runtime, signé, installé et essayé sur un Galaxy S23.

Il se fait passer pour un satellite de Cornelius : **aucune modification du serveur**. On
l'installe, on l'associe depuis l'administration, et il écoute — soit en calculant
« Cornelius » sur le téléphone sans rien envoyer, soit en transmettant le son en continu
comme une carte ESP32.

Vérifié sur le téléphone : association, connexion, conversation complète, annulation
d'écho active, 100 trames de 2 560 octets en 8 secondes en mode direct, zéro octet en
veille en mode économie.

Deux limites : voix seule, sans cartes ; et le modèle du mot d'appel reste à confirmer
sur une vraie voix.

Côté distribution : pas de pièce jointe GitHub (25 Mo maximum, et le format est refusé),
il faut une Release. L'APK part avec ses licences — modèle en CC BY-NC-SA 4.0, donc usage
non commercial.

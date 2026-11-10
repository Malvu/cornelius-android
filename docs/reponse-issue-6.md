# Suite pour l'issue #6 de Cornelius

Contexte : https://github.com/lfpoulain/Cornelius/issues/6 — proposition d'architecture
distribuée serveur/perception, fermée le 23 octobre 2026 par l'auteur (« déjà prévu dans
la roadmap »). Un commentaire y a déjà été posté le 1er novembre avec le lien vers l'APK.

Ce texte est la suite : il précise où l'application se situe par rapport à la proposition,
et ce qui reste ouvert.

---

Merci pour ce fil, il tombe bien — et il mérite une précision, parce qu'elle change ce
qu'il reste à faire.

La partie **vidéo** de la proposition existe déjà : c'est la caméra du navigateur, dont
les images partent vers le serveur sur le réseau local. Ce qui manquait, c'était un client
**audio** sur le téléphone, pour parler à Cornelius depuis une autre pièce sans passer par
un onglet.

Et pour ça, il n'y a rien à ajouter au serveur : le protocole des satellites fait déjà le
travail — association par jeton, PCM 16 kHz dans les deux sens, une seule conversation à
la fois dans la maison. Le téléphone se déclare comme un satellite ordinaire, exactement
comme une carte ESP32.

C'est ce que fait l'APK dont je parlais plus haut : Kotlin et ONNX Runtime, le mot
d'appel calculé sur le téléphone, aucun changement côté serveur. Il n'affiche rien : ni
cartes, ni caméra, ni images. C'est un interphone, pas un miroir.

Reste donc l'autre moitié de la proposition : plusieurs points de capture **vidéo**, et
leur arbitrage. Là, tout est à faire.

Application personnelle, non officielle, sans lien avec vous ; le modèle du mot d'appel
reste en CC BY-NC-SA 4.0.

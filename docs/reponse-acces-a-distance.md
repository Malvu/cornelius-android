# Texte à poster — l'accès à distance

Suite du commentaire de l'issue #6, à poster tel quel.

---

Un point qui manquait à mon message précédent : le téléphone fonctionne aussi **hors de
la maison**, et cela ne demande aucune modification de Cornelius.

Le principe tient en une phrase : Cornelius refuse les adresses publiques, alors on ne lui
en présente pas. Le téléphone rejoint la maison par un tunnel WireGuard et arrive avec une
adresse privée, `10.6.0.3` chez moi. Le serveur voit un appareil du réseau local, avec le
même jeton, le même certificat et la même règle « réseau local seulement ». Rien n'a été
changé dans Cornelius, pas une ligne.

Côté maison : un serveur WireGuard dans le routeur quand il en propose un, sinon sur
l'ordinateur de Cornelius ; un nom DuckDNS ou asuscomm pour l'adresse qui change ; et un
seul port UDP ouvert, 51820. Pas de port 3443, pas de DMZ.

Côté téléphone : le profil WireGuard, avec deux restrictions qui comptent. Les adresses
autorisées limitées au réseau de Cornelius (`192.168.50.0/24`), sinon toute la 4G passe par
la maison. Et l'application Cornelius ajoutée à la liste des applications du tunnel, sinon
elle sort en direct et échoue hors du Wi-Fi.

Vérifié aujourd'hui, Wi-Fi coupé, en 4G : ouverture du miroir en 186 ms, puis une
conversation complète. Le journal de Cornelius montre « Satellite connecté », et la
connexion vient de `10.6.0.3`, donc du tunnel. L'adresse dans l'application reste
`192.168.50.3:3443` : le certificat ne contient que l'adresse du réseau local, c'est le
tunnel qui porte les paquets.

Quatre pièges m'ont coûté du temps, et mériteraient une page de documentation : une règle
de redirection affichée active mais désactivée en réalité ; deux routeurs empilés, où la
redirection se pose sur celui qui reçoit l'adresse publique ; un profil WireGuard limité à
une seule application, qui n'était pas Cornelius ; et l'adresse publique qui change sans
nom pour la suivre.

Deux ajouts aideraient, sans toucher au serveur : un bouton qui teste l'accès et dit ce qui
bloque, et l'export du profil WireGuard en QR code depuis les réglages.

Application personnelle, non officielle, sans lien avec vous ; modèle du mot d'appel en
CC BY-NC-SA 4.0, usage familial et non commercial.

# Publier : le dépôt, le ticket, la demande de fusion

Les trois étapes sont prêtes. Il reste à remplacer `Malvu` par ton identifiant
GitHub, puis à pousser.

## 1. Le dépôt de l'application

Le dépôt est déjà initialisé et le premier envoi est fait (`main`, commit unique).

```sh
git remote add origin https://github.com/Malvu/cornelius-android.git
git push -u origin main
```

Puis, dans **Releases → Draft a new release** :

- étiquette `v1.0`, titre « Cornelius sur le téléphone, 1.0 » ;
- joindre **deux fichiers**, que GitHub refuse dans le dépôt : `Cornelius-satellite.apk`
  et `Cornelius-satellite-1.0.zip` ;
- coller dans la description l'empreinte de l'APK :
  `73bb67363c77e5673f2873e03d6d96747015a0aee931c0b4fbb21ca358996b4c`

Le dépôt ne contient pas l'APK : `*.apk` et `*.zip` sont ignorés. C'est voulu, un binaire
de 83 Mo dans l'historique Git ne se retire plus.

## 2. Le ticket, à ouvrir sur lfpoulain/Cornelius

**Titre**

```
Satellite vocal sur Android, et accès à distance sans modifier le serveur
```

**Corps**

Bonjour,

Je vous propose une page de documentation pour un satellite Android, et l'accès hors du
réseau local qui va avec. Aucun changement de code : l'application parle le protocole des
satellites, comme une carte ESP32.

**Ce que c'est**

Une application Android en Kotlin, avec ONNX Runtime, qui fait d'un téléphone un satellite
vocal : voix seule, service au premier plan, mot d'appel calculé sur le téléphone ou son en
continu comme une carte ESP32. Elle s'associe depuis l'administration comme n'importe quel
satellite, avec un jeton, et rien ne change côté serveur.

**Ce qui est vérifié**

- association et conversation complètes sur un Galaxy S23 (Android 13) ;
- mode direct : 100 trames de 2 560 octets en 8 secondes, au rythme attendu ;
- mode économie : zéro octet tant que « Cornelius » n'a pas été entendu ;
- en 4G, Wi-Fi coupé : ouverture du miroir en 186 ms, conversation complète, connexion vue
  par Cornelius en `10.6.0.3`.

**L'accès à distance**

Cornelius refuse les adresses publiques, alors on ne lui en présente pas. Un tunnel
WireGuard ramène le téléphone à la maison avec une adresse privée. Un seul port UDP ouvert,
pas de 3443, pas de DMZ, et la règle « réseau local seulement » reste intacte.

**Les limites, dites franchement**

- voix seule, pas de cartes ni d'images ;
- le modèle du mot d'appel reste à confirmer sur une vraie voix ;
- un jeton par téléphone ;
- le modèle du mot d'appel est en CC BY-NC-SA 4.0 : usage familial, non commercial.
  L'application est personnelle et non officielle.

**La proposition**

Une page `docs/fonctionnalites/satellite-android.md`, un lien dans l'index de la
documentation, une entrée au CHANGELOG. La demande de fusion suit, si vous préférez la
relire après le ticket.

L'application et son APK sont ici : `https://github.com/Malvu/cornelius-android`.

## 3. La demande de fusion, sur le même dépôt

Une seule branche, un seul sujet : la documentation. Pas de code, pas de binaire.

**Titre**

```
Documentation : le satellite Android et l'accès à distance
```

**Corps**

Ajoute la page [Le satellite Android](docs/fonctionnalites/satellite-android.md), un lien
dans l'index, une entrée au CHANGELOG, et un renvoi depuis la page des satellites audio.

**Ce qui change pour l'utilisateur**

La documentation explique comment installer et associer l'application, les deux modes
d'écoute, et l'accès hors du réseau local par un tunnel WireGuard, avec les quatre pièges
rencontrés (une règle de redirection inactive, deux routeurs empilés, un profil limité à
une autre application, l'adresse publique qui change).

**Ce qui n'est pas touché**

Aucun code, aucun binaire, aucune dépendance. Le serveur ne bouge pas, et sa règle
« réseau local seulement » reste telle quelle.

**Comment c'est vérifié**

Les chiffres de la page viennent d'essais réels : association, conversation, 186 ms en 4G
Wi-Fi coupé, connexion vue en `10.6.0.3` dans le journal. Les liens internes sont relatifs
et vérifiés. Le contenu ne fait qu'ajouter des fichiers Markdown.

## Ordre conseillé

1. Pousser l'application et créer la Release, pour avoir un lien à donner.
2. Ouvrir le ticket, attendre l'avis de l'auteur.
3. Envoyer la demande de fusion, en la liant au ticket.

Le dépôt Cornelius local contient déjà la branche `docs/satellite-android` avec son commit.
Il ne reste qu'à la pousser sur un fork :

```sh
git remote add fork https://github.com/Malvu/Cornelius.git
git push -u fork docs/satellite-android
```

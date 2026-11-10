Bonjour,

Je vous propose une page de documentation pour un satellite Android, et l'accès hors du réseau local qui va avec. Aucun changement de code : l'application parle le protocole des satellites, comme une carte ESP32.

## Ce que c'est

Une application Android en Kotlin, avec ONNX Runtime, qui fait d'un téléphone un satellite vocal : voix seule, service au premier plan, mot d'appel calculé sur le téléphone ou son en continu comme une carte ESP32. Elle s'associe depuis l'administration comme n'importe quel satellite, avec un jeton, et rien ne change côté serveur.

## Ce qui est vérifié

- association et conversation complètes sur un Galaxy S23 (Android 13) ;
- mode direct : 100 trames de 2 560 octets en 8 secondes, au rythme attendu ;
- mode économie : zéro octet tant que « Cornelius » n'a pas été entendu ;
- en 4G, Wi-Fi coupé : ouverture du miroir en 186 ms, conversation complète, connexion vue par Cornelius en `10.6.0.3`.

## L'accès à distance

Cornelius refuse les adresses publiques, alors on ne lui en présente pas. Un tunnel WireGuard ramène le téléphone à la maison avec une adresse privée. Un seul port UDP ouvert, pas de 3443, pas de DMZ, et la règle « réseau local seulement » reste intacte.

## Les limites, dites franchement

- voix seule, pas de cartes ni d'images ;
- le modèle du mot d'appel reste à confirmer sur une vraie voix ;
- un jeton par téléphone ;
- le modèle du mot d'appel est en CC BY-NC-SA 4.0 : usage familial, non commercial. L'application est personnelle et non officielle.

## La proposition

Une page `docs/fonctionnalites/satellite-android.md`, un lien dans l'index de la documentation, une entrée au CHANGELOG. La demande de fusion suit, si vous préférez la relire après le ticket.

L'application et son APK sont ici : https://github.com/Malvu/cornelius-android

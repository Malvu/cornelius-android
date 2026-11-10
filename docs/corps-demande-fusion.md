Suite du ticket #43.

Ajoute la page [Le satellite Android](docs/fonctionnalites/satellite-android.md), un lien dans l'index, une entrée au CHANGELOG, et un renvoi depuis la page des satellites audio.

## Ce qui change pour l'utilisateur

La documentation explique comment installer et associer l'application, les deux modes d'écoute, et l'accès hors du réseau local par un tunnel WireGuard, avec les quatre pièges rencontrés : une règle de redirection inactive, deux routeurs empilés, un profil limité à une autre application, l'adresse publique qui change.

## Ce qui n'est pas touché

Aucun code, aucun binaire, aucune dépendance. Le serveur ne bouge pas, et sa règle « réseau local seulement » reste telle quelle.

## Comment c'est vérifié

Les chiffres de la page viennent d'essais réels : association, conversation, 186 ms en 4G Wi-Fi coupé, connexion vue en `10.6.0.3` dans le journal. Les liens internes sont relatifs et vérifiés. Le changement n'ajoute que des fichiers Markdown.

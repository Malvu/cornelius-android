# Accès à distance, hors du réseau local

Le téléphone sert de satellite à la maison, mais aussi dehors. Cette page explique
comment, ce qu'il faut côté routeur, et les pièges à éviter.

## Ce que Cornelius accepte

Cornelius refuse toute adresse publique : c'est la règle « Accès réservé au réseau
local » (`docs/utilisation/reseau-local.md` du dépôt Cornelius). Il n'y a donc rien à
ouvrir sur lui, ni le port 3443, ni un nom de domaine, ni une redirection vers sa page.

## Le principe retenu

On ne présente pas d'adresse publique à Cornelius. Le téléphone rejoint la maison par un
tunnel WireGuard et arrive avec une adresse **privée**, `10.6.0.3` dans mon installation.
Le serveur voit un appareil du réseau local, avec le même jeton d'association, le même
certificat et la même règle. **Aucune ligne de Cornelius n'est modifiée.**

## Ce qu'il faut côté maison

| Élément | Détail |
|---|---|
| Un serveur WireGuard | dans le routeur quand il en propose un (Asus, OpenWrt, Fritz), sinon sur l'ordinateur de Cornelius, sinon sur une box qui sait le faire |
| Une adresse stable | l'adresse publique change parfois : un nom DuckDNS ou asuscomm évite de retoucher le profil |
| Un seul port ouvert | UDP 51820 par défaut, et rien d'autre |
| Le réseau du tunnel | `10.6.0.0/24`, le téléphone en `10.6.0.3` |

## Ce qu'il faut sur le téléphone

1. L'application **WireGuard** officielle, avec un profil par téléphone.
2. Dans ce profil, **Adresses IP autorisées** limitées au réseau de Cornelius :
   `192.168.50.0/24`. Sans cette limite, tout le trafic du téléphone passe par la maison,
   et la 4G vous coûte cher.
3. Dans **Applications**, le choix « Inclure uniquement » doit contenir **Cornelius**.
   Un profil qui ne contient qu'une autre application laisse l'application sortir en
   direct, et elle échoue hors du Wi-Fi.
4. Dans l'application Cornelius, le mode **économie** : le téléphone reconnaît le mot
   d'appel sans réseau et n'envoie du son qu'ensuite.

## L'adresse dans l'application ne change pas

Le certificat de Cornelius ne contient que son adresse sur le réseau local
(`192.168.50.3`, `nuc`, `nuc.local`). On garde donc `192.168.50.3:3443` comme adresse,
avec ou sans tunnel : **c'est le tunnel qui porte les paquets, pas le nom**. Rien à
réassocier, rien à réinstaller.

## Vérifier que le trafic passe bien par le tunnel

1. Couper le Wi-Fi du téléphone, lancer l'écoute, dire « Cornelius ».
2. Dans le **Journal** de Cornelius : « Satellite connecté », puis la conversation.
3. Sur le serveur, `ss -tn state established '( sport = :3443 )'` montre l'adresse du
   client. `10.6.0.3` prouve le passage par le tunnel. `192.168.50.x` signifie que le
   téléphone est en direct, donc que le tunnel ne sert pas.

Mesures du 10 novembre 2026, en 4G, Wi-Fi coupé : ouverture du miroir en **186 ms**,
puis conversation complète avec la connexion vue en `10.6.0.3`.

## Les quatre pièges rencontrés

1. **Une règle affichée active mais désactivée.** L'interrupteur de la box paraissait
   vert, la règle était en réalité `enable: false`. Toujours lire l'état réel, pas la
   couleur.
2. **Deux routeurs empilés.** La redirection se pose sur celui qui reçoit l'adresse
   publique, pas sur celui qui porte Cornelius. Un `tracert` le montre tout de suite.
3. **Un profil WireGuard limité à une seule application**, et ce n'était pas Cornelius.
   Le satellite échouait partout, y compris à la maison, sans message explicite.
4. **L'adresse publique qui change.** Sans nom tenu à jour, il faut retoucher le profil
   et l'adresse de terminaison.

## Ce que cela ne change pas

- Aucun port nouveau n'est ouvert sur Cornelius.
- Aucune adresse publique n'est acceptée par le serveur.
- Même certificat, même jeton, même association.
- Une seule conversation à la fois dans la maison.

## Pistes pour l'application

Deux ajouts aideraient, sans toucher au serveur :

- un bouton **Vérifier l'accès à distance** qui teste l'adresse publique et le port, puis
  affiche un verdict clair ;
- l'**export du profil WireGuard en QR code** depuis les réglages, pour éviter le
  transport d'un fichier de configuration.

## Voir aussi

- [Accès depuis le réseau local](https://github.com/lfpoulain/Cornelius/blob/main/docs/utilisation/reseau-local.md)
- [Les satellites audio](https://github.com/lfpoulain/Cornelius/blob/main/docs/fonctionnalites/satellites.md)
- [Dépannage](https://github.com/lfpoulain/Cornelius/blob/main/docs/utilisation/depannage.md)

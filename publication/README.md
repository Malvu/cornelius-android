# Cornelius sur le téléphone

Application Android qui fait d'un téléphone un **satellite vocal de Cornelius** : on dit
« Cornelius », on pose sa question, Cornelius répond par le haut-parleur.

Elle ne remplace pas Cornelius : elle s'y ajoute. Rien à installer sur le serveur.

Version 1.0 — 1er novembre 2026.

## Ce qu'il faut

- Un téléphone sous Android 8 ou plus récent.
- Cornelius allumé sur le réseau local, accessible par `https://<adresse>:3443`.
- Le mot de passe de l'administration de Cornelius ne doit plus être celui de départ
  (`cornelius`), sinon aucun appareil du réseau n'est accepté.

## Installer

1. Copier `Cornelius-satellite.apk` sur le téléphone.
2. Ouvrir le fichier, accepter « Installer une application inconnue ».
3. Lancer **Cornelius**.

L'application demande le micro : il lui sert à écouter. Sans lui, rien ne fonctionne.

## Associer le téléphone

1. Dans l'application, vérifier l'adresse de Cornelius, puis **Démarrer l'écoute**.
2. L'écran indique que le téléphone attend.
3. Dans Cornelius : **Administration → Satellites audio → Nouveaux satellites**. Le
   téléphone apparaît sous un nom du genre `cornelius-android-a1b2c3`.
4. Cliquer **Associer** et lui donner un nom : « Téléphone », « Cuisine »…

C'est fait une seule fois : le téléphone garde son jeton.

## Les deux modes

**Économie** (par défaut). Le téléphone reconnaît lui-même le mot « Cornelius », sans
réseau, et n'envoie du son qu'ensuite. Il garde deux secondes d'avance pour ne pas
couper le début de la phrase.

**Direct**. Le son part en continu vers Cornelius, qui cherche le mot d'appel. C'est ce
que font les satellites ESP32. À essayer si le mode économie rate trop souvent.

Le seuil de reconnaissance se règle dans l'application.

## Hors de la maison

Le téléphone fonctionne aussi en 4G, sans Wi-Fi. Il faut un tunnel WireGuard vers la
maison, et c'est le routeur qui le fournit, pas Cornelius : celui-ci n'accepte que les
adresses du réseau local, et un tunnel lui présente justement une adresse privée.

En pratique : un seul port UDP ouvert (51820), un nom pour suivre l'adresse publique, et
sur le téléphone un profil WireGuard limité au réseau de Cornelius et à l'application
Cornelius. L'adresse dans l'application ne change pas : `https://<adresse>:3443`.

Mesuré en 4G, Wi-Fi coupé : ouverture du miroir en 186 ms, conversation complète.

## Ce qui reste où

- **Sur le téléphone** : le calcul du mot d'appel en mode économie. Rien ne sort tant que
  « Cornelius » n'a pas été entendu.
- **Sur Cornelius** : le son de la conversation, la voix de Cornelius, la mémoire, la
  maison connectée. La clé OpenAI reste sur le serveur, jamais sur le téléphone.
- **Nulle part** : ni image, ni enregistrement. L'application n'écrit aucun son sur le
  disque et n'envoie rien en dehors du réseau local.

Pendant une conversation, le son passe par Cornelius puis chez OpenAI, comme avec le
miroir ou un satellite du commerce.

## Limites

- **Voix seule** : pas de cartes, pas d'images. C'est le protocole des satellites.
- **Une conversation à la fois** dans la maison : si le miroir parle déjà, le téléphone
  reçoit un refus poli.
- **Certificat** : Cornelius crée son propre certificat. L'application le retient au
  premier contact, et refuse de continuer s'il change. Il faut alors appuyer sur
  « Oublier le certificat » dans les réglages.
- **Batterie** : le mode économie calcule en continu, le mode direct envoie du son en
  continu. Les deux coûtent, différemment.

## Licences

Voir `LICENCES.md`. En résumé : l'application embarque des modèles qui interdisent tout
usage commercial. **Cet APK est fourni pour un usage familial, personnel, non commercial.**

Empreinte SHA-256 de `Cornelius-satellite.apk` :

```
73bb67363c77e5673f2873e03d6d96747015a0aee931c0b4fbb21ca358996b4c
```

## En cas de problème

- « L'application ne voit pas Cornelius » : vérifier l'adresse (port 3443), que le
  téléphone est sur le même réseau Wi-Fi, et que le mot de passe de l'administration a
  été changé.
- « Le certificat a changé » : c'est normal si l'adresse de l'ordinateur change.
  Réglages → Oublier le certificat.
- « Le mot n'est pas reconnu » : baisser le seuil, ou passer en mode direct.

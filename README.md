# Cornelius sur le téléphone — satellite Android

Application Android native qui fait d'un téléphone un **satellite vocal de Cornelius** :
on dit « Cornelius », on pose sa question, Cornelius répond par le haut-parleur.

Le téléphone parle à Cornelius par le protocole des satellites audio, le même que les
cartes ESP32 (`docs/developpement/satellites-protocole.md` du dépôt Cornelius). Rien à
changer sur le serveur.

## Ce que ça apporte par rapport à l'onglet du navigateur

| | Navigateur | Cette application |
|---|---|---|
| Écran éteint, application en arrière-plan | non | oui (service au premier plan) |
| Mot d'appel calculé sur le téléphone | non | oui, avec ONNX Runtime |
| Réseau quand personne ne parle | le son part en continu | rien ne part (mode économie) |
| Annulation d'écho | celle du navigateur | celle d'Android, annoncée à Cornelius |
| Son | compressé par WebRTC | PCM 16 kHz, comme un satellite |

## Ce qu'il faut

- Un téléphone sous Android 8 ou plus récent (testé pour être compilé avec Android 15).
- Cornelius allumé, accessible sur le réseau local (`https://<adresse>:3443`).
- Le mot de passe de l'administration de Cornelius ne doit plus être celui de départ
  (`cornelius`) : sinon aucun appareil du réseau n'est accepté.

## Installer

1. Copier `Cornelius-satellite.apk` sur le téléphone.
2. Ouvrir le fichier, accepter « Installer une application inconnue ».
3. Lancer **Cornelius**.

## Associer le téléphone

1. Dans l'application, vérifier l'adresse (`192.168.50.3:3443` par défaut), puis
   **Démarrer l'écoute** et accepter le micro.
2. L'écran affiche : « Ouvre l'administration de Cornelius, puis Satellites audio ».
3. Dans Cornelius : **Administration → Satellites audio → Nouveaux satellites**.
   Le téléphone apparaît sous le nom `cornelius-android-xxxxxx`.
4. Cliquer **Associer**, donner un nom (« Téléphone », « Cuisine »…).

Le téléphone enregistre son jeton, se connecte, et affiche « Connecté ».

## Les deux façons d'écouter

**Économie** (par défaut). Le téléphone calcule lui-même le mot d'appel avec les trois
modèles ONNX, et n'envoie du son à Cornelius qu'après avoir entendu « Cornelius ».
Il garde deux secondes de son d'avance : la phrase n'est pas coupée. Le serveur réentend
le mot dans ce qu'on lui envoie et ouvre la conversation, exactement comme avec un
satellite du commerce.
→ beaucoup moins de réseau et de batterie.

**Direct**. Le son part en continu vers Cornelius, qui cherche le mot d'appel. C'est le
comportement des satellites ESP32, et le mode à essayer si le mode économie rate trop
souvent.

Le seuil du mot d'appel (50 % par défaut) se règle dans l'application. Plus bas : plus
sensible, mais plus de fausses ouvertures.

## Hors du réseau local

Le téléphone fonctionne aussi en 4G, sans Wi-Fi, par un tunnel WireGuard qui le ramène à la
maison. Cornelius n'accepte que les adresses privées : le téléphone arrive donc avec
l'adresse du tunnel (`10.6.0.x`), et **rien ne change sur le serveur**.

Le principe, les réglages du routeur et les quatre pièges à éviter sont dans
[`docs/acces-a-distance.md`](docs/acces-a-distance.md). En résumé : un seul port UDP ouvert
(51820), un profil WireGuard limité au réseau de Cornelius (`192.168.50.0/24`), et
l'application Cornelius ajoutée aux applications du tunnel.

Mesuré le 10 novembre 2026, Wi-Fi coupé : ouverture du miroir en **186 ms**, conversation
complète, connexion vue par Cornelius en `10.6.0.3`.

## Vérifications faites

### Sur un vrai téléphone (Galaxy S23, Android 13), le 1er novembre 2026

Le téléphone a parlé à un **faux Cornelius** installé sur l'ordinateur, joint par
`adb reverse tcp:3443 tcp:3443` : le vrai serveur n'a pas été touché.

| Vérification | Résultat |
|---|---|
| Installation de l'APK, lancement, affichage de l'écran | oui, aucun plantage |
| Association (`/satellite/pair`) : identifiant, protocole, nom et jeton reçus | oui, appareil `cornelius-android-114831`, nom « Téléphone de test » |
| Connexion (`/satellite`), `hello` envoyé | oui, avec `"aec": true` : l'annulation d'écho du téléphone est active |
| `welcome`, `settings`, `listen` reçus et appliqués | oui, le nom du satellite s'affiche |
| Conversation : `wake`, `connecting`, `ready`, son, `closed` | oui, l'écran suit chaque étape |
| Son de Cornelius reçu et lu par le haut-parleur | oui, sans erreur de lecture |
| Mode direct : le micro part vers le serveur | oui, 100 trames de 2 560 octets en 8 s, au rythme attendu |
| Mode économie : rien ne part tant que le mot n'est pas entendu | oui, aucune trame envoyée |
| État remis à zéro après désinstallation des données | oui, adresse et jeton oubliés |

Captures dans `outils/captures/` : `ecran5.png` (conversation terminée), `ecran6.png`
(réglages et modes).

### Sur l'ordinateur

| Vérification | Résultat |
|---|---|
| Le calcul Kotlin du mot d'appel donne les mêmes nombres que la référence Python (mel, embeddings, scores) | écart maximal 1,9 × 10⁻⁶ sur le mel, 1,7 × 10⁻⁵ sur les embeddings, 9,3 × 10⁻⁸ sur les scores |
| Cette référence Python est bien celle d'openWakeWord 0.5.1, comme le serveur Cornelius | écart maximal 4,9 × 10⁻⁹ après l'amorçage |
| Association, `hello`, `welcome`, `listen`, trames de 2 560 octets, décodage du son | testé contre un faux Cornelius (WebSocket + TLS auto-signé) |
| Refus si le certificat change | testé : connexion refusée, empreinte affichée |
| APK compilé et signé | oui |

**Pas encore testé** : le vrai Cornelius, et la détection du mot d'appel par une vraie
voix. C'est la seule chose que je n'ai pas pu provoquer : le modèle ne se déclenche pas
sur les voix de synthèse dont je dispose. D'où le mode direct, qui ne dépend pas de lui.

## Limites connues

- **Certificat** : Cornelius s'auto-signe. L'application le retient au premier contact
  (empreinte SHA-256 de la clé publique). S'il change — par exemple quand l'adresse de
  l'ordinateur change — la connexion est refusée et il faut appuyer sur
  « Oublier le certificat ».
- **Voix seule** : comme un satellite ESP32, pas de cartes ni d'images sur le téléphone.
  Seule la voix revient.
- **Le modèle du mot d'appel** est celui fourni par Cornelius, entraîné sur des voix de
  synthèse françaises. Il peut rater une voix réelle. Dans ce cas : baisser le seuil,
  ou passer en mode direct.
- **Un jeton par téléphone** : deux téléphones associés = deux satellites distincts.
- **Batterie** : en mode économie, le calcul ONNX tourne en continu (environ 1,5 ms par
  bloc de 80 ms sur un ordinateur ; à mesurer sur le téléphone). En mode direct, c'est
  le réseau qui coûte.


## Pour diffuser l'application

Le dossier `publication/` contient ce qu'il faut donner avec l'APK :

| Fichier | Rôle |
|---|---|
| `Cornelius-satellite.apk` | l'application |
| `README.md` | mode d'emploi pour la personne qui l'installe |
| `LICENCES.md` | ce que contient l'APK, sous quelles licences, et ce que cela impose |
| `licences/` | les textes eux-mêmes : Apache 2.0, MIT d'ONNX Runtime, et la licence du modèle Cornelius |

`Cornelius-satellite-1.0.zip` (38 Mo) rassemble le tout en un fichier.

Ne pas séparer l'APK de ces fichiers : la licence du modèle du mot d'appel
(CC BY-NC-SA 4.0) impose de garder les attributions, et Apache 2.0 impose de fournir
une copie de son texte.
## Comment c'est fait

```
app/src/main/java/com/cornelius/satellite/
├── Moteur.kt                 le chef d'orchestre : micro, mot d'appel, protocole
├── Etat.kt                   ce que l'écran affiche
├── data/Reglages.kt          réglages et identifiant du téléphone
├── net/ConfianceCornelius.kt certificat auto-signé, empreinte retenue
├── net/Appairage.kt          wss://…/satellite/pair
├── net/Satellite.kt          wss://…/satellite (JSON + son binaire)
├── audio/Microphone.kt       AudioRecord 16 kHz + annulation d'écho
├── audio/HautParleur.kt      AudioTrack 16 kHz
├── wake/WakePipeline.kt      les trois modèles ONNX (pur Kotlin, testable sur PC)
├── wake/DetecteurMotAppel.kt seuil et délai anti-doublon
├── service/ServiceCornelius.kt  écoute en arrière-plan
└── ui/                       écran Compose (orbe, état, réglages)
```

Le module `verification` compile **les mêmes fichiers** (`wake/`, `net/`) en Kotlin/JVM,
pour les tester sans téléphone.

```
verification/                 vérifications sur ordinateur
outils/reference.py           référence Python du pipeline (openWakeWord 0.5.1)
outils/verif-officielle.py    compare cette référence à openWakeWord officiel
outils/fabrique-audio-test.py fabrique un WAV de test varié
outils/conversion-tts.py      convertit une phrase de synthèse en 16 kHz mono
outils/faux-cornelius.py      faux serveur pour essayer sur un vrai téléphone (adb reverse)
```

## Recompiler

Il faut le JDK 21, le SDK Android et Gradle 8.11. Sur ce poste, outils/installer-sdk-android.ps1 a installé le SDK dans D:\Android\sdk.

```powershell
# une fois : indiquer le SDK
# local.properties : sdk.dir=D\:\\Android\\sdk

gradle :app:assembleDebug        # APK de test, signé avec la clé de débogage
gradle :app:assembleRelease      # APK « release », signé ici aussi avec la clé de débogage
```

Vérifications :

```powershell
python outils/fabrique-audio-test.py
python outils/reference.py --wav outils/audio/test-pipeline.wav --sortie outils/vecteurs/pipeline
python outils/verif-officielle.py
gradle -p verification run                     # pipeline contre la référence
gradle -p verification run --args="protocole"  # dialogue avec un faux Cornelius
```

## Modèles et licences

| Fichier | Origine | Licence |
|---|---|---|
| `cornelius.onnx` | dépôt Cornelius (`models/cornelius.onnx`) | CC BY-NC-SA 4.0 |
| `melspectrogram.onnx` | openWakeWord 0.5.1 | Apache 2.0 |
| `embedding_model.onnx` | openWakeWord 0.5.1 | Apache 2.0 |
| ONNX Runtime Android | Microsoft | MIT |

L'application reste un usage familial, non commercial, comme Cornelius.

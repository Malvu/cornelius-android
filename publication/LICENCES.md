# Licences

Cette application n'est pas un logiciel libre de droits : elle assemble des éléments
soumis à plusieurs licences, dont une interdit tout usage commercial.

**En un mot : usage familial, personnel, non commercial. Redistribution possible en
gardant les textes ci-dessous et en citant Cornelius.**

## Ce que contient l'application

| Élément | Origine | Licence | Texte |
|---|---|---|---|
| `models/cornelius.onnx` (modèle du mot d'appel) | Cornelius, Les Frères Poulain, entraîné avec wakeword-generator | **CC BY-NC-SA 4.0** — pas d'usage commercial, partage à l'identique, attribution | [`licences/cornelius.onnx.LICENSE`](licences/cornelius.onnx.LICENSE) |
| `models/melspectrogram.onnx`, `models/embedding_model.onnx` (prétraitement) | openWakeWord, David Scripka | **Apache 2.0** | [`licences/apache-2.0.txt`](licences/apache-2.0.txt) |
| ONNX Runtime (`libonnxruntime.so`) | Microsoft | **MIT** | [`licences/mit-onnxruntime.txt`](licences/mit-onnxruntime.txt) |
| Interface et code (Kotlin, Jetpack Compose, AndroidX, OkHttp, Kotlin) | Pierre Grosjean, Google, Square, JetBrains | **Apache 2.0** | [`licences/apache-2.0.txt`](licences/apache-2.0.txt) |
| Protocole des satellites, documentation | Cornelius, Les Frères Poulain | PolyForm Noncommercial 1.0.0 | <https://github.com/lfpoulain/Cornelius/blob/main/LICENSE> |

## Le modèle du mot d'appel

C'est la pièce la plus contrainte. `licences/cornelius.onnx.LICENSE` est le fichier
d'origine, livré avec le modèle par ses auteurs. Il indique :

- la licence **CC BY-NC-SA 4.0** (<https://creativecommons.org/licenses/by-nc-sa/4.0/deed.fr>) ;
- les attributions des données d'entraînement, qu'il faut conserver ;
- que toute copie du modèle doit garder ce fichier, ou les mêmes attributions ;
- que l'usage commercial est exclu.

Empreinte du modèle livré ici, pour vérifier qu'il n'a pas été modifié :

```
9c8adc857126b786fa8db31ed82d1518181e549c08d51ca823e8bdd8942b29cd
```

## Ce que cela implique si vous partagez l'application

- Vous pouvez la donner, la copier, l'installer dans votre entourage : usage non
  commercial.
- Vous devez laisser ce fichier `LICENCES.md`, le dossier `licences/` et ce README
  avec l'APK.
- Vous ne pouvez pas la vendre, ni l'inclure dans une offre payante, ni la modifier puis
  la republier sans reprendre les mêmes conditions.
- Si vous modifiez l'application, la mention « modifié par … » est de rigueur, et les
  sources modifiées doivent être partagées sous la même licence.

## Le reste

Le nom « Cornelius », le miroir, le serveur et sa documentation appartiennent à leurs
auteurs (Les Frères Poulain) et restent sous **PolyForm Noncommercial License 1.0.0**.
Cette application n'est pas éditée par eux : c'est un client écrit à part, qui parle leur
protocole.

Les empreintes des deux modèles d'openWakeWord livrés :

```
melspectrogram.onnx   ba2b0e0f8b7b875369a2c89cb13360ff53bac436f2895cced9f479fa65eb176f
embedding_model.onnx  70d164290c1d095d1d4ee149bc5e00543250a7316b59f31d056cff7bd3075c1f
```

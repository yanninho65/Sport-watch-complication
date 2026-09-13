# Sports Complication (Galaxy Watch)

Complication Wear OS qui affiche le score d'un match en direct :
- **LONG_TEXT** → pour l'emplacement central du visage Zenith
  (ex. `PSG 2-1 OM · 64'`)
- **SMALL_IMAGE** → pour les complications cercle du Dashboard Samsung
  (logo de l'équipe, en couleur)

## État actuel (squelette)

Ce dépôt contient uniquement la partie **montre**. Le service
`ScoreComplicationService` répond aux deux types de complications, mais
les données viennent d'un cache statique en mémoire (`MatchScoreStore`),
pas encore relié au téléphone. Il n'y a donc pas encore de match réel
affiché — seulement l'aperçu `PSG 2-1 OM · 64'` visible dans le
sélecteur de complications de la montre.

**Prochaine étape** : une app téléphone qui laisse choisir un match dans
TheSportsDB, l'interroge périodiquement, et pousse les mises à jour vers
la montre via la Wear Data Layer API. Le `MatchScoreStore` sera alors
alimenté par un `WearableListenerService` côté montre.

## Compiler sans Android Studio

Ce projet est buildé via GitHub Actions, pas en local :

1. Pousse ce dépôt sur GitHub (branche `main`)
2. Le workflow `.github/workflows/build.yml` se déclenche automatiquement
   (ou lance-le manuellement depuis l'onglet **Actions** → **Build APK**
   → **Run workflow**)
3. Une fois le run terminé, ouvre le run → section **Artifacts** →
   télécharge `sports-complication-debug` (contient le fichier `.apk`)
4. Installe l'APK sur la montre (via ADB en Wi-Fi, ou une app comme
   *Wear Installer*)

## Structure du projet

```
sports-complication-watch/
├── build.gradle.kts              racine — déclare les plugins
├── settings.gradle.kts           racine — inclut le module :wear
├── wear/
│   ├── build.gradle.kts          dépendances du module montre
│   └── src/main/
│       ├── AndroidManifest.xml   déclare le service de complication
│       ├── kotlin/.../
│       │   ├── ScoreComplicationService.kt
│       │   └── MatchScore.kt     modèle de données + cache en mémoire
│       └── res/                  icône placeholder, strings
└── .github/workflows/build.yml   build CI (Java 17, Android SDK, Gradle 8.9)
```

## Tester la complication sur la montre

Une fois l'APK installé :
1. Sur le visage **Zenith**, assigne la complication "Score en direct" à
   l'emplacement central (type LONG_TEXT)
2. Sur le **Dashboard** Samsung, assigne-la à une des complications
   cercle (type SMALL_IMAGE)
3. Tu devrais voir l'aperçu `PSG 2-1 OM · 64'` et un logo placeholder —
   c'est normal tant que l'intégration téléphone n'est pas branchée

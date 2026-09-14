# Sports Complication (Galaxy Watch)

Complication Wear OS qui affiche le score d'un match en direct :
- **LONG_TEXT** → pour l'emplacement central du visage Zenith
  (ex. `PSG 2-1 OM · 64'`)
- **SMALL_IMAGE** → pour les complications cercle du Dashboard Samsung
  (logo de l'équipe, en couleur)

Deux modules dans ce repo :
- `wear/` — la complication elle-même (montre)
- `mobile/` — l'app téléphone pour choisir le match à suivre

## État actuel

**Montre (`wear/`)** : le service `ScoreComplicationService` répond aux
deux types de complications, mais les données viennent d'un cache
statique en mémoire (`MatchScoreStore`), pas encore relié au téléphone.
Il contient pour l'instant un match factice codé en dur (PSG 2-1 OM ·
64', logos rouge/bleu sans tint) pour valider le rendu réel sur la
montre avant que l'app téléphone existe.

**Téléphone (`mobile/`)** : recherche d'équipe par nom via TheSportsDB
(`searchteams.php`), puis liste des matchs récents/à venir de l'équipe
choisie (`eventslast.php` + `eventsnext.php`). Sélectionner un match
affiche pour l'instant un simple Toast — l'envoi réel vers la montre
n'est pas encore branché.

**Prochaine étape** : envoyer le match sélectionné vers la montre via
la Wear Data Layer API (`DataClient` côté téléphone,
`WearableListenerService` côté montre pour alimenter
`MatchScoreStore`), avec un polling périodique pour les mises à jour
de score.

## Compiler sans Android Studio

Ce projet est buildé via GitHub Actions, pas en local :

1. Pousse ce dépôt sur GitHub (branche `main`)
2. Le workflow `.github/workflows/build.yml` se déclenche automatiquement
   (ou lance-le manuellement depuis l'onglet **Actions** → **Build APK**
   → **Run workflow**)
3. Une fois le run terminé, ouvre le run → section **Artifacts** →
   télécharge `wear-debug` (complication) et/ou `mobile-debug` (app
   téléphone)
4. Installe l'APK sur l'appareil concerné (via ADB en Wi-Fi, ou une app
   comme GeminiMan WearOS Manager pour la montre)

## Structure du projet

```
sports-complication-watch/
├── build.gradle.kts              racine — déclare les plugins
├── settings.gradle.kts           racine — inclut :wear et :mobile
├── wear/
│   ├── build.gradle.kts          dépendances du module montre
│   └── src/main/
│       ├── AndroidManifest.xml   déclare le service de complication
│       ├── debug.keystore        clé de signature fixe (voir plus bas)
│       ├── kotlin/.../
│       │   ├── ScoreComplicationService.kt
│       │   └── MatchScore.kt     modèle de données + cache en mémoire
│       └── res/                  icônes (placeholder + test PSG/OM), strings
├── mobile/
│   ├── build.gradle.kts          dépendances du module téléphone
│   └── src/main/
│       ├── AndroidManifest.xml   déclare MainActivity
│       ├── kotlin/.../
│       │   ├── MainActivity.kt       recherche équipe → liste matchs
│       │   ├── SportsDbApi.kt        client TheSportsDB
│       │   ├── Models.kt             TeamResult / MatchResult
│       │   ├── TeamsAdapter.kt
│       │   └── MatchesAdapter.kt
│       └── res/layout/            activity_main, item_team, item_match
└── .github/workflows/build.yml   build CI (Java 17, Android SDK, Gradle 8.9)
```

## Pourquoi un keystore de debug fixe (`wear/debug.keystore`)

Sans ça, chaque run GitHub Actions génère un keystore de debug
aléatoire, donc chaque nouvel APK est signé différemment — impossible
d'installer une mise à jour par-dessus une version précédente
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Ce keystore committé (mot de
passe `android`, alias `androiddebugkey` — valeurs standard, sans enjeu
de sécurité pour un debug local) garantit une signature stable entre
les builds.

## Tester la complication sur la montre

Une fois l'APK `wear` installé :
1. Sur le visage **Zenith**, assigne la complication "Score en direct" à
   l'emplacement central (type LONG_TEXT)
2. Sur le **Dashboard** Samsung, assigne-la à une des complications
   cercle (type SMALL_IMAGE)
3. Tu devrais voir `PSG 2-1 OM · 64'` et le logo de test coloré — c'est
   normal tant que l'intégration téléphone n'est pas branchée

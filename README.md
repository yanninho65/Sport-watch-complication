# Sports Complication (Galaxy Watch)

Complication Wear OS qui affiche le score d'un match en direct :
- **LONG_TEXT** → pour l'emplacement central du visage Zenith
  (ex. `PSG 2-1 OM · 64'`)
- **SMALL_IMAGE** → pour les complications cercle du Dashboard Samsung
  (logo de l'équipe à domicile, en couleur)

Deux modules dans ce repo :
- `wear/` — la complication elle-même (montre)
- `mobile/` — l'app téléphone pour choisir le match à suivre

## État actuel

**Montre (`wear/`)** : `ScoreComplicationService` répond aux deux types
de complications. `MatchListenerService` reçoit les mises à jour du
téléphone (chemin `/match`), décode les logos reçus en Asset, met à
jour `MatchScoreStore`, et force un rafraîchissement immédiat.
`MatchClock` calcule une minute de jeu **estimée** à partir du statut
("1H"/"2H"/"Match Finished"...) et de l'heure de coup d'envoi — voir
sa documentation pour les hypothèses (45 min/mi-temps, 15 min de
pause). `UPDATE_PERIOD_SECONDS=60` fait tourner cette minute toute
seule, même sans nouvelle donnée du téléphone.

**Téléphone (`mobile/`)** : recherche d'équipe par nom via TheSportsDB,
puis liste des matchs récents/à venir. Sélectionner un match :
1. Télécharge les logos des deux équipes (`lookupteam.php`) et les
   convertit en Asset
2. Envoie le tout à la montre (équipes, score, statut, horodatage du
   coup d'envoi, logos)
3. Relit le match toutes les 60 secondes (`lookupevent.php`) et
   renvoie une mise à jour si le score ou le statut a changé, jusqu'à
   ce que le match soit terminé

## Limites connues

- **Minute estimée, pas officielle** : TheSportsDB (plan gratuit) ne
  fournit pas de minute de jeu en direct fiable, seulement un statut
  texte ("1H"/"2H"). La minute affichée est calculée localement à
  partir de l'heure de coup d'envoi et peut dériver de quelques
  minutes (arrêts de jeu non pris en compte).
- **Polling actif seulement app ouverte** : le rafraîchissement
  périodique tourne dans une coroutine liée au cycle de vie de
  l'Activity — il s'arrête si tu quittes l'app côté téléphone. Une
  vraie mise à jour en arrière-plan nécessiterait un Service
  persistant (pas encore fait).
- **Deux cercles, deux logos** : assigne "Score en direct" à un premier
  cercle du Dashboard → l'écran de config demande "Domicile" ou
  "Extérieur" → choisis "Domicile". Assigne-le à un second cercle →
  choisis "Extérieur" cette fois. Chaque cercle retient son choix
  indépendamment (`TeamSidePrefs`, par `complicationInstanceId`).

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
│       ├── AndroidManifest.xml   déclare les deux services
│       ├── debug.keystore        clé de signature fixe (voir plus bas)
│       ├── kotlin/.../
│       │   ├── ScoreComplicationService.kt
│       │   ├── MatchListenerService.kt   reçoit les données du téléphone
│       │   ├── MatchClock.kt     minute de jeu estimée
│       │   ├── TeamSideConfigActivity.kt   choix domicile/extérieur par cercle
│       │   ├── TeamSidePrefs.kt  stockage de ce choix par emplacement
│       │   └── MatchScore.kt     modèle de données + cache en mémoire
│       └── res/                  icônes, layout config, strings
├── mobile/
│   ├── build.gradle.kts          dépendances du module téléphone
│   └── src/main/
│       ├── AndroidManifest.xml   déclare MainActivity
│       ├── debug.keystore        même clé que côté montre
│       ├── kotlin/.../
│       │   ├── MainActivity.kt   recherche → sélection → envoi + polling
│       │   ├── SportsDbApi.kt    client TheSportsDB (matchs + logos)
│       │   ├── Models.kt         TeamResult / MatchResult
│       │   ├── TeamsAdapter.kt
│       │   └── MatchesAdapter.kt
│       └── res/layout/            activity_main, item_team, item_match
└── .github/workflows/build.yml   build CI (Java 17, Android SDK, Gradle 8.9)
```

## Pourquoi un keystore de debug fixe (`wear/debug.keystore` et `mobile/debug.keystore`)

Sans ça, chaque run GitHub Actions génère un keystore de debug
aléatoire, donc chaque nouvel APK est signé différemment — impossible
d'installer une mise à jour par-dessus une version précédente
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Ce keystore committé (mot de
passe `android`, alias `androiddebugkey` — valeurs standard, sans enjeu
de sécurité pour un debug local) garantit une signature stable entre
les builds. **C'est aussi une exigence de la Data Layer API** (voir
ci-dessous) : les deux modules utilisent le même fichier keystore pour
être signés à l'identique.

## Pourquoi `mobile` et `wear` ont le même applicationId

La Data Layer API (utilisée pour envoyer le match du téléphone vers la
montre) **exige que les deux apps partagent le même nom de package ET
soient signées avec la même clé** — sinon Play Services traite les deux
apps comme complètement étrangères l'une à l'autre : l'envoi réussit
silencieusement côté téléphone, mais rien n'arrive jamais côté montre,
sans la moindre erreur. D'où `applicationId = "com.yann.sportscomplication"`
identique dans les deux `build.gradle.kts` (seul le `namespace`, qui ne
sert qu'à l'organisation du code Kotlin, diffère).

## Tester la complication sur la montre

Une fois l'APK `wear` installé :
1. Sur le visage **Zenith**, assigne la complication "Score en direct" à
   l'emplacement central (type LONG_TEXT)
2. Sur le **Dashboard** Samsung, assigne-la à une des complications
   cercle (type SMALL_IMAGE)
3. Cherche un match dans l'app téléphone et sélectionne-le — la montre
   devrait se mettre à jour en quelques secondes, puis continuer à se
   rafraîchir toutes les minutes tant que l'app téléphone reste ouverte

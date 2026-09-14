# Sports Complication (Galaxy Watch)

Complication Wear OS qui affiche le score d'un match en direct :
- **LONG_TEXT** → pour l'emplacement central du visage Zenith
  (ex. `PSG 2-1 OM · 1ère MT`)
- **SMALL_IMAGE** → pour les complications cercle du Dashboard Samsung
  (image composée réunissant les deux logos d'équipe ET le score dans
  un seul cercle, voir `ComplicationImageComposer`)

Deux modules dans ce repo :
- `wear/` — la complication elle-même (montre)
- `mobile/` — l'app téléphone pour choisir le match à suivre

## État actuel

**Montre (`wear/`)** : `ScoreComplicationService` répond aux deux types
de complications. Pour SMALL_IMAGE, `ComplicationImageComposer` dessine
à la volée une image carrée unique (logo domicile à gauche, score au
centre, logo extérieur à droite, fond circulaire sombre pour la
lisibilité) — le système la recadre en cercle, tout est donc positionné
sur la bande horizontale centrale pour ne rien perdre au recadrage.
Aucune config n'est demandée à l'assignation : le rendu est le même
sur n'importe quel cercle du Dashboard. `MatchListenerService` reçoit
les mises à jour du téléphone (chemin `/match`), décode les deux logos
reçus en Asset, met à jour `MatchScoreStore`, et force un
rafraîchissement immédiat. Un DataItem `cleared=true` (envoyé quand le
suivi est arrêté côté téléphone) réinitialise la complication à "Aucun
match". `MatchClock` traduit le statut brut de TheSportsDB ("1H", "2H",
"HT", "FT"...) en français, **sans calculer de minute par déduction**
— voir la section "Pourquoi pas de minute de jeu chiffrée" plus bas.
`UPDATE_PERIOD_SECONDS=60` fait rappeler la complication chaque minute
même sans nouvelle donnée du téléphone.

**Téléphone (`mobile/`)** : recherche par **équipe**, **joueur** ou
**ligue** via TheSportsDB (sélecteur en haut de l'écran), puis liste
des **matchs du jour uniquement** correspondant à la recherche.
Sélectionner un match démarre `MatchFollowService`, un service de
premier plan (notification persistante) qui :
1. Télécharge les logos des deux équipes (`lookupteam.php`) et les
   convertit en Asset
2. Envoie le tout à la montre (équipes, score, statut, horodatage du
   coup d'envoi, logos)
3. Relit le match toutes les 60 secondes (`lookupevent.php`) et
   renvoie une mise à jour si le score ou le statut a changé, jusqu'à
   ce que le match soit terminé

Comme c'est un Service de premier plan (pas une coroutine liée à
l'Activity), **le suivi continue même si l'app téléphone est fermée ou
balayée hors des apps récentes**. Un bouton "Arrêter le suivi", affiché
dans une carte en haut de l'écran tant qu'un match est suivi, permet de
tout stopper à la main (service arrêté + complication remise à zéro
côté montre).

## Pourquoi pas de minute de jeu chiffrée

TheSportsDB (plan gratuit, `lookupevent.php`/`eventsnext.php`/
`eventslast.php`) ne renvoie **aucun champ de minute en direct** — vérifié
directement sur l'API, pas seulement dans la doc. Le seul champ
disponible est un statut texte (`strStatus` : `NS`, `1H`, `HT`, `2H`,
`FT`...). Une vraie minute chiffrée (`strProgress`, format
"mm:ss - 1st/2nd...") n'existe que sur l'API **Livescores V2**, réservée
aux abonnés **Premium** (9$/mois, authentification par en-tête
`X-API-KEY` — voir https://www.thesportsdb.com/pricing).

`MatchClock.kt` a donc été réécrit pour ne plus *estimer* de minute à
partir de l'heure de coup d'envoi (comme avant) : il traduit
directement le statut réel donné par l'API. Si tu passes un jour au
plan Premium, il devient possible d'ajouter un `SportsDbApiV2` dédié
qui appelle `/api/v2/json/livescore/soccer` pour récupérer une vraie
minute — pas fait ici pour ne pas ajouter de dépendance payante sans
que tu l'aies décidé.

## Limites connues

- **Pas de minute chiffrée sans Premium** — voir section ci-dessus.
- **Plan gratuit limité en résultats** : les recherches (équipe/joueur)
  ne renvoient souvent qu'1-2 résultats, et la recherche par ligue
  (`all_leagues.php`, filtré côté client faute d'endpoint de recherche
  textuelle) ne porte que sur une dizaine de grandes ligues de football
  (Premier League, Liga, Serie A, Bundesliga, **Ligue 1**...) —
  largement suffisant pour PSG/OM, mais pas pour des ligues plus
  confidentielles ou d'autres sports. Un passage en clé Premium lève
  ces limites.
- **Recherche par équipe/joueur limitée aux derniers/prochains matchs** :
  `getMatchesForTeam` (utilisé par les recherches équipe et joueur)
  s'appuie sur `eventslast.php`/`eventsnext.php`, qui ne renvoient
  qu'un nombre réduit de matchs avec la clé gratuite — si le seul match
  du jour d'une équipe n'est pas dans ce lot réduit, la recherche
  affichera "Aucun match aujourd'hui" même s'il y en a un. La recherche
  par ligue n'a pas cette limite (`eventsday.php` filtre par date côté
  serveur, sur toute la ligue).
- **Batterie Samsung** : `MatchFollowService` est un vrai foreground
  service, mais certains téléphones Samsung appliquent une optimisation
  batterie agressive qui peut quand même le couper. Si le suivi
  s'arrête de façon inattendue, désactiver l'optimisation de batterie
  pour cette app (Paramètres > Batterie > Sports Complication > Non
  optimisée).
- **Image cercle dense** : le cercle du Dashboard est minuscule à
  l'écran, et `ComplicationImageComposer` y fait tenir deux logos ET
  le score. Pour un score à deux chiffres des deux côtés (ex.
  "12-9"), logos et texte se touchent presque — reste lisible mais
  c'est un compromis assumé, pas un bug.

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
│   ├── debug.keystore            clé de signature fixe (voir plus bas)
│   └── src/main/
│       ├── AndroidManifest.xml   déclare les deux services (complication + listener)
│       ├── kotlin/.../
│       │   ├── ScoreComplicationService.kt
│       │   ├── ComplicationImageComposer.kt   dessine l'image combinée (2 logos + score) du SMALL_IMAGE
│       │   ├── MatchListenerService.kt   reçoit les données du téléphone (+ signal "cleared")
│       │   ├── MatchClock.kt     traduit le statut TheSportsDB en français (sans calcul de minute)
│       │   └── MatchScore.kt     modèle de données + cache en mémoire
│       └── res/                  icônes, strings
├── mobile/
│   ├── build.gradle.kts          dépendances du module téléphone
│   ├── debug.keystore            même clé que côté montre
│   └── src/main/
│       ├── AndroidManifest.xml   déclare MainActivity + MatchFollowService
│       ├── kotlin/.../
│       │   ├── MainActivity.kt          recherche (équipe/joueur/ligue) → sélection → démarre le suivi
│       │   ├── MatchFollowService.kt    foreground service : polling + envoi montre en arrière-plan
│       │   ├── WatchSync.kt             envoi vers la montre (partagé Activity/Service)
│       │   ├── FollowedMatchPrefs.kt    persiste le match suivi pour ré-afficher la carte au retour
│       │   ├── SportsDbApi.kt           client TheSportsDB (équipes, joueurs, ligues, matchs, logos)
│       │   ├── Models.kt                TeamResult / PlayerResult / LeagueResult / MatchResult
│       │   ├── SimpleListAdapter.kt     liste générique (équipes, joueurs, ligues)
│       │   └── MatchesAdapter.kt
│       └── res/
│           ├── layout/    activity_main, item_team, item_match
│           └── drawable/  ic_notification.xml (icône de la notification de suivi)
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
   cercle (type SMALL_IMAGE) — aucune config ne s'affiche, le cercle
   montrera directement les deux logos + le score
3. Cherche un match dans l'app téléphone (équipe, joueur ou ligue) et
   sélectionne-le — la montre devrait se mettre à jour en quelques
   secondes, puis continuer à se rafraîchir toutes les minutes, même si
   tu fermes l'app téléphone. Le bouton "Arrêter le suivi" dans l'app
   téléphone remet la complication à "Aucun match".

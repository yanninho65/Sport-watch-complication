# Sports Complication (Galaxy Watch)

Complication Wear OS qui affiche le score d'un match en direct :
- **LONG_TEXT** → pour l'emplacement central du visage Zenith
  (ex. `PSG 2-1 OM · 1ère MT`)
- **SMALL_IMAGE** → pour un emplacement "petit rectangle" qui accepte une
  image en couleur (image composée réunissant les deux logos d'équipe ET
  le score, couleurs d'origine conservées, voir `ComplicationImageComposer`)
  — **rendu des logos peu fiable sur Zenith, voir "Limites connues"**
- **MONOCHROMATIC_IMAGE** → pour un emplacement "petit rectangle" qui
  n'accepte que du monochrome (ex. bande au-dessus de la carte
  notification) — même principe, logos et score composés en silhouette
  blanche que le système teinte lui-même (voir `ComplicationImageComposer`)
- **SHORT_TEXT** → pour les petits rectangles de Zenith spécifiquement
  (confirmé : Zenith ne propose que SHORT_TEXT/LONG_TEXT/RANGED_VALUE/
  MONOCHROMATIC_ICON selon l'emplacement, et ces rectangles-là n'acceptent
  pas MONOCHROMATIC_IMAGE) — icône = les deux logos en silhouette côte à
  côte, texte = le score seul (ex. `2-1`) —
  **rendu de l'icône peu fiable sur Zenith, voir "Limites connues"**

Deux modules dans ce repo :
- `wear/` — la complication elle-même (montre)
- `mobile/` — l'app téléphone pour choisir le match à suivre

## État actuel

**Montre (`wear/`)** : `ScoreComplicationService` répond aux quatre types
de complications. Pour SMALL_IMAGE, `ComplicationImageComposer` compose
une image rectangulaire large (logo domicile, score, logo extérieur,
tous resserrés près du centre plutôt que près des bords), en couleurs
d'origine, sans fond peint — le fond sombre de la case hôte suffit, et
un contour noir derrière le score garantit la lisibilité quel que soit
le fond. Deux versions précédentes de ce rendu ont été abandonnées
(cercle pour le Dashboard Samsung, puis logos près des bords d'un
rectangle large) : voir "Limites connues" plus bas pour le détail et la
décision finale sur ce point.
Pour MONOCHROMATIC_IMAGE, `ComplicationImageComposer` compose une image
rectangulaire large avec le même agencement resserré, mais en silhouette
blanche uniquement — les logos couleur sont recolorés en blanc via leur
canal alpha, sans fond peint, pour correspondre à la convention
monochrome (le système applique ensuite sa propre teinte). Pour
SHORT_TEXT, `ComplicationImageComposer` compose une petite icône carrée
avec les deux logos en silhouette côte à côte (sans score dedans) ; le
score (ex. `2-1`) passe par le champ texte natif du SHORT_TEXT, limité à
7 caractères par l'API — largement suffisant.
Aucune config n'est demandée à l'assignation, quel que soit le type : le
rendu est le même partout. `MatchListenerService` reçoit
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

## Choix du sport et intégration tennis

Avant de chercher, l'app téléphone demande d'abord **quel sport suivre**
(`radioSport` dans `activity_main.xml`) :

- **Football** → comportement historique, TheSportsDB, recherche par
  équipe/joueur/ligue (sélecteur `radioSearchMode`, visible uniquement
  dans ce mode)
- **Tennis** → [Live Tennis API](https://livetennisapi.com), recherche
  par **joueur uniquement** (pas d'équipe/ligue en tennis) — voir
  `LiveTennisApi.kt`

Le volley a été envisagé puis écarté : les API gratuites trouvées pour
ce sport (ex. API-VOLLEYBALL) avaient une couverture jugée trop limitée
pour être utile ici. Seul le tennis a été intégré.

**Pourquoi Live Tennis API plutôt que TheSportsDB pour le tennis** :
TheSportsDB ne modélise que `intHomeScore`/`intAwayScore` (un score
global par équipe) et un champ texte libre `strResult`, non structuré et
peu fiable pour cette question — pas de champ dédié au nombre de sets ni
au score du set en cours. Live Tennis API renvoie au contraire un objet
`score` structuré : `sets` (sets gagnés par chaque joueur), `games`
(score du set en cours, par set), `points` (score du jeu en cours) et
`server`. Voir https://docs.livetennisapi.com pour la référence complète.

**Ce qui est fait** : le sélecteur de sport, la recherche de joueur, le
chargement de ses matchs du jour (en direct + à venir) et le suivi
(polling + envoi à la montre) via `MatchFollowService`/`WatchSync`, avec
`homeScore`/`awayScore` portant le nombre de **sets** gagnés. En plus,
sur la montre, le champ LONG_TEXT (emplacement central de Zenith)
affiche désormais aussi le score de JEUX du set en cours quand le match
est en direct (ex. `Alcaraz 2-1 Sinner · 3e set 4-3`) — `wear/MatchClock.kt`
(fonction `tennisLabel`/`liveSetLabel`) déduit le numéro du set du nombre
de sets déjà gagnés et met en forme `currentSetHomeGames`/
`currentSetAwayGames`, reçus bruts du téléphone (`score.games`, dernier
élément de chaque liste — voir `LiveTennisApi.parseMatch`). Statuts
tennis traduits : "upcoming" → "À venir · HH:mm", "live" → le libellé de
set ci-dessus (ou "En direct" si le score de jeux n'est pas encore
connu), "completed" → "Terminé", "cancelled" → "Annulé".

**Ce qui reste un TODO séparé** : afficher aussi le score du JEU en
cours (`points`, ex. "30-15") — l'API le fournit déjà dans le même appel
que `games`, mais rien ne le relaie encore côté téléphone ; et les
rendus compacts (SMALL_IMAGE/MONOCHROMATIC_IMAGE/SHORT_TEXT) ne montrent
toujours que le score de sets, sans le set en cours — manque de place
plausible dans ces petits formats, pas vérifié sur la montre.

**Clé API requise** : contrairement à la clé de test publique partagée
de TheSportsDB, Live Tennis API exige une clé personnelle — gratuite
mais nominative. Inscription sur
https://livetennisapi.com/subscribe/free (email uniquement, aucune
carte bancaire), clé affichée immédiatement sur
https://livetennisapi.com/account. À reporter dans la constante
`API_KEY` de `LiveTennisApi.kt` avant de builder — tel quel (valeur
placeholder), toutes les recherches tennis échoueront silencieusement
(401 → liste vide, "Aucun joueur trouvé").

**Plan gratuit Live Tennis API — limite à connaître** : 30
requêtes/minute **et seulement 100/jour**. Ce deuxième plafond n'existe
pas chez TheSportsDB (juste 30/min, pas de limite journalière) — c'est
pourquoi le suivi d'un match de tennis interroge l'API toutes les
**3 minutes** au lieu de toutes les 60 secondes comme en foot (voir
`MatchFollowService.pollIntervalMillis`) : à 60s, un seul match de 2-3h
épuiserait à lui seul le quota du jour, recherches de joueurs comprises.

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

- **Tennis : 100 requêtes/jour seulement (plan gratuit Live Tennis
  API)** — voir "Choix du sport et intégration tennis" ci-dessus pour le
  détail et l'intervalle de polling adapté en conséquence. Le volley n'a
  pas été intégré, couverture jugée trop limitée côté API gratuites.
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
- **Icône SHORT_TEXT limitée à un seul élément** : le champ icône d'un
  SHORT_TEXT est typé `MonochromaticImage` dans l'API Wear OS — aucune
  version couleur n'est possible pour ce champ, sur aucune montre. Il ne
  peut afficher qu'une seule image simple, d'où le choix de
  `composeMonochromeIcon` (deux logos compressés côte à côte) plutôt que
  d'essayer d'en caser deux à taille lisible.
- **Aucune information sur l'emplacement cible** : Android ne transmet
  pas à `ComplicationDataSourceService` la forme/taille de l'emplacement
  qui demande les données — deux emplacements SMALL_IMAGE différents
  reçoivent forcément la même image. Un seul rendu SMALL_IMAGE est donc
  possible à la fois pour toute l'app (voir "État actuel" ci-dessus).
- **Logos dans les petits rectangles Zenith : rendu peu fiable,
  abandonné.** Plusieurs versions testées sur la montre (logos aux bords
  d'un rectangle large, puis resserrés près du centre) — dans le
  meilleur cas un seul logo apparaissait (souvent partiellement), et
  d'un essai à l'autre, sans changement de code entre les deux, la même
  case SHORT_TEXT est passée d'"icône visible" à "aucune icône". La
  cause exacte n'a pas pu être identifiée avec certitude : Zenith ne
  publie pas les dimensions réelles de ses emplacements, et l'API de
  complication ne les transmet pas non plus à l'app — impossible donc de
  garantir un recadrage cohérent. Le pipeline de données (téléchargement
  et envoi des deux logos, téléphone → montre) a été vérifié et traite
  domicile/extérieur de façon strictement identique : ce n'est pas un
  bug côté données. Décision : ne pas continuer à ajuster à l'aveugle.
  Le texte (score, et le nom des équipes en LONG_TEXT au centre) reste
  le canal fiable sur Zenith ; les logos dans les petits rectangles
  restent implémentés tels quels (couleur ou silhouette selon le type)
  mais sans garantie d'affichage.

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
│       │   ├── ComplicationImageComposer.kt   dessine les images combinées (2 logos + score) du SMALL_IMAGE, MONOCHROMATIC_IMAGE et SHORT_TEXT
│       │   ├── MatchListenerService.kt   reçoit les données du téléphone (+ sport, + signal "cleared")
│       │   ├── MatchClock.kt     traduit le statut foot (TheSportsDB) OU tennis (Live Tennis API) en français, + score du set en cours en tennis
│       │   └── MatchScore.kt     modèle de données (+ sport, + score du set en cours) + cache en mémoire
│       └── res/                  icônes, strings
├── mobile/
│   ├── build.gradle.kts          dépendances du module téléphone
│   ├── debug.keystore            même clé que côté montre
│   └── src/main/
│       ├── AndroidManifest.xml   déclare MainActivity + MatchFollowService
│       ├── kotlin/.../
│       │   ├── MainActivity.kt          choix du sport (foot/tennis) → recherche → sélection → démarre le suivi
│       │   ├── MatchFollowService.kt    foreground service : polling (60s foot / 3min tennis) + envoi montre en arrière-plan
│       │   ├── WatchSync.kt             envoi vers la montre (partagé Activity/Service)
│       │   ├── FollowedMatchPrefs.kt    persiste le match suivi (+ sport) pour ré-afficher la carte au retour
│       │   ├── SportsDbApi.kt           client TheSportsDB — foot (équipes, joueurs, ligues, matchs, logos)
│       │   ├── LiveTennisApi.kt         client Live Tennis API — tennis (joueurs, matchs, score par sets)
│       │   ├── Models.kt                Sport / TeamResult / PlayerResult / TennisPlayerResult / LeagueResult / MatchResult
│       │   ├── SimpleListAdapter.kt     liste générique (équipes, joueurs de foot ou de tennis, ligues)
│       │   └── MatchesAdapter.kt
│       └── res/
│           ├── layout/    activity_main (radioSport + radioSearchMode), item_team, item_match
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
2. Sur les petits rectangles de Zenith, teste les types proposés selon
   l'emplacement : SHORT_TEXT (score en texte, ex. `2-1`) ou SMALL_IMAGE
   (logo(s) + score composés en image) — le score s'affiche de façon
   fiable, mais l'affichage des logos y est expérimental et peu fiable
   (voir "Limites connues") ; aucune config ne s'affiche à l'assignation,
   quel que soit le type retenu
3. Cherche un match dans l'app téléphone (équipe, joueur ou ligue) et
   sélectionne-le — la montre devrait se mettre à jour en quelques
   secondes, puis continuer à se rafraîchir toutes les minutes, même si
   tu fermes l'app téléphone. Le bouton "Arrêter le suivi" dans l'app
   téléphone remet la complication à "Aucun match".

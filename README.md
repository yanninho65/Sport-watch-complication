# Sports Complication (Galaxy Watch)

Complication Wear OS qui affiche le score d'un match en direct. Ce
README est un document de travail pour Claude (pas pour un humain qui
découvrirait le projet) : à relire en entier au début de chaque
conversation sur ce repo, avant de toucher au code. Il ne contient que
ce qui n'est pas trivialement redéductible du code lui-même — des faits
externes vérifiés (limites d'API, codes de statut), des décisions déjà
tranchées (pour ne pas les rouvrir sans raison), et des pièges connus.
Le détail des formats de notification Sofascore et des expressions
régulières correspondantes vit dans les commentaires de
`SofascoreNotificationParser.kt` lui-même, pas ici — ce fichier ne
reproduit qu'un exemple minimal à titre d'orientation.

- **LONG_TEXT** → emplacement central du visage Zenith. Format :
  statut/temps EN PREMIER, puis équipes/score (ex. `P1 · PSG 2-1 OM`)
  — voir "Statuts affichés".
- **SMALL_IMAGE** → petit rectangle acceptant une image couleur : les
  deux logos + le score, composés par `ComplicationImageComposer`.
- **MONOCHROMATIC_IMAGE** → petit rectangle monochrome uniquement :
  même composition, en silhouette blanche (le système teinte).
- **SHORT_TEXT** → spécifique aux petits rectangles de Zenith (Zenith
  n'expose que SHORT_TEXT/LONG_TEXT/RANGED_VALUE/MONOCHROMATIC_ICON
  selon l'emplacement, pas MONOCHROMATIC_IMAGE sur ces cases). Icône =
  les deux logos en silhouette côte à côte (le type `MonochromaticImage`
  de l'API Wear OS n'admet qu'une seule image, jamais de couleur —
  d'où ce compromis plutôt que deux logos illisibles). Texte = le
  score seul (`2-1`, 7 caractères max, largement suffisant).
- **Rendu des logos peu fiable sur Zenith** (SMALL_IMAGE et l'icône
  SHORT_TEXT) : plusieurs dispositions testées, résultat incohérent
  d'un essai à l'autre sans changement de code, cause non identifiable
  avec certitude (Zenith ne publie pas les dimensions réelles de ses
  emplacements, l'API de complication ne les transmet pas non plus —
  et de toute façon **Android ne transmet à `ComplicationDataSourceService`
  aucune info sur l'emplacement cible**, donc deux emplacements
  SMALL_IMAGE différents reçoivent forcément le même rendu). Pipeline
  de données vérifié correct (domicile/extérieur traités identiquement).
  Décision prise : ne plus chercher à corriger à l'aveugle — le texte
  (score, et les noms d'équipe en LONG_TEXT) est le canal fiable sur
  Zenith, les logos restent implémentés tels quels mais sans garantie.

Deux modules : `wear/` (complication, montre) et `mobile/` (app
téléphone pour choisir le match à suivre).

## État actuel

**Montre (`wear/`)** : `ScoreComplicationService` répond aux quatre
types ci-dessus, sans configuration à l'assignation (rendu identique
partout). Un tap sur la complication ouvre l'app Sofascore **sur la
montre** (`sofascoreTapAction`, nécessite un `<queries>` dans le
manifeste côté montre depuis Android 11). `MatchListenerService` reçoit
les mises à jour du téléphone (chemin `/match`), décode les logos en
Asset, met à jour `MatchScoreStore`, force un rafraîchissement immédiat ;
un DataItem `cleared=true` (envoyé quand le suivi s'arrête côté
téléphone, OU quand le repli Sofascore n'a plus de notif active à
afficher — voir plus bas) réinitialise à "Aucun match". `MatchClock`
traduit le statut brut en français **sans jamais calculer de minute par
déduction** (voir "Pourquoi pas de minute de jeu chiffrée"). Le score
affiché entoure de crochets le côté qui vient de marquer/gagner le
dernier set quand cette info est connue (`MatchScore.lastScorer`,
repris des crochets de la notif Sofascore elle-même — jamais renseigné
pour un match suivi via TheSportsDB/Live Tennis API). `UPDATE_PERIOD_SECONDS=60`
fait rappeler la complication chaque minute même sans nouvelle donnée.

**Téléphone (`mobile/`)** : recherche par équipe/joueur/ligue (choix de
l'API et du sport, voir section suivante), résultats limités aux
**matchs du jour**. Sélectionner un match démarre `MatchFollowService`,
foreground service (le suivi continue app fermée/balayée) qui
télécharge les logos, envoie tout à la montre, puis relit le match
périodiquement (60s foot, 3min tennis — voir plus bas) et ne renvoie
une mise à jour que si score/statut a changé, jusqu'à la fin du match.
Bouton "Arrêter le suivi" pour stopper à la main (service arrêté +
complication remise à zéro). **Sur certains Samsung, l'optimisation
batterie agressive peut couper ce service malgré le statut foreground**
— si le suivi s'arrête sans raison, désactiver l'optimisation batterie
pour l'app (Paramètres > Batterie > Sports Complication > Non
optimisée). **Recherche équipe/joueur limitée aux derniers/prochains
matchs retournés par l'API gratuite** (`eventslast.php`/`eventsnext.php`,
lot réduit) : peut afficher "Aucun match aujourd'hui" à tort si le
match du jour n'est pas dans ce lot. La recherche par ligue
(`eventsday.php`, filtre par date côté serveur) n'a pas cette limite.

## Choix de l'API, du sport et intégration tennis

Deux questions posées avant de chercher : l'**API** (`radioApi` —
TheSportsDB ou Live Tennis API) puis le **sport** (`radioSportsDbSport`,
TheSportsDB uniquement — tennis implicite et mono-sport pour Live
Tennis API) : Tous sports (défaut), Football, Basketball, Handball,
Rugby, Volleyball. Le filtre sport est **côté client uniquement**
(`searchteams.php`/`searchplayers.php` n'acceptent pas ce paramètre) :
la recherche interroge TheSportsDB normalement puis écarte les
résultats dont `strSport` ne correspond pas. Une version antérieure
forçait un sport sans option "Tous sports" et cassait la recherche dès
que `strSport` ne correspondait pas exactement — d'où "Tous sports" en
défaut désormais.

**Limite à connaître** : avec la clé gratuite, la recherche par ligue
ne renvoie de toute façon qu'une dizaine de grandes ligues de
**football** (Premier League, Liga, Serie A, Bundesliga, Ligue 1...) —
choisir un autre sport pour une recherche de ligue donnera très
probablement 0 résultat. Les recherches équipe/joueur, elles, peuvent
couvrir d'autres sports selon ce que la clé gratuite indexe, mais ne
renvoient souvent qu'1-2 résultats. Un passage en clé Premium lève ces
limites.

**TheSportsDB → Live Tennis API pour le tennis** : TheSportsDB ne
modélise qu'un score global par équipe (pas de sets/jeux structurés).
Live Tennis API renvoie `score.sets`/`games`/`points`/`server` (voir
https://docs.livetennisapi.com). Le volley a été envisagé puis écarté
comme 3e API (couverture jugée trop limitée côté API gratuites
trouvées) — il est suivable via le repli Sofascore à la place (voir
plus bas).

**Ce qui est fait** : sélecteurs, recherche joueur, suivi (polling +
envoi montre), `homeScore`/`awayScore` = sets gagnés. Sur la montre,
LONG_TEXT affiche aussi le score de JEUX du set en cours quand connu
(ex. `3e set 4-3 · Alcaraz 2-1 Sinner`) — `MatchClock.liveSetLabel`
déduit le numéro de set du total de sets gagnés, `currentSetHomeGames`/
`currentSetAwayGames` viennent tels quels du téléphone
(`LiveTennisApi.parseMatch`, dernier élément de `score.games`).
**TODO non fait** : score du JEU en cours (`points`, déjà dans la même
réponse API, rien ne le relaie encore) ; SMALL_IMAGE/MONOCHROMATIC_IMAGE/
SHORT_TEXT ne montrent que le score de sets, jamais le set en cours.

**Clé Live Tennis API saisie DANS L'APP, jamais dans le code** (dépôt
public) : inscription gratuite sur
https://livetennisapi.com/subscribe/free (email seulement), clé visible
sur https://livetennisapi.com/account, collée dans le champ dédié une
fois "Live Tennis API" choisi (`TennisApiKeyPrefs.kt`, SharedPreferences
non chiffrées — suffisant pour ne pas exposer la clé publiquement, pas
à l'épreuve d'un téléphone rooté). Sans clé, la recherche tennis
affiche directement une invite au lieu d'appeler l'API pour rien.
**Plafond gratuit : 30 req/min ET 100/jour** (TheSportsDB n'a que le
premier) — d'où un polling tennis à 3 minutes plutôt que 60 secondes
(un seul match de 2-3h épuiserait sinon le quota du jour à lui seul).
Clé absente/vidée pendant un suivi : échec silencieux du
rafraîchissement (comme une panne réseau), dernier score connu affiché
jusqu'à nouvelle clé valide.

## Statuts affichés (P1/MT/Fin...)

Vocabulaire unifié entre sports plutôt que les codes bruts de chaque
API, traduit dans `wear/MatchClock.kt` (`label`), affiché EN PREMIER
dans LONG_TEXT (`P1 · PSG 2-1 OM`) :

| Code brut TheSportsDB | Sport(s) | Affiché |
|---|---|---|
| `1H` | Foot, rugby, handball | `P1` |
| `HT` | Foot, rugby, handball, basket | `MT` |
| `2H` | Foot, rugby, handball | `P2` |
| `Q1`/`Q2`/`Q3`/`Q4` | Basket, football américain | `P1`/`P2`/`P3`/`P4` |
| `FT` / `AOT` | Tous | `Fin` |
| `AET` | Foot, rugby | `Fin (a.p.)` |
| `PEN` (foot) / `AP` (handball) | — | `Fin (tab)` |

(codes confirmés sur https://www.thesportsdb.com/docs_api_data,
15/09/2026 ; reste des statuts déjà gérés inchangé :
`ET`/`BT`/`P`/`SUSP`/`INT`/`PST`/`CANC`/`ABD`/`AWD`/`WO` ; hockey sur
glace : `P1`/`P2`/`P3` déjà dans ce format côté API, passent tels
quels)

**Basket (repli Sofascore)** : mêmes codes `Q1`/`Q2`/`Q3`/`Q4`/`HT`/`FT`
que TheSportsDB ci-dessus, mais déduits des libellés "Xe quart-temps a
commencé/terminé"/"Mi-temps terminé" de la notif — voir
`SofascoreNotificationParser.parseBasketball`. **Handball (repli
Sofascore)** : mêmes libellés qu'au foot (mi-temps, pas de
quart-temps) — le gabarit foot suffit déjà, aucun code dédié.
**Correction de score au foot (repli Sofascore)** : un but peut être
invalidé après coup (VAR) — Sofascore pousse alors "Correction du
score : H - A", reconnu au même titre qu'un but classique. **Score
final "Match terminé : H - A" (tous sports, repli Sofascore)** :
toujours prioritaire dès qu'il est présent, peu importe sa position
dans la notif — nécessaire car Sofascore ne respecte pas toujours
l'ordre chronologique attendu (ex. volley : la ligne de fin du 3e set
peut arriver APRÈS "Match terminé" ; basket : la toute dernière
période n'est parfois jamais envoyée séparément).

**Tennis** : aucun indicateur de période en cours de match (le score en
sets suffit) — seule la fin (`completed`) est traduite, en `Fin`.

**Tennis de table, volley (repli Sofascore)** : indicateur `S<N>` (N =
numéro du set en cours) affiché en cours de match, calculé côté
téléphone — voir section Sofascore. Coïncide avec le format brut
TheSportsDB pour le volley (`S1`..`S5`) sans lien direct.

**But horodaté en foot (repli Sofascore)** : `"MM+"` (ex. `"37+"`)
plutôt que la minute nue — TheSportsDB (suivi manuel) ne fournit de
toute façon aucune minute en direct sur le plan gratuit (section
suivante). Si la notif ne donne aucune minute (ligue peu couverte) :
statut vide, la complication affiche alors juste le score sans rien
devant (`ScoreComplicationService` omet le séparateur quand le statut
est vide — vaut aussi pour tout statut vide, pas seulement ce cas).

## Pourquoi pas de minute de jeu chiffrée

TheSportsDB (plan gratuit, `lookupevent.php`/`eventsnext.php`/
`eventslast.php`) ne renvoie **aucun champ de minute en direct** —
vérifié sur l'API elle-même, pas juste dans la doc. Seul un statut
texte (`strStatus`). Une vraie minute (`strProgress`,
"mm:ss - 1st/2nd...") n'existe que sur l'API Livescores V2, réservée
au plan **Premium** (9$/mois, voir https://www.thesportsdb.com/pricing).
`MatchClock.kt` traduit donc le statut réel sans jamais l'estimer à
partir de l'heure de coup d'envoi. Si passage en Premium un jour :
ajouter un `SportsDbApiV2` dédié appelant
`/api/v2/json/livescore/soccer` — pas fait pour ne pas ajouter de
dépendance payante non demandée.

## Repli "pas de match sélectionné" : notifications Sofascore

Quand aucun match n'est suivi manuellement,
`SofascoreNotificationListenerService` relit les notifications de
l'app Sofascore (`com.sofascore.results`, vérifié via sa fiche Play
Store) et pousse un score déduit à la montre. Détail des gabarits et
de tous les exemples réels confirmés : commentaires de
`SofascoreNotificationParser.kt` — ici, seulement les mécanismes et
faits qui ne sont pas évidents à la lecture du code.

- **Une notif = un match, mise à jour en place** (confirmé sur
  appareil) : jusqu'à 6 lignes d'historique (`EXTRA_TEXT_LINES`, style
  Inbox, plafond Android), déjà triées du plus récent au plus ancien
  (`InboxStyle.addLine()` affiche dans l'ordre d'ajout, Sofascore
  ajoute chaque événement en tête — ne PAS re-trier).
  Chaque notif est identifiée par sa propre
  `StatusBarNotification.getKey()`, **pas par le `groupKey` système**
  — Sofascore semble regrouper toutes ses notifications (tous matchs
  confondus) sous la même clé de groupe, donc grouper par `groupKey`
  mélangeait les lignes de matchs différents.
- **Sport déduit du contenu des lignes**, aucun indicateur dédié dans
  la notif : "Match terminé : H-A" (score final) toujours vérifié EN
  PREMIER, peu importe le sport ; puis basket si une ligne contient
  "quart-temps" ; puis tennis/tennis de table/volley si une ligne "Xe
  set terminé" est présente — ces deux derniers partagent EXACTEMENT le
  même gabarit de ligne que le tennis, distingués par un test
  arithmétique (somme des deux scores de la ligne la plus récente =
  numéro d'ordre du set ⟺ décompte cumulatif de sets, impossible en
  tennis où ce sont des jeux ≥6) ; sinon foot (sert aussi au handball,
  mêmes libellés de mi-temps). Exemple minimal (foot, 12/09/2026,
  Real Madrid - Rayo Vallecano) :
  ```
  35' But : [3] - 0  Jude Bellingham
  ```
  Le PREMIER nombre est toujours le score domicile ; les crochets
  entourent le score de l'équipe qui vient de marquer/gagner le set
  (non exploités pour déterminer domicile/extérieur).
- **Ambiguïté au tout début d'un match** : "Match commencé" est le même
  libellé dans tous les sports, indistinguable tant qu'aucun set n'est
  terminé — un match à sets qui démarre est donc affiché brièvement
  avec le gabarit foot (0-0, `P1`) jusqu'à la fin du 1er set.
- **Événement non reconnu** (sport pas encore couvert, ou format pas
  encore vu) → repli neutre : texte brut de la notif la plus récente
  affiché tel quel, jamais de score inventé. Le parseur grandit au fur
  et à mesure des exemples réels envoyés par Yann, pas d'un coup (pas
  d'accès direct à l'app Sofascore pour explorer les formats à
  l'avance).
- **Accès aux notifications** : permission spéciale non demandable au
  runtime (contrairement à `POST_NOTIFICATIONS`) — bouton dédié dans
  l'app qui ouvre directement le réglage système.
- **Quel match suivre en repli** : bouton "Repli Sofascore : … (changer)"
  → liste (dernière notif en tête, par défaut ET toujours actif) ou
  choix d'un match précis, persisté par la clé de sa notification
  (`SofascorePrefs.kt`) ; retombe automatiquement sur "dernière notif"
  si ce match disparaît (terminé, notif supprimée). **Plus aucune notif
  Sofascore active ET aucun match suivi par API** → la complication
  repasse à "Aucun match" (`WatchSync.sendCleared`, corrigé le
  15/09/2026 — une version antérieure laissait la montre bloquée sur le
  dernier score Sofascore connu même après suppression de la notif).
  Pas de logos dans ce mode (pas d'extraction fiable depuis une notif
  tierce) : texte seul (LONG_TEXT/SHORT_TEXT).
- **Un match suivi par l'API ne bloque le repli Sofascore que TANT
  QU'IL EST EN COURS** (corrigé le 15/09/2026) : `MatchFollowService`
  vide désormais `FollowedMatchPrefs` (+ relance immédiatement le repli
  Sofascore) dès que le match qu'il suivait se termine tout seul — pas
  seulement sur arrêt manuel ("Arrêter le suivi", déjà géré côté
  `MainActivity.stopFollowing()`). Avant ce correctif, un match API fini
  restait indéfiniment considéré comme "suivi", empêchant le repli
  Sofascore de reprendre la main tant que Yann ne rouvrait pas l'app
  pour arrêter le suivi à la main.
- **Crochets = qui vient de marquer/gagner le dernier set** : repris
  tels quels de la notif Sofascore (voir plus haut) dans
  `MatchResult.lastScorer`/`MatchScore.lastScorer`, affichés par
  `MatchScore.scoreText()` (ex. `PSG [2]-1 OM`) — jamais renseignés
  pour un match suivi via TheSportsDB/Live Tennis API.

## Compiler sans Android Studio

Buildé via GitHub Actions, pas en local :

1. Pousse sur `main`
2. Le workflow `.github/workflows/build.yml` se déclenche seul (ou
   lance-le à la main : onglet **Actions** → **Build APK** → **Run
   workflow**)
3. Run terminé → **Artifacts** → télécharge `wear-debug` et/ou
   `mobile-debug`
4. Installe sur l'appareil (ADB Wi-Fi, ou une app comme GeminiMan
   WearOS Manager pour la montre)

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
│       │   ├── MatchClock.kt     traduit le statut (tous sports TheSportsDB + tennis Live Tennis API) en français, + score du set en cours en tennis
│       │   └── MatchScore.kt     modèle de données (+ sport, + score du set en cours) + cache en mémoire
│       └── res/                  icônes, strings
├── mobile/
│   ├── build.gradle.kts          dépendances du module téléphone
│   ├── debug.keystore            même clé que côté montre
│   └── src/main/
│       ├── AndroidManifest.xml   déclare MainActivity + MatchFollowService + SofascoreNotificationListenerService
│       ├── kotlin/.../
│       │   ├── MainActivity.kt          choix API (TheSportsDB/tennis) → sport → recherche → sélection → démarre le suivi ; + boutons accès notifications et choix du repli Sofascore
│       │   ├── MatchFollowService.kt    foreground service : polling (60s TheSportsDB / 3min tennis) + envoi montre en arrière-plan
│       │   ├── WatchSync.kt             envoi vers la montre (partagé Activity/Service)
│       │   ├── FollowedMatchPrefs.kt    persiste le match suivi (+ source API) pour ré-afficher la carte au retour
│       │   ├── SportsDbApi.kt           client TheSportsDB — multi-sport (équipes, joueurs, ligues, matchs, logos), filtré par sport côté client si un sport précis est choisi
│       │   ├── LiveTennisApi.kt         client Live Tennis API — tennis (joueurs, matchs, score par sets), clé passée en paramètre (jamais en dur)
│       │   ├── TennisApiKeyPrefs.kt     stocke la clé Live Tennis API saisie dans l'app (SharedPreferences, jamais dans le code — dépôt public)
│       │   ├── SofascoreNotificationListenerService.kt   repli "pas de match sélectionné" : lit les notifs Sofascore, mode "dernière" ou match choisi (SofascorePrefs)
│       │   ├── SofascoreNotificationParser.kt            transforme les lignes de notif (foot/handball/basket/tennis/tennis de table/volley) en statut/score — vocabulaire MatchClock.kt réutilisé, tous les exemples réels confirmés sont documentés ici
│       │   ├── SofascorePrefs.kt        persiste le choix dernière notif / match précis (clé de notification) pour le repli Sofascore
│       │   ├── Models.kt                ApiSource / TeamResult / PlayerResult / TennisPlayerResult / LeagueResult / MatchResult
│       │   ├── SimpleListAdapter.kt     liste générique (équipes, joueurs de foot/tennis/etc., ligues, choix du repli Sofascore)
│       │   └── MatchesAdapter.kt
│       └── res/
│           ├── layout/    activity_main (radioApi, radioSportsDbSport, champ clé tennis, radioSearchMode, boutons accès notifications + choix repli Sofascore), item_team, item_match
│           └── drawable/  ic_notification.xml (icône de la notification de suivi)
└── .github/workflows/build.yml   build CI (Java 17, Android SDK, Gradle 8.9)
```

## Pourquoi un keystore de debug fixe (`wear/debug.keystore` et `mobile/debug.keystore`)

Sans ça, chaque run GitHub Actions génère un keystore aléatoire, donc
chaque APK est signé différemment — impossible d'installer une mise à
jour par-dessus une version précédente
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Keystore committé (mot de
passe `android`, alias `androiddebugkey` — valeurs standard, sans enjeu
de sécurité pour du debug local) pour une signature stable entre
builds. **Aussi une exigence de la Data Layer API** (voir ci-dessous) :
les deux modules doivent être signés à l'identique.

## Pourquoi `mobile` et `wear` ont le même applicationId

La Data Layer API (envoi téléphone → montre) **exige le même nom de
package ET la même clé de signature** — sinon Play Services traite les
deux apps comme étrangères : l'envoi réussit silencieusement côté
téléphone, rien n'arrive jamais côté montre, sans erreur. D'où
`applicationId = "com.yann.sportscomplication"` identique dans les deux
`build.gradle.kts` (seul le `namespace`, purement Kotlin, diffère).

## Tester la complication sur la montre

1. Sur **Zenith**, assigne "Score en direct" à l'emplacement central
   (LONG_TEXT)
2. Sur les petits rectangles, teste SHORT_TEXT ou SMALL_IMAGE selon
   l'emplacement — le score est fiable, les logos expérimentaux (voir
   plus haut) ; aucune config à l'assignation
3. Cherche un match côté téléphone et sélectionne-le — mise à jour
   montre en quelques secondes puis toutes les minutes, même app
   téléphone fermée. "Arrêter le suivi" remet à "Aucun match".

package com.yann.sportscomplication.mobile

/**
 * Transforme les lignes d'une notification Sofascore (une notif = un match,
 * voir SofascoreNotificationListenerService) en un [MatchResult] de repli.
 * Détecte le sport à partir du contenu des lignes (pas d'indicateur dédié
 * dans la notif) :
 * - présence d'au moins une ligne "Xe set terminé" ET la somme des deux
 *   scores de la plus RÉCENTE de ces lignes égale son numéro d'ordre
 *   (ex. "3e set terminé : 2 - 1" -> 2+1=3) -> sport à décompte de sets
 *   cumulatif ([parseSetTally] — tennis de table, volley)
 * - présence d'au moins une ligne "Xe set terminé" SANS cette égalité
 *   -> tennis ([parseTennis])
 * - sinon -> gabarit foot ([parseFootball])
 *
 * [lines] doit être trié DU PLUS RÉCENT AU PLUS ANCIEN (comme affiché dans
 * le panneau de notifications).
 *
 * Un match qui vient de démarrer (coup d'envoi) affiche 0-0 — la notif
 * Sofascore confirmée pour cet événement ("Match commencé", sans score) ne
 * le précise pas, voir [firstHalfStarted]. **Ambiguïté entre sports à ce
 * stade précis** : "Match commencé" est le MÊME libellé dans tous les
 * sports (confirmé, voir les exemples plus bas) et rien ne les distingue
 * tant qu'aucun set n'est encore terminé — un match à sets qui vient tout
 * juste de démarrer est donc affiché avec le gabarit foot (0-0, statut
 * "P1" côté montre) jusqu'à la fin du 1er set, où le bon vocabulaire prend
 * le relais automatiquement. Léger défaut d'affichage temporaire, sans
 * conséquence au-delà de quelques minutes en début de match.
 *
 * Exemple réel observé pour le foot (capture d'écran, 12/09/2026, Real
 * Madrid - Rayo Vallecano) :
 *   "Match terminé : 4 - 1"
 *   "90' But : [4] - 1  Kylian Mbappé"
 *   "51' But : 3 - [1]  Sergio Camello"
 *   "2de mi-temps a commencé: 3 - 0"
 *   "Mi-temps : 3 - 0"
 *   "35' But : [3] - 0  Jude Bellingham"
 * Les crochets entourent le score de l'équipe qui vient de marquer — pas
 * utilisés ici (on ne distingue pas domicile/extérieur par ce biais), mais
 * ça confirme que le PREMIER nombre est toujours le score domicile.
 *
 * Exemple réel observé pour le foot SANS minute ni buteur précis (capture
 * d'écran, 15/09/2026, Kasuka FC - Karketu Díli, ligue moins couverte par
 * Sofascore) :
 *   "But: [2] - 0 Kasuka FC"
 * Le nom qui suit est ici celui de l'ÉQUIPE, pas d'un joueur — voir
 * [goalNoMinute].
 *
 * Exemple réel observé pour le tennis (capture d'écran, 14/09/2026,
 * E. Jacquemot - L. Samsonova) :
 *   "1er set terminé : 6 - [7] L. Samsonova"
 *   "Match commencé"
 * et pour un match complet (15/09/2026, M. Kouamé - R. Matsuda) :
 *   "Match terminé : 2 - 0 M. Kouamé"
 *   "2d set terminé : [6] - 3 M. Kouamé"
 *   "1er set terminé : [6] - 1 M. Kouamé"
 *   "Match commencé"
 *
 * Exemple réel observé pour le tennis de table (capture d'écran,
 * 15/09/2026, Choi H. - Wang J.) :
 *   "3e set terminé : [2] - 1 Choi H."
 * Contrairement au tennis, "[2] - 1" n'est PAS le score de jeux/points du
 * set qui vient de finir : c'est déjà le nombre de SETS gagnés par
 * chacun (2 pour Choi H., 1 pour Wang J.) — voir [parseSetTally] pour la
 * façon dont on distingue les deux cas. Présumé pareil en volley (même
 * modèle de notification chez Sofascore), à confirmer sur un exemple réel
 * le jour où Yann en enverra un.
 *
 * Les noms de joueurs sont déjà au format "Initiale. Nom" dans la notif
 * elle-même (titre ET lignes d'événement, tennis ET tennis de table) —
 * rien à transformer côté app.
 */
object SofascoreNotificationParser {

    // --- Foot ---

    private val matchFinished = Regex("""Match termin[ée]\s*:\s*(\d+)\s*-\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val halfTime = Regex("""Mi-temps\s*:\s*(\d+)\s*-\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val secondHalfStarted = Regex("""2\w*\s*mi-temps a commenc[ée]\s*:\s*(\d+)\s*-\s*(\d+)""", RegexOption.IGNORE_CASE)
    // CONFIRMÉ par un exemple réel (14/09/2026) : Sofascore écrit
    // "Match commencé" — sans "a" (pas "Match A commencé") et sans
    // score accolé. Le "a" et le score restent tous deux optionnels
    // dans le gabarit pour couvrir aussi une éventuelle formulation
    // "Le match a commencé (: 0 - 0)" ou "1ère mi-temps a commencé" si
    // Sofascore les utilise ailleurs (foot à un autre niveau).
    // PARTAGÉ AVEC TOUS LES AUTRES SPORTS : ce même libellé "Match
    // commencé" est aussi celui observé en tennis et présumé pareil
    // ailleurs (voir doc de classe) — c'est sans conséquence ici puisque
    // parse() ne l'utilise que si AUCUNE ligne "set terminé" n'est
    // présente, donc uniquement avant la fin du 1er set.
    private val firstHalfStarted = Regex(
        """(?:1\w*\s*mi-temps|match)(?:\s+a)?\s+commenc[ée](?:\s*:\s*(\d+)\s*-\s*(\d+))?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Gabarit générique "MM' <libellé> : score - score" — couvre "But"
     * (confirmé) et, par construction, tout futur libellé horodaté par une
     * minute (carton, but annulé/corrigé après VAR...) SANS avoir besoin de
     * connaître le mot exact : quel que soit l'événement, les deux nombres
     * après les deux-points sont déjà le score à jour calculé par Sofascore,
     * donc pas besoin de les recalculer nous-mêmes (ex. une correction de
     * score après un but annulé donnera directement le bon score ici).
     */
    private val timedEvent = Regex(
        """(\d{1,3})'\s*[^:]+?\s*:\s*(?:\[(\d+)\]|(\d+))\s*-\s*(?:\[(\d+)\]|(\d+))""",
        RegexOption.IGNORE_CASE
    )

    // CONFIRMÉ par un exemple réel (15/09/2026, Kasuka FC - Karketu Díli,
    // une ligue moins couverte par Sofascore) : pas de minute du tout sur
    // certaines lignes de but ("But: [2] - 0 Kasuka FC", le nom qui suit
    // étant alors celui de l'ÉQUIPE plutôt que d'un joueur — sans
    // conséquence ici, ce nom n'est de toute façon pas exploité, ni ici ni
    // dans [timedEvent], seuls les deux scores comptent). Sans minute, pas
    // de "MM+" possible : statut vide plutôt que d'en inventer une — voir
    // wear/ScoreComplicationService.kt, qui omet alors le "· " devant le
    // score plutôt que d'afficher un statut vide ou trompeur.
    private val goalNoMinute = Regex(
        """But\s*:\s*(?:\[(\d+)\]|(\d+))\s*-\s*(?:\[(\d+)\]|(\d+))""",
        RegexOption.IGNORE_CASE
    )

    // --- Tennis / sports à set ---

    /**
     * "1er set terminé : 6 - [7] L. Samsonova" (tennis) ou "3e set terminé
     * : [2] - 1 Choi H." (tennis de table) -> groupe 1 = numéro d'ordre du
     * set, groupes 2/3 = score du premier nombre (domicile, bracketé ou
     * non), groupes 4/5 = score du second nombre (extérieur), groupe 6 =
     * nom du vainqueur du set (reste de la ligne après le score). Le
     * MÊME gabarit sert aux deux sports — voir [parse] pour comment on les
     * distingue (la somme des deux scores égale, ou non, le numéro
     * d'ordre du set).
     */
    private val setEnded = Regex(
        """(\d+)\w*\s*set termin[ée]\s*:\s*(?:\[(\d+)\]|(\d+))\s*-\s*(?:\[(\d+)\]|(\d+))\s+(.+)""",
        RegexOption.IGNORE_CASE
    )

    // Pas d'exemple réel de fin de match en tennis de table/volley pour
    // l'instant — gabarit délibérément permissif (juste "Match terminé",
    // sans exiger de score accolé, contrairement à [matchFinished] côté
    // foot), présumé identique au tennis (confirmé, lui, par un exemple
    // réel — voir doc de classe) : le score final n'est de toute façon
    // pas lu sur CETTE ligne, il vient de [setEnded] (comptage cumulé en
    // tennis via [parseTennis], lu directement sur la dernière ligne en
    // tennis de table/volley via [parseSetTally]), donc peu importe le
    // format exact du reste de cette ligne.
    private val setSportFinished = Regex("""Match termin[ée]""", RegexOption.IGNORE_CASE)

    /**
     * @return un MatchResult avec un statut au vocabulaire déjà connu de
     * wear/MatchClock.kt — FT/HT/2H/1H (traduits en Fin/MT/P2/P1 côté
     * montre) en foot, sauf le but horodaté qui pousse directement "MM+"
     * (ex. "37+", voir [timedEvent]) ou sans minute (statut vide, voir
     * [goalNoMinute]) ; live/completed en tennis (traduit en Fin côté
     * montre) ; "S<N>"/"Fin" en tennis de table/volley (déjà le format
     * affiché tel quel côté montre, voir [parseSetTally]) — pour ne RIEN
     * avoir à changer côté montre au niveau du parsing (la traduction en
     * français, elle, se fait dans MatchClock.kt). Null si aucune ligne
     * foot n'est reconnue ET qu'aucun set n'est détecté (ex. sport pas
     * encore couvert, ou événement foot/tennis pas encore couvert) — dans
     * ce cas, SofascoreNotificationListenerService retombe sur un repli
     * neutre qui affiche le texte brut de la ligne la plus récente.
     */
    fun parse(homeTeam: String, awayTeam: String, lines: List<String>): MatchResult? {
        val mostRecentSet = lines.firstNotNullOfOrNull { setEnded.find(it.trim()) }

        if (mostRecentSet != null) {
            val ordinal = mostRecentSet.groupValues[1].toIntOrNull()
            val firstScore = mostRecentSet.groupValues[2].ifBlank { mostRecentSet.groupValues[3] }.toIntOrNull()
            val secondScore = mostRecentSet.groupValues[4].ifBlank { mostRecentSet.groupValues[5] }.toIntOrNull()

            // Voir la doc de classe : cette égalité ne peut être vraie que
            // si les deux scores sont un décompte CUMULATIF de sets
            // (tennis de table, volley), pas des jeux/points du set qui
            // vient de finir (tennis).
            if (ordinal != null && firstScore != null && secondScore != null && firstScore + secondScore == ordinal) {
                return parseSetTally(homeTeam, awayTeam, lines, ordinal, firstScore, secondScore)
            }
            return parseTennis(homeTeam, awayTeam, lines)
        }
        return parseFootball(homeTeam, awayTeam, lines)
    }

    private fun parseFootball(homeTeam: String, awayTeam: String, lines: List<String>): MatchResult? {
        for (raw in lines) {
            val line = raw.trim()

            matchFinished.find(line)?.let { m ->
                return build(homeTeam, awayTeam, m.groupValues[1], m.groupValues[2], "FT")
            }
            halfTime.find(line)?.let { m ->
                return build(homeTeam, awayTeam, m.groupValues[1], m.groupValues[2], "HT")
            }
            secondHalfStarted.find(line)?.let { m ->
                return build(homeTeam, awayTeam, m.groupValues[1], m.groupValues[2], "2H")
            }
            firstHalfStarted.find(line)?.let { m ->
                val home = m.groupValues[1].ifBlank { "0" }
                val away = m.groupValues[2].ifBlank { "0" }
                return build(homeTeam, awayTeam, home, away, "1H")
            }
            timedEvent.find(line)?.let { m ->
                val minute = m.groupValues[1]
                val home = m.groupValues[2].ifBlank { m.groupValues[3] }
                val away = m.groupValues[4].ifBlank { m.groupValues[5] }
                // "37+" (demandé par Yann le 15/09/2026), pas juste la
                // minute nue : évite de passer par la règle générique de
                // wear/MatchClock.kt ("$status'", pensée pour un tout
                // autre cas — une minute brute parfois renvoyée par
                // TheSportsDB, sans rapport avec un but) et affiche
                // directement le format voulu via sa branche `else`.
                return build(homeTeam, awayTeam, home, away, "$minute+")
            }
            goalNoMinute.find(line)?.let { m ->
                val home = m.groupValues[1].ifBlank { m.groupValues[2] }
                val away = m.groupValues[3].ifBlank { m.groupValues[4] }
                return build(homeTeam, awayTeam, home, away, "")
            }
        }
        return null
    }

    /**
     * Compte les sets gagnés par chaque joueur en additionnant TOUTES les
     * lignes [setEnded] présentes (pas juste la plus récente, contrairement
     * au tennis de table/volley) : chaque ligne de fin de set nomme son
     * vainqueur, on incrémente son compteur en comparant ce nom à
     * [homeTeam]/[awayTeam] (comparaison exacte, insensible à la casse —
     * les deux viennent de la même notif Sofascore donc au même format
     * "Initiale. Nom", pas de transformation nécessaire). "Match terminé"
     * (peu importe le contenu après, voir [setSportFinished]) donne le
     * statut "completed", sinon "live" puisqu'on sait déjà qu'au moins un
     * set est fini. Pas de score de jeux du set en cours (Sofascore ne le
     * donne pas en direct dans ces notifs) : [MatchResult.currentSetHomeGames]/
     * [MatchResult.currentSetAwayGames] restent null, wear/MatchClock.kt
     * affiche alors "En direct" plutôt qu'un score de set détaillé.
     */
    private fun parseTennis(homeTeam: String, awayTeam: String, lines: List<String>): MatchResult {
        var homeSets = 0
        var awaySets = 0
        for (raw in lines) {
            val line = raw.trim()
            setEnded.find(line)?.let { m ->
                when (m.groupValues[6].trim()) {
                    homeTeam -> homeSets++
                    awayTeam -> awaySets++
                    else -> Unit // nom du vainqueur inattendu (format différent ?) — set ignoré du décompte plutôt que de deviner
                }
            }
        }
        val finished = lines.any { setSportFinished.containsMatchIn(it) }
        return MatchResult(
            id = "sofascore_fallback",
            source = ApiSource.LIVE_TENNIS,
            idHomeTeam = null,
            idAwayTeam = null,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            homeScore = homeSets.toString(),
            awayScore = awaySets.toString(),
            date = SportsDbApi.todayUtcDateString(),
            time = null,
            status = if (finished) "completed" else "live",
            league = "Sofascore",
            kickoffEpochMillis = null
        )
    }

    /**
     * Tennis de table, et présumé volley (voir doc de classe) : la ligne
     * "Xe set terminé" la plus récente donne DÉJÀ le score total à jour
     * (nombre de sets gagnés par chacun) — pas besoin d'additionner
     * plusieurs lignes comme au tennis, [mostRecentSetOrdinal]/
     * [firstScore]/[secondScore] viennent directement de [parse]. Statut
     * "S<N>" (N = numéro du set en cours = dernier set terminé + 1) tant
     * que le match n'est pas fini, "Fin" sinon (voir [setSportFinished]) —
     * "S<N>" est déjà le format utilisé tel quel par TheSportsDB pour le
     * volley (`S1`..`S5`, voir wear/MatchClock.kt), affiché sans
     * traduction dédiée côté montre : cohérent par construction, sans
     * l'avoir cherché.
     */
    private fun parseSetTally(
        homeTeam: String,
        awayTeam: String,
        lines: List<String>,
        mostRecentSetOrdinal: Int,
        firstScore: Int,
        secondScore: Int
    ): MatchResult {
        val finished = lines.any { setSportFinished.containsMatchIn(it) }
        val status = if (finished) "Fin" else "S${mostRecentSetOrdinal + 1}"
        return MatchResult(
            id = "sofascore_fallback",
            source = ApiSource.SPORTS_DB,
            idHomeTeam = null,
            idAwayTeam = null,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            homeScore = firstScore.toString(),
            awayScore = secondScore.toString(),
            date = SportsDbApi.todayUtcDateString(),
            time = null,
            status = status,
            league = "Sofascore",
            kickoffEpochMillis = null
        )
    }

    private fun build(
        homeTeam: String,
        awayTeam: String,
        homeScore: String,
        awayScore: String,
        status: String
    ) = MatchResult(
        id = "sofascore_fallback",
        source = ApiSource.SPORTS_DB,
        idHomeTeam = null,
        idAwayTeam = null,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        homeScore = homeScore,
        awayScore = awayScore,
        date = SportsDbApi.todayUtcDateString(),
        time = null,
        status = status,
        league = "Sofascore",
        kickoffEpochMillis = null
    )
}

package com.yann.sportscomplication.mobile

/**
 * Transforme les lignes d'une notification Sofascore (une notif = un match,
 * voir SofascoreNotificationListenerService) en un [MatchResult] de repli.
 * Détecte le sport à partir du contenu des lignes (pas d'indicateur dédié
 * dans la notif) : la présence d'au moins une ligne "Xe set terminé" fait
 * basculer sur le vocabulaire tennis ([parseTennis]) ; sinon, gabarit foot
 * ([parseFootball]).
 *
 * [lines] doit être trié DU PLUS RÉCENT AU PLUS ANCIEN (comme affiché dans
 * le panneau de notifications).
 *
 * Un match qui vient de démarrer (coup d'envoi) affiche 0-0 — la notif
 * Sofascore confirmée pour cet événement ("Match commencé", sans score) ne
 * le précise pas, voir [firstHalfStarted]. **Ambiguïté foot/tennis à ce
 * stade précis** : "Match commencé" est le MÊME libellé dans les deux
 * sports (confirmé, voir l'exemple tennis plus bas) et rien ne les
 * distingue tant qu'aucun set n'est encore terminé — un match de tennis qui
 * vient tout juste de démarrer est donc affiché avec le gabarit foot (0-0,
 * statut "1H") jusqu'à la fin du 1er set, où le vocabulaire tennis prend le
 * relais automatiquement. Léger défaut d'affichage temporaire, sans
 * conséquence au-delà de quelques minutes en début de match.
 *
 * Exemple réel observé (capture d'écran, 12/09/2026, Real Madrid - Rayo
 * Vallecano) :
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
 * Exemple réel observé pour le tennis (capture d'écran, 14/09/2026,
 * E. Jacquemot - L. Samsonova) :
 *   "1er set terminé : 6 - [7] L. Samsonova"
 *   "Match commencé"
 * Les noms de joueurs sont déjà au format "Initiale. Nom" dans la notif
 * elle-même (titre ET lignes d'événement) — rien à transformer côté app.
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
    // PARTAGÉ AVEC LE TENNIS : ce même libellé "Match commencé" est aussi
    // celui observé en tennis (voir doc de classe) — c'est sans
    // conséquence ici puisque parse() ne l'utilise que si AUCUNE ligne
    // "set terminé" n'est présente, donc uniquement avant la fin du 1er set.
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

    // --- Tennis ---

    /**
     * "1er set terminé : 6 - [7] L. Samsonova" -> groupe 1 = nom du
     * vainqueur du set (reste de la ligne après le score). Les deux
     * nombres de score de JEUX ne sont pas capturés : on ne les affiche
     * pas (Sofascore ne donne le score de jeux qu'À LA FIN d'un set, pas
     * pendant — pas de quoi alimenter [MatchResult.currentSetHomeGames]
     * en direct), seul le nom du vainqueur sert à incrémenter le compteur
     * de sets ([parseTennis]).
     */
    private val setEnded = Regex(
        """\d+\w*\s*set termin[ée]\s*:\s*(?:\[\d+\]|\d+)\s*-\s*(?:\[\d+\]|\d+)\s+(.+)""",
        RegexOption.IGNORE_CASE
    )

    // Pas d'exemple réel de fin de match en tennis pour l'instant — gabarit
    // délibérément permissif (juste "Match terminé", sans exiger de score
    // accolé, contrairement à [matchFinished] côté foot) : le score final
    // en sets n'est de toute façon pas lu ici, il est déduit en comptant
    // les lignes [setEnded] (voir [parseTennis]), donc peu importe le
    // format exact du reste de cette ligne.
    private val tennisMatchFinished = Regex("""Match termin[ée]""", RegexOption.IGNORE_CASE)

    /**
     * @return un MatchResult avec un statut au vocabulaire déjà connu de
     * wear/MatchClock.kt — FT/HT/2H/1H (traduits en Fin/MT/P2/P1 côté
     * montre) en foot, sauf le but horodaté qui pousse directement "MM+"
     * (ex. "37+", voir [timedEvent]) ; live/completed en tennis (traduit
     * en Fin côté montre) — pour ne RIEN avoir à changer côté montre au
     * niveau du parsing (la traduction en français, elle, se fait dans
     * MatchClock.kt). Null si aucune ligne foot n'est reconnue ET qu'aucun
     * set n'est détecté (ex. sport pas encore couvert, ou événement
     * foot/tennis pas encore couvert) — dans ce cas,
     * SofascoreNotificationListenerService retombe sur un repli neutre
     * qui affiche le texte brut de la ligne la plus récente.
     */
    fun parse(homeTeam: String, awayTeam: String, lines: List<String>): MatchResult? {
        if (lines.any { setEnded.containsMatchIn(it) }) {
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
        }
        return null
    }

    /**
     * Compte les sets gagnés par chaque joueur en additionnant TOUTES les
     * lignes [setEnded] présentes (pas juste la plus récente, contrairement
     * au foot) : chaque ligne de fin de set nomme son vainqueur, on
     * incrémente son compteur en comparant ce nom à [homeTeam]/[awayTeam]
     * (comparaison exacte, insensible à la casse — les deux viennent de la
     * même notif Sofascore donc au même format "Initiale. Nom", pas de
     * transformation nécessaire). "Match terminé" (peu importe le contenu
     * après, voir [tennisMatchFinished]) donne le statut "completed",
     * sinon "live" puisqu'on sait déjà qu'au moins un set est fini. Pas de
     * score de jeux du set en cours (Sofascore ne le donne pas en direct
     * dans ces notifs) : [MatchResult.currentSetHomeGames]/
     * [MatchResult.currentSetAwayGames] restent null, wear/MatchClock.kt
     * affiche alors "En direct" plutôt qu'un score de set détaillé.
     */
    private fun parseTennis(homeTeam: String, awayTeam: String, lines: List<String>): MatchResult {
        var homeSets = 0
        var awaySets = 0
        for (raw in lines) {
            val line = raw.trim()
            setEnded.find(line)?.let { m ->
                when (m.groupValues[1].trim()) {
                    homeTeam -> homeSets++
                    awayTeam -> awaySets++
                    else -> Unit // nom du vainqueur inattendu (format différent ?) — set ignoré du décompte plutôt que de deviner
                }
            }
        }
        val finished = lines.any { tennisMatchFinished.containsMatchIn(it) }
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

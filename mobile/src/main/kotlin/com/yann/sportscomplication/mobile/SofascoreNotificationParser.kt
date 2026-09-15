package com.yann.sportscomplication.mobile

/**
 * Transforme les lignes d'un groupe de notifications Sofascore (un groupe =
 * un match, voir SofascoreNotificationListenerService) en un [MatchResult]
 * de repli. NE COUVRE QUE LE FOOT pour l'instant — le tennis sera ajouté dès
 * qu'un exemple réel de notif (fin de set / fin de match) sera disponible.
 *
 * [lines] doit être trié DU PLUS RÉCENT AU PLUS ANCIEN (comme affiché dans
 * le panneau de notifications) : on s'arrête à la première ligne reconnue,
 * ce qui donne l'état courant du match même si des lignes plus anciennes et
 * non reconnues (carton, corner...) se trouvent plus bas dans la liste.
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
 */
object SofascoreNotificationParser {

    private val matchFinished = Regex("""Match termin[ée]\s*:\s*(\d+)\s*-\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val halfTime = Regex("""Mi-temps\s*:\s*(\d+)\s*-\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val secondHalfStarted = Regex("""2\w*\s*mi-temps a commenc[ée]\s*:\s*(\d+)\s*-\s*(\d+)""", RegexOption.IGNORE_CASE)
    // Non confirmé par un exemple réel (pas encore vu de notif de coup
    // d'envoi) — best-effort, sans risque si ça ne matche jamais.
    private val firstHalfStarted = Regex("""(?:1\w*\s*mi-temps|match) a commenc[ée]\s*:\s*(\d+)\s*-\s*(\d+)""", RegexOption.IGNORE_CASE)

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

    /**
     * @return un MatchResult avec un statut au vocabulaire déjà connu de
     * wear/MatchClock.kt (FT/HT/2H/1H, ou un nombre nu affiché "$statut'")
     * — pour ne RIEN avoir à changer côté montre. Null si aucune ligne
     * n'est reconnue (ex. tennis, ou événement foot pas encore couvert) —
     * dans ce cas, SofascoreNotificationListenerService retombe sur un
     * repli neutre qui affiche le texte brut de la ligne la plus récente.
     */
    fun parse(homeTeam: String, awayTeam: String, lines: List<String>): MatchResult? {
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
                return build(homeTeam, awayTeam, m.groupValues[1], m.groupValues[2], "1H")
            }
            timedEvent.find(line)?.let { m ->
                val minute = m.groupValues[1]
                val home = m.groupValues[2].ifBlank { m.groupValues[3] }
                val away = m.groupValues[4].ifBlank { m.groupValues[5] }
                return build(homeTeam, awayTeam, home, away, minute)
            }
        }
        return null
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

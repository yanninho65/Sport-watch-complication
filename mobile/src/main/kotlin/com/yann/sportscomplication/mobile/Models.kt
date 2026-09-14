package com.yann.sportscomplication.mobile

data class TeamResult(
    val id: String,
    val name: String
)

data class MatchResult(
    val id: String,
    val idHomeTeam: String?,
    val idAwayTeam: String?,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: String?,
    val awayScore: String?,
    val date: String,
    val time: String?,
    val status: String,
    val league: String,
    /** Horodatage du coup d'envoi (UTC, epoch ms), ou null si non calculable. */
    val kickoffEpochMillis: Long?
) {
    /** Ex. "PSG 2-1 OM" si le score est connu, sinon "PSG vs OM". */
    val title: String
        get() = if (homeScore != null && awayScore != null) {
            "$homeTeam $homeScore-$awayScore $awayTeam"
        } else {
            "$homeTeam vs $awayTeam"
        }

    val details: String
        get() = "$league · $date${time?.let { " $it" } ?: ""} · $status"

    /** Un match dans cet état n'a plus besoin d'être suivi (arrêt du polling). */
    val isFinished: Boolean
        get() = status.contains("Finished", ignoreCase = true) ||
            status.equals("FT", ignoreCase = true) ||
            status.contains("Postponed", ignoreCase = true) ||
            status.contains("Cancelled", ignoreCase = true) ||
            status.contains("Abandoned", ignoreCase = true)
}

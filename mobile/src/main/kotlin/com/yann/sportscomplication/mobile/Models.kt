package com.yann.sportscomplication.mobile

data class TeamResult(
    val id: String,
    val name: String
)

data class MatchResult(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: String?,
    val awayScore: String?,
    val date: String,
    val time: String?,
    val status: String,
    val league: String
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

    /**
     * Ce qu'on envoie à la montre comme équivalent de la "minute" —
     * TheSportsDB (plan gratuit) ne donne pas de minute de jeu en direct
     * fiable, donc on utilise le statut si connu ("Match Finished"...),
     * sinon la date/heure du match à venir.
     */
    val minuteLabel: String
        get() = status.ifBlank { "$date${time?.let { " $it" } ?: ""}" }
}

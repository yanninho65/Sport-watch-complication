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
}

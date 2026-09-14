package com.yann.sportscomplication.mobile

import android.content.Context

/**
 * Persiste (SharedPreferences) le dernier match suivi, pour que
 * MainActivity puisse réafficher la carte "match suivi" après avoir été
 * fermée puis rouverte — le polling réel vit dans MatchFollowService,
 * pas ici ; ce fichier ne fait que mémoriser le dernier état connu pour
 * l'affichage.
 */
object FollowedMatchPrefs {

    private const val PREFS_NAME = "followed_match"

    fun save(context: Context, match: MatchResult) {
        prefs(context).edit().apply {
            putString("id", match.id)
            putString("apiSource", match.source.name)
            putString("idHomeTeam", match.idHomeTeam)
            putString("idAwayTeam", match.idAwayTeam)
            putString("homeTeam", match.homeTeam)
            putString("awayTeam", match.awayTeam)
            putString("homeScore", match.homeScore)
            putString("awayScore", match.awayScore)
            if (match.currentSetHomeGames != null) {
                putInt("currentSetHomeGames", match.currentSetHomeGames)
            } else {
                remove("currentSetHomeGames")
            }
            if (match.currentSetAwayGames != null) {
                putInt("currentSetAwayGames", match.currentSetAwayGames)
            } else {
                remove("currentSetAwayGames")
            }
            putString("date", match.date)
            putString("time", match.time)
            putString("status", match.status)
            putString("league", match.league)
            if (match.kickoffEpochMillis != null) {
                putLong("kickoff", match.kickoffEpochMillis)
            } else {
                remove("kickoff")
            }
        }.apply()
    }

    fun load(context: Context): MatchResult? {
        val p = prefs(context)
        val id = p.getString("id", null) ?: return null
        val homeTeam = p.getString("homeTeam", null) ?: return null
        val awayTeam = p.getString("awayTeam", null) ?: return null
        // Repli SPORTS_DB : des prefs écrites avant l'introduction du tennis
        // n'ont pas cette clé.
        val source = p.getString("apiSource", null)?.let { name ->
            ApiSource.values().firstOrNull { it.name == name }
        } ?: ApiSource.SPORTS_DB
        return MatchResult(
            id = id,
            source = source,
            idHomeTeam = p.getString("idHomeTeam", null),
            idAwayTeam = p.getString("idAwayTeam", null),
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            homeScore = p.getString("homeScore", null),
            awayScore = p.getString("awayScore", null),
            currentSetHomeGames = if (p.contains("currentSetHomeGames")) p.getInt("currentSetHomeGames", 0) else null,
            currentSetAwayGames = if (p.contains("currentSetAwayGames")) p.getInt("currentSetAwayGames", 0) else null,
            date = p.getString("date", null) ?: "?",
            time = p.getString("time", null),
            status = p.getString("status", null).orEmpty(),
            league = p.getString("league", null).orEmpty(),
            kickoffEpochMillis = if (p.contains("kickoff")) p.getLong("kickoff", 0L) else null
        )
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

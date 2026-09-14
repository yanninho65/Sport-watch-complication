package com.yann.sportscomplication.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Client minimal pour TheSportsDB (endpoints v1 en lecture seule, sans
 * dépendance réseau externe — HttpURLConnection + org.json intégrés à
 * Android suffisent pour ce volume d'appels).
 *
 * NOTE : "3" est la clé de test publique documentée par TheSportsDB. Si
 * les quotas deviennent limitants, remplace-la par une clé gratuite
 * obtenue sur https://www.thesportsdb.com/api.php (inscription requise).
 */
object SportsDbApi {

    private const val API_KEY = "3"
    private const val BASE_URL = "https://www.thesportsdb.com/api/v1/json/$API_KEY"

    suspend fun searchTeams(query: String): List<TeamResult> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val json = fetchJson("$BASE_URL/searchteams.php?t=$encoded")
        val teams = json.optJSONArray("teams") ?: return@withContext emptyList()

        (0 until teams.length()).mapNotNull { i ->
            val team = teams.optJSONObject(i) ?: return@mapNotNull null
            val id = team.optString("idTeam", null) ?: return@mapNotNull null
            val name = team.optString("strTeam", null) ?: return@mapNotNull null
            TeamResult(id = id, name = name)
        }
    }

    /**
     * Combine les derniers matchs joués et les prochains matchs d'une
     * équipe, les plus proches de "maintenant" en premier.
     */
    suspend fun getMatchesForTeam(teamId: String): List<MatchResult> = withContext(Dispatchers.IO) {
        val last = fetchEvents("$BASE_URL/eventslast.php?id=$teamId")
        val next = fetchEvents("$BASE_URL/eventsnext.php?id=$teamId")
        last + next
    }

    private fun fetchEvents(url: String): List<MatchResult> {
        val json = fetchJson(url)
        val events = json.optJSONArray("events") ?: return emptyList()

        return (0 until events.length()).mapNotNull { i ->
            val e = events.optJSONObject(i) ?: return@mapNotNull null
            val id = e.optString("idEvent", null) ?: return@mapNotNull null
            MatchResult(
                id = id,
                homeTeam = e.optString("strHomeTeam", "?"),
                awayTeam = e.optString("strAwayTeam", "?"),
                homeScore = nullableString(e, "intHomeScore"),
                awayScore = nullableString(e, "intAwayScore"),
                date = e.optString("dateEvent", "?"),
                time = nullableString(e, "strTime"),
                status = e.optString("strStatus", ""),
                league = e.optString("strLeague", "")
            )
        }
    }

    private fun nullableString(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        val value = json.optString(key)
        return value.ifBlank { null }
    }

    private fun fetchJson(urlString: String): JSONObject {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            val code = connection.responseCode
            if (code !in 200..299) return JSONObject()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } catch (e: Exception) {
            JSONObject()
        } finally {
            connection.disconnect()
        }
    }
}

package com.yann.sportscomplication.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Client minimal pour TheSportsDB (endpoints v1 en lecture seule, sans
 * dépendance réseau externe — HttpURLConnection + org.json intégrés à
 * Android suffisent pour ce volume d'appels).
 *
 * NOTE : "3" est la clé de test publique documentée par TheSportsDB. Si
 * les quotas deviennent limitants, remplace-la par une clé gratuite
 * obtenue sur https://www.thesportsdb.com/api.php (inscription requise).
 *
 * LIMITES DU PLAN GRATUIT (vérifiées en pratique, pas seulement dans la
 * doc) : les recherches (équipe/joueur) ne renvoient souvent qu'1-2
 * résultats, et all_leagues.php ne renvoie qu'une dizaine de grandes
 * ligues de football (Premier League, Liga, Serie A, Bundesliga,
 * Ligue 1...) — largement suffisant pour PSG/OM/Ligue 1, mais pas pour
 * des ligues plus confidentielles ou d'autres sports. Un passage en
 * clé Premium lève ces limites.
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
            val id = nullableString(team, "idTeam") ?: return@mapNotNull null
            val name = nullableString(team, "strTeam") ?: return@mapNotNull null
            TeamResult(id = id, name = name)
        }
    }

    /** Recherche de joueurs par nom — chaque résultat porte son équipe actuelle (idTeam/strTeam), si connue. */
    suspend fun searchPlayers(query: String): List<PlayerResult> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val json = fetchJson("$BASE_URL/searchplayers.php?p=$encoded")
        // Particularité de cet endpoint : la racine JSON s'appelle "player"
        // (singulier), contrairement à "teams"/"events" (pluriels) ailleurs
        // dans l'API — vérifié en pratique, pas une coquille de notre part.
        val players = json.optJSONArray("player") ?: return@withContext emptyList()

        (0 until players.length()).mapNotNull { i ->
            val p = players.optJSONObject(i) ?: return@mapNotNull null
            val id = nullableString(p, "idPlayer") ?: return@mapNotNull null
            val name = nullableString(p, "strPlayer") ?: return@mapNotNull null
            PlayerResult(
                id = id,
                name = name,
                teamId = nullableString(p, "idTeam"),
                teamName = nullableString(p, "strTeam")
            )
        }
    }

    /**
     * Recherche de ligues par nom. Il n'existe pas d'endpoint v1 de
     * recherche textuelle pour les ligues (contrairement aux équipes et
     * joueurs) — on récupère donc la liste complète via all_leagues.php
     * et on filtre côté client sur le nom. Avec la clé gratuite, cette
     * liste ne contient qu'une dizaine de grandes ligues de football
     * (voir note de classe ci-dessus).
     */
    suspend fun searchLeagues(query: String): List<LeagueResult> = withContext(Dispatchers.IO) {
        val json = fetchJson("$BASE_URL/all_leagues.php")
        val leagues = json.optJSONArray("leagues") ?: return@withContext emptyList()

        (0 until leagues.length()).mapNotNull { i ->
            val l = leagues.optJSONObject(i) ?: return@mapNotNull null
            val id = nullableString(l, "idLeague") ?: return@mapNotNull null
            val name = nullableString(l, "strLeague") ?: return@mapNotNull null
            if (!name.contains(query, ignoreCase = true)) return@mapNotNull null
            LeagueResult(id = id, name = name, sport = l.optString("strSport", ""))
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

    /**
     * Matchs du jour pour une ligue donnée (déjà filtrés "aujourd'hui" côté
     * serveur par eventsday.php, contrairement à getMatchesForTeam qui doit
     * être filtré côté client — voir MainActivity.isToday).
     */
    suspend fun getMatchesForLeagueToday(leagueId: String): List<MatchResult> = withContext(Dispatchers.IO) {
        fetchEvents("$BASE_URL/eventsday.php?d=${todayUtcDateString()}&l=$leagueId")
    }

    /** Relit un match précis par son id — utilisé pour le polling périodique. */
    suspend fun lookupEvent(eventId: String): MatchResult? = withContext(Dispatchers.IO) {
        val json = fetchJson("$BASE_URL/lookupevent.php?id=$eventId")
        val events = json.optJSONArray("events") ?: return@withContext null
        val e = events.optJSONObject(0) ?: return@withContext null
        parseMatch(e)
    }

    /** URL du badge d'une équipe, ou null si introuvable. */
    suspend fun getTeamBadgeUrl(teamId: String): String? = withContext(Dispatchers.IO) {
        val json = fetchJson("$BASE_URL/lookupteam.php?id=$teamId")
        val teams = json.optJSONArray("teams") ?: return@withContext null
        val team = teams.optJSONObject(0) ?: return@withContext null
        // Le nom exact du champ varie selon la doc/version de l'API
        // ("strTeamBadge" le plus souvent, "strBadge" dans certains
        // exemples) — on essaie les deux par prudence.
        nullableString(team, "strTeamBadge") ?: nullableString(team, "strBadge")
    }

    /** Télécharge les octets bruts d'une image (logo). Null si échec. */
    suspend fun downloadBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            if (connection.responseCode !in 200..299) return@withContext null
            connection.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Date du jour au format "yyyy-MM-dd" en UTC — TheSportsDB exprime ses
     * dates d'événement en UTC (`dateEvent`), donc "aujourd'hui" doit être
     * comparé dans le même référentiel pour que le filtrage soit correct.
     */
    fun todayUtcDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

    /** Vrai si [eventDate] (format "yyyy-MM-dd") correspond à aujourd'hui en UTC. */
    fun isToday(eventDate: String): Boolean = eventDate == todayUtcDateString()

    private fun fetchEvents(url: String): List<MatchResult> {
        val json = fetchJson(url)
        val events = json.optJSONArray("events") ?: return emptyList()
        return (0 until events.length()).mapNotNull { i ->
            events.optJSONObject(i)?.let { parseMatch(it) }
        }
    }

    private fun parseMatch(e: JSONObject): MatchResult? {
        val id = nullableString(e, "idEvent") ?: return null
        val date = e.optString("dateEvent", "?")
        val time = nullableString(e, "strTime")

        return MatchResult(
            id = id,
            idHomeTeam = nullableString(e, "idHomeTeam"),
            idAwayTeam = nullableString(e, "idAwayTeam"),
            homeTeam = e.optString("strHomeTeam", "?"),
            awayTeam = e.optString("strAwayTeam", "?"),
            homeScore = nullableString(e, "intHomeScore"),
            awayScore = nullableString(e, "intAwayScore"),
            date = date,
            time = time,
            status = e.optString("strStatus", ""),
            league = e.optString("strLeague", ""),
            kickoffEpochMillis = parseKickoffEpochMillis(date, time)
        )
    }

    /** [date] et [time] sont exprimés en UTC par TheSportsDB. */
    private fun parseKickoffEpochMillis(date: String, time: String?): Long? {
        if (time.isNullOrBlank() || date == "?") return null
        return try {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            formatter.parse("$date $time")?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun nullableString(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        val value = json.optString(key)
        return value.ifBlank { null }
    }

    private fun fetchJson(urlString: String): JSONObject {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val code = connection.responseCode
            if (code !in 200..299) return JSONObject()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } catch (e: Exception) {
            JSONObject()
        } finally {
            connection?.disconnect()
        }
    }
}

package com.yann.sportscomplication.mobile

import android.content.Intent
import android.os.Bundle

/**
 * Quelle API interroger pour rafraîchir un [MatchResult] (voir
 * MainActivity.ApiMode, LiveTennisApi.kt et
 * MatchFollowService.pollIntervalMillis). Ne dit PAS quel sport précis
 * a été cherché — TheSportsDB couvre plusieurs sports (foot, basket,
 * baseball... voir MainActivity.SportsDbSport) sous ce même SPORTS_DB.
 */
enum class ApiSource { SPORTS_DB, LIVE_TENNIS }

data class TeamResult(
    val id: String,
    val name: String
)

/** Résultat d'une recherche par joueur — [teamId]/[teamName] sont son équipe actuelle (peut être null). */
data class PlayerResult(
    val id: String,
    val name: String,
    val teamId: String?,
    val teamName: String?
)

/** Résultat d'une recherche de joueur de tennis (Live Tennis API) — id numérique côté API, contrairement aux ids texte de TheSportsDB. */
data class TennisPlayerResult(
    val id: Int,
    val name: String,
    val tour: String?,
    val ranking: Int?,
    val country: String?
)

/** Résultat d'une recherche par ligue (ex. "French Ligue 1", id "4334"). */
data class LeagueResult(
    val id: String,
    val name: String,
    val sport: String
)

data class MatchResult(
    val id: String,
    /** SPORTS_DB par défaut pour rester compatible avec le code existant — voir [ApiSource]. */
    val source: ApiSource = ApiSource.SPORTS_DB,
    val idHomeTeam: String?,
    val idAwayTeam: String?,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: String?,
    val awayScore: String?,
    /**
     * Score de JEUX du set en cours (tennis uniquement) — null en football,
     * et null en tennis tant qu'aucun set n'a commencé. À ne pas confondre
     * avec homeScore/awayScore ci-dessus, qui portent le nombre de SETS
     * gagnés. Alimenté par LiveTennisApi.parseMatch (dernier élément du
     * tableau `games` de l'API), relayé tel quel à la montre par WatchSync
     * — c'est wear/MatchClock.kt qui le met en forme ("3e set 4-3"),
     * comme il le fait déjà pour le statut brut de TheSportsDB.
     */
    val currentSetHomeGames: Int? = null,
    val currentSetAwayGames: Int? = null,
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

    /**
     * Un match dans cet état n'a plus besoin d'être suivi (arrêt du polling).
     * "completed" couvre le statut brut de Live Tennis API (tennis) ;
     * "Cancelled" couvre déjà celui de TheSportsDB ET de Live Tennis API,
     * qui utilisent tous deux ce mot (voir ApiSource, LiveTennisApi.kt).
     */
    val isFinished: Boolean
        get() = status.contains("Finished", ignoreCase = true) ||
            status.equals("FT", ignoreCase = true) ||
            status.contains("Postponed", ignoreCase = true) ||
            status.contains("Cancelled", ignoreCase = true) ||
            status.contains("Abandoned", ignoreCase = true) ||
            status.equals("completed", ignoreCase = true)
}

// Clés utilisées pour faire voyager un MatchResult dans un Bundle/Intent,
// entre MainActivity et MatchFollowService (voir toExtras / toMatchResult
// ci-dessous). Un Service n'a pas accès au MatchResult que possédait
// l'Activity qui l'a lancé — il faut le lui transmettre explicitement.
private const val EXTRA_ID = "com.yann.sportscomplication.mobile.extra.ID"
private const val EXTRA_API_SOURCE = "com.yann.sportscomplication.mobile.extra.API_SOURCE"
private const val EXTRA_ID_HOME_TEAM = "com.yann.sportscomplication.mobile.extra.ID_HOME_TEAM"
private const val EXTRA_ID_AWAY_TEAM = "com.yann.sportscomplication.mobile.extra.ID_AWAY_TEAM"
private const val EXTRA_HOME_TEAM = "com.yann.sportscomplication.mobile.extra.HOME_TEAM"
private const val EXTRA_AWAY_TEAM = "com.yann.sportscomplication.mobile.extra.AWAY_TEAM"
private const val EXTRA_HOME_SCORE = "com.yann.sportscomplication.mobile.extra.HOME_SCORE"
private const val EXTRA_AWAY_SCORE = "com.yann.sportscomplication.mobile.extra.AWAY_SCORE"
private const val EXTRA_CURRENT_SET_HOME_GAMES = "com.yann.sportscomplication.mobile.extra.CURRENT_SET_HOME_GAMES"
private const val EXTRA_CURRENT_SET_AWAY_GAMES = "com.yann.sportscomplication.mobile.extra.CURRENT_SET_AWAY_GAMES"
private const val EXTRA_DATE = "com.yann.sportscomplication.mobile.extra.DATE"
private const val EXTRA_TIME = "com.yann.sportscomplication.mobile.extra.TIME"
private const val EXTRA_STATUS = "com.yann.sportscomplication.mobile.extra.STATUS"
private const val EXTRA_LEAGUE = "com.yann.sportscomplication.mobile.extra.LEAGUE"
private const val EXTRA_KICKOFF = "com.yann.sportscomplication.mobile.extra.KICKOFF"

/** Sérialise ce match dans un Bundle, pour le transmettre à MatchFollowService via un Intent. */
fun MatchResult.toExtras(): Bundle = Bundle().apply {
    putString(EXTRA_ID, id)
    putString(EXTRA_API_SOURCE, source.name)
    putString(EXTRA_ID_HOME_TEAM, idHomeTeam)
    putString(EXTRA_ID_AWAY_TEAM, idAwayTeam)
    putString(EXTRA_HOME_TEAM, homeTeam)
    putString(EXTRA_AWAY_TEAM, awayTeam)
    putString(EXTRA_HOME_SCORE, homeScore)
    putString(EXTRA_AWAY_SCORE, awayScore)
    currentSetHomeGames?.let { putInt(EXTRA_CURRENT_SET_HOME_GAMES, it) }
    currentSetAwayGames?.let { putInt(EXTRA_CURRENT_SET_AWAY_GAMES, it) }
    putString(EXTRA_DATE, date)
    putString(EXTRA_TIME, time)
    putString(EXTRA_STATUS, status)
    putString(EXTRA_LEAGUE, league)
    kickoffEpochMillis?.let { putLong(EXTRA_KICKOFF, it) }
}

/** Reconstruit un MatchResult depuis les extras posés par [toExtras], ou null si incomplet/absent. */
fun Intent.toMatchResult(): MatchResult? {
    val id = getStringExtra(EXTRA_ID) ?: return null
    val homeTeam = getStringExtra(EXTRA_HOME_TEAM) ?: return null
    val awayTeam = getStringExtra(EXTRA_AWAY_TEAM) ?: return null
    // Repli SPORTS_DB : un Bundle écrit avant l'introduction du tennis
    // (ex. FollowedMatchPrefs déjà en place sur le téléphone de Yann)
    // n'a pas cette clé — on ne veut pas planter dessus.
    val source = getStringExtra(EXTRA_API_SOURCE)?.let { name ->
        ApiSource.values().firstOrNull { it.name == name }
    } ?: ApiSource.SPORTS_DB
    return MatchResult(
        id = id,
        source = source,
        idHomeTeam = getStringExtra(EXTRA_ID_HOME_TEAM),
        idAwayTeam = getStringExtra(EXTRA_ID_AWAY_TEAM),
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        homeScore = getStringExtra(EXTRA_HOME_SCORE),
        awayScore = getStringExtra(EXTRA_AWAY_SCORE),
        currentSetHomeGames = if (hasExtra(EXTRA_CURRENT_SET_HOME_GAMES)) getIntExtra(EXTRA_CURRENT_SET_HOME_GAMES, 0) else null,
        currentSetAwayGames = if (hasExtra(EXTRA_CURRENT_SET_AWAY_GAMES)) getIntExtra(EXTRA_CURRENT_SET_AWAY_GAMES, 0) else null,
        date = getStringExtra(EXTRA_DATE) ?: "?",
        time = getStringExtra(EXTRA_TIME),
        status = getStringExtra(EXTRA_STATUS).orEmpty(),
        league = getStringExtra(EXTRA_LEAGUE).orEmpty(),
        kickoffEpochMillis = if (hasExtra(EXTRA_KICKOFF)) getLongExtra(EXTRA_KICKOFF, 0L) else null
    )
}

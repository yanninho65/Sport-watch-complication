package com.yann.sportscomplication

import android.graphics.Bitmap

/**
 * Représente l'état d'un match tel qu'affiché par la complication.
 *
 * [apiSource] est la valeur brute envoyée par le téléphone ("SPORTS_DB"
 * ou "LIVE_TENNIS", voir l'enum ApiSource dans mobile/Models.kt) —
 * MatchClock.kt s'en sert pour savoir quel vocabulaire de statut
 * interpréter et comment construire le libellé (un set en tennis n'a
 * pas d'équivalent en football). Ne dit pas quel sport précis a été
 * cherché côté TheSportsDB (foot, basket, baseball...).
 *
 * [homeScore] et [awayScore] sont nullables car un match pas encore
 * commencé n'a pas de score. En tennis, ils portent le nombre de SETS
 * gagnés par chaque joueur (pas de jeux ni de points).
 *
 * [currentSetHomeGames] / [currentSetAwayGames] portent le score de
 * JEUX du set en cours (tennis uniquement, null en football et null en
 * tennis tant qu'aucun set n'a commencé) — voir MatchClock.tennisLabel.
 *
 * [status] est la valeur brute renvoyée par l'API du sport concerné
 * ("Not Started", "1H", "2H", "Match Finished"... en football ;
 * "upcoming", "live", "completed", "cancelled"... en tennis), traduite
 * telle quelle par MatchClock.kt (aucune minute n'est plus calculée par
 * déduction). [kickoffEpochMillis] est l'horodatage du coup d'envoi
 * (UTC), affiché tel quel pour les matchs pas encore commencés
 * ("À venir · 20:00").
 *
 * [homeLogo] / [awayLogo] sont les vrais logos d'équipe reçus du
 * téléphone (Asset de la Data Layer API), ou `null` si le téléchargement
 * a échoué côté téléphone (systématiquement `null` en tennis, où il n'y
 * a pas de logo) — dans ce cas la complication retombe sur une icône
 * placeholder locale.
 */
data class MatchScore(
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val currentSetHomeGames: Int? = null,
    val currentSetAwayGames: Int? = null,
    val status: String,
    val kickoffEpochMillis: Long?,
    val homeLogo: Bitmap?,
    val awayLogo: Bitmap?,
    val apiSource: String = "SPORTS_DB"
)

/**
 * Cache en mémoire du dernier score reçu.
 *
 * ATTENTION : ce cache est volatile — il est perdu si le système tue le
 * processus de l'app entre deux requêtes de complication. Si le
 * processus watch redémarre, on revient à `null` ("aucun match") jusqu'à
 * la prochaine mise à jour envoyée par le téléphone. À corriger plus tard
 * en persistant la dernière valeur connue.
 *
 * Alimenté par MatchListenerService, qui reçoit les mises à jour du
 * téléphone via la Wear Data Layer API.
 */
object MatchScoreStore {

    @Volatile
    var current: MatchScore? = null
}

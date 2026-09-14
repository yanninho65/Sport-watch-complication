package com.yann.sportscomplication

import android.graphics.Bitmap

/**
 * Représente l'état d'un match tel qu'affiché par la complication.
 *
 * [homeScore] et [awayScore] sont nullables car un match pas encore
 * commencé n'a pas de score.
 *
 * [status] est la valeur brute renvoyée par TheSportsDB ("Not Started",
 * "1H", "2H", "Match Finished"...). [kickoffEpochMillis] est l'horodatage
 * du coup d'envoi (UTC), utilisé pour calculer une minute de jeu estimée
 * — voir MatchClock.kt. TheSportsDB (plan gratuit) ne fournit pas de
 * minute de jeu en direct fiable, donc ce n'est qu'une approximation qui
 * peut dériver de quelques minutes (arrêts de jeu, etc.).
 *
 * [homeLogo] / [awayLogo] sont les vrais logos d'équipe reçus du
 * téléphone (Asset de la Data Layer API), ou `null` si le téléchargement
 * a échoué côté téléphone — dans ce cas la complication retombe sur une
 * icône placeholder locale.
 */
data class MatchScore(
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val status: String,
    val kickoffEpochMillis: Long?,
    val homeLogo: Bitmap?,
    val awayLogo: Bitmap?
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

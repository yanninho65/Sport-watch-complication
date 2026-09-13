package com.yann.sportscomplication

/**
 * Représente l'état d'un match tel qu'affiché par la complication.
 *
 * Pour l'instant, [homeLogoResId] et [awayLogoResId] pointent vers une
 * icône placeholder locale. À l'étape suivante (intégration avec le
 * téléphone), ils seront remplacés par des logos réels téléchargés depuis
 * TheSportsDB — probablement en passant par un Icon.createWithBitmap(...)
 * plutôt qu'une resource id compilée en dur.
 */
data class MatchScore(
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int,
    val awayScore: Int,
    val minute: String,
    val homeLogoResId: Int,
    val awayLogoResId: Int
)

/**
 * Cache en mémoire du dernier score reçu.
 *
 * ATTENTION : ce cache est volatile — il est perdu si le système tue le
 * processus de l'app entre deux requêtes de complication. À l'étape
 * suivante, il faudra persister la dernière valeur connue (ex. via
 * SharedPreferences ou DataStore) pour que la complication survive à un
 * redémarrage du processus, et un WearableListenerService viendra
 * alimenter ce cache à partir des messages envoyés par le téléphone via
 * la Data Layer API.
 */
object MatchScoreStore {

    @Volatile
    var current: MatchScore? = null
}

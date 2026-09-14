package com.yann.sportscomplication

/**
 * Représente l'état d'un match tel qu'affiché par la complication.
 *
 * [homeScore] et [awayScore] sont nullables car un match pas encore
 * commencé n'a pas de score.
 *
 * [homeLogoResId] et [awayLogoResId] pointent vers une icône placeholder
 * locale. À l'étape suivante, ils seront remplacés par des logos réels
 * reçus du téléphone via Asset (Data Layer API) plutôt qu'une resource id
 * compilée en dur.
 */
data class MatchScore(
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val minute: String,
    val homeLogoResId: Int,
    val awayLogoResId: Int
)

/**
 * Cache en mémoire du dernier score reçu.
 *
 * ATTENTION : ce cache est volatile — il est perdu si le système tue le
 * processus de l'app entre deux requêtes de complication. Ça devient un
 * vrai problème maintenant que les données viennent du téléphone : si le
 * processus watch redémarre, on revient à `null` ("aucun match") jusqu'à
 * la prochaine mise à jour envoyée par le téléphone. À corriger plus tard
 * en persistant la dernière valeur connue (ex. SharedPreferences).
 *
 * Alimenté par MatchListenerService, qui reçoit les mises à jour du
 * téléphone via la Wear Data Layer API.
 */
object MatchScoreStore {

    @Volatile
    var current: MatchScore? = null
}

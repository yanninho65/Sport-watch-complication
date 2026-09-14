package com.yann.sportscomplication

/**
 * Calcule un libellé de statut/minute à afficher, à partir des données
 * disponibles gratuitement chez TheSportsDB (statut texte + horaire du
 * match), puisqu'aucune minute de jeu en direct fiable n'est fournie par
 * l'API gratuite.
 *
 * Hypothèses simplificatrices (documentées ici pour ne pas les oublier) :
 *  - 1ère mi-temps = 45 minutes après le coup d'envoi
 *  - pause de 15 minutes
 *  - 2e mi-temps = 45 minutes après la reprise estimée
 * Les arrêts de jeu, prolongations, etc. ne sont pas pris en compte —
 * c'est une estimation, pas le vrai chronomètre officiel.
 */
object MatchClock {

    private const val HALF_MINUTES = 45
    private const val HALFTIME_BREAK_MINUTES = 15
    private const val MINUTE_MILLIS = 60_000L

    fun label(match: MatchScore, nowMillis: Long = System.currentTimeMillis()): String {
        val status = match.status
        val kickoff = match.kickoffEpochMillis

        return when {
            status.isBlank() -> "?"

            status.contains("Not Started", ignoreCase = true) -> "À venir"

            status.contains("Finished", ignoreCase = true) ||
                status.equals("FT", ignoreCase = true) -> "Terminé"

            status.contains("Postponed", ignoreCase = true) ||
                status.contains("Cancelled", ignoreCase = true) ||
                status.contains("Abandoned", ignoreCase = true) -> status

            kickoff == null -> status

            status.contains("1H", ignoreCase = true) -> {
                val elapsed = ((nowMillis - kickoff) / MINUTE_MILLIS).toInt().coerceIn(0, HALF_MINUTES)
                "$elapsed'"
            }

            status.contains("2H", ignoreCase = true) -> {
                val secondHalfStart = kickoff + (HALF_MINUTES + HALFTIME_BREAK_MINUTES) * MINUTE_MILLIS
                val elapsed = (HALF_MINUTES + (nowMillis - secondHalfStart) / MINUTE_MILLIS)
                    .toInt()
                    .coerceIn(HALF_MINUTES, HALF_MINUTES * 2)
                "$elapsed'"
            }

            else -> status
        }
    }
}

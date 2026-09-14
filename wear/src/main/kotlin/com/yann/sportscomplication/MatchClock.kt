package com.yann.sportscomplication

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Traduit le statut brut renvoyé par TheSportsDB en un libellé court pour
 * la complication — SANS calculer de minute de jeu par déduction.
 *
 * Pourquoi pas de minute chiffrée : le plan gratuit de TheSportsDB ne
 * fournit une minute de jeu en direct (champ `strProgress`, format
 * "mm:ss - 1st/2nd...") que via l'API Livescores V2, réservée aux
 * abonnés Premium (9$/mois — voir https://www.thesportsdb.com/pricing).
 * `lookupevent.php` (utilisé ici, plan gratuit) ne renvoie qu'un statut
 * texte, sans minute. Ce fichier se contente donc de traduire ce texte
 * en français, sans y ajouter le moindre calcul basé sur l'heure du
 * coup d'envoi.
 *
 * Si Yann passe un jour au plan Premium, il devient possible d'appeler
 * `/api/v2/json/livescore/soccer` (en-tête `X-API-KEY`) pour récupérer
 * `strProgress` et afficher une vraie minute — à faire dans un futur
 * SportsDbApiV2 dédié, pas ici.
 *
 * Les statuts testés ci-dessous couvrent à la fois les codes courts
 * officiels documentés par TheSportsDB pour le football (NS, 1H, HT,
 * 2H, FT... voir https://www.thesportsdb.com/docs_api_data) et les
 * libellés plus longs ("Not Started", "Match Finished"...) réellement
 * observés en pratique sur lookupevent.php / eventsnext.php / eventslast.php
 * avec la clé gratuite.
 */
object MatchClock {

    private val kickoffTimeFormat = SimpleDateFormat("HH:mm", Locale.FRANCE)

    fun label(match: MatchScore): String {
        val status = match.status.trim()

        return when {
            status.isBlank() -> "?"

            status.equals("NS", ignoreCase = true) ||
                status.equals("TBD", ignoreCase = true) ||
                status.contains("Not Started", ignoreCase = true) -> {
                val kickoff = match.kickoffEpochMillis
                if (kickoff != null) "À venir · ${kickoffTimeFormat.format(Date(kickoff))}" else "À venir"
            }

            status.equals("HT", ignoreCase = true) -> "Mi-temps"

            status.equals("1H", ignoreCase = true) || status.contains("1H", ignoreCase = true) -> "1ère MT"

            status.equals("2H", ignoreCase = true) || status.contains("2H", ignoreCase = true) -> "2e MT"

            status.equals("ET", ignoreCase = true) -> "Prolongation"

            status.equals("BT", ignoreCase = true) -> "Pause"

            status.equals("P", ignoreCase = true) -> "Tirs au but"

            status.equals("FT", ignoreCase = true) || status.contains("Finished", ignoreCase = true) -> "Terminé"

            status.equals("AET", ignoreCase = true) -> "Terminé (a.p.)"

            status.equals("PEN", ignoreCase = true) -> "Terminé (tab)"

            status.equals("SUSP", ignoreCase = true) || status.contains("Suspended", ignoreCase = true) -> "Suspendu"

            status.equals("INT", ignoreCase = true) || status.contains("Interrupted", ignoreCase = true) -> "Interrompu"

            status.equals("PST", ignoreCase = true) || status.contains("Postponed", ignoreCase = true) -> "Reporté"

            status.equals("CANC", ignoreCase = true) || status.contains("Cancelled", ignoreCase = true) -> "Annulé"

            status.equals("ABD", ignoreCase = true) || status.contains("Abandoned", ignoreCase = true) -> "Abandonné"

            status.equals("AWD", ignoreCase = true) -> "Forfait technique"

            status.equals("WO", ignoreCase = true) -> "Walkover"

            // TheSportsDB retourne parfois directement un nombre dans
            // strStatus pour certaines ligues — c'est une vraie donnée de
            // l'API, pas un calcul, donc on l'affiche telle quelle.
            status.toIntOrNull() != null -> "$status'"

            else -> status
        }
    }
}

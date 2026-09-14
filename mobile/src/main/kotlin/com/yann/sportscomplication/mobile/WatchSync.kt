package com.yann.sportscomplication.mobile

import android.content.Context
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * Centralise l'envoi de données vers la montre via la Wear Data Layer
 * API (chemin "/match"). Avant ce fichier, cette logique vivait
 * uniquement dans MainActivity ; elle est maintenant partagée avec
 * MatchFollowService (qui continue le polling en arrière-plan quand
 * l'app téléphone est fermée) pour éviter deux implémentations qui
 * pourraient diverger.
 */
object WatchSync {

    private const val MATCH_PATH = "/match"

    /** Télécharge le logo d'une équipe et le convertit en Asset, ou null si [teamId] est null ou en cas d'échec réseau. */
    suspend fun downloadLogo(teamId: String?): Asset? {
        if (teamId == null) return null
        val url = try {
            SportsDbApi.getTeamBadgeUrl(teamId)
        } catch (e: Exception) {
            null
        } ?: return null

        val bytes = try {
            SportsDbApi.downloadBytes(url)
        } catch (e: Exception) {
            null
        } ?: return null

        return Asset.createFromBytes(bytes)
    }

    fun sendMatch(context: Context, match: MatchResult, homeLogo: Asset?, awayLogo: Asset?) {
        val request = PutDataMapRequest.create(MATCH_PATH).apply {
            dataMap.putString("homeTeam", match.homeTeam)
            dataMap.putString("awayTeam", match.awayTeam)
            dataMap.putString("homeScore", match.homeScore ?: "")
            dataMap.putString("awayScore", match.awayScore ?: "")
            dataMap.putString("status", match.status)
            match.kickoffEpochMillis?.let { dataMap.putLong("kickoffEpochMillis", it) }
            homeLogo?.let { dataMap.putAsset("homeLogo", it) }
            awayLogo?.let { dataMap.putAsset("awayLogo", it) }
            // Force un DataChanged même si le contenu texte n'a pas bougé
            // depuis le dernier envoi (la Data Layer API ignore sinon un
            // putDataItem identique au précédent).
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request)
    }

    /**
     * Signale à la montre qu'aucun match n'est plus suivi (Yann a appuyé
     * sur "Arrêter le suivi" côté téléphone) — remet la complication à
     * "Aucun match" au lieu de la laisser figée sur le dernier score
     * connu. Voir MatchListenerService.kt côté montre pour la réception.
     */
    fun sendCleared(context: Context) {
        val request = PutDataMapRequest.create(MATCH_PATH).apply {
            dataMap.putBoolean("cleared", true)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request)
    }
}

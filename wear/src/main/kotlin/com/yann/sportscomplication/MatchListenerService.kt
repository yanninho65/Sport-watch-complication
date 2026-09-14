package com.yann.sportscomplication

import android.content.ComponentName
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * Reçoit les mises à jour de match envoyées par l'app téléphone via la
 * Wear Data Layer API (chemin "/match"), met à jour MatchScoreStore, puis
 * force un rafraîchissement immédiat de la complication plutôt que
 * d'attendre le prochain cycle système.
 *
 * Les logos restent en placeholder pour l'instant — l'envoi des vrais
 * logos d'équipe (via Asset) est la prochaine étape.
 */
class MatchListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            for (event in dataEvents) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                if (event.dataItem.uri.path != MATCH_PATH) continue

                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val homeTeam = dataMap.getString("homeTeam") ?: continue
                val awayTeam = dataMap.getString("awayTeam") ?: continue
                val homeScore = dataMap.getString("homeScore")?.toIntOrNull()
                val awayScore = dataMap.getString("awayScore")?.toIntOrNull()
                val minute = dataMap.getString("minute").orEmpty()

                MatchScoreStore.current = MatchScore(
                    homeTeam = homeTeam,
                    awayTeam = awayTeam,
                    homeScore = homeScore,
                    awayScore = awayScore,
                    minute = minute,
                    // TODO(logos) : remplacer par les vrais logos reçus en
                    // Asset une fois cette étape branchée côté téléphone.
                    homeLogoResId = R.drawable.ic_score_complication,
                    awayLogoResId = R.drawable.ic_score_complication
                )

                requestComplicationRefresh()
            }
        } finally {
            dataEvents.release()
        }
    }

    private fun requestComplicationRefresh() {
        val requester = ComplicationDataSourceUpdateRequester.create(
            context = applicationContext,
            complicationDataSourceComponent = ComponentName(
                applicationContext,
                ScoreComplicationService::class.java
            )
        )
        requester.requestUpdateAll()
    }

    companion object {
        private const val MATCH_PATH = "/match"
    }
}

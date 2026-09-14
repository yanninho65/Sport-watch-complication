package com.yann.sportscomplication

import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest

/**
 * Fournit les données de score en direct aux complications compatibles.
 *
 * Deux types sont supportés (voir AndroidManifest.xml) :
 *  - LONG_TEXT   : pour l'emplacement central de Zenith
 *                  (ex. "PSG 2-1 OM · 64'")
 *  - SMALL_IMAGE : pour les complications cercle du Dashboard Samsung
 *                  (logo d'équipe en couleur — domicile ou extérieur
 *                  selon la préférence de CET emplacement précis, voir
 *                  TeamSideConfigActivity / TeamSidePrefs)
 *
 * Les données viennent de MatchScoreStore, alimenté par
 * MatchListenerService (Wear Data Layer API). UPDATE_PERIOD_SECONDS=60
 * dans le manifest fait que le système rappelle onComplicationRequest
 * environ chaque minute même sans nouvelle donnée du téléphone — ça
 * permet à MatchClock de recalculer une minute de jeu à jour sans
 * dépendre d'un renvoi du téléphone à chaque tick.
 */
class ScoreComplicationService : ComplicationDataSourceService() {

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val match = MatchScoreStore.current

        val data: ComplicationData = when (request.complicationType) {
            ComplicationType.LONG_TEXT -> buildLongText(match)
            ComplicationType.SMALL_IMAGE -> buildSmallImage(match, request.complicationInstanceId)
            else -> NoDataComplicationData()
        }

        listener.onComplicationData(data)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        // Données d'exemple affichées dans le sélecteur de complications de
        // la montre, pour que Yann voie un aperçu avant d'avoir un vrai
        // match en cours.
        val preview = MatchScore(
            homeTeam = "PSG",
            awayTeam = "OM",
            homeScore = 2,
            awayScore = 1,
            status = "1H",
            kickoffEpochMillis = System.currentTimeMillis() - 20 * 60_000L,
            homeLogo = null,
            awayLogo = null
        )
        return when (type) {
            ComplicationType.LONG_TEXT -> buildLongText(preview)
            ComplicationType.SMALL_IMAGE -> buildSmallImage(preview, complicationInstanceId = -1)
            else -> null
        }
    }

    private fun buildLongText(match: MatchScore?): ComplicationData {
        if (match == null) {
            val placeholder = PlainComplicationText.Builder("Aucun match").build()
            return LongTextComplicationData.Builder(
                text = placeholder,
                contentDescription = PlainComplicationText.Builder(
                    "Aucun match sélectionné"
                ).build()
            ).build()
        }

        val scoreText = if (match.homeScore != null && match.awayScore != null) {
            "${match.homeScore}-${match.awayScore}"
        } else {
            "vs"
        }
        val text = "${match.homeTeam} $scoreText ${match.awayTeam} · ${MatchClock.label(match)}"

        return LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(text).build()
        ).build()
    }

    private fun buildSmallImage(match: MatchScore?, complicationInstanceId: Int): ComplicationData {
        // Chaque cercle du Dashboard retient s'il doit montrer l'équipe à
        // domicile ou à l'extérieur (choisi via TeamSideConfigActivity au
        // moment où l'utilisateur assigne ce fournisseur à ce cercle).
        val side = TeamSidePrefs.getSide(this, complicationInstanceId)
        val bitmap = if (side == TeamSidePrefs.SIDE_AWAY) match?.awayLogo else match?.homeLogo

        val icon = if (bitmap != null) {
            Icon.createWithBitmap(bitmap)
        } else {
            Icon.createWithResource(this, R.drawable.ic_score_complication)
        }

        val teamName = match?.let { if (side == TeamSidePrefs.SIDE_AWAY) it.awayTeam else it.homeTeam }
        val description = teamName ?: "Aucun match sélectionné"

        return SmallImageComplicationData.Builder(
            // PHOTO plutôt qu'ICON : garantit que la couleur d'origine du
            // logo n'est jamais teintée par le thème du cadran.
            // Contrepartie : pas d'affichage en mode Always-On Display.
            smallImage = SmallImage.Builder(icon, SmallImageType.PHOTO).build(),
            contentDescription = PlainComplicationText.Builder(description).build()
        ).build()
    }
}

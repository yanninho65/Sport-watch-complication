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
 *                  (logo de l'équipe, en couleur)
 *
 * Étape actuelle (squelette) : les données viennent d'un cache statique en
 * mémoire (MatchScoreStore), pas encore alimenté par le téléphone. La
 * prochaine étape branchera ce cache sur un WearableListenerService qui
 * recevra les mises à jour via la Wear Data Layer API, et déclenchera un
 * ComplicationDataSourceUpdateRequester pour forcer le rafraîchissement
 * immédiat des complications actives.
 */
class ScoreComplicationService : ComplicationDataSourceService() {

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val match = MatchScoreStore.current

        val data: ComplicationData = when (request.complicationType) {
            ComplicationType.LONG_TEXT -> buildLongText(match)
            ComplicationType.SMALL_IMAGE -> buildSmallImage(match)
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
            minute = "64'",
            homeLogoResId = R.drawable.ic_score_complication,
            awayLogoResId = R.drawable.ic_score_complication
        )
        return when (type) {
            ComplicationType.LONG_TEXT -> buildLongText(preview)
            ComplicationType.SMALL_IMAGE -> buildSmallImage(preview)
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
        val text = "${match.homeTeam} $scoreText ${match.awayTeam} · ${match.minute}"

        return LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(text).build()
        ).build()
    }

    private fun buildSmallImage(match: MatchScore?): ComplicationData {
        // TODO(intégration téléphone) : remplacer la resource locale par le
        // vrai logo de l'équipe à domicile, téléchargé depuis TheSportsDB
        // et fourni ici via Icon.createWithBitmap(...) plutôt que
        // createWithResource(...).
        //
        // Choix de SmallImageType.PHOTO plutôt que ICON : PHOTO garantit
        // que la couleur d'origine du logo n'est jamais teintée par le
        // thème du cadran. Contrepartie : ce type de complication ne
        // s'affiche pas en mode Always-On Display (écran éteint/veille),
        // seulement en mode interactif. Si l'affichage en AOD s'avère
        // important, on pourra reconsidérer ICON malgré la teinte.
        val logoResId = match?.homeLogoResId ?: R.drawable.ic_score_complication
        val icon = Icon.createWithResource(this, logoResId)

        val description = match?.let { "${it.homeTeam} contre ${it.awayTeam}" }
            ?: "Aucun match sélectionné"

        return SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(icon, SmallImageType.PHOTO).build(),
            contentDescription = PlainComplicationText.Builder(description).build()
        ).build()
    }
}

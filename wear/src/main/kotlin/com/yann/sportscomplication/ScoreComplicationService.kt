package com.yann.sportscomplication

import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest

/**
 * Fournit les données de score en direct aux complications compatibles.
 *
 * Quatre types sont supportés (voir AndroidManifest.xml) :
 *  - LONG_TEXT          : pour l'emplacement central de Zenith
 *                         (ex. "PSG 2-1 OM · 64'")
 *  - SMALL_IMAGE        : pour les complications cercle du Dashboard Samsung
 *                         (image composée réunissant les deux logos ET le
 *                         score, en couleur — voir ComplicationImageComposer)
 *  - MONOCHROMATIC_IMAGE : pour un emplacement qui n'accepte que du
 *                         monochrome et affiche une seule image (logos +
 *                         score composés en silhouette — voir
 *                         ComplicationImageComposer.composeMonochromeWide)
 *  - SHORT_TEXT          : pour les petits rectangles de Zenith (confirmé :
 *                         Zenith ne propose que SHORT_TEXT/LONG_TEXT/
 *                         RANGED_VALUE/MONOCHROMATIC_ICON selon l'emplacement,
 *                         et ce rectangle-là n'accepte pas
 *                         MONOCHROMATIC_IMAGE) — icône = les deux logos en
 *                         silhouette côte à côte (composeMonochromeIcon),
 *                         texte = le score seul (max 7 caractères)
 *
 * Les données viennent de MatchScoreStore, alimenté par
 * MatchListenerService (Wear Data Layer API). UPDATE_PERIOD_SECONDS=60
 * dans le manifest fait que le système rappelle onComplicationRequest
 * environ chaque minute même sans nouvelle donnée du téléphone — utile
 * si jamais un rafraîchissement système est nécessaire, même si
 * MatchClock n'a plus de minute à recalculer par lui-même (voir
 * MatchClock.kt : le statut affiché vient tel quel de TheSportsDB).
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
            ComplicationType.MONOCHROMATIC_IMAGE -> buildMonochromaticImage(match)
            ComplicationType.SHORT_TEXT -> buildShortText(match)
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
            ComplicationType.SMALL_IMAGE -> buildSmallImage(preview)
            ComplicationType.MONOCHROMATIC_IMAGE -> buildMonochromaticImage(preview)
            ComplicationType.SHORT_TEXT -> buildShortText(preview)
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

    private fun buildSmallImage(match: MatchScore?): ComplicationData {
        val icon = Icon.createWithBitmap(ComplicationImageComposer.composeCombined(match))

        val description = match?.let {
            val scoreText = if (it.homeScore != null && it.awayScore != null) {
                "${it.homeScore}-${it.awayScore}"
            } else {
                "vs"
            }
            "${it.homeTeam} $scoreText ${it.awayTeam}"
        } ?: "Aucun match sélectionné"

        return SmallImageComplicationData.Builder(
            // PHOTO plutôt qu'ICON : garantit que les couleurs d'origine
            // des logos ne sont jamais teintées par le thème du cadran.
            // Contrepartie : pas d'affichage en mode Always-On Display.
            smallImage = SmallImage.Builder(icon, SmallImageType.PHOTO).build(),
            contentDescription = PlainComplicationText.Builder(description).build()
        ).build()
    }

    private fun buildMonochromaticImage(match: MatchScore?): ComplicationData {
        val icon = Icon.createWithBitmap(ComplicationImageComposer.composeMonochromeWide(match))

        val description = match?.let {
            val scoreText = if (it.homeScore != null && it.awayScore != null) {
                "${it.homeScore}-${it.awayScore}"
            } else {
                "vs"
            }
            "${it.homeTeam} $scoreText ${it.awayTeam}"
        } ?: "Aucun match sélectionné"

        return MonochromaticImageComplicationData.Builder(
            monochromaticImage = MonochromaticImage.Builder(icon).build(),
            contentDescription = PlainComplicationText.Builder(description).build()
        ).build()
    }

    private fun buildShortText(match: MatchScore?): ComplicationData {
        val scoreText = if (match?.homeScore != null && match.awayScore != null) {
            "${match.homeScore}-${match.awayScore}"
        } else {
            "vs"
        }

        val description = match?.let {
            "${it.homeTeam} $scoreText ${it.awayTeam}"
        } ?: "Aucun match sélectionné"

        val icon = Icon.createWithBitmap(ComplicationImageComposer.composeMonochromeIcon(match))

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(scoreText).build(),
            contentDescription = PlainComplicationText.Builder(description).build()
        )
            .setMonochromaticImage(MonochromaticImage.Builder(icon).build())
            .build()
    }
}

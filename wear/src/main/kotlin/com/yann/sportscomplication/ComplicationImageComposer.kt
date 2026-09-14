package com.yann.sportscomplication

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * Compose une image carrée unique combinant les deux logos d'équipe et le
 * score, destinée à la complication cercle (Dashboard Samsung).
 *
 * Le système recadre les SMALL_IMAGE de type PHOTO en cercle (voir
 * ScoreComplicationService). Tout le contenu important est donc placé sur
 * la bande horizontale qui passe par le centre — c'est la corde la plus
 * large du cercle — pour ne rien perdre au recadrage : logo domicile à
 * gauche, score au centre, logo extérieur à droite. Un fond circulaire
 * plein est peint en dessous pour garantir un contraste correct quel que
 * soit le cadran ou le thème derrière la complication.
 *
 * Le résultat est petit et dense (le cercle du Dashboard est minuscule à
 * l'écran) — pour un score à deux chiffres des deux côtés, les logos et
 * le texte se touchent presque. C'est un compromis assumé : tout faire
 * tenir dans un seul cercle laisse forcément moins de place à chaque
 * élément qu'avec deux cercles séparés (mode SIDE_HOME / SIDE_AWAY).
 */
object ComplicationImageComposer {

    private const val SIZE = 320
    private const val CENTER = SIZE / 2f
    private const val RADIUS = SIZE / 2f
    private const val LOGO_SIZE = 80f
    private const val LOGO_OFFSET_X = 90f
    private const val SCORE_TEXT_SIZE = 34f

    fun composeCombined(match: MatchScore?): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B1F27")
        }
        canvas.drawCircle(CENTER, CENTER, RADIUS, backgroundPaint)

        drawLogo(canvas, match?.homeLogo, CENTER - LOGO_OFFSET_X, CENTER)
        drawLogo(canvas, match?.awayLogo, CENTER + LOGO_OFFSET_X, CENTER)
        drawScoreText(canvas, scoreText(match))

        return bitmap
    }

    private fun scoreText(match: MatchScore?): String {
        return if (match?.homeScore != null && match.awayScore != null) {
            "${match.homeScore}-${match.awayScore}"
        } else {
            "vs"
        }
    }

    private fun drawLogo(canvas: Canvas, logo: Bitmap?, centerX: Float, centerY: Float) {
        val half = LOGO_SIZE / 2f
        if (logo != null) {
            val dest = RectF(centerX - half, centerY - half, centerX + half, centerY + half)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(logo, null, dest, paint)
        } else {
            // Pas de logo reçu (téléchargement échoué côté téléphone, ou
            // aucun match) : simple pastille de substitution plutôt que de
            // laisser un trou dans l'image.
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#3A4150")
            }
            canvas.drawCircle(centerX, centerY, half, placeholderPaint)
        }
    }

    private fun drawScoreText(canvas: Canvas, text: String) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = SCORE_TEXT_SIZE
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        // Contour noir derrière le remplissage blanc : lisible même si le
        // fond circulaire (#1B1F27) est partiellement recouvert par les
        // logos tout proches.
        val strokePaint = Paint(fillPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = Color.BLACK
        }
        val bounds = Rect()
        fillPaint.getTextBounds(text, 0, text.length, bounds)
        val baselineY = CENTER - bounds.exactCenterY()
        canvas.drawText(text, CENTER, baselineY, strokePaint)
        canvas.drawText(text, CENTER, baselineY, fillPaint)
    }
}

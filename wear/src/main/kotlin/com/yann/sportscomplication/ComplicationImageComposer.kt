package com.yann.sportscomplication

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF

/**
 * Compose les images combinant les deux logos d'équipe et le score,
 * destinées aux complications qui ne peuvent pas afficher texte et image
 * séparément.
 *
 * Deux formats :
 *  - [composeCombined] : cercle en couleur (Dashboard Samsung, SMALL_IMAGE).
 *  - [composeMonochromeWide] : rectangle large en silhouette blanche
 *    (emplacement MONOCHROMATIC_IMAGE, ex. bande au-dessus de la carte
 *    notification).
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

    // Format "petit rectangle large" (ex. bande d'icônes au-dessus de la
    // carte notification sur le visage testé) — MONOCHROMATIC_IMAGE, donc
    // pas de fond peint : seule la silhouette compte, le système applique
    // sa propre teinte par-dessus les pixels opaques.
    private const val WIDE_WIDTH = 480
    private const val WIDE_HEIGHT = 200
    private const val WIDE_CENTER_Y = WIDE_HEIGHT / 2f
    private const val WIDE_LOGO_SIZE = 150f
    private const val WIDE_LOGO_MARGIN = 8f
    private const val WIDE_SCORE_TEXT_SIZE = 84f

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

    /**
     * Compose une image rectangulaire large en un seul ton (silhouette
     * blanche sur fond transparent), destinée à un emplacement
     * MONOCHROMATIC_IMAGE de type "petit rectangle" — logo domicile à
     * gauche, score au centre, logo extérieur à droite.
     *
     * Contrairement à [composeCombined] (SMALL_IMAGE, cercle, couleurs
     * d'origine conservées), ici tout doit être une silhouette blanche
     * opaque sur fond transparent : c'est la convention attendue par
     * ComplicationType.MONOCHROMATIC_IMAGE, le système applique ensuite sa
     * propre teinte par-dessus (voir [drawMonochromeLogo]). Pas de fond
     * peint non plus, pour ne pas apparaître comme un bloc plein une fois
     * teinté.
     */
    fun composeMonochromeWide(match: MatchScore?): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDE_WIDTH, WIDE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val logoHalf = WIDE_LOGO_SIZE / 2f
        val homeCenterX = logoHalf + WIDE_LOGO_MARGIN
        val awayCenterX = WIDE_WIDTH - logoHalf - WIDE_LOGO_MARGIN

        drawMonochromeLogo(canvas, match?.homeLogo, homeCenterX, WIDE_CENTER_Y, logoHalf)
        drawMonochromeLogo(canvas, match?.awayLogo, awayCenterX, WIDE_CENTER_Y, logoHalf)
        drawWideScoreText(canvas, scoreText(match))

        return bitmap
    }

    private fun drawMonochromeLogo(canvas: Canvas, logo: Bitmap?, centerX: Float, centerY: Float, half: Float) {
        if (logo != null) {
            val dest = RectF(centerX - half, centerY - half, centerX + half, centerY + half)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                // Recolore tous les pixels opaques du logo en blanc uni,
                // en conservant le canal alpha d'origine (donc la
                // silhouette) — le logo couleur devient une icône
                // monochrome sans perdre sa forme. Ne fonctionne bien que
                // si le PNG source a un fond transparent (cas normal pour
                // un logo d'équipe téléchargé via lookupteam.php) ; un
                // logo sur fond plein donnerait un simple carré blanc.
                colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(logo, null, dest, paint)
        } else {
            // Pas de logo reçu : pastille semi-transparente plutôt qu'un
            // trou, cohérente avec le placeholder du mode cercle.
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 90
            }
            canvas.drawCircle(centerX, centerY, half * 0.8f, placeholderPaint)
        }
    }

    private fun drawWideScoreText(canvas: Canvas, text: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = WIDE_SCORE_TEXT_SIZE
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val baselineY = WIDE_CENTER_Y - bounds.exactCenterY()
        canvas.drawText(text, WIDE_WIDTH / 2f, baselineY, paint)
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

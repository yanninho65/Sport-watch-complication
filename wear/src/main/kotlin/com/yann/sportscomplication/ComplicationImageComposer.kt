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
 * Deux formats, tous les deux en rectangle large (logo domicile à gauche,
 * score au centre, logo extérieur à droite) — même agencement, seule la
 * teinte change :
 *  - [composeColorWide] : SMALL_IMAGE, couleurs d'origine des logos
 *    conservées, pas de fond peint (le fond de la case hôte suffit).
 *  - [composeMonochromeWide] : MONOCHROMATIC_IMAGE, silhouette blanche sur
 *    fond transparent — le système applique sa propre teinte par-dessus.
 *  - [composeMonochromeIcon] : icône carrée en silhouette, sans texte
 *    (emplacement SHORT_TEXT — le score passe par le champ texte séparé,
 *    voir ScoreComplicationService — ex. les petits rectangles Zenith).
 *
 * Ancienne version : [composeColorWide] dessinait un cercle plein
 * (Dashboard Samsung, cases circulaires). Abandonné — Yann ne s'en sert
 * plus — au profit du rectangle large, qui correspond à l'usage actuel
 * (petits rectangles Zenith). Un cercle scale mal dans un rectangle large :
 * en aperçu (mode "contain"), il rapetissait au point de rendre les logos
 * illisibles, ne laissant que le texte du score visible au centre.
 */
object ComplicationImageComposer {

    // Rectangle large partagé par composeColorWide et composeMonochromeWide.
    private const val WIDE_WIDTH = 480
    private const val WIDE_HEIGHT = 200
    private const val WIDE_CENTER_Y = WIDE_HEIGHT / 2f
    private const val WIDE_LOGO_SIZE = 150f
    private const val WIDE_LOGO_MARGIN = 8f
    private const val WIDE_SCORE_TEXT_SIZE = 84f
    private const val WIDE_SCORE_STROKE_WIDTH = 6f

    // Icône carrée pour SHORT_TEXT (ex. les petits rectangles Zenith qui
    // n'acceptent pas MONOCHROMATIC_IMAGE/SMALL_IMAGE) — les deux logos
    // côte à côte en silhouette, sans score ni fond : le score passe par
    // le champ texte du SHORT_TEXT lui-même (voir ScoreComplicationService).
    private const val ICON_SIZE = 128
    private const val ICON_CENTER_Y = ICON_SIZE / 2f
    private const val ICON_LOGO_SIZE = 100f
    private const val ICON_LOGO_MARGIN = 4f

    /**
     * Compose l'image rectangulaire large en couleur (logos d'origine,
     * non recolorés), destinée à un emplacement SMALL_IMAGE de type
     * "petit rectangle" — logo domicile à gauche, score au centre, logo
     * extérieur à droite. Pas de fond peint : la case hôte (Zenith,
     * fond sombre arrondi) fournit déjà le contraste ; un contour noir
     * derrière le texte du score garantit la lisibilité au cas où.
     */
    fun composeColorWide(match: MatchScore?): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDE_WIDTH, WIDE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val logoHalf = WIDE_LOGO_SIZE / 2f
        val homeCenterX = logoHalf + WIDE_LOGO_MARGIN
        val awayCenterX = WIDE_WIDTH - logoHalf - WIDE_LOGO_MARGIN

        drawColorLogo(canvas, match?.homeLogo, homeCenterX, WIDE_CENTER_Y, logoHalf)
        drawColorLogo(canvas, match?.awayLogo, awayCenterX, WIDE_CENTER_Y, logoHalf)
        drawWideScoreText(canvas, scoreText(match), withStroke = true)

        return bitmap
    }

    /**
     * Même agencement que [composeColorWide], mais en silhouette blanche
     * sur fond transparent — convention attendue par
     * ComplicationType.MONOCHROMATIC_IMAGE, le système applique ensuite
     * sa propre teinte par-dessus (voir [drawMonochromeLogo]). Pas de
     * contour sur le texte ici : une fois teinté, remplissage et contour
     * deviendraient indiscernables (même opacité, même couleur finale).
     */
    fun composeMonochromeWide(match: MatchScore?): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDE_WIDTH, WIDE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val logoHalf = WIDE_LOGO_SIZE / 2f
        val homeCenterX = logoHalf + WIDE_LOGO_MARGIN
        val awayCenterX = WIDE_WIDTH - logoHalf - WIDE_LOGO_MARGIN

        drawMonochromeLogo(canvas, match?.homeLogo, homeCenterX, WIDE_CENTER_Y, logoHalf)
        drawMonochromeLogo(canvas, match?.awayLogo, awayCenterX, WIDE_CENTER_Y, logoHalf)
        drawWideScoreText(canvas, scoreText(match), withStroke = false)

        return bitmap
    }

    /**
     * Compose l'icône carrée (deux logos côte à côte, en silhouette, sans
     * texte) utilisée par le SHORT_TEXT. Le score n'est pas dans l'image :
     * il passe par le champ texte du SHORT_TEXT (limité à 7 caractères,
     * largement suffisant pour "2-1" ou "vs").
     */
    fun composeMonochromeIcon(match: MatchScore?): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val logoHalf = ICON_LOGO_SIZE / 2f
        val homeCenterX = logoHalf + ICON_LOGO_MARGIN
        val awayCenterX = ICON_SIZE - logoHalf - ICON_LOGO_MARGIN

        drawMonochromeLogo(canvas, match?.homeLogo, homeCenterX, ICON_CENTER_Y, logoHalf)
        drawMonochromeLogo(canvas, match?.awayLogo, awayCenterX, ICON_CENTER_Y, logoHalf)

        return bitmap
    }

    private fun drawColorLogo(canvas: Canvas, logo: Bitmap?, centerX: Float, centerY: Float, half: Float) {
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
            // trou, cohérente avec le placeholder de la version couleur.
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 90
            }
            canvas.drawCircle(centerX, centerY, half * 0.8f, placeholderPaint)
        }
    }

    private fun drawWideScoreText(canvas: Canvas, text: String, withStroke: Boolean) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = WIDE_SCORE_TEXT_SIZE
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        fillPaint.getTextBounds(text, 0, text.length, bounds)
        val baselineY = WIDE_CENTER_Y - bounds.exactCenterY()
        if (withStroke) {
            // Contour noir derrière le remplissage blanc : lisible sur
            // n'importe quel fond de case, puisque cette version couleur
            // n'est jamais retintée par le système (contrairement à
            // MONOCHROMATIC_IMAGE, où stroke et fill fusionneraient).
            val strokePaint = Paint(fillPaint).apply {
                style = Paint.Style.STROKE
                strokeWidth = WIDE_SCORE_STROKE_WIDTH
                color = Color.BLACK
            }
            canvas.drawText(text, WIDE_WIDTH / 2f, baselineY, strokePaint)
        }
        canvas.drawText(text, WIDE_WIDTH / 2f, baselineY, fillPaint)
    }

    private fun scoreText(match: MatchScore?): String {
        return if (match?.homeScore != null && match.awayScore != null) {
            "${match.homeScore}-${match.awayScore}"
        } else {
            "vs"
        }
    }
}

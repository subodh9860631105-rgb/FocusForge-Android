package com.focusforge.pro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Visual language for FocusForge Pro.
 *
 * Dark "shadow glass" surfaces over an aurora backdrop: every panel is a soft
 * vertical gradient with a bright top edge and a faint sheen, which reads as an
 * illuminated pane of glass instead of a flat rectangle.
 */
object Theme {

    // ---- core palette -------------------------------------------------
    const val BG = "#05080F"
    const val BG_DEEP = "#03060B"
    const val SURFACE = "#111C2F"
    const val SURFACE_HI = "#17243B"
    const val SURFACE_LO = "#0A1220"
    const val HAIRLINE = "#243449"
    const val TEXT = "#F1F5FB"
    const val TEXT_DIM = "#8FA0B8"
    const val TEXT_MUTE = "#5F7086"

    // ---- accents ------------------------------------------------------
    const val ACCENT = "#5B8CFF"        // primary indigo/blue
    const val ACCENT_2 = "#22D3EE"      // cyan
    const val GOOD = "#34D399"          // mint
    const val WARN = "#FBBF24"          // amber
    const val BAD = "#FB7185"           // rose
    const val VIOLET = "#A78BFA"
    const val LIME = "#A3E635"

    fun color(hex: String): Int = Color.parseColor(hex)

    // ---- colour maths -------------------------------------------------
    fun mix(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt()
        )
    }

    fun lighten(hex: String, f: Float): Int = mix(color(hex), Color.WHITE, f)
    fun darken(hex: String, f: Float): Int = mix(color(hex), Color.BLACK, f)

    /** Base colour of a glass panel: brighter than the requested fill so it glows. */
    fun surfaceTop(fillHex: String): Int = when (fillHex.uppercase()) {
        "#10192D", "#0B1528", "#0D1526", "#111C31" -> color(SURFACE_HI)
        else -> mix(color(fillHex), Color.WHITE, 0.04f)
    }

    /**
     * Gradient glass panel: vertical ramp, hairline border and a soft top sheen.
     * Returns a LayerDrawable so callers can keep assigning it to `background`.
     */
    fun glass(fillHex: String, strokeHex: String, radiusPx: Float, sheen: Boolean = true): LayerDrawable {
        val top = surfaceTop(fillHex)
        val bottom = darken(fillHex, 0.45f)
        val base = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setStroke(2, mix(color(strokeHex), Color.WHITE, 0.18f))
        }
        if (!sheen) return LayerDrawable(arrayOf(base))

        val glow = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(withAlpha(Color.WHITE, 26), withAlpha(Color.WHITE, 0))
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
        }
        return LayerDrawable(arrayOf(base, glow))
    }

    /** Accent-tinted panel used for primary cards. */
    fun accentGlass(accentHex: String, radiusPx: Float): LayerDrawable {
        val accent = color(accentHex)
        val top = mix(color(SURFACE_HI), accent, 0.16f)
        val bottom = mix(color(SURFACE_LO), accent, 0.06f)
        val base = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setStroke(2, withAlpha(accent, 150))
        }
        val glow = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(withAlpha(accent, 34), withAlpha(accent, 0))
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
        }
        return LayerDrawable(arrayOf(base, glow))
    }

    /** Tinted soft pill, used for chips and tags. */
    fun pill(accentHex: String, radiusPx: Float, strong: Boolean = false): GradientDrawable {
        val accent = color(accentHex)
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            if (strong) intArrayOf(lighten(accentHex, 0.18f), darken(accentHex, 0.18f))
            else intArrayOf(withAlpha(accent, 46), withAlpha(accent, 20))
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setStroke(2, withAlpha(accent, if (strong) 235 else 110))
        }
    }

    fun withAlpha(colorInt: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(colorInt), Color.green(colorInt), Color.blue(colorInt))

    private fun withAlpha(colorInt: Int, a: Float): Int = withAlpha(colorInt, (a * 255).toInt())
}

/**
 * Ambient background: three slow, blurred colour wells over near-black, plus a
 * very faint grid. Cheap (one shader per well, no blur pass) but it makes every
 * translucent card look like it is floating.
 */
class AuroraBackdropView(context: Context) : View(context) {

    private val wells = listOf(
        floatArrayOf(0.18f, 0.06f, 0.62f, 0xFF3B5BFF.toInt().toFloat()),   // indigo, top left
        floatArrayOf(0.92f, 0.22f, 0.55f, 0xFF14B8C8.toInt().toFloat()),   // cyan, top right
        floatArrayOf(0.55f, 0.92f, 0.70f, 0xFF7C3AED.toInt().toFloat())    // violet, bottom
    )
    private var shaderA: Shader? = null
    private var shaderB: Shader? = null
    private var shaderC: Shader? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0FFFFFFF")
        strokeWidth = 1f
    }
    private var built = false

    private fun build(w: Int, h: Int) {
        if (w <= 0 || h <= 0 || built) return
        built = true
        val r1 = max(w, h) * wells[0][2]
        shaderA = RadialGradient(w * wells[0][0], h * wells[0][1], r1,
            intArrayOf(Theme.withAlpha(wells[0][3].toInt(), 74), 0), Shader.TileMode.CLAMP)
        shaderB = RadialGradient(w * wells[1][0], h * wells[1][1], max(w, h) * wells[1][2],
            intArrayOf(Theme.withAlpha(wells[1][3].toInt(), 54), 0), Shader.TileMode.CLAMP)
        shaderC = RadialGradient(w * wells[2][0], h * wells[2][1], max(w, h) * wells[2][2],
            intArrayOf(Theme.withAlpha(wells[2][3].toInt(), 58), 0), Shader.TileMode.CLAMP)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        built = false
        build(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        build(width, height)
        canvas.drawColor(Color.parseColor(Theme.BG_DEEP))
        val vert = LinearGradient(0f, 0f, 0f, height.toFloat(),
            intArrayOf(Color.parseColor("#070C16"), Color.parseColor(Theme.BG_DEEP)), Shader.TileMode.CLAMP)
        paint.shader = vert
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        listOf(shaderA, shaderB, shaderC).forEach { s ->
            if (s != null) {
                paint.shader = s
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
        }

        // faint vertical grid for depth
        paint.shader = null
        val step = width / 8f
        var x = step
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += step
        }
    }
}

/**
 * Animated progress ring used for the focus goal and the workout overview.
 * Draws a soft track, a gradient arc and a rounded cap.
 */
class RingView(context: Context, private var progress: Float = 0f, private val accentHex: String = Theme.ACCENT) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        color = Color.parseColor("#1B2942")
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 26f
        strokeCap = Paint.Cap.ROUND
        color = Theme.withAlpha(Theme.color(accentHex), 40)
    }
    private val rect = RectF()

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = glow.strokeWidth / 2f + 2f
        val size = min(width, height).toFloat() - pad * 2
        val l = (width - size) / 2f
        val t = (height - size) / 2f
        rect.set(l, t, l + size, t + size)
        track.strokeWidth = size * 0.09f
        arc.strokeWidth = track.strokeWidth
        glow.strokeWidth = track.strokeWidth * 1.7f

        canvas.drawArc(rect, 0f, 360f, false, track)
        val sweep = 360f * progress
        if (sweep > 0.5f) {
            arc.shader = LinearGradient(l, t, l + size, t + size,
                intArrayOf(Theme.color(accentHex), Theme.lighten(accentHex, 0.45f)),
                Shader.TileMode.CLAMP)
            canvas.drawArc(rect, -90f, sweep, false, glow)
            canvas.drawArc(rect, -90f, sweep, false, arc)
        }
    }
}

package com.focusforge.pro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.LruCache
import android.view.MotionEvent
import android.view.View

/**
 * Loads the baked 3D mannequin demo frames that ship in assets/motion.
 *
 * Decoding happens on a single background thread and every bitmap is memoised in
 * an LRU cache shared by all views, so scrolling the exercise list or opening a
 * guide never blocks the UI thread twice for the same frame.
 */
object MotionLibrary {

    const val FRAME_COUNT = 18
    private const val ROOT = "motion"

    private val cache = object : LruCache<String, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val missing = HashSet<String>()
    private val present = HashSet<String>()
    private val inFlight = HashSet<String>()
    private val main = Handler(Looper.getMainLooper())
    private val workerHandler: Handler by lazy {
        val t = HandlerThread("motion-decode")
        t.start()
        Handler(t.looper)
    }

    /** Nearest baked demo for exercises that do not have their own clip yet. */
    fun fallbackFor(animType: Int): String = when (animType) {
        0 -> "bench_bb"
        1 -> "row_bb"
        2 -> "curl_db"
        3 -> "squat_smith"
        4 -> "shoulder_db"
        5 -> "plank_hold"
        else -> "squat_smith"
    }

    fun resolve(context: Context, assetId: String): String =
        if (has(context, assetId)) assetId else fallbackFor(0)

    fun has(context: Context, assetId: String): Boolean {
        if (assetId.isEmpty()) return false
        if (present.contains(assetId)) return true
        if (missing.contains(assetId)) return false
        return try {
            val list = context.assets.list("$ROOT/$assetId")
            val ok = list != null && list.any { it.startsWith("f") && it.endsWith(".webp") }
            if (ok) present.add(assetId) else missing.add(assetId)
            ok
        } catch (e: Exception) {
            missing.add(assetId)
            false
        }
    }

    fun framePath(assetId: String, index: Int): String =
        "$ROOT/$assetId/f%02d.webp".format(index % FRAME_COUNT)

    fun cached(path: String): Bitmap? = cache.get(path)

    // ------------------------------------------------------------------
    // Animation budget: a phone should not run dozens of decoders at once,
    // so only a handful of on-screen figures animate in real time. Everyone
    // else shows a still frame until a slot frees up.
    // ------------------------------------------------------------------
    private const val MAX_LIVE_FIGURES = 6
    private val liveHolders = ArrayList<Any>()
    private val waiters = ArrayList<() -> Unit>()

    fun requestLiveSlot(owner: Any): Boolean {
        synchronized(liveHolders) {
            if (liveHolders.contains(owner)) return true
            if (liveHolders.size >= MAX_LIVE_FIGURES) return false
            liveHolders.add(owner)
            return true
        }
    }

    fun releaseLiveSlot(owner: Any) {
        var next: (() -> Unit)? = null
        synchronized(liveHolders) {
            liveHolders.remove(owner)
            if (waiters.isNotEmpty()) next = waiters.removeAt(0)
        }
        next?.let { main.post(it) }
    }

    fun awaitLiveSlot(callback: () -> Unit) {
        synchronized(liveHolders) {
            if (waiters.size < 64) waiters.add(callback)
        }
    }

    /** Decode off the UI thread; [onReady] is invoked on the main thread. */
    fun request(context: Context, path: String, onReady: (Bitmap) -> Unit) {
        cache.get(path)?.let { onReady(it); return }
        synchronized(inFlight) {
            if (!inFlight.add(path)) return
        }
        val app = context.applicationContext
        workerHandler.post {
            val bmp = decode(app, path)
            synchronized(inFlight) { inFlight.remove(path) }
            main.post {
                if (bmp != null) {
                    cache.put(path, bmp)
                    onReady(bmp)
                }
            }
        }
    }

    fun posterPath(assetId: String) = "$ROOT/$assetId/poster.webp"

    private fun decode(context: Context, path: String): Bitmap? = try {
        context.assets.open(path).use { stream ->
            val opts = BitmapFactory.Options()
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888
            opts.inScaled = false
            BitmapFactory.decodeStream(stream, null, opts)
        }
    } catch (e: Exception) {
        null
    }

    /** Body map + mask + highlight overlays (static, kept for the session). */
    private val bodyInFlight = HashSet<String>()

    fun bodyAsset(context: Context, path: String, onReady: (Bitmap) -> Unit) {
        cache.get("bm:$path")?.let { onReady(it); return }
        synchronized(bodyInFlight) {
            if (!bodyInFlight.add(path)) return
        }
        val app = context.applicationContext
        workerHandler.post {
            val bmp = decode(app, path)
            synchronized(bodyInFlight) { bodyInFlight.remove(path) }
            main.post {
                if (bmp != null) {
                    cache.put("bm:$path", bmp)
                    onReady(bmp)
                }
            }
        }
    }

    fun cachedBody(path: String): Bitmap? = cache.get("bm:$path")
}

/**
 * Plays a baked 3D mannequin demonstration: rounded glass card, smooth looping
 * playback, tap to pause, and a slim rep indicator underneath.
 */
class MotionFigureView @JvmOverloads constructor(
    context: Context,
    private val requestedId: String,
    private val showBackdrop: Boolean = true,
    private val showRepBar: Boolean = true
) : View(context) {

    private val cp = context
    private var assetId: String = MotionLibrary.resolve(context, requestedId)
    private var frame = 0
    private var bitmap: Bitmap? = null
    private var paused = false
    private var attached = false
    private var live = false

    private var speed = 1f      // 1x = ~15 fps, reads as a calm, clear demo
    private val frameMs: Long get() = (66L / speed).toLong().coerceAtLeast(20L)

    private var prevBitmap: Bitmap? = null
    private var blendStart = 0L
    private val blendMs = 42L
    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B"); style = Paint.Style.FILL
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8"); style = Paint.Style.FILL
    }
    private val fillGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3DFFD7A8"); style = Paint.Style.FILL
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0B1324"); style = Paint.Style.FILL
    }
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#93C5FD"); textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val src = Rect()

    private companion object {
        const val STILL_FRAME = 4
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!attached || paused || !live) return
            prevBitmap = bitmap
            blendStart = System.currentTimeMillis()
            frame = (frame + 1) % MotionLibrary.FRAME_COUNT
            ensureFrame()
            invalidate()
            postDelayed(this, frameMs)
        }
    }

    init {
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, 420f,
            Color.parseColor("#152238"), Color.parseColor("#0B1324"),
            Shader.TileMode.CLAMP
        )
        isClickable = true
        contentDescription = "Animated 3D exercise demonstration"
    }

    fun setExercise(id: String) {
        assetId = MotionLibrary.resolve(cp, id)
        frame = 0
        bitmap = null
        ensureFrame()
        invalidate()
    }

    fun isPaused(): Boolean = paused

    fun setSpeed(multiplier: Float) {
        speed = multiplier.coerceIn(0.2f, 2.5f)
        if (!paused) {
            removeCallbacks(ticker)
            post(ticker)
        }
    }

    fun setPaused(value: Boolean) {
        paused = value
        if (!paused) {
            removeCallbacks(ticker)
            post(ticker)
        }
        invalidate()
    }

    fun isAnimated(): Boolean = live

    private fun ensureFrame() {
        val path = MotionLibrary.framePath(assetId, frame)
        MotionLibrary.cached(path)?.let {
            bitmap = it
            return
        }
        MotionLibrary.request(cp, path) { b ->
            bitmap = b
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        live = MotionLibrary.requestLiveSlot(this)
        ensureFrame()
        removeCallbacks(ticker)
        if (live) {
            post(ticker)
        } else {
            // Show a representative still until a playback slot frees up.
            frame = STILL_FRAME
            ensureFrame()
            MotionLibrary.awaitLiveSlot {
                if (attached && !live) {
                    live = true
                    removeCallbacks(ticker)
                    post(ticker)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        attached = false
        live = false
        removeCallbacks(ticker)
        MotionLibrary.releaseLiveSlot(this)
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
            setPaused(!paused)
            return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        bgPaint.shader = LinearGradient(
            0f, 0f, w, h,
            Color.parseColor("#16243A"), Color.parseColor("#0A1120"),
            Shader.TileMode.CLAMP
        )
        if (showBackdrop) {
            canvas.drawRoundRect(0f, 0f, w, h, w * 0.14f, w * 0.14f, bgPaint)
        }

        val bmp = bitmap
        if (bmp != null) {
            val barSpace = if (showRepBar) h * 0.07f else 0f
            val availW = w * 0.94f
            val availH = (h - barSpace) * 0.96f
            val scale = minOf(availW / bmp.width, availH / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val left = (w - dw) / 2f
            val top = (h - barSpace - dh) / 2f

            // soft elliptical contact shadow under the athlete
            floorPaint.shader = RadialGradient(
                w / 2f, top + dh * 0.985f, dw * 0.34f,
                Color.parseColor("#4D04070C"), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            canvas.save()
            canvas.scale(1f, 0.26f, w / 2f, top + dh * 0.985f)
            canvas.drawCircle(w / 2f, top + dh * 0.985f, dw * 0.34f, floorPaint)
            canvas.restore()

            src.set(0, 0, bmp.width, bmp.height)
            val dst = RectF(left, top, left + dw, top + dh)

            // short crossfade between consecutive frames keeps the loop fluid
            val prev = prevBitmap
            if (prev != null && blendStart > 0L) {
                val t = ((System.currentTimeMillis() - blendStart).toFloat() / blendMs).coerceIn(0f, 1f)
                if (t < 1f) {
                    bmpPaint.alpha = 255
                    canvas.drawBitmap(prev, src, dst, bmpPaint)
                    bmpPaint.alpha = (255 * t).toInt().coerceIn(0, 255)
                } else {
                    prevBitmap = null
                    bmpPaint.alpha = 255
                }
            } else {
                bmpPaint.alpha = 255
            }
            canvas.drawBitmap(bmp, src, dst, bmpPaint)
            bmpPaint.alpha = 255
        }

        if (showRepBar) {
            val barH = h * 0.020f
            val barY = h - h * 0.045f
            val pad = w * 0.10f
            canvas.drawRoundRect(pad, barY, w - pad, barY + barH, barH / 2f, barH / 2f, trackPaint)
            val progress = frame.toFloat() / MotionLibrary.FRAME_COUNT
            val tip = pad + (w - 2 * pad) * progress
            fillPaint.shader = LinearGradient(pad, 0f, w - pad, 0f,
                Color.parseColor("#22D3EE"), Color.parseColor("#5B8CFF"), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(pad, barY, tip, barY + barH, barH / 2f, barH / 2f, fillPaint)
            // glowing head of the progress bar
            canvas.drawCircle(tip, barY + barH / 2f, barH * 1.9f, fillGlowPaint)
        }

        if (paused && showBackdrop) {
            badgeText.textSize = w * 0.075f
            val label = "PAUSED"
            val tw = badgeText.measureText(label)
            val padH = w * 0.05f
            canvas.drawRoundRect(
                w / 2f - tw / 2f - padH, w * 0.06f,
                w / 2f + tw / 2f + padH, w * 0.06f + w * 0.12f,
                w * 0.06f, w * 0.06f, badgePaint
            )
            canvas.drawText(label, w / 2f - tw / 2f, w * 0.06f + w * 0.086f, badgeText)
        }
    }
}

/**
 * Interactive front + back anatomy map built from the baked mannequin art.
 *
 * Taps are resolved by sampling the per-view region mask, so the hit areas match
 * the artwork exactly instead of hand-tuned rectangles.
 */
class AnatomyMapView @JvmOverloads constructor(
    context: Context,
    private var activeMuscle: String,
    private val onMuscleTapped: ((String) -> Unit)? = null,
    private val hintText: String = "Tap a muscle group to filter exercises"
) : View(context) {

    enum class Region(val label: String, val color: Int) {
        CHEST("Chest", 0xFFEB2D2D.toInt()),
        ABS("Abs", 0xFF28D75F.toInt()),
        ARMS("Arms", 0xFF3C69F5.toInt()),
        SHOULDERS("Shoulders", 0xFFF5AA28.toInt()),
        BACK("Back", 0xFFC846DC.toInt()),
        CORE("Core", 0xFF1EC8DC.toInt()),
        LEGS("Legs", 0xFFFA6EB4.toInt()),
        GLUTES("Glutes", 0xFF96D73C.toInt()),
        NONE("", 0)
    }

    private val frontBmp = MotionLibrary.cachedBody(FRONT_IMG)
    private val backBmp = MotionLibrary.cachedBody(BACK_IMG)
    private var front: Bitmap? = frontBmp
    private var back: Bitmap? = backBmp
    private var frontMask = MotionLibrary.cachedBody(FRONT_MASK)
    private var backMask = MotionLibrary.cachedBody(BACK_MASK)
    private val highlights = HashMap<String, Bitmap?>()

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7DD3FC")
        textSize = 12f * context.resources.displayMetrics.density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64748B")
        textSize = 9.5f * context.resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7F1D1D") }
    private val pillText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val src = Rect()

    init {
        isClickable = true
        loadAssets()
    }

    private fun loadAssets() {
        MotionLibrary.bodyAsset(context, FRONT_IMG) { front = it; invalidate() }
        MotionLibrary.bodyAsset(context, BACK_IMG) { back = it; invalidate() }
        MotionLibrary.bodyAsset(context, FRONT_MASK) { frontMask = it; invalidate() }
        MotionLibrary.bodyAsset(context, BACK_MASK) { backMask = it; invalidate() }
    }

    fun setActiveMuscle(muscle: String) {
        activeMuscle = muscle
        invalidate()
    }

    private fun cellRect(index: Int, out: RectF) {
        // index 0 = front (left), 1 = back (right)
        val cellW = width / 2f
        val pad = width * 0.02f
        val top = height * 0.14f
        val bottom = height * 0.86f
        out.set(index * cellW + pad, top, (index + 1) * cellW - pad, bottom)
    }

    private fun fitRect(bmp: Bitmap, cell: RectF): RectF {
        val scale = minOf(cell.width() / bmp.width, cell.height() / bmp.height)
        val dw = bmp.width * scale
        val dh = bmp.height * scale
        val l = cell.centerX() - dw / 2f
        val t = cell.centerY() - dh / 2f
        return RectF(l, t, l + dw, t + dh)
    }

    private fun regionFor(name: String): Region = when {
        name.equals("Chest", true) -> Region.CHEST
        name.equals("Abs", true) -> Region.ABS
        name.equals("Arms", true) -> Region.ARMS
        name.equals("Shoulders", true) -> Region.SHOULDERS
        name.equals("Back", true) -> Region.BACK
        name.equals("Core", true) -> Region.CORE
        name.equals("Legs", true) -> Region.LEGS
        name.equals("Glutes", true) -> Region.GLUTES
        else -> Region.NONE
    }

    private fun overlay(view: String, region: String): Bitmap? {
        val key = "${view}_$region"
        if (highlights.containsKey(key)) return highlights[key]
        val bmp = MotionLibrary.cachedBody("body_map_${view}_$region.webp")
        if (bmp != null) {
            highlights[key] = bmp
            return bmp
        }
        MotionLibrary.bodyAsset(context, "body_map_${view}_$region.webp") {
            highlights[key] = it
            invalidate()
        }
        return null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cell = RectF()
        canvas.drawText("FRONT", width * 0.25f, height * 0.09f, titlePaint)
        canvas.drawText("BACK", width * 0.75f, height * 0.09f, titlePaint)

        drawFigure(canvas, 0, front, frontMask, cell)
        drawFigure(canvas, 1, back, backMask, cell)

        if (hintText.isNotEmpty()) {
            canvas.drawText(hintText, width / 2f, height * 0.955f, hintPaint)
        }
    }

    private fun drawFigure(canvas: Canvas, index: Int, body: Bitmap?, mask: Bitmap?, cell: RectF) {
        cellRect(index, cell)
        if (body == null) return
        val dst = fitRect(body, cell)
        src.set(0, 0, body.width, body.height)
        canvas.drawBitmap(body, src, dst, bmpPaint)

        val muscle = activeMuscle
        if (muscle.isNotEmpty() && !muscle.equals("All", true)) {
            val view = if (index == 0) "front" else "back"
            val hl = overlay(view, muscle)
            if (hl != null) canvas.drawBitmap(hl, src, dst, bmpPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && onMuscleTapped != null) {
            val index = if (event.x < width / 2f) 0 else 1
            val cell = RectF()
            cellRect(index, cell)
            val body = if (index == 0) front else back
            val mask = if (index == 0) frontMask else backMask
            if (body != null && mask != null) {
                val dst = fitRect(body, cell)
                val px = ((event.x - dst.left) / dst.width() * mask.width).toInt()
                val py = ((event.y - dst.top) / dst.height() * mask.height).toInt()
                if (px in 0 until mask.width && py in 0 until mask.height) {
                    val c = mask.getPixel(px, py)
                    val region = when (c) {
                        0xFFEB2D2D.toInt() -> Region.CHEST
                        0xFF28D75F.toInt() -> Region.ABS
                        0xFF3C69F5.toInt() -> Region.ARMS
                        0xFFF5AA28.toInt() -> Region.SHOULDERS
                        0xFFC846DC.toInt() -> Region.BACK
                        0xFF1EC8DC.toInt() -> Region.CORE
                        0xFFFA6EB4.toInt() -> Region.LEGS
                        0xFF96D73C.toInt() -> Region.GLUTES
                        else -> Region.NONE
                    }
                    if (region != Region.NONE) {
                        onMuscleTapped.invoke(region.label)
                        performClick()
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    companion object {
        private const val FRONT_IMG = "body_map_front.webp"
        private const val BACK_IMG = "body_map_back.webp"
        private const val FRONT_MASK = "body_map_front_mask.png"
        private const val BACK_MASK = "body_map_back_mask.png"
    }
}

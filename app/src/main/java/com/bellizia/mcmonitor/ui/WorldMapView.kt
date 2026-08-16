package com.bellizia.mcmonitor.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.bellizia.mcmonitor.data.Privacy
import com.bellizia.mcmonitor.lgsm.PlayerPos
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Mappa 2D del mondo (piano X/Z) navigabile con trascinamento e pizzico.
 * Disegna griglia dei chunk, spawn, giocatori e la scia dei loro spostamenti.
 */
class WorldMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    /** Pixel per blocco. */
    private var scale = 0.35f
    private var centerX = 0.0
    private var centerZ = 0.0

    private var players: List<PlayerPos> = emptyList()
    private var trails: Map<String, List<PlayerPos>> = emptyMap()
    private var selected: String? = null

    var onPlayerTap: ((PlayerPos) -> Unit)? = null
    var onViewportChanged: (() -> Unit)? = null

    private val bg = Paint().apply { color = Color.parseColor("#0E1512") }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F3A2E"); strokeWidth = 1f; style = Paint.Style.STROKE
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3D6B52"); strokeWidth = 2f; style = Paint.Style.STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4E7F63"); textSize = 22f
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.parseColor("#0E1512")
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 30f; isFakeBoldText = true
    }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B7D8C4"); textSize = 26f
    }
    private val spawnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC107"); style = Paint.Style.STROKE; strokeWidth = 3f
    }

    private val palette = listOf(
        "#4CAF50", "#42A5F5", "#FFCA28", "#AB47BC", "#EF5350",
        "#26C6DA", "#FF7043", "#9CCC65", "#EC407A", "#7E57C2"
    ).map { Color.parseColor(it) }

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomAround(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                centerX += dx / scale
                centerZ += dy / scale
                invalidate()
                onViewportChanged?.invoke()
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val hit = players.minByOrNull { hypot(screenX(it.x) - e.x, screenY(it.z) - e.y) }
                if (hit != null && hypot(screenX(hit.x) - e.x, screenY(hit.z) - e.y) < 70f) {
                    selected = if (selected == hit.name) null else hit.name
                    invalidate()
                    onPlayerTap?.invoke(hit)
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                zoomAround(1.8f, e.x, e.y)
                return true
            }
        })

    fun setData(players: List<PlayerPos>, trails: Map<String, List<PlayerPos>>) {
        this.players = players
        this.trails = trails
        invalidate()
    }

    fun centerOn(x: Double, z: Double, minScale: Float = 0.8f) {
        centerX = x
        centerZ = z
        if (scale < minScale) scale = minScale
        invalidate()
        onViewportChanged?.invoke()
    }

    /** Inquadra tutti i giocatori visibili; se non ce ne sono torna sullo spawn. */
    fun fitAll() {
        if (players.isEmpty()) {
            centerOn(0.0, 0.0, 0.35f)
            return
        }
        val minX = players.minOf { it.x }
        val maxX = players.maxOf { it.x }
        val minZ = players.minOf { it.z }
        val maxZ = players.maxOf { it.z }
        centerX = (minX + maxX) / 2
        centerZ = (minZ + maxZ) / 2
        val spanX = max(64.0, maxX - minX) * 1.4
        val spanZ = max(64.0, maxZ - minZ) * 1.4
        if (width > 0 && height > 0) {
            scale = min(width / spanX, height / spanZ).toFloat().coerceIn(0.02f, 8f)
        }
        invalidate()
        onViewportChanged?.invoke()
    }

    fun zoomBy(factor: Float) = zoomAround(factor, width / 2f, height / 2f)

    fun scaleLabel(): String = "%.2f px/blocco".format(scale)

    private fun zoomAround(factor: Float, fx: Float, fy: Float) {
        val worldX = worldX(fx)
        val worldZ = worldZ(fy)
        scale = (scale * factor).coerceIn(0.02f, 8f)
        // Mantiene fermo il punto sotto le dita.
        centerX = worldX - (fx - width / 2f) / scale
        centerZ = worldZ - (fy - height / 2f) / scale
        invalidate()
        onViewportChanged?.invoke()
    }

    private fun screenX(x: Double) = (width / 2f + (x - centerX) * scale).toFloat()
    private fun screenY(z: Double) = (height / 2f + (z - centerZ) * scale).toFloat()
    private fun worldX(px: Float) = centerX + (px - width / 2f) / scale
    private fun worldZ(py: Float) = centerZ + (py - height / 2f) / scale

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
        drawGrid(canvas)
        drawSpawn(canvas)
        drawTrails(canvas)
        drawPlayers(canvas)
        drawHud(canvas)
    }

    private fun gridStep(): Int {
        var step = 16
        while (step * scale < 70f && step < 65536) step *= 2
        return step
    }

    private fun drawGrid(canvas: Canvas) {
        val step = gridStep()
        val left = worldX(0f)
        val right = worldX(width.toFloat())
        val top = worldZ(0f)
        val bottom = worldZ(height.toFloat())

        var x = floor(left / step) * step
        while (x <= right) {
            val sx = screenX(x)
            canvas.drawLine(sx, 0f, sx, height.toFloat(), if (abs(x) < 0.01) axisPaint else gridPaint)
            canvas.drawText(x.roundToInt().toString(), sx + 6f, 26f, labelPaint)
            x += step
        }
        var z = floor(top / step) * step
        while (z <= bottom) {
            val sy = screenY(z)
            canvas.drawLine(0f, sy, width.toFloat(), sy, if (abs(z) < 0.01) axisPaint else gridPaint)
            canvas.drawText(z.roundToInt().toString(), 6f, sy - 6f, labelPaint)
            z += step
        }
    }

    private fun drawSpawn(canvas: Canvas) {
        val sx = screenX(0.0)
        val sy = screenY(0.0)
        canvas.drawCircle(sx, sy, 12f, spawnPaint)
        canvas.drawLine(sx - 18f, sy, sx + 18f, sy, spawnPaint)
        canvas.drawLine(sx, sy - 18f, sx, sy + 18f, spawnPaint)
    }

    private fun drawTrails(canvas: Canvas) {
        players.forEachIndexed { index, player ->
            val trail = trails[player.name] ?: return@forEachIndexed
            if (trail.size < 2) return@forEachIndexed
            val color = palette[index % palette.size]
            val path = Path()
            var started = false
            trail.filter { it.dimension == player.dimension }.forEach { p ->
                val sx = screenX(p.x)
                val sy = screenY(p.z)
                if (!started) {
                    path.moveTo(sx, sy); started = true
                } else {
                    path.lineTo(sx, sy)
                }
            }
            trailPaint.color = color
            trailPaint.alpha = if (selected == null || selected == player.name) 150 else 50
            canvas.drawPath(path, trailPaint)
        }
    }

    private fun drawPlayers(canvas: Canvas) {
        players.forEachIndexed { index, player ->
            val color = palette[index % palette.size]
            val sx = screenX(player.x)
            val sy = screenY(player.z)
            val faded = selected != null && selected != player.name
            dotPaint.color = color
            dotPaint.alpha = if (faded) 90 else 255
            canvas.drawCircle(sx, sy, if (selected == player.name) 16f else 12f, dotPaint)
            canvas.drawCircle(sx, sy, if (selected == player.name) 16f else 12f, dotStroke)
            namePaint.alpha = if (faded) 110 else 255
            canvas.drawText(Privacy.name(player.name), sx + 20f, sy - 10f, namePaint)
            if (selected == player.name) {
                canvas.drawText(
                    "X ${player.x.roundToInt()} Y ${player.y.roundToInt()} Z ${player.z.roundToInt()}",
                    sx + 20f, sy + 22f, hudPaint
                )
            }
        }
    }

    private fun drawHud(canvas: Canvas) {
        val text = "centro X ${centerX.roundToInt()}  Z ${centerZ.roundToInt()}   ·   ${scaleLabel()}   ·   griglia ${gridStep()} blocchi"
        canvas.drawText(text, 16f, height - 20f, hudPaint)
        canvas.drawText("N ↑", width - 70f, 40f, hudPaint)
    }
}

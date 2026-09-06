package com.bellizia.mcmonitor.ui.svago

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.bellizia.mcmonitor.ui.Testa
import kotlin.math.abs
import kotlin.math.min

/**
 * Disegna il campo e raccoglie le mosse.
 *
 * Tutto a rettangoli pieni, come le icone del resto dell'app: nessuna immagine
 * da mettere nell'archivio, quindi il peso aggiunto è zero. E a caselle grandi
 * i rettangoli sono anche la cosa giusta da vedere.
 *
 * Le mosse arrivano da uno strisciamento del dito. La tastiera a frecce sta
 * fuori, nella schermata: qui dentro non ci si mette perché coprirebbe il campo
 * proprio nel momento in cui lo si guarda.
 */
class CampoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var stato: Stato? = null
        set(value) {
            field = value
            invalidate()
        }

    var onMossa: ((Direzione) -> Unit)? = null

    private val pennello = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val riquadro = RectF()

    private companion object {
        const val FONDO = 0xFF0E1512.toInt()
        const val GHIACCIO = 0xFFA8D8F0.toInt()
        const val GHIACCIO_ORLO = 0xFF7FB8D8.toInt()
        const val USCITA = 0xFF6A3E8C.toInt()
        const val USCITA_DENTRO = 0xFFB86AD9.toInt()
        const val LUCE = 0x33FFFFFF
        const val OMBRA = 0x44000000

        /** Quanto strisciare perché conti come una mossa, in pixel densità 1. */
        const val SOGLIA_DP = 24f
    }

    private fun coloreDi(b: Blocco): Int = when (b) {
        Blocco.TERRA -> 0xFF8B5A3C.toInt()
        Blocco.PIETRA -> 0xFF8A8A8A.toInt()
        Blocco.FERRO -> 0xFFC8B49A.toInt()
        Blocco.DIAMANTE -> 0xFF4AEDD9.toInt()
        Blocco.OSSIDIANA -> 0xFF241A33.toInt()
        Blocco.GHIAIA -> 0xFF9E9689.toInt()
    }

    private val gesti = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        /**
         * La direzione è quella dove il dito è andato **di più**.
         *
         * Un dito non striscia mai dritto: chiedendo una diagonale pulita non
         * si muoverebbe mai niente, e chi gioca darebbe la colpa al gioco.
         */
        override fun onFling(
            giu: MotionEvent?, su: MotionEvent, vx: Float, vy: Float,
        ): Boolean {
            val partenza = giu ?: return false
            val dx = su.x - partenza.x
            val dy = su.y - partenza.y
            val soglia = SOGLIA_DP * resources.displayMetrics.density
            if (abs(dx) < soglia && abs(dy) < soglia) return false
            val d = if (abs(dx) > abs(dy)) {
                if (dx > 0) Direzione.DESTRA else Direzione.SINISTRA
            } else {
                if (dy > 0) Direzione.GIU else Direzione.SU
            }
            onMossa?.invoke(d)
            return true
        }
    })

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gesti.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val s = stato ?: return
        val lato = min(width.toFloat() / s.larghezza, height.toFloat() / s.altezza)
        val offX = (width - lato * s.larghezza) / 2f
        val offY = (height - lato * s.altezza) / 2f

        fun casella(x: Int, y: Int) {
            riquadro.set(offX + x * lato, offY + y * lato, offX + (x + 1) * lato, offY + (y + 1) * lato)
        }

        pennello.color = FONDO
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pennello)

        // Il suolo.
        for (y in 0 until s.altezza) for (x in 0 until s.larghezza) {
            casella(x, y)
            when (s.suoloDi(Punto(x, y))) {
                Suolo.NORMALE -> {
                    pennello.color = 0xFF16211C.toInt()
                    canvas.drawRect(riquadro, pennello)
                }
                Suolo.GHIACCIO -> {
                    pennello.color = GHIACCIO
                    canvas.drawRect(riquadro, pennello)
                    // Una riga più scura in fondo: senza, una distesa di
                    // ghiaccio è una macchia azzurra e non si contano le
                    // caselle — che è proprio quello che serve fare.
                    pennello.color = GHIACCIO_ORLO
                    canvas.drawRect(
                        riquadro.left, riquadro.bottom - lato / 8f,
                        riquadro.right, riquadro.bottom, pennello
                    )
                }
                Suolo.USCITA -> {
                    pennello.color = USCITA
                    canvas.drawRect(riquadro, pennello)
                    pennello.color = USCITA_DENTRO
                    canvas.drawRect(riquadro.inset(lato / 5f), pennello)
                }
            }
        }

        // I blocchi, con il rilievo dei pulsanti di pietra: la luce in alto a
        // sinistra e l'ombra in basso a destra bastano a far leggere una
        // griglia di quadrati come una parete.
        s.blocchi.forEach { (p, b) ->
            casella(p.x, p.y)
            pennello.color = coloreDi(b)
            canvas.drawRect(riquadro, pennello)
            val spessore = lato / 8f
            pennello.color = LUCE
            canvas.drawRect(riquadro.left, riquadro.top, riquadro.right, riquadro.top + spessore, pennello)
            canvas.drawRect(riquadro.left, riquadro.top, riquadro.left + spessore, riquadro.bottom, pennello)
            pennello.color = OMBRA
            canvas.drawRect(riquadro.left, riquadro.bottom - spessore, riquadro.right, riquadro.bottom, pennello)
            canvas.drawRect(riquadro.right - spessore, riquadro.top, riquadro.right, riquadro.bottom, pennello)
        }

        s.picconi.forEach { (p, piccone) ->
            casella(p.x, p.y)
            disegnaPiccone(canvas, piccone, lato)
        }

        casella(s.giocatore.x, s.giocatore.y)
        disegnaTesta(canvas, lato)
    }

    /** Il manico in diagonale e la punta del colore del materiale. */
    private fun disegnaPiccone(canvas: Canvas, piccone: Piccone, lato: Float) {
        val u = lato / 8f
        val l = riquadro.left
        val t = riquadro.top
        pennello.color = 0xFF6B4423.toInt()
        for (i in 2..5) canvas.drawRect(l + i * u, t + i * u, l + (i + 1) * u, t + (i + 1) * u, pennello)
        pennello.color = when (piccone) {
            Piccone.LEGNO -> 0xFFA9713B.toInt()
            Piccone.PIETRA -> 0xFF8A8A8A.toInt()
            Piccone.FERRO -> 0xFFE6E6E6.toInt()
            Piccone.DIAMANTE -> 0xFF4AEDD9.toInt()
        }
        canvas.drawRect(l + u, t + u, l + 4 * u, t + 2 * u, pennello)
        canvas.drawRect(l + u, t + u, l + 2 * u, t + 4 * u, pennello)
    }

    /**
     * La faccia è la stessa che l'app disegna per i giocatori: una in meno da
     * inventare, e la stessa mano dappertutto.
     */
    private val faccia = Testa.disegna("steve")

    private fun disegnaTesta(canvas: Canvas, lato: Float) {
        val u = lato / Testa.LATO
        for (y in 0 until Testa.LATO) for (x in 0 until Testa.LATO) {
            pennello.color = faccia[y][x]
            canvas.drawRect(
                riquadro.left + x * u, riquadro.top + y * u,
                riquadro.left + (x + 1) * u, riquadro.top + (y + 1) * u, pennello
            )
        }
    }

    private fun RectF.inset(quanto: Float): RectF =
        RectF(left + quanto, top + quanto, right - quanto, bottom - quanto)
}

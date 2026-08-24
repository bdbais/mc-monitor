package com.bellizia.mcmonitor.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.R
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.data.Privacy
import com.bellizia.mcmonitor.databinding.ActivityInventarioBinding
import com.bellizia.mcmonitor.lgsm.Giocatore
import com.bellizia.mcmonitor.lgsm.Oggetto
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Cosa ha addosso e nello zaino un giocatore, disposto come nel gioco.
 *
 * Le caselle sono quelle di Minecraft e nello stesso ordine — armatura, mano
 * secondaria, cintura, zaino, baule dell'End — perché chi apre questa schermata
 * ha in testa quella disposizione: rimetterla in fila alfabetica vorrebbe dire
 * costringerlo a ricostruirla a mente.
 *
 * Le caselle non hanno l'icona dell'oggetto: le texture di Minecraft sono di
 * Mojang e non si possono mettere dentro un'app. C'è il nome, la quantità, e la
 * barra di usura quando l'oggetto si consuma.
 */
class InventarioActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GIOCATORE = "giocatore"
        const val EXTRA_ONLINE = "online"
    }

    private lateinit var binding: ActivityInventarioBinding
    private val nome: String get() = intent.getStringExtra(EXTRA_GIOCATORE).orEmpty()
    private val online: Boolean get() = intent.getBooleanExtra(EXTRA_ONLINE, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInventarioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
        applyInsets()

        binding.sottotitolo.text = Privacy.name(nome)
        binding.btnHelp.setOnClickListener { HelpDialog.show(this, Help.INVENTARIO) }
        binding.swipe.setOnRefreshListener { carica() }
        carica()
    }

    private fun carica() {
        if (nome.isBlank()) {
            binding.stato.text = "Nessun giocatore."
            return
        }
        binding.swipe.isRefreshing = true
        binding.stato.text = if (online) {
            "$nome è collegato: chiedo al server di salvare, poi leggo il suo file…"
        } else {
            "Leggo il file di $nome…"
        }
        lifecycleScope.launch {
            val esito = runCatching { McRepository.inventario(nome, salvaPrima = online) }
            (esito.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (isFinishing || isDestroyed) return@launch
            binding.swipe.isRefreshing = false
            esito.fold(
                onSuccess = { (g, salvato) -> mostra(g, salvato) },
                onFailure = {
                    binding.stato.text = it.userMessage()
                    svuota()
                }
            )
        }
    }

    private fun svuota() {
        binding.armatura.removeAllViews()
        binding.cintura.removeAllViews()
        binding.zaino.removeAllViews()
        binding.bauleEnd.removeAllViews()
        binding.titoloCintura.visible(false)
        binding.titoloZaino.visible(false)
        binding.titoloEnd.visible(false)
        binding.rigaAddosso.visible(false)
        binding.nota.text = ""
    }

    private fun mostra(g: Giocatore, salvato: Boolean) {
        binding.rigaAddosso.visible(true)
        binding.titoloCintura.visible(true)
        binding.titoloZaino.visible(true)

        binding.stato.text = buildString {
            val pezzi = mutableListOf<String>()
            g.cuori?.let { pezzi += "$it cuori" }
            g.fame?.let { pezzi += "fame $it/20" }
            g.livelli?.let { pezzi += "livello $it" }
            g.dimensione?.let { pezzi += it }
            append(pezzi.joinToString(" · "))
            g.posizione?.let { (x, y, z) ->
                if (isNotEmpty()) append('\n')
                append("X ${x.roundToInt()}  Y ${y.roundToInt()}  Z ${z.roundToInt()}")
            }
        }

        // La faccia: due caratteri grandi con il colore ricavato dal nome. Non è
        // la skin — quella sta sui server di Mojang e andrebbe chiesta a loro,
        // mandandogli il nome di una persona per un dettaglio grafico.
        binding.avatar.setImageDrawable(null)
        binding.avatar.visible(false)
        binding.rigaAddosso.removeView(binding.avatar)
        if (binding.rigaAddosso.getChildAt(0) !is TextView) {
            binding.rigaAddosso.addView(faccia(), 0)
        }

        binding.armatura.removeAllViews()
        val e = g.equipaggiamento
        binding.armatura.addView(riga(listOf("Testa" to e.testa, "Petto" to e.petto)))
        binding.armatura.addView(riga(listOf("Gambe" to e.gambe, "Piedi" to e.piedi)))
        binding.armatura.addView(riga(listOf("Altra mano" to e.manoSecondaria)))

        binding.cintura.removeAllViews()
        g.cintura.forEachIndexed { i, o ->
            binding.cintura.addView(
                casella(o, if (i == g.inMano) "in mano" else "casella ${i + 1}", i == g.inMano)
            )
        }

        binding.zaino.removeAllViews()
        val nelloZaino = g.zaino.filterNotNull()
        if (nelloZaino.isEmpty()) {
            binding.zaino.addView(vuoto("Zaino vuoto."))
        } else {
            nelloZaino.forEach { binding.zaino.addView(casella(it, null, false)) }
        }

        val end = g.bauleDellEnd.filterNotNull()
        binding.titoloEnd.visible(end.isNotEmpty())
        binding.bauleEnd.removeAllViews()
        end.forEach { binding.bauleEnd.addView(casella(it, null, false)) }

        binding.nota.text = buildString {
            append(
                if (salvato) {
                    "Ho chiesto al server di salvare prima di leggere, quindi questo è " +
                            "aggiornato a un attimo fa."
                } else if (online) {
                    "Il server non ha risposto alla richiesta di salvare: questo è l'ultimo " +
                            "salvataggio, non per forza adesso."
                } else {
                    "Minecraft riscrive questo file quando il giocatore esce e a ogni " +
                            "salvataggio del mondo: è l'ultima volta che è uscito."
                }
            )
            append("\n\nLe caselle sono quelle del gioco. Non ci sono le icone degli oggetti: ")
            append("le texture di Minecraft sono di Mojang e non stanno dentro l'app.")
        }
    }

    /** Due lettere grandi, con un colore che dipende dal nome. */
    private fun faccia(): View = TextView(this).apply {
        val d = resources.displayMetrics.density
        layoutParams = LinearLayout.LayoutParams((96 * d).toInt(), (96 * d).toInt()).apply {
            marginEnd = (12 * d).toInt()
        }
        gravity = Gravity.CENTER
        typeface = Typeface.MONOSPACE
        textSize = 30f
        setTextColor(Color.WHITE)
        text = Privacy.name(nome).take(2).uppercase()
        setBackgroundColor(coloreDi(nome))
    }

    /**
     * Un colore stabile per un nome.
     *
     * Serve solo a distinguere due giocatori a colpo d'occhio: lo stesso nome dà
     * sempre lo stesso colore, e non si somigliano fra loro.
     */
    private fun coloreDi(nome: String): Int {
        val h = nome.lowercase().fold(7) { a, c -> a * 31 + c.code }
        return Color.HSVToColor(floatArrayOf((h % 360 + 360) % 360f, 0.45f, 0.42f))
    }

    private fun riga(caselle: List<Pair<String, Oggetto?>>): View {
        val l = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        caselle.forEach { (etichetta, o) ->
            l.addView(casella(o, etichetta, false).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
        return l
    }

    /** Una casella, piena o vuota. */
    private fun casella(o: Oggetto?, etichetta: String?, inMano: Boolean): View {
        val d = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
            setBackgroundResource(
                if (inMano) R.drawable.bg_stone_button else R.drawable.bg_panel_inset
            )
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.setMargins(0, (3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt())
            layoutParams = p
        }

        etichetta?.let {
            box.addView(TextView(this).apply {
                text = it
                textSize = 10f
                setTextColor(
                    ContextCompat.getColor(
                        this@InventarioActivity,
                        if (inMano) R.color.grass else R.color.text_dim
                    )
                )
            })
        }

        box.addView(TextView(this).apply {
            textSize = 13f
            if (o == null) {
                text = "—"
                setTextColor(ContextCompat.getColor(this@InventarioActivity, R.color.text_dim))
            } else {
                setTextColor(ContextCompat.getColor(this@InventarioActivity, R.color.text))
                text = buildString {
                    append(o.etichetta)
                    if (o.quantita > 1) append("  ×").append(o.quantita)
                    if (o.incantato) append("  ✦")
                }
            }
        })

        // La barra dell'usura: si vede solo quando l'oggetto si consuma, e
        // diventa rossa quando sta per rompersi.
        o?.usura?.let { u ->
            box.addView(TextView(this).apply {
                textSize = 10f
                val rimasto = ((1 - u) * 100).roundToInt()
                text = "resistenza $rimasto%"
                setTextColor(
                    ContextCompat.getColor(
                        this@InventarioActivity,
                        if (u > 0.85) R.color.warning else R.color.text_dim
                    )
                )
            })
        }

        if (o != null && o.nomeDato != null) {
            box.addView(TextView(this).apply {
                text = o.nomeCorto
                textSize = 10f
                setTextColor(ContextCompat.getColor(this@InventarioActivity, R.color.text_dim))
            })
        }
        return box
    }

    private fun vuoto(testo: String): View = TextView(this).apply {
        text = testo
        textSize = 12f
        setTextColor(ContextCompat.getColor(this@InventarioActivity, R.color.text_dim))
    }

    private fun applyInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appbar.setPadding(0, bars.top, 0, 0)
            view.setPadding(bars.left, 0, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}

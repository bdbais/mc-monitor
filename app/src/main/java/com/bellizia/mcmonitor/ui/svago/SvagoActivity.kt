package com.bellizia.mcmonitor.ui.svago

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.bellizia.mcmonitor.data.Prefs
import android.widget.TextView
import android.widget.FrameLayout
import android.widget.EditText
import android.view.Gravity
import android.text.InputFilter
import android.graphics.Typeface
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.bellizia.mcmonitor.databinding.ActivitySvagoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * I due campi, con le regole da sala giochi.
 *
 * Tre vite, un punteggio che scende con le mosse, e ricominciare costa. Senza
 * un costo un rompicapo con l'annulla infinito si risolve a forza bruta, e la
 * mossa che rovina tutto — quella su cui vive il gioco — smette di far paura.
 *
 * In fondo non c'è un livello tre: c'è l'indirizzo di dove continua.
 */
class SvagoActivity : AppCompatActivity() {

    private lateinit var b: ActivitySvagoBinding

    private var indice = 0
    private var vite = VITE
    private var punti = 0
    private var mosse = 0
    private var stato: Stato? = null
    private val suoni = Suoni()

    private companion object {
        const val VITE = 3
        const val BASE = 1000
        const val COSTO_MOSSA = 10
        const val MINIMO = 100
        const val SEGUITO = "https://minestrat.bais.info"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySvagoBinding.inflate(layoutInflater)
        setContentView(b.root)
        // Uno schermo che si spegne a metà di un ragionamento è la cosa più
        // fastidiosa che possa fare un rompicapo.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        b.campo.onMossa = { muovi(it) }
        b.su.setOnClickListener { muovi(Direzione.SU) }
        b.giu.setOnClickListener { muovi(Direzione.GIU) }
        b.sinistra.setOnClickListener { muovi(Direzione.SINISTRA) }
        b.destra.setOnClickListener { muovi(Direzione.DESTRA) }
        b.ricomincia.setOnClickListener { perdiUnaVita() }

        suoni.acceso = Prefs.suoniAccesi
        aggiornaAudio()
        b.audio.setOnClickListener {
            Prefs.suoniAccesi = !Prefs.suoniAccesi
            suoni.acceso = Prefs.suoniAccesi
            aggiornaAudio()
        }

        carica(0)
    }

    override fun onDestroy() {
        // Una traccia audio e' una risorsa di sistema: sette lasciate aperte
        // per una partita finita si notano quando il telefono scalda.
        suoni.chiudi()
        super.onDestroy()
    }

    private fun carica(quale: Int) {
        indice = quale
        mosse = 0
        val campo = Livelli.TUTTI[quale]
        stato = campo.crea()
        b.titolo.text = campo.titolo
        b.suggerimento.text = campo.suggerimento
        b.campo.stato = stato
        aggiornaTesta()
    }

    private fun aggiornaTesta() {
        b.vite.text = "♥".repeat(vite.coerceAtLeast(0))
        b.punteggio.text = "PUNTI $punti   MOSSE $mosse   ${indice + 1}/${Livelli.TUTTI.size}"
    }

    private fun aggiornaAudio() {
        b.audio.alpha = if (Prefs.suoniAccesi) 1f else 0.35f
    }

    /**
     * Il suono si sceglie da quello che è successo, non da quello che si è
     * premuto: la stessa freccia può camminare, scavare o non fare niente.
     */
    private fun faiSentire(prima: Stato, esito: Mossa, d: Direzione) {
        when (esito.esito) {
            Esito.NULLA -> suoni.rifiutato()
            Esito.CREPATO -> suoni.picconata(bersaglio(prima, d)?.durezza ?: 0)
            Esito.SCAVATO -> suoni.rotto()
            Esito.MOSSO -> {
                val passi = kotlin.math.abs(esito.stato.giocatore.x - prima.giocatore.x) +
                        kotlin.math.abs(esito.stato.giocatore.y - prima.giocatore.y)
                if (passi > 1) suoni.scivolata(passi)
                // Raccogliere copre il fruscio: è la cosa più importante delle
                // due, e sentirle insieme non farebbe capire nessuna delle due.
                if (esito.stato.inMano?.piccone != prima.inMano?.piccone) suoni.raccolto()
            }
            Esito.VINTO -> Unit
        }
    }

    private fun bersaglio(stato: Stato, d: Direzione): Blocco? =
        stato.bloccoIn(Punto(stato.giocatore.x + d.dx, stato.giocatore.y + d.dy))

    private fun muovi(d: Direzione) {
        val ora = stato ?: return
        if (ora.vinto) return
        val esito = Motore.muovi(ora, d)
        faiSentire(ora, esito, d)
        // Una mossa che non fa niente non conta: sbattere contro un muro non
        // deve costare punti, o si finisce a giocare guardando il contatore
        // invece che il campo.
        if (esito.esito == Esito.NULLA) return
        mosse++
        stato = esito.stato
        // La vista riceve il prima e il dopo: la differenza fra «ha scavato» e
        // «ha camminato» la sa chi ha chiamato il motore, e farla dedurre di
        // nuovo al disegno vorrebbe dire due regole da tenere d'accordo.
        b.campo.mostra(ora, esito, d)
        aggiornaTesta()
        if (esito.stato.vinto) vinto()
    }

    private fun vinto() {
        suoni.vinto()
        val guadagno = (BASE - mosse * COSTO_MOSSA).coerceAtLeast(MINIMO)
        punti += guadagno
        aggiornaTesta()
        if (indice + 1 < Livelli.TUTTI.size) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Fatto! +$guadagno")
                .setMessage("${mosse} mosse.")
                .setCancelable(false)
                .setPositiveButton("Avanti") { _, _ -> carica(indice + 1) }
                .show()
        } else {
            fine()
        }
    }

    private fun perdiUnaVita() {
        suoni.persa()
        vite--
        if (vite <= 0) {
            finePartita("GAME OVER") {
                vite = VITE
                punti = 0
                carica(0)
            }
            return
        }
        carica(indice)
    }

    /**
     * La fine di una partita, comunque sia andata.
     *
     * Prima le iniziali, poi la tabella, poi cosa si fa adesso. In quest'ordine
     * e non in un altro: chiedere le iniziali dopo aver gia' mostrato la
     * classifica toglie l'unico momento per cui si giocava.
     */
    private fun finePartita(titolo: String, poi: () -> Unit) {
        val tabella = Record.leggi(Prefs.tabellaRecord)
        if (!Record.entra(tabella, punti)) {
            mostraTabellone(titolo, tabella, null, poi)
            return
        }
        val casella = EditText(this).apply {
            hint = "AAA"
            filters = arrayOf(InputFilter.LengthFilter(3), InputFilter.AllCaps())
            setSingleLine()
            gravity = Gravity.CENTER
            textSize = 28f
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("$titolo · $punti punti")
            .setMessage("Sei in classifica. Tre lettere.")
            .setView(FrameLayout(this).apply {
                setPadding(60, 20, 60, 0)
                addView(casella)
            })
            .setCancelable(false)
            .setPositiveButton("Metti") { _, _ ->
                val riga = Record.Riga(Record.iniziali(casella.text?.toString().orEmpty()), punti)
                val nuova = Record.inserisci(tabella, riga)
                Prefs.tabellaRecord = Record.scrivi(nuova)
                mostraTabellone(titolo, nuova, riga, poi)
            }
            .show()
    }

    private fun mostraTabellone(
        titolo: String,
        tabella: List<Record.Riga>,
        mia: Record.Riga?,
        poi: () -> Unit,
    ) {
        val corpo = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 16f
            setPadding(60, 30, 60, 10)
            text = Record.tabellone(tabella, mia)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("$titolo · $punti")
            .setView(corpo)
            .setCancelable(false)
            .setPositiveButton("Da capo") { _, _ -> poi() }
            .setNegativeButton("Basta") { _, _ -> finish() }
            .show()
    }

    /**
     * La fine dei due campi.
     *
     * Non è una schermata di pubblicità: è il punto in cui il gioco finisce
     * davvero e si dice dove continua. Chi non vuole chiude e ha giocato a un
     * gioco finito lo stesso.
     */
    private fun fine() {
        MaterialAlertDialogBuilder(this)
            .setTitle("MINESTRAT")
            .setMessage(
                "Finiti tutti e due. Punteggio: $punti.\n\n" +
                        "Qui dentro ce n'erano due. Quello vero ha sette mondi, i mostri, " +
                        "la redstone e un drago in fondo — e i livelli se li inventa da solo.\n\n" +
                        "È gratis e sta nello stesso posto da cui hai preso questa app."
            )
            .setCancelable(false)
            .setPositiveButton("Vai a prenderlo") { _, _ ->
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SEGUITO)))
                }
                finish()
            }
            .setNegativeButton("Il tabellone") { _, _ ->
                finePartita("FINITO") { vite = VITE; punti = 0; carica(0) }
            }
            .show()
    }
}

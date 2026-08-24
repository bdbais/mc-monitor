package com.bellizia.mcmonitor.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.ActivityPostaBinding
import com.bellizia.mcmonitor.databinding.ItemCopiaBinding
import com.bellizia.mcmonitor.lgsm.Messaggio
import com.bellizia.mcmonitor.lgsm.Posta
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * La posta per chi non c'e'.
 *
 * L'admin lascia un messaggio a un giocatore scollegato, e glielo consegna il
 * server quando quello rientra. Sta sul server e non qui per un motivo solo: il
 * telefono non e' acceso nel momento in cui il giocatore entra.
 */
class PostaActivity : AppCompatActivity() {

    companion object {
        /** Il nome su cui aprire subito la scrittura, se si arriva da un giocatore. */
        const val EXTRA_GIOCATORE = "giocatore"
    }

    private lateinit var binding: ActivityPostaBinding
    private val orario = SimpleDateFormat("d MMM, HH:mm", Locale.ITALIAN)

    private var inAttesa: List<Messaggio> = emptyList()
    private var online: List<String> = emptyList()
    /** I nomi gia' visti, per non farli riscrivere a mano. */
    private var noti: List<String> = emptyList()
    private var consegnaAttiva = false
    /** Per non far scattare il listener mentre e' l'app a muovere l'interruttore. */
    private var sistemando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
        applyInsets()

        binding.sottotitolo.text = Prefs.load().displayName
        binding.btnHelp.setOnClickListener { HelpDialog.show(this, Help.POSTA) }
        binding.btnScrivi.setOnClickListener { chiediDestinatario() }
        binding.btnRegistro.setOnClickListener { registro() }
        binding.swipe.setOnRefreshListener { load() }
        // Ruotando lo schermo il sistema rimette da solo l'interruttore com'era,
        // e il listener partiva come se l'avesse toccato l'utente: compariva dal
        // nulla il dialogo che chiede di installare la consegna sul server.
        // Lo stato di questa schermata viene solo da load(), mai dal ripristino.
        binding.consegna.isSaveEnabled = false
        binding.consegna.setOnCheckedChangeListener { _, acceso ->
            if (!sistemando && acceso != consegnaAttiva) cambiaConsegna(acceso)
        }
        load()

        // Arrivando dal pannello di un giocatore il nome e' gia' deciso: si
        // apre direttamente il foglio per scrivere.
        if (savedInstanceState == null) {
            intent.getStringExtra(EXTRA_GIOCATORE)
                ?.takeIf { Posta.nomeValido(it) }
                ?.let { chiediTesto(it) }
        }
    }

    // ------------------------------------------------------------ lettura

    private fun load() {
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            val messaggi = runCatching { McRepository.postaLeggi() }
            // Chi c'e' adesso serve solo per dire "e' collegato, glielo mando
            // subito": se non si riesce a sapere, non e' un errore.
            val chiCe = runCatching { McRepository.online(withPositions = false).names }
            val attiva = runCatching { McRepository.postaConsegnaAttiva() }
            val whitelist = runCatching { McRepository.whitelist().map { p -> p.name } }
            binding.swipe.isRefreshing = false

            messaggi.fold(
                onSuccess = { inAttesa = it },
                onFailure = {
                    binding.intestazione.text = it.userMessage()
                    binding.elenco.removeAllViews()
                    binding.titoloElenco.visible(false)
                    sistemando = true
                    binding.consegna.isEnabled = false
                    binding.consegna.isChecked = false
                    sistemando = false
                    binding.consegnaNota.text =
                        "Non so dire se la consegna è attiva: prima devo riuscire a leggere il server."
                    return@launch
                }
            )
            online = chiCe.getOrDefault(emptyList())
            consegnaAttiva = attiva.getOrDefault(false)
            noti = (online + whitelist.getOrDefault(emptyList()) + inAttesa.map { m -> m.giocatore })
                .distinct()
                .sortedBy { n -> n.lowercase() }

            sistemando = true
            binding.consegna.isEnabled = attiva.isSuccess
            binding.consegna.isChecked = consegnaAttiva
            sistemando = false
            binding.consegnaNota.text = when {
                attiva.isFailure ->
                    "Non so dire se la consegna è attiva: " +
                            attiva.exceptionOrNull()?.userMessage().orEmpty()
                consegnaAttiva ->
                    "Il server controlla ogni minuto e consegna a chi e' rientrato. " +
                            "Spegnendola i messaggi in attesa restano dove sono."
                else ->
                    "Senza, i messaggi restano in attesa e non li consegna nessuno. " +
                            "Accendendola metto sul server un piccolo script e una riga di cron."
            }
            render()
        }
    }

    private fun render() {
        binding.intestazione.text =
            "Un messaggio lasciato qui arriva in chat al giocatore quando rientra, " +
                    "anche se il telefono e' spento e tu non ci sei."

        binding.titoloElenco.visible(true)
        binding.titoloElenco.text = when (inAttesa.size) {
            0 -> "Niente in attesa."
            1 -> "1 messaggio in attesa"
            else -> "${inAttesa.size} messaggi in attesa"
        }

        binding.elenco.removeAllViews()
        inAttesa.forEach { binding.elenco.addView(riga(it)) }

        binding.esclusi.text = if (inAttesa.isEmpty()) {
            ""
        } else {
            "Tocca un messaggio per toglierlo prima che parta."
        }
    }

    private fun riga(m: Messaggio): View {
        val item = ItemCopiaBinding.inflate(layoutInflater, binding.elenco, false)
        item.titolo.text = "a ${m.giocatore}"
        item.quando.text = orario.format(Date(m.quando * 1000))
        item.dettagli.text = m.testo
        val collegato = online.any { it.equals(m.giocatore, ignoreCase = true) }
        item.nota.visible(collegato || !consegnaAttiva)
        item.nota.text = when {
            // La consegna spenta viene prima: con l'interruttore giu' non arriva
            // niente a nessuno, collegato o no, e promettere il contrario proprio
            // a chi ha appena spento sarebbe la bugia piu' facile da prendere.
            !consegnaAttiva -> "La consegna \u00e8 spenta: resta qui finch\u00e9 non la accendi."
            else -> "\u00c8 collegato adesso: gli arriva entro un minuto."
        }
        item.card.setOnClickListener { chiediSeTogliere(m) }
        return item.root
    }

    // ----------------------------------------------------------- scrittura

    private fun chiediDestinatario() {
        val campo = AutoCompleteTextView(this).apply {
            hint = "Nome del giocatore"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setAdapter(
                ArrayAdapter(
                    this@PostaActivity,
                    android.R.layout.simple_list_item_1,
                    noti
                )
            )
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("A chi scrivi?")
            .setView(conMargini(campo))
            .setPositiveButton("Avanti") { _, _ ->
                val nome = campo.text.toString().trim()
                when {
                    !Posta.nomeValido(nome) ->
                        avviso("Non e' un nome valido", "Un nome Minecraft e' fatto di lettere, numeri e trattini bassi, al massimo 16.")
                    online.any { it.equals(nome, ignoreCase = true) } -> chiediSeSubito(nome)
                    else -> chiediTesto(nome)
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    /** Scrivere in cassetta a chi e' gia' collegato sarebbe un giro inutile. */
    private fun chiediSeSubito(nome: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("$nome e' collegato")
            .setMessage(
                "E' in gioco adesso. Vuoi scrivergli subito in chat, o lasciargli " +
                        "comunque un messaggio per la prossima volta?"
            )
            .setPositiveButton("Subito") { _, _ -> chiediTesto(nome, subito = true) }
            .setNeutralButton("Lascia in attesa") { _, _ -> chiediTesto(nome) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun chiediTesto(nome: String, subito: Boolean = false) {
        val campo = EditText(this).apply {
            hint = "Il messaggio"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setLines(3)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (subito) "Scrivi a $nome" else "Messaggio per $nome")
            .setMessage(
                if (subito) "Gli arriva subito in chat."
                else "Gli arriva in chat quando rientra."
            )
            .setView(conMargini(campo))
            .setPositiveButton("Manda") { _, _ ->
                val testo = Posta.ripulisci(campo.text.toString())
                if (testo.isBlank()) return@setPositiveButton
                if (subito) mandaSubito(nome, testo) else accoda(nome, testo)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun accoda(nome: String, testo: String) = lavora {
        McRepository.postaAccoda(nome, testo)
        "Lasciato in attesa: gli arriva quando rientra."
    }

    private fun mandaSubito(nome: String, testo: String) = lavora {
        McRepository.send("tell $nome [MC Monitor] $testo")
        "Mandato a $nome."
    }

    private fun chiediSeTogliere(m: Messaggio) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Togliere il messaggio?")
            .setMessage("A ${m.giocatore}: «${m.testo}»\n\nNon gli arrivera' piu'.")
            .setPositiveButton("Togli") { _, _ ->
                lavora { McRepository.postaCancella(m) }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // ------------------------------------------------- quello che e' partito

    /**
     * Il registro delle consegne.
     *
     * Non e' un di piu': quando il server non conferma ne' che il messaggio e'
     * arrivato ne' che il giocatore non c'era, la posta risulta partita ma
     * segnata "non confermato". Quella riga ha senso solo se qualcuno la puo'
     * leggere -- e dentro c'e' il testo per intero, cosi' si riscrive da li'.
     */
    private fun registro() {
        val testo = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(48, 24, 48, 8)
            text = "Leggo il registro sul server…"
        }
        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle("Cosa è già partito")
            .setView(ScrollView(this).apply { addView(testo) })
            .setPositiveButton("Chiudi", null)
            .show()

        lifecycleScope.launch {
            val esito = runCatching { McRepository.postaConsegnati() }
            (esito.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (isFinishing || isDestroyed || !dialogo.isShowing) return@launch
            testo.text = esito.fold(
                onSuccess = { righe ->
                    if (righe.isEmpty()) {
                        "Non è ancora partito niente."
                    } else {
                        righe.joinToString("\n\n") { c ->
                            buildString {
                                append(c.quando).append("  →  ").append(c.giocatore).append('\n')
                                append(c.testo)
                                if (c.illeggibile) append("\n(era illeggibile: messo da parte)")
                            }
                        }
                    }
                },
                onFailure = { it.userMessage() }
            )
        }
    }

    // ------------------------------------------------------------ consegna

    private fun cambiaConsegna(acceso: Boolean) {
        if (!acceso) {
            spegni()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Accendere la consegna?")
            .setMessage(
                "Metto sul computer un piccolo script e una riga di cron che lo lancia " +
                        "ogni minuto. Quando non c'e' niente in attesa lo script esce subito " +
                        "senza toccare il server.\n\nDel crontab tengo una copia di com'era, " +
                        "e quello che c'e' dentro di altri non lo tocco."
            )
            .setPositiveButton("Accendi") { _, _ ->
                lavora { McRepository.postaConsegna(true) }
            }
            .setNegativeButton("Annulla") { _, _ -> rimettiInterruttore() }
            .setOnCancelListener { rimettiInterruttore() }
            .show()
    }

    private fun spegni() = lavora { McRepository.postaConsegna(false) }

    private fun rimettiInterruttore() {
        sistemando = true
        binding.consegna.isChecked = consegnaAttiva
        sistemando = false
    }

    // -------------------------------------------------------------- utili

    private fun lavora(azione: suspend () -> String) {
        binding.progress.visible(true)
        binding.btnScrivi.isEnabled = false
        binding.consegna.isEnabled = false
        lifecycleScope.launch {
            val esito = runCatching { azione() }
            // runCatching prende anche la CancellationException: senza rilanciarla,
            // uscendo dalla schermata a meta' operazione si finiva ad aprire un
            // dialogo su un'activity gia' morta.
            (esito.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (isFinishing || isDestroyed) return@launch
            binding.progress.visible(false)
            binding.btnScrivi.isEnabled = true
            binding.consegna.isEnabled = true
            esito.fold(
                onSuccess = { messaggio -> avviso("Fatto", messaggio) },
                onFailure = { avviso("Non ha funzionato", it.userMessage()) }
            )
            load()
        }
    }

    private fun avviso(titolo: String, testo: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titolo)
            .setMessage(testo)
            .setPositiveButton("Ho capito", null)
            .show()
    }

    private fun conMargini(view: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val m = (20 * resources.displayMetrics.density).toInt()
        setPadding(m, m / 2, m, 0)
        addView(view)
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

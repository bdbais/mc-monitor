package com.bellizia.mcmonitor.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup.LayoutParams
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.bellizia.mcmonitor.R
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bellizia.mcmonitor.MainActivity
import com.bellizia.mcmonitor.rcon.RconManager
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.lgsm.Registro
import com.bellizia.mcmonitor.data.Privacy
import com.bellizia.mcmonitor.databinding.FragmentConsoleBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Scheda "Console": coda del log del server e invio comandi. */
class ConsoleFragment : Fragment() {

    private var _b: FragmentConsoleBinding? = null
    private val b get() = _b!!
    private var busy = false

    /** Ultimo log ricevuto per intero: il filtro lavora su questo, non sul server. */
    private var logCompleto = ""

    /** Testo cercato: vuoto quando la ricerca e' spenta. */
    private var filtro = ""

    /** Righe mandate a capo invece che scorrevoli in orizzontale. */
    private var wrapLines = false

    /** Solo log a schermo: spariscono controlli, scorciatoie, campo e barra delle schede. */
    private var fullscreen = false

    /** Come stavano le righe prima del tutto schermo, per rimetterle come erano. */
    private var wrapPrimaDelloSchermo = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentConsoleBinding.inflate(inflater, container, false)
        return b.root
    }

    /**
     * Comandi rapidi: quelli che finiscono con uno spazio attendono un argomento,
     * gli altri sono completi e basta premere Invia.
     */
    private val quickCommands = listOf(
        "list", "say ", "tp ", "gamemode survival ", "time set day", "time set night",
        "weather clear", "difficulty ", "whitelist list", "whitelist add ", "kick ",
        "ban ", "pardon ", "op ", "deop ", "save-all", "seed", "kill @e[type=item]"
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.swipe.setOnRefreshListener { load() }
        b.btnSend.setOnClickListener { send() }
        b.input.setOnEditorActionListener { _, _, _ -> send(); true }
        buildQuickCommands()

        b.btnCerca.setOnClickListener { toggleFiltro() }
        b.btnMacro.setOnClickListener {
            startActivity(Intent(requireContext(), MacroActivity::class.java))
        }
        b.filtro.doAfterTextChanged {
            filtro = it?.toString().orEmpty()
            renderLog()
        }
        /*
         * Nessun gestore di tocchi sul registro.
         *
         * Prima c'era un doppio tap che copiava la riga, ma il rilevatore
         * dichiarava di aver gestito ogni tocco e il TextView non ne vedeva
         * nessuno: selezionare era impossibile. La selezione di Android da'
         * gia' scegli, seleziona tutto, copia e condividi -- meglio di
         * qualsiasi scorciatoia scritta a mano.
         */
        b.btnWrap.setOnClickListener { setWrap(!wrapLines) }
        b.btnFullscreen.setOnClickListener { setFullscreen(true) }
        b.btnEsciSchermo.setOnClickListener { setFullscreen(false) }
        b.btnComandi.setOnClickListener { showCommandList() }
        b.btnRegistro.setOnClickListener { menuRegistro() }

        /*
         * In orizzontale lo spazio in altezza e' pochissimo: barra, schede,
         * interruttore, scorciatoie e campo di testo lasciavano al log due righe.
         * Qui la fila di scorciatoie diventa un pulsante, l'interruttore perde
         * l'etichetta e le righe vanno a capo, che su uno schermo largo si legge
         * meglio dello scorrimento laterale.
         */
        val orizzontale = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        b.rigaComandi.visible(!orizzontale)
        b.btnComandi.visible(orizzontale)
        if (orizzontale) b.autoRefresh.text = ""
        setWrap(orizzontale)

        // In tutto schermo il tasto indietro riporta alla scheda invece di uscire.
        val indietro = requireActivity().onBackPressedDispatcher.addCallback(this, false) {
            setFullscreen(false)
        }
        backCallback = indietro

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    if (b.autoRefresh.isChecked && Prefs.load().isComplete) load()
                    delay(6_000)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.load().isComplete && b.log.text.isNullOrBlank()) load()
        controllaRcon()
    }

    // ------------------------------------------------------------ RCON

    /**
     * Com'e' messo RCON per il server aperto adesso.
     *
     * [SPENTO] e [NON_RISPONDE] finiscono nello stesso posto — le impostazioni — ma
     * non sono la stessa cosa e non vanno raccontate allo stesso modo: nel primo
     * caso non e' mai stato acceso, nel secondo qualcuno l'ha acceso e qualcosa si
     * e' rotto dopo.
     */
    private enum class StatoRcon { ATTIVO, SPENTO, NON_RISPONDE }

    companion object {
        /**
         * I server per cui il messaggio e' gia' comparso in questo giro dell'app.
         *
         * Il riquadro resta finche' RCON non funziona, ma la finestra si apre una
         * volta sola: ripeterla a ogni passaggio sulla scheda la trasformerebbe in
         * una cosa da chiudere senza leggere.
         */
        private val avvisati = mutableSetOf<String>()

        /** Ultima volta che si e' provato davvero a parlare con RCON, per server. */
        private val ultimaProva = mutableMapOf<String, Long>()

        private const val PAUSA_FRA_PROVE_MS = 60_000L
    }

    private fun controllaRcon() {
        val cfg = Prefs.load()
        if (!cfg.isComplete) {
            mostraAvviso(null)
            return
        }

        if (!cfg.rconUsable) {
            mostraAvviso(StatoRcon.SPENTO)
            return
        }

        // RCON risulta configurato: se risponde non c'e' niente da dire. La prova
        // costa un giro di rete, quindi non si rifa' a ogni ritorno sulla scheda.
        val adesso = System.currentTimeMillis()
        val ultima = ultimaProva[cfg.id] ?: 0L
        if (adesso - ultima < PAUSA_FRA_PROVE_MS) return
        ultimaProva[cfg.id] = adesso

        viewLifecycleOwner.lifecycleScope.launch {
            val esito = runCatching { RconManager.test(cfg) }
            if (_b == null) return@launch
            mostraAvviso(if (esito.isSuccess) StatoRcon.ATTIVO else StatoRcon.NON_RISPONDE)
        }
    }

    private fun mostraAvviso(stato: StatoRcon?) {
        val bind = _b ?: return
        if (stato == null || stato == StatoRcon.ATTIVO) {
            bind.avvisoRcon.visible(false)
            return
        }

        val spento = stato == StatoRcon.SPENTO
        bind.avvisoRconTitolo.text =
            if (spento) "RCON non è attivo" else "RCON non risponde"
        bind.avvisoRconTesto.text = if (spento) {
            "I comandi partono lo stesso, per la stessa strada che usa la tastiera del " +
                    "computer, ma il server non ha modo di rispondere: qui sotto non vedrai " +
                    "l'esito, solo quello che finisce nel registro. Con RCON la risposta " +
                    "arriva subito, e resta fuori dal registro."
        } else {
            "RCON è configurato, ma non ha risposto. Il server potrebbe essere spento, " +
                    "la porta o la password potrebbero non essere più quelle scritte in " +
                    "server.properties. Intanto i comandi continuano a partire alla cieca."
        }
        bind.btnAvvisoRcon.text = if (spento) "Attiva RCON" else "Controlla RCON"
        bind.btnAvvisoRcon.setOnClickListener { vaiAImpostazioni() }
        bind.btnAvvisoDopo.setOnClickListener { bind.avvisoRcon.visible(false) }
        bind.avvisoRcon.visible(true)

        val cfg = Prefs.load()
        if (cfg.id !in avvisati) {
            avvisati += cfg.id
            spiegaRcon(spento)
        }
    }

    /** La finestra che si apre entrando: dice cosa manca e porta dove si sistema. */
    private fun spiegaRcon(spento: Boolean) {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (spento) "La console funziona a metà" else "RCON non risponde")
            .setMessage(
                if (spento) {
                    "Senza RCON i comandi vengono scritti nella console del server come se " +
                            "li battessi sulla tastiera di quel computer: partono, ma la " +
                            "risposta non torna indietro. Vedrai solo quello che il server " +
                            "scrive da sé nel registro.\n\n" +
                            "L'app sa attivare RCON da sola: genera la password, la scrive in " +
                            "server.properties, riavvia e prova che funzioni. Il collegamento " +
                            "passa dentro il tunnel SSH, quindi non c'è nessuna porta da " +
                            "aprire sul firewall."
                } else {
                    "RCON è configurato in questa app, ma il server non ha risposto.\n\n" +
                            "Di solito è una di tre cose: il server è spento, la porta è " +
                            "cambiata, oppure la password nell'app non è più quella scritta " +
                            "in server.properties. Nelle impostazioni c'è \"Prova RCON\", che " +
                            "dice quale delle tre.\n\n" +
                            "Nel frattempo i comandi partono lo stesso, ma alla cieca."
                }
            )
            .setPositiveButton(if (spento) "Attiva RCON" else "Vai a controllare") { _, _ ->
                vaiAImpostazioni()
            }
            .setNegativeButton("Continua così", null)
            .show()
    }

    private fun vaiAImpostazioni() {
        (activity as? MainActivity)?.openTab("Impostazioni")
            ?: toast("Le impostazioni di RCON sono nella scheda Impostazioni")
    }

    private var backCallback: androidx.activity.OnBackPressedCallback? = null

    /** Le stesse scorciatoie delle chip, in elenco: serve quando la fila non c'e'. */
    private fun showCommandList() {
        val voci = quickCommands.map { it.trim() }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Comandi rapidi")
            .setItems(voci) { _, indice ->
                val comando = quickCommands[indice]
                b.input.setText(comando)
                b.input.setSelection(comando.length)
                b.input.requestFocus()
                if (comando.endsWith(" ")) showKeyboard() else hideKeyboard()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    /**
     * Righe a capo o scorrimento laterale. Con l'andare a capo si legge tutto
     * senza trascinare, ma le colonne del log si perdono: per questo resta una
     * scelta, non una regola.
     */
    private fun setWrap(wrap: Boolean) {
        wrapLines = wrap
        val bind = _b ?: return
        bind.btnWrap.alpha = if (wrap) 1f else 0.45f
        // Dentro uno scorrevole orizzontale la larghezza non basta a far andare a
        // capo: il testo viene misurato senza limiti. Il limite va messo a mano,
        // e la larghezza reale si conosce solo dopo il primo disegno.
        bind.logScroll.post {
            val b2 = _b ?: return@post
            b2.log.maxWidth = if (wrap) b2.logScroll.width.coerceAtLeast(1) else Int.MAX_VALUE
            b2.log.layoutParams = b2.log.layoutParams.apply {
                width = if (wrap) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT
            }
            b2.log.requestLayout()
            if (wrap) b2.logScroll.scrollTo(0, 0)
        }
    }

    /** Tutto schermo: resta il log e basta, in verticale come in orizzontale. */
    private fun setFullscreen(on: Boolean) {
        fullscreen = on
        val bind = _b ?: return
        bind.rigaControlli.visible(!on)
        bind.rigaComandi.visible(!on && resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE)
        bind.rigaInvio.visible(!on)
        bind.btnEsciSchermo.visible(on)
        backCallback?.isEnabled = on
        // A tutto schermo si legge, non si confrontano colonne: le righe vanno a
        // capo da sole, e uscendo tornano come le aveva lasciate chi legge.
        if (on) {
            wrapPrimaDelloSchermo = wrapLines
            setWrap(true)
        } else {
            setWrap(wrapPrimaDelloSchermo)
        }
        (activity as? com.bellizia.mcmonitor.MainActivity)?.hideChrome(on)
        if (on) hideKeyboard()
        // Il salto in fondo va fatto quando il log ha gia' la nuova altezza,
        // altrimenti si ferma dove finiva prima e sembra bloccato a meta'.
        bind.scroll.postDelayed({ _b?.scroll?.fullScroll(View.FOCUS_DOWN) }, 150)
    }

    /**
     * La ricerca filtra quello che e' gia' arrivato, non chiede altro al server:
     * il log e' gia' in mano, e cosi' funziona anche mentre la rete fa i capricci.
     */
    private fun toggleFiltro() {
        val bind = _b ?: return
        val acceso = bind.rigaFiltro.visibility != View.VISIBLE
        bind.rigaFiltro.visible(acceso)
        bind.btnCerca.alpha = if (acceso) 1f else 0.45f
        if (acceso) {
            bind.filtro.requestFocus()
            showKeyboardFor(bind.filtro)
        } else {
            bind.filtro.setText("")
            filtro = ""
            hideKeyboard()
            renderLog()
        }
    }

    private fun renderLog() {
        val bind = _b ?: return
        // Prima il filtro degli errori, poi la ricerca per testo: cercare una
        // parola dentro i soli errori e' utile, il contrario non vuol dire niente.
        val base = if (soloErrori) Registro.soloGuai(logCompleto) else logCompleto
        if (filtro.isBlank()) {
            bind.log.text = if (soloErrori && base.isBlank()) {
                "Nessun errore nel registro."
            } else base
            bind.esitoFiltro.text = ""
            return
        }
        val righe = base.lines().filter { it.contains(filtro, ignoreCase = true) }
        bind.log.text = if (righe.isEmpty()) "(nessuna riga con \"$filtro\")" else righe.joinToString("\n")
        bind.esitoFiltro.text = "${righe.size} righe"
    }



    private fun showKeyboardFor(target: View) {
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun buildQuickCommands() {
        quickCommands.forEach { command ->
            val chip = layoutInflater.inflate(R.layout.item_command_chip, b.quickCommands, false) as Chip
            chip.text = command.trim()
            chip.setOnClickListener {
                b.input.setText(command)
                b.input.setSelection(command.length)
                b.input.requestFocus()
                if (command.endsWith(" ")) showKeyboard() else hideKeyboard()
            }
            b.quickCommands.addView(chip)
        }
    }

    private fun showKeyboard() {
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.showSoftInput(b.input, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(b.input.windowToken, 0)
    }

    private fun load() {
        if (busy) return
        if (!Prefs.load().isComplete) {
            b.swipe.isRefreshing = false
            b.log.text = "Configura il server nella scheda Impostazioni."
            return
        }
        busy = true
        b.swipe.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { McRepository.log(400) }
            busy = false
            val bind = _b ?: return@launch
            logCompleto = Privacy.text(
                result.getOrElse { "Errore lettura log:\n${it.userMessage()}" }.trim().ifBlank { "(log vuoto)" },
                Prefs.load()
            )
            renderLog()
            bind.swipe.isRefreshing = false
            bind.scroll.post { bind.scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun send() {
        val command = b.input.text?.toString()?.trim().orEmpty()
        if (command.isEmpty()) return
        if (!configured()) return
        b.btnSend.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { McRepository.send(command) }
            val bind = _b ?: return@launch
            bind.btnSend.isEnabled = true
            result.onSuccess {
                bind.input.setText("")
                toast("Comando inviato: $command")
                delay(1_200)
                load()
            }.onFailure { error ->
                // Cambiando server il fragment può essere già staccato: senza questo
                // controllo il dialogo cercherebbe un contesto che non esiste più.
                if (!isAdded) return@onFailure
                // I messaggi di errore dell'invio sono lunghi e vanno letti per intero.
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Comando non inviato")
                    .setMessage(error.userMessage())
                    .setPositiveButton("Chiudi", null)
                    .show()
            }
        }
    }

    // -------------------------------------------------------------- registro

    /** Se il registro a schermo mostra solo le righe che parlano di un guaio. */
    private var soloErrori = false

    /**
     * Cosa fare col registro.
     *
     * Tre cose che servono nello stesso momento -- quando qualcosa si e' rotto:
     * trovare l'errore in mezzo a centinaia di righe, prenderlo, e mandarlo a
     * qualcuno. Stanno insieme perche' si usano insieme.
     */
    private fun menuRegistro() {
        val guai = Registro.quantiGuai(logCompleto)
        val voci = arrayOf(
            if (soloErrori) "Mostra tutto il registro"
            else if (guai > 0) "Mostra solo gli errori ($guai)"
            else "Mostra solo gli errori (nessuno)",
            "Copia tutto",
            "Condividi il registro"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Registro")
            .setItems(voci) { _, quale ->
                when (quale) {
                    0 -> { soloErrori = !soloErrori; renderLog() }
                    1 -> copiaTutto()
                    2 -> condividiRegistro()
                }
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun copiaTutto() {
        if (logCompleto.isBlank()) { toast("Non c'è ancora niente da copiare"); return }
        requireContext().getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("registro del server", logCompleto))
        toast("Registro copiato")
    }

    /**
     * Scrive il registro in un file e lo passa a un'altra app: posta, messaggi,
     * quello che c'e'.
     *
     * Gli indirizzi IP vengono coperti prima di uscire -- in un log di Minecraft
     * sono le case dei giocatori, e per capire un errore non servono mai. In cima
     * al file c'e' scritto che sono stati coperti, o chi lo riceve li cerchera' a
     * lungo senza trovarli.
     */
    private fun condividiRegistro() {
        if (logCompleto.isBlank()) { toast("Non c'è ancora niente da condividere"); return }
        if (!requireContext().puoCondividereFile()) {
            copiaTutto()
            toast("Questa copia dell'app non può passare file: l'ho messo negli appunti")
            return
        }
        val cfg = Prefs.load()
        val quando = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.ITALY)
            .format(java.util.Date())
        val testo = Registro.perCondivisione(
            logCompleto,
            listOf(
                // La versione dal gestore pacchetti e non da BuildConfig, che questo
                // progetto non genera.
                "MC Monitor ${runCatching {
                    requireContext().packageManager
                        .getPackageInfo(requireContext().packageName, 0).versionName
                }.getOrNull() ?: "?"}",
                "Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                // Anche l'intestazione passa dal mascheramento: nel corpo
                // l'indirizzo e' coperto, e lasciarlo in chiaro qui sopra
                // vanificherebbe tutto il resto.
                "server: ${Privacy.text(cfg.displayName, cfg)}",
                "RCON: ${if (cfg.rconUsable) "attivo" else "non attivo"}",
                "registro del $quando",
            )
        )
        runCatching {
            val file = java.io.File(requireContext().cacheDir, "mc-monitor-registro-$quando.txt")
            file.writeText(testo)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.updates", file
            )
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Registro di ${cfg.displayName}")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Manda il registro"
                )
            )
        }.onFailure { toast("Non sono riuscito a preparare il file: ${it.message}") }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Uscendo dalla scheda la barra dell'app deve tornare, altrimenti resta
        // nascosta anche nelle altre schede.
        if (fullscreen) (activity as? com.bellizia.mcmonitor.MainActivity)?.hideChrome(false)
        backCallback = null
        _b = null
    }
}

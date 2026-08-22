package com.bellizia.mcmonitor.ui

import android.content.ClipData
import android.content.ClipboardManager
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
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
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
        b.filtro.doAfterTextChanged {
            filtro = it?.toString().orEmpty()
            renderLog()
        }
        b.log.setOnClickListener(null)
        b.log.setOnTouchListener(doppioTapPerCopiare())
        b.btnWrap.setOnClickListener { setWrap(!wrapLines) }
        b.btnFullscreen.setOnClickListener { setFullscreen(true) }
        b.btnEsciSchermo.setOnClickListener { setFullscreen(false) }
        b.btnComandi.setOnClickListener { showCommandList() }

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
        if (filtro.isBlank()) {
            bind.log.text = logCompleto
            bind.esitoFiltro.text = ""
            return
        }
        val righe = logCompleto.lines().filter { it.contains(filtro, ignoreCase = true) }
        bind.log.text = if (righe.isEmpty()) "(nessuna riga con \"$filtro\")" else righe.joinToString("\n")
        bind.esitoFiltro.text = "${righe.size} righe"
    }

    /**
     * Due tocchi su una riga la selezionano tutta e la copiano: nel log serve
     * spesso prendere una riga intera per incollarla altrove, e trascinare le
     * maniglie della selezione su testo monospazio e' un supplizio.
     */
    private fun doppioTapPerCopiare(): View.OnTouchListener {
        val rilevatore = GestureDetector(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val bind = _b ?: return false
                    val riga = rigaAlPunto(bind.log, e.x, e.y) ?: return false
                    requireContext().getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("riga di log", riga))
                    toast("Riga copiata")
                    return true
                }
            }
        )
        return View.OnTouchListener { view, event ->
            val gestito = rilevatore.onTouchEvent(event)
            if (!gestito) view.performClick()
            gestito
        }
    }

    /** Quale riga del TextView sta sotto il dito. */
    private fun rigaAlPunto(view: TextView, x: Float, y: Float): String? {
        val layout = view.layout ?: return null
        val riga = layout.getLineForVertical((y - view.totalPaddingTop).toInt().coerceAtLeast(0))
        val inizio = layout.getLineStart(riga)
        val fine = layout.getLineEnd(riga)
        return view.text?.substring(inizio, fine)?.trim()?.takeIf { it.isNotBlank() }
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

    override fun onDestroyView() {
        super.onDestroyView()
        // Uscendo dalla scheda la barra dell'app deve tornare, altrimenti resta
        // nascosta anche nelle altre schede.
        if (fullscreen) (activity as? com.bellizia.mcmonitor.MainActivity)?.hideChrome(false)
        backCallback = null
        _b = null
    }
}

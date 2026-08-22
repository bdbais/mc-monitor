package com.bellizia.mcmonitor.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.ai.AiProvider
import com.bellizia.mcmonitor.ai.GeneratedMacro
import com.bellizia.mcmonitor.ai.MacroGenerator
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.ModRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.ActivityMacroBinding
import com.bellizia.mcmonitor.databinding.ItemMacroBinding
import com.bellizia.mcmonitor.lgsm.Macro
import com.bellizia.mcmonitor.lgsm.Macros
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Le macro: una fila di comandi con un nome, da lanciare in un tocco.
 *
 * Quasi niente di quello che fa un amministratore è un comando solo. Mettere il
 * server in manutenzione vuol dire avvisare, aspettare, avvisare ancora,
 * salvare: a mano si sbaglia l'ordine o si salta un pezzo, e capita sempre nel
 * momento in cui si ha fretta.
 *
 * Quelle già pronte non si possono rovinare: aprendone una si può cambiarla, ma
 * quello che si salva diventa una macro propria e l'originale resta dov'è.
 */
class MacroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMacroBinding
    private var esecuzione: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMacroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
        applyInsets()

        binding.sottotitolo.text = Prefs.load().displayName
        binding.btnHelp.setOnClickListener { HelpDialog.show(this, Help.MACRO) }
        binding.btnNuova.setOnClickListener { modifica(null) }
        binding.btnAi.setOnClickListener { generaConAi() }
        binding.btnFerma.setOnClickListener { ferma() }
        render()
    }

    override fun onDestroy() {
        esecuzione?.cancel()
        super.onDestroy()
    }

    private fun render() {
        val mie = Prefs.macros()
        binding.nessunaMia.visible(mie.isEmpty())
        binding.mie.removeAllViews()
        mie.forEach { binding.mie.addView(riga(it, binding.mie)) }

        binding.pronte.removeAllViews()
        Macros.catalogue.forEach { binding.pronte.addView(riga(it, binding.pronte)) }
    }

    private fun riga(macro: Macro, parent: LinearLayout): View {
        val item = ItemMacroBinding.inflate(layoutInflater, parent, false)
        item.nome.text = macro.name
        item.spiegazione.text = macro.description
        item.passi.text = buildString {
            append("${macro.steps} comandi")
            val buchi = Macros.placeholders(macro.commands)
            if (buchi.isNotEmpty()) append(" · chiede ${buchi.joinToString(", ")}")
            if (macro.risky) append(" · da usare con giudizio")
        }
        item.card.setOnClickListener { conferma(macro) }
        item.btnModifica.setOnClickListener { modifica(macro) }
        return item.root
    }

    // ------------------------------------------------------------ esecuzione

    /** Prima si riempiono i buchi, poi si mostra cosa partirà davvero. */
    private fun conferma(macro: Macro) {
        val buchi = Macros.placeholders(macro.commands)
        if (buchi.isEmpty()) {
            mostraAnteprima(macro, macro.commands)
            return
        }

        val contenitore = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 8)
        }
        val campi = buchi.associateWith { nome ->
            EditText(this).apply {
                hint = nome
                setSingleLine()
                inputType = InputType.TYPE_CLASS_TEXT
            }.dialogoVisibile().also { contenitore.addView(it) }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(macro.name)
            .setMessage("Riempi quello che serve:")
            .setView(contenitore)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Avanti") { _, _ ->
                val valori = campi.mapValues { (_, campo) -> campo.text?.toString().orEmpty().trim() }
                if (valori.values.any { it.isBlank() }) {
                    toast("Serve riempire tutti i campi")
                    conferma(macro)
                } else {
                    mostraAnteprima(macro, Macros.fill(macro.commands, valori))
                }
            }
            .show()
    }

    private fun mostraAnteprima(macro: Macro, comandi: List<String>) {
        val elenco = comandi.joinToString("\n") { riga ->
            val attesa = Macros.waitSeconds(riga)
            if (attesa != null) "· aspetta $attesa secondi" else "· $riga"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Lanciare \"${macro.name}\"?")
            .setMessage(
                buildString {
                    if (macro.risky) {
                        append("Questa cambia il mondo o disturba chi sta giocando.\n\n")
                    }
                    append("Partono in quest'ordine:\n\n")
                    append(elenco)
                }
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Lancia") { _, _ -> esegui(macro, comandi) }
            .show()
    }

    private fun esegui(macro: Macro, comandi: List<String>) {
        occupato(true)
        binding.diario.visible(true)
        binding.diario.text = "▶ ${macro.name}\n"
        esecuzione = lifecycleScope.launch {
            var fatti = 0
            for (riga in comandi) {
                if (!isActive) break
                val attesa = Macros.waitSeconds(riga)
                if (attesa != null) {
                    scrivi("… aspetto $attesa secondi")
                    // Un secondo alla volta: così "Ferma" ferma davvero, invece
                    // di aspettare la fine della pausa.
                    repeat(attesa) {
                        if (!isActive) return@repeat
                        delay(1000)
                    }
                    continue
                }
                val esito = runCatching { McRepository.send(riga) }
                fatti++
                esito.fold(
                    onSuccess = { risposta ->
                        scrivi("$riga\n   ${risposta.trim().lines().firstOrNull().orEmpty()}")
                    },
                    onFailure = { errore ->
                        scrivi("$riga\n   ERRORE: ${errore.userMessage()}")
                        // Se il server non risponde, insistere con gli altri
                        // comandi non serve a niente.
                        scrivi("Macro interrotta.")
                        return@launch
                    }
                )
            }
            scrivi(if (isActive) "Fatto: $fatti comandi." else "Fermata a metà: $fatti comandi.")
        }
        esecuzione?.invokeOnCompletion { occupato(false) }
    }

    private fun mostraTesto(titolo: String, testo: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titolo)
            .setMessage(testo)
            .setPositiveButton("Chiudi", null)
            .show()
    }

    private fun ferma() {
        esecuzione?.cancel()
        scrivi("Fermata.")
    }

    private fun scrivi(testo: String) {
        binding.diario.append("$testo\n")
        binding.scorrevole.post { binding.scorrevole.fullScroll(View.FOCUS_DOWN) }
    }

    // ------------------------------------------------------ scritta dall'AI

    /**
     * Farsi scrivere una macro descrivendola a parole.
     *
     * Il servizio è di chi usa l'app: la chiave si prende gratis e resta sul
     * telefono. Quello che torna indietro non si salva da solo — si legge, si
     * corregge se serve, e solo allora diventa una macro.
     */
    private fun generaConAi() {
        if (Prefs.aiKey.isBlank()) {
            impostaChiave { generaConAi() }
            return
        }

        val contenitore = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 8)
        }
        val richiesta = EditText(this).apply {
            hint = "per esempio: prepara il server per una gara di costruzione"
            minLines = 2
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }.dialogoVisibile()
        val nota = TextView(this).apply {
            text = "Quello che scrivi qui viene mandato a ${AiProvider.byId(Prefs.aiProvider).label}. " +
                    "Niente del server ci va insieme, a parte versione e loader."
            textSize = 11f
        }
        contenitore.addView(richiesta)
        contenitore.addView(nota)

        MaterialAlertDialogBuilder(this)
            .setTitle("Descrivi la macro")
            .setView(contenitore)
            .setNeutralButton("Cambia chiave") { _, _ -> impostaChiave { generaConAi() } }
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Scrivila") { _, _ ->
                val testo = richiesta.text?.toString()?.trim().orEmpty()
                if (testo.isBlank()) toast("Scrivi cosa deve fare") else chiedi(testo)
            }
            .show()
    }

    private fun chiedi(richiesta: String) {
        occupato(true)
        binding.diario.visible(true)
        binding.diario.text = "▶ chiedo a ${AiProvider.byId(Prefs.aiProvider).label}…\n"
        lifecycleScope.launch {
            // Versione e loader aiutano il servizio a non proporre comandi che su
            // questo server non esistono. Se non si sanno, si chiede lo stesso.
            val env = runCatching { ModRepository.environment() }.getOrNull()
            val esito = runCatching {
                MacroGenerator.generate(
                    provider = AiProvider.byId(Prefs.aiProvider),
                    key = Prefs.aiKey,
                    request = richiesta,
                    minecraft = env?.minecraftVersion.orEmpty(),
                    loader = env?.loader.orEmpty()
                )
            }
            occupato(false)
            binding.diario.visible(false)
            esito.fold(
                onSuccess = { rivedi(it) },
                onFailure = { mostraTesto("Non l'ha scritta", it.userMessage()) }
            )
        }
    }

    /** Si legge prima di salvarla: l'ha scritta qualcun altro. */
    private fun rivedi(generata: GeneratedMacro) {
        val elenco = generata.macro.commands.joinToString("\n") { riga ->
            val attesa = Macros.waitSeconds(riga)
            if (attesa != null) "· aspetta $attesa secondi" else "· $riga"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(generata.macro.name)
            .setMessage(
                buildString {
                    append(generata.macro.description)
                    append("\n\n")
                    append(elenco)
                    if (generata.rejected.isNotEmpty()) {
                        append("\n\nTolti perché non ammessi in una macro scritta da un servizio ")
                        append("(spengono il server o decidono chi può entrare):\n")
                        append(generata.rejected.joinToString("\n") { "· $it" })
                    }
                    append("\n\nLeggila prima di tenerla: l'ha scritta un servizio, non tu.")
                }
            )
            .setNeutralButton("Correggi") { _, _ -> modifica(generata.macro) }
            .setNegativeButton("Butta via", null)
            .setPositiveButton("Tieni") { _, _ ->
                Prefs.saveMacro(generata.macro)
                toast("Macro salvata")
                render()
            }
            .show()
    }

    private fun impostaChiave(poi: () -> Unit) {
        val servizi = AiProvider.entries
        val contenitore = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 8)
        }
        contenitore.addView(TextView(this).apply {
            text = "Serve una chiave del servizio, che si prende gratis in due minuti. " +
                    "Resta su questo telefono: nell'app non ce n'è nessuna, e senza questa " +
                    "il resto funziona lo stesso."
            textSize = 12f
        })

        // I radio stanno dentro il riquadro invece che nella lista di sistema:
        // con il telefono girato quella lista spinge i pulsanti fuori dallo schermo.
        val gruppo = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        servizi.forEachIndexed { i, servizio ->
            gruppo.addView(RadioButton(this).apply {
                id = i
                text = "${servizio.label}\n${servizio.where}"
                textSize = 13f
                isChecked = servizio.id == Prefs.aiProvider
            })
        }
        if (gruppo.checkedRadioButtonId < 0) gruppo.check(0)
        contenitore.addView(gruppo)

        val campo = EditText(this).apply {
            hint = "incolla qui la chiave"
            setText(Prefs.aiKey)
            setSingleLine()
        }.dialogoVisibile()
        contenitore.addView(campo)

        // Girato, con la tastiera aperta, resta poco schermo: il riquadro scorre
        // invece di far sparire i pulsanti.
        val scorrevole = ScrollView(this).apply { addView(contenitore) }

        MaterialAlertDialogBuilder(this)
            .setTitle("Chi scrive le macro")
            .setView(scorrevole)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Salva") { _, _ ->
                val chiave = campo.text?.toString()?.trim().orEmpty()
                if (chiave.isBlank()) {
                    toast("Senza chiave non si può")
                } else {
                    Prefs.aiProvider = servizi[gruppo.checkedRadioButtonId.coerceIn(0, servizi.size - 1)].id
                    Prefs.aiKey = chiave
                    poi()
                }
            }
            .show()
    }

    // ------------------------------------------------------------- modifica

    /**
     * Aprire una macro già pronta non la cambia: quello che si salva nasce come
     * macro propria, con un nome nuovo. Così ci si può basare su una di quelle
     * senza il rischio di perderla.
     */
    private fun modifica(macro: Macro?) {
        val contenitore = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 8)
        }
        val nome = EditText(this).apply {
            hint = "nome della macro"
            setText(if (macro?.builtin == true) "${macro.name} (mia)" else macro?.name.orEmpty())
            setSingleLine()
        }.dialogoVisibile()
        val spiegazione = EditText(this).apply {
            hint = "a cosa serve"
            setText(macro?.description.orEmpty())
            setSingleLine()
        }.dialogoVisibile()
        val comandi = EditText(this).apply {
            hint = "un comando per riga"
            setText(macro?.commands?.joinToString("\n").orEmpty())
            minLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }.dialogoVisibile()
        val nota = TextView(this).apply {
            text = "Un comando per riga, senza la barra iniziale. " +
                    "<giocatore> o <x> diventano domande. " +
                    "${Macros.WAIT} 30 non è un comando: aspetta 30 secondi."
            textSize = 11f
        }
        listOf(nome, spiegazione, comandi, nota).forEach { contenitore.addView(it) }

        val costruttore = MaterialAlertDialogBuilder(this)
            .setTitle(if (macro == null) "Macro nuova" else macro.name)
            .setView(contenitore)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Salva", null)
        if (macro?.builtin == false) costruttore.setNeutralButton("Elimina", null)

        costruttore.show().also { d ->
            d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = nome.text?.toString()?.trim().orEmpty()
                val righe = Macros.parseCommands(comandi.text?.toString().orEmpty())
                when {
                    n.isBlank() -> toast("Serve un nome")
                    righe.isEmpty() -> toast("Serve almeno un comando")
                    else -> {
                        d.dismiss()
                        Prefs.saveMacro(
                            Macro(
                                // Modificando una già pronta ne nasce una nuova:
                                // l'originale non si tocca.
                                id = if (macro == null || macro.builtin) "" else macro.id,
                                name = n,
                                description = spiegazione.text?.toString()?.trim().orEmpty(),
                                commands = righe,
                                builtin = false,
                                risky = macro?.risky ?: false
                            )
                        )
                        toast("Macro salvata")
                        render()
                    }
                }
            }
            if (macro?.builtin == false) {
                d.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    d.dismiss()
                    Prefs.deleteMacro(macro.id)
                    toast("Macro eliminata")
                    render()
                }
            }
        }
    }

    // -------------------------------------------------------------- utilità

    private fun occupato(attivo: Boolean) {
        binding.progress.visible(attivo)
        binding.btnFerma.visible(attivo)
        binding.btnNuova.isEnabled = !attivo
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
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

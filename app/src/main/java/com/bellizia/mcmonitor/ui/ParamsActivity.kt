package com.bellizia.mcmonitor.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.ActivityParamsBinding
import com.bellizia.mcmonitor.databinding.ItemParamBinding
import com.bellizia.mcmonitor.lgsm.GameVersion
import com.bellizia.mcmonitor.lgsm.ServerParam
import com.bellizia.mcmonitor.lgsm.ServerParams
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Tutti i parametri di LinuxGSM, modificabili.
 *
 * Il file di configurazione decide quanta memoria dare a Java, quale versione
 * scaricare, quanti backup tenere: prima si poteva cambiare solo la versione, e
 * per il resto bisognava collegarsi al computer a mano. Ogni scrittura lascia
 * una copia datata del file, perché una riga sbagliata qui impedisce al server
 * di partire.
 */
class ParamsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParamsBinding
    private var params: List<ServerParam> = emptyList()

    /** Le chiavi scritte in questa sessione: solo alcune chiedono un riavvio. */
    private val toccati = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParamsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
        applyInsets()

        binding.sottotitolo.text = Prefs.load().displayName
        binding.btnHelp.setOnClickListener { HelpDialog.show(this, Help.PARAMS) }
        binding.swipe.setOnRefreshListener { load() }
        binding.btnAltro.setOnClickListener { editDialog("", "", nuovo = true) }
        binding.btnRiavvia.setOnClickListener { riavvia() }
        load()
    }

    private fun load() {
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            val esito = runCatching { McRepository.params() }
            binding.swipe.isRefreshing = false
            esito.fold(
                onSuccess = {
                    params = it
                    render()
                },
                onFailure = {
                    binding.intestazione.text = it.userMessage()
                    binding.elenco.removeAllViews()
                }
            )
        }
    }

    private fun render() {
        val attivi = params.filter { !it.commented }
        val file = GameVersion.configPath(Prefs.load()).substringAfterLast('/')
        binding.intestazione.text = buildString {
            if (attivi.isEmpty()) {
                append("In $file non c'è scritto niente, ed è normale: è il file delle tue ")
                append("modifiche, e nasce vuoto.\n\n")
                append("Il server sta girando lo stesso, con i valori di fabbrica di LinuxGSM ")
                append("(memoria 1024, quattro backup, log tenuti sette giorni). Quelli stanno ")
                append("in un altro file, che non si tocca: qui si scrivono le differenze.")
            } else {
                append("$file, l'unico file che questa schermata legge e modifica: ")
                append("${attivi.size} righe scritte.\n\n")
                append("Quello che qui non compare non è spento: sta usando il valore di ")
                append("fabbrica di LinuxGSM.")
            }
            append("\n\nOgni modifica tiene una copia datata del file.")
        }

        binding.elenco.removeAllViews()
        attivi.sortedBy { it.key }.forEach { param ->
            binding.elenco.addView(
                riga(param.key, param.value, param.description, binding.elenco, nuovo = false)
            )
        }

        binding.aggiungibili.removeAllViews()
        ServerParams.addable(params).forEach { info ->
            binding.aggiungibili.addView(
                riga(info.key, "", info.what, binding.aggiungibili, nuovo = true)
            )
        }
        // Il riavvio butta fuori i giocatori: si propone solo per le righe che
        // finiscono davvero nella riga di lancio. Il resto LinuxGSM lo rilegge da sé.
        binding.btnRiavvia.visible(ServerParams.needsRestart(toccati))
    }

    /**
     * Una riga dell'elenco.
     *
     * [nuovo] lo decide il chiamante, non il valore. Deducendolo dal valore vuoto,
     * una riga presente nel file ma vuota — cioè proprio quella che impedisce al
     * server di partire — si riapriva come "Aggiungi un parametro" e perdeva il
     * pulsante "Togli": l'unica riga da cancellare era anche l'unica che l'app
     * non lasciava più cancellare.
     */
    private fun riga(
        chiave: String,
        valore: String,
        spiegazione: String,
        parent: LinearLayout,
        nuovo: Boolean
    ): View {
        val item = ItemParamBinding.inflate(layoutInflater, parent, false)
        item.chiave.text = chiave
        item.valore.text = when {
            nuovo -> "valore di fabbrica"
            valore.isBlank() -> "riga vuota: il server potrebbe non partire"
            else -> valore
        }
        val effetto = ServerParams.effectLabel(chiave)
        item.spiegazione.visible(spiegazione.isNotBlank())
        item.spiegazione.text = if (effetto.isBlank()) spiegazione else "$spiegazione\n$effetto"
        val apri = { editDialog(chiave, valore, nuovo) }
        item.card.setOnClickListener { apri() }
        item.btnModifica.setOnClickListener { apri() }
        return item.root
    }

    private fun editDialog(chiave: String, valore: String, nuovo: Boolean) {
        val contenitore = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val campoChiave = EditText(this).apply {
            hint = "nome del parametro"
            setText(chiave)
            isEnabled = chiave.isBlank()
            setSingleLine()
        }.dialogoVisibile()
        val campoValore = EditText(this).apply {
            hint = "valore"
            setText(valore)
            setSingleLine()
        }.dialogoVisibile()
        val nota = TextView(this).apply {
            text = ServerParams.describe(chiave)
            textSize = 12f
            visible(text.isNotBlank())
        }
        contenitore.addView(campoChiave)
        contenitore.addView(campoValore)
        contenitore.addView(nota)

        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(if (nuovo) "Aggiungi un parametro" else chiave)
            .setView(contenitore)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Salva", null)
        if (!nuovo) dialogo.setNeutralButton("Togli", null)

        dialogo.show().also { d ->
            d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val k = campoChiave.text?.toString()?.trim().orEmpty()
                val v = campoValore.text?.toString()?.trim().orEmpty()
                when {
                    !ServerParams.isValidKey(k) ->
                        toast("Nome non valido: solo lettere, numeri e trattino basso")
                    v.isBlank() -> toast(
                        "Un valore vuoto e' una riga che il server esegue lo stesso: " +
                                "per tornare al valore di fabbrica usa Togli."
                    )
                    else -> {
                        d.dismiss()
                        scrivi(k, v)
                    }
                }
            }
            if (!nuovo) {
                d.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    d.dismiss()
                    confermaTogli(chiave)
                }
            }
        }
    }

    private fun scrivi(chiave: String, valore: String) {
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            val esito = runCatching { McRepository.setParam(chiave, valore) }
            binding.swipe.isRefreshing = false
            esito.fold(
                onSuccess = {
                    toccati += chiave
                    toast("$chiave salvato")
                    load()
                },
                onFailure = { showText("Non salvato", it.userMessage()) }
            )
        }
    }

    /**
     * Togliere un parametro non lo cancella: lo commenta. LinuxGSM torna al suo
     * valore di fabbrica e la riga resta lì, a ricordare cosa c'era scritto.
     */
    private fun confermaTogli(chiave: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Togliere $chiave?")
            .setMessage(
                "La riga viene commentata, non cancellata: LinuxGSM userà il valore " +
                        "di fabbrica e potrai rimetterla quando vuoi."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Togli") { _, _ ->
                binding.swipe.isRefreshing = true
                lifecycleScope.launch {
                    val esito = runCatching { McRepository.disableParam(chiave) }
                    binding.swipe.isRefreshing = false
                    esito.fold(
                        onSuccess = {
                            toccati += chiave
                            toast("$chiave tolto")
                            load()
                        },
                        onFailure = { showText("Non tolto", it.userMessage()) }
                    )
                }
            }
            .show()
    }

    private fun riavvia() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Riavviare il server?")
            .setMessage("I parametri appena cambiati valgono dal prossimo avvio. I giocatori online verranno disconnessi.")
            .setNegativeButton("Più tardi", null)
            .setPositiveButton("Riavvia") { _, _ ->
                binding.swipe.isRefreshing = true
                lifecycleScope.launch {
                    val esito = runCatching { McRepository.restart() }
                    binding.swipe.isRefreshing = false
                    showText("Riavvio", esito.getOrElse { it.userMessage() })
                    toccati.clear()
                    render()
                }
            }
            .show()
    }

    private fun showText(titolo: String, testo: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titolo)
            .setMessage(testo)
            .setPositiveButton("Chiudi", null)
            .show()
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

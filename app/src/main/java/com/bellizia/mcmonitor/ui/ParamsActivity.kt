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
    private var modificato = false

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
        binding.intestazione.text = buildString {
            append("${attivi.size} parametri attivi in ")
            append(GameVersion.configPath(Prefs.load()).substringAfterLast('/'))
            append(".\nOgni modifica tiene una copia del file, e vale al prossimo riavvio del server.")
        }

        binding.elenco.removeAllViews()
        attivi.sortedBy { it.key }.forEach { param ->
            binding.elenco.addView(riga(param.key, param.value, param.description, binding.elenco))
        }

        binding.aggiungibili.removeAllViews()
        ServerParams.addable(params).forEach { (chiave, spiegazione) ->
            binding.aggiungibili.addView(riga(chiave, "", spiegazione, binding.aggiungibili))
        }
        binding.btnRiavvia.visible(modificato)
    }

    private fun riga(chiave: String, valore: String, spiegazione: String, parent: LinearLayout): View {
        val item = ItemParamBinding.inflate(layoutInflater, parent, false)
        item.chiave.text = chiave
        item.valore.text = if (valore.isBlank()) "(non impostato)" else valore
        item.spiegazione.visible(spiegazione.isNotBlank())
        item.spiegazione.text = spiegazione
        val apri = { editDialog(chiave, valore, nuovo = valore.isBlank()) }
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
        }
        val campoValore = EditText(this).apply {
            hint = "valore"
            setText(valore)
            setSingleLine()
        }
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
                    modificato = true
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
                            modificato = true
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
                    modificato = false
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

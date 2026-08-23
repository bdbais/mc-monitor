package com.bellizia.mcmonitor.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.ActivityParamsBinding
import com.bellizia.mcmonitor.databinding.ItemParamBinding
import com.bellizia.mcmonitor.lgsm.GameVersion
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
    private var params: List<ServerParams.ParamEffettivo> = emptyList()
    private var filtro: String = ""

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
        binding.cerca.doAfterTextChanged {
            filtro = it?.toString()?.trim().orEmpty()
            if (params.isNotEmpty()) render()
        }
        binding.btnRiavvia.setOnClickListener { riavvia() }
        load()
    }

    private fun load() {
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            val esito = runCatching { McRepository.paramsChain() }
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
        val cerca = filtro.lowercase()
        val visibili = params.filter { cerca.isBlank() || it.key.lowercase().contains(cerca) }
        val nostri = params.count { it.nostro != null }

        binding.intestazione.text = buildString {
            append("${params.size} parametri in vigore su questo server, ")
            append("letti da tutti i file di LinuxGSM messi in fila.\n\n")
            if (nostri == 0) {
                append("Nessuno è scritto nel file di questo server, ed è normale: ")
                append("quel file nasce vuoto e contiene solo le differenze. ")
                append("Tutto quello che vedi qui viene dai valori di fabbrica.")
            } else {
                append("$nostri sono scritti nel file di questo server; ")
                append("gli altri vengono dai valori di fabbrica o dal file comune.")
            }
            append("\n\nOgni modifica tiene una copia datata del file.")
        }

        binding.elenco.removeAllViews()
        if (visibili.isEmpty()) {
            binding.elenco.addView(TextView(this).apply {
                text = if (cerca.isBlank()) "Niente da mostrare." else "Nessun parametro con \"$filtro\"."
                textSize = 13f
            })
        }
        visibili.forEach { binding.elenco.addView(riga(it, binding.elenco)) }

        // Restano da proporre solo quelli del catalogo che non compaiono in
        // nessuno dei file: tutto il resto e' gia' li sopra, con il suo valore.
        val presenti = params.map { it.key }.toSet()
        val mancanti = ServerParams.catalogue.filterNot { it.key in presenti }
        binding.aggiungibili.removeAllViews()
        mancanti.forEach { info ->
            binding.aggiungibili.addView(
                riga(
                    ServerParams.ParamEffettivo(
                        key = info.key,
                        value = "",
                        da = ServerParams.Da.ISTANZA,
                        nostro = null,
                        coperto = false
                    ),
                    binding.aggiungibili,
                    daAggiungere = true
                )
            )
        }
        binding.btnRiavvia.visible(ServerParams.needsRestart(toccati))
    }

    /**
     * Una riga dell'elenco.
     *
     * Accanto al valore c'e' scritto da dove viene: e' l'informazione che
     * mancava del tutto. Un parametro che non e' scritto nel file di questo
     * server non e' "non impostato" — ha un valore, e il server lo sta usando.
     */
    private fun riga(
        p: ServerParams.ParamEffettivo,
        parent: LinearLayout,
        daAggiungere: Boolean = false
    ): View {
        val item = ItemParamBinding.inflate(layoutInflater, parent, false)
        item.chiave.text = p.key
        item.valore.text = when {
            daAggiungere -> "non c'è in nessun file"
            p.value.isBlank() -> "(vuoto)"
            else -> p.value
        }

        item.spiegazione.visible(true)
        item.spiegazione.text = buildString {
            val spiega = ServerParams.describe(p.key)
            if (spiega.isNotBlank()) append(spiega).append('\n')
            if (daAggiungere) {
                append("Non compare in nessun file: vale il valore di fabbrica di LinuxGSM.")
            } else {
                append("da ").append(p.da.etichetta)
                if (p.nostro != null && p.da != ServerParams.Da.ISTANZA) {
                    append(" — qui c'è scritto \"").append(p.nostro).append("\", ma non comanda")
                }
                val effetto = ServerParams.effectLabel(p.key)
                if (effetto.isNotBlank()) append('\n').append(effetto)
            }
        }

        val apri = {
            if (p.coperto) spiegaCoperto(p) else editDialog(p.key, p.nostro ?: p.value, daAggiungere || p.nostro == null)
        }
        item.card.setOnClickListener { apri() }
        item.btnModifica.setOnClickListener { apri() }
        return item.root
    }

    /**
     * Il caso in cui scrivere non servirebbe a niente.
     *
     * LinuxGSM carica i file dei segreti DOPO quello dell'istanza: se una chiave
     * sta li, quella comanda. Scrivendola qui, l'app mostrerebbe il valore nuovo
     * e il server continuerebbe a usare il vecchio.
     */
    private fun spiegaCoperto(p: ServerParams.ParamEffettivo) {
        MaterialAlertDialogBuilder(this)
            .setTitle(p.key)
            .setMessage(
                "Questo parametro vale \"${p.value}\" e viene da ${p.da.etichetta}.\n\n" +
                        "${p.da.spiegazione}\n\n" +
                        "Quel file viene caricato dopo quello su cui l'app scrive: cambiarlo " +
                        "da qui non avrebbe nessun effetto, e la schermata ti direbbe una " +
                        "cosa non vera. Va modificato collegandosi al computer."
            )
            .setPositiveButton("Ho capito", null)
            .show()
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

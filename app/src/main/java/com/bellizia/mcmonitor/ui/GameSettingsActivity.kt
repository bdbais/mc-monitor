package com.bellizia.mcmonitor.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
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
import com.bellizia.mcmonitor.data.ModRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.ActivityGameSettingsBinding
import com.bellizia.mcmonitor.databinding.ItemSettingBinding
import com.bellizia.mcmonitor.lgsm.GameSettings
import com.bellizia.mcmonitor.lgsm.Impostazione
import com.bellizia.mcmonitor.lgsm.Tipo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Le impostazioni del server: quelle che si cambiano davvero.
 *
 * Difficoltà, messaggio di benvenuto, quanti giocatori entrano, quanto lontano si
 * vede. Vivono in `server.properties`, e fino a ieri dall'app non si potevano
 * toccare: c'era solo la schermata dei parametri di LinuxGSM, che è un altro
 * file e riguarda come il server viene acceso, non come ci si gioca.
 *
 * Niente si scrive al tocco. Le modifiche si accumulano, si vedono tutte insieme,
 * e partono con un solo pulsante: una connessione, una copia di sicurezza, un
 * messaggio. A metà strada il server avrebbe una configurazione che non ha scelto
 * nessuno.
 */
class GameSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameSettingsBinding

    private var attuali: Map<String, String> = emptyMap()
    private val modifiche = linkedMapOf<String, String>()
    private var versione: String? = null
    private var salvando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { esci() }
        applyInsets()

        binding.sottotitolo.text = Prefs.load().displayName
        binding.btnHelp.setOnClickListener { HelpDialog.show(this, Help.GAME_SETTINGS) }
        binding.swipe.setOnRefreshListener { load() }
        binding.btnSalva.setOnClickListener { conferma() }
        binding.btnTecniche.setOnClickListener {
            startActivity(Intent(this, ParamsActivity::class.java))
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = esci()
        })
        load()
    }

    /** Uscire con delle modifiche in sospeso le butterebbe via in silenzio. */
    private fun esci() {
        if (modifiche.isEmpty()) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Ci sono ${modifiche.size} modifiche non salvate")
            .setMessage("Uscendo adesso restano com'erano.")
            .setNegativeButton("Resto qui", null)
            .setPositiveButton("Esci senza salvare") { _, _ -> finish() }
            .show()
    }

    private fun load() {
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            val esito = runCatching { McRepository.properties() }
            // La versione serve per le impostazioni che dal 1.21.9 sono diventate
            // regole di gioco: se non si sa, si scrive il file e basta.
            versione = runCatching { ModRepository.environment().minecraftVersion }.getOrNull()
            binding.swipe.isRefreshing = false
            esito.fold(
                onSuccess = {
                    attuali = it
                    modifiche.clear()
                    binding.titoloAltre.visible(true)
                    render()
                },
                onFailure = {
                    binding.intestazione.text = it.userMessage()
                    binding.gruppi.removeAllViews()
                    // Senza il file non c'e' "il resto del file": lasciare il
                    // titolo sopra il vuoto e' peggio che non mostrarlo.
                    binding.titoloAltre.visible(false)
                    binding.altre.text = ""
                    binding.btnSalva.visible(false)
                }
            )
        }
    }

    private fun valoreDi(imp: Impostazione): String =
        modifiche[imp.key] ?: attuali[imp.key] ?: imp.diFabbrica

    private fun render() {
        binding.intestazione.text = buildString {
            append("Sono le impostazioni del gioco, in serverfiles/server.properties. ")
            append("Niente parte finché non premi Salva.")
            val v = versione
            if (!v.isNullOrBlank()) append("\n\nQuesto server è Minecraft $v.")
        }

        binding.gruppi.removeAllViews()
        GameSettings.gruppi.forEach { (titolo, impostazioni) ->
            // Un'impostazione che questa versione di Minecraft non ha si nasconde:
            // mostrarla vuol dire farla cambiare per niente.
            val visibili = impostazioni.filter { presente(it) }
            if (visibili.isEmpty()) return@forEach

            binding.gruppi.addView(TextView(this).apply {
                text = titolo
                setTextAppearance(com.bellizia.mcmonitor.R.style.CardTitle)
                setPadding(0, 18, 0, 6)
            })
            visibili.forEach { binding.gruppi.addView(riga(it)) }
        }

        val note = attuali.keys.filterNot { GameSettings.byKey(it) != null }
        binding.altre.text = if (note.isEmpty()) {
            "Nel file non c'è altro."
        } else {
            "Nel file ci sono altre ${note.size} righe che questa schermata non tocca " +
                    "(porte, indirizzi, messa a punto fine). Restano come sono."
        }

        binding.btnSalva.visible(modifiche.isNotEmpty())
        binding.btnSalva.text = "Salva ${modifiche.size} modifiche"
    }

    /**
     * Se questa impostazione esiste ancora su questo server.
     *
     * `pause-when-empty-seconds` sui server più vecchi non c'è: si mostra solo se
     * la riga è nel file. Quelle diventate regole di gioco restano invece
     * visibili, perché cambiano ancora — solo per un'altra strada.
     */
    private fun presente(imp: Impostazione): Boolean =
        attuali.containsKey(imp.key) || imp.key != "pause-when-empty-seconds"

    private fun riga(imp: Impostazione): View {
        val item = ItemSettingBinding.inflate(layoutInflater, binding.gruppi, false)
        val valore = valoreDi(imp)
        val cambiata = modifiche.containsKey(imp.key)

        item.titolo.text = if (cambiata) "${imp.titolo}  •" else imp.titolo
        item.spiegazione.text = buildString {
            append(imp.spiegazione)
            if (GameSettings.isGamerule(imp, versione)) {
                append("\nSu Minecraft ${versione.orEmpty()} questa non è più una riga del file: ")
                append("l'app la manda al server come regola di gioco.")
            } else if (imp.applica == com.bellizia.mcmonitor.lgsm.Applica.SUBITO) {
                append("\nCambia subito, senza riavviare.")
            }
        }

        val avviso = imp.attenzione?.invoke(valore)
        item.avviso.visible(!avviso.isNullOrBlank())
        item.avviso.text = avviso.orEmpty()

        if (imp.booleana) {
            item.valore.visible(false)
            item.interruttore.visible(true)
            item.interruttore.setOnCheckedChangeListener(null)
            item.interruttore.isChecked = valore == "true"
            item.interruttore.setOnCheckedChangeListener { _, acceso ->
                proponi(imp, if (acceso) "true" else "false")
            }
            item.card.setOnClickListener { item.interruttore.toggle() }
        } else {
            item.interruttore.visible(false)
            item.valore.visible(true)
            item.valore.text = imp.etichetta(valore)
            item.card.setOnClickListener { chiedi(imp, valore) }
        }
        return item.root
    }

    /**
     * Mette una modifica in coda, o la toglie se il valore è tornato quello di
     * partenza: chi rimette com'era non deve trovarsi un "1 modifica" da salvare.
     */
    private fun proponi(imp: Impostazione, valore: String) {
        val errore = GameSettings.validate(imp, valore)
        if (errore != null) {
            toast("${imp.titolo}: $errore")
            render()
            return
        }
        val partenza = attuali[imp.key] ?: imp.diFabbrica
        if (valore == partenza) modifiche.remove(imp.key) else modifiche[imp.key] = valore
        render()
    }

    private fun chiedi(imp: Impostazione, valore: String) {
        when (imp.tipo) {
            Tipo.SCELTA -> MaterialAlertDialogBuilder(this)
                .setTitle(imp.titolo)
                .setItems(imp.scelte.map { it.second }.toTypedArray()) { _, quale ->
                    proponi(imp, imp.scelte[quale].first)
                }
                .setNegativeButton("Annulla", null)
                .show()

            Tipo.NUMERO -> scrivi(
                imp = imp,
                valore = valore,
                nota = "Da ${imp.min} a ${imp.max} ${imp.unita}. Di fabbrica ${imp.diFabbrica}.",
                numerico = true
            )

            Tipo.TESTO -> scrivi(
                imp = imp,
                valore = valore,
                nota = if (imp.maxCaratteri > 0) "Al massimo ${imp.maxCaratteri} caratteri." else "",
                numerico = false
            )

            Tipo.INTERRUTTORE -> Unit
        }
    }

    private fun scrivi(imp: Impostazione, valore: String, nota: String, numerico: Boolean) {
        val contenitore = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 8)
        }
        val campo = EditText(this).apply {
            setText(valore)
            setSingleLine()
            inputType =
                if (numerico) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
        }.dialogoVisibile()
        contenitore.addView(campo)
        if (nota.isNotBlank()) {
            contenitore.addView(TextView(this).apply {
                text = nota
                textSize = 12f
            })
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(imp.titolo)
            .setMessage(imp.spiegazione)
            .setView(contenitore)
            .setNeutralButton("Di fabbrica") { _, _ -> proponi(imp, imp.diFabbrica) }
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Va bene") { _, _ ->
                proponi(imp, campo.text?.toString()?.trim().orEmpty())
            }
            .show()
    }

    // ------------------------------------------------------------ salvataggio

    private fun conferma() {
        if (modifiche.isEmpty()) return
        val elenco = modifiche.entries.joinToString("\n") { (chiave, valore) ->
            val imp = GameSettings.byKey(chiave)
            val da = attuali[chiave] ?: imp?.diFabbrica.orEmpty()
            "· ${imp?.titolo ?: chiave}: ${imp?.etichetta(da) ?: da} → ${imp?.etichetta(valore) ?: valore}"
        }
        val avvisi = modifiche.mapNotNull { (chiave, valore) ->
            GameSettings.byKey(chiave)?.attenzione?.invoke(valore)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Salvare ${modifiche.size} modifiche?")
            .setMessage(
                buildString {
                    append(elenco)
                    if (avvisi.isNotEmpty()) {
                        append("\n\n")
                        append(avvisi.joinToString("\n") { "⚠ $it" })
                    }
                    append("\n\nDel file resta una copia con la data nel nome.")
                }
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Salva") { _, _ -> salva() }
            .show()
    }

    private fun salva() {
        if (salvando) return
        salvando = true
        binding.swipe.isRefreshing = true
        binding.btnSalva.isEnabled = false
        val daScrivere = LinkedHashMap(modifiche)
        lifecycleScope.launch {
            val esito = runCatching { McRepository.setProperties(daScrivere, versione) }
            salvando = false
            binding.swipe.isRefreshing = false
            binding.btnSalva.isEnabled = true
            esito.fold(
                onSuccess = { messaggio ->
                    toast(messaggio)
                    load()
                },
                onFailure = { showText("Non salvato", it.userMessage()) }
            )
        }
    }

    // -------------------------------------------------------------- utilità

    private fun showText(titolo: String, testo: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titolo)
            .setMessage(testo)
            .setPositiveButton("Chiudi", null)
            .show()
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
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

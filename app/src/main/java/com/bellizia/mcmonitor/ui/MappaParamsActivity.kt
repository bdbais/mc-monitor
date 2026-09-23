package com.bellizia.mcmonitor.ui

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
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.ActivityGameSettingsBinding
import com.bellizia.mcmonitor.databinding.ItemSettingBinding
import com.bellizia.mcmonitor.lgsm.ConfigTesto
import com.bellizia.mcmonitor.lgsm.MappaParametri
import com.bellizia.mcmonitor.lgsm.MappaWeb
import com.bellizia.mcmonitor.lgsm.Tipo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Le impostazioni della mappa del mondo.
 *
 * Nasce da un guasto che si vedeva solo su un server vero: BlueMap installato,
 * server acceso, e nessuna mappa da nessuna parte. BlueMap aspettava il
 * permesso di scaricare da Mojang i file del gioco — una riga in un file che
 * dall'app non si poteva toccare — e l'app, che quella riga non la sapeva
 * leggere, diceva «forse sta ancora disegnando il mondo». Per sempre.
 *
 * Le impostazioni sono poche apposta: quelle senza cui la mappa non funziona, e
 * quelle che dicono dove si affaccia. Tutto il resto del file resta dei tre
 * programmi, e questa schermata non lo tocca nemmeno per sbaglio — si
 * sostituisce una riga e si rimette il file com'era.
 *
 * Niente parte al tocco: le modifiche si accumulano e partono con un pulsante
 * solo. A metà strada la mappa avrebbe una configurazione che non ha scelto
 * nessuno.
 */
class MappaParamsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SLUG = "slug"
    }

    private lateinit var binding: ActivityGameSettingsBinding
    private lateinit var mappa: MappaWeb.Mappa

    /** Il contenuto dei file come sta sul server: si parte sempre da qui. */
    private var testi: Map<String, String> = emptyMap()
    private val modifiche = linkedMapOf<String, String>()
    private var salvando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mappa = MappaWeb.perSlug(intent.getStringExtra(EXTRA_SLUG).orEmpty())
            ?: MappaWeb.CATALOGO.first()
        binding = ActivityGameSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { esci() }
        applyInsets()

        binding.sottotitolo.text = "${mappa.nome} · ${Prefs.load().displayName}"
        binding.btnHelp.visible(false)
        binding.titoloAltre.visible(false)
        binding.swipe.setOnRefreshListener { load() }
        binding.btnSalva.setOnClickListener { conferma() }
        load()
    }

    private fun esci() {
        if (modifiche.isEmpty()) return finish()
        MaterialAlertDialogBuilder(this)
            .setTitle("Esci senza salvare?")
            .setMessage("Hai ${modifiche.size} modifiche non ancora scritte sul server.")
            .setPositiveButton("Esci") { _, _ -> finish() }
            .setNegativeButton("Resta", null)
            .show()
    }

    private fun load() {
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            val esito = runCatching { McRepository.leggiConfigMappa(mappa) }
            binding.swipe.isRefreshing = false
            esito.fold(
                onSuccess = {
                    testi = it
                    modifiche.clear()
                    render()
                },
                onFailure = {
                    binding.intestazione.text = it.userMessage()
                    binding.gruppi.removeAllViews()
                    binding.altre.text = ""
                    binding.btnSalva.visible(false)
                }
            )
        }
    }

    /** Quello che c'è adesso: la modifica in coda, o il file, o il valore di fabbrica. */
    private fun valoreDi(p: MappaParametri.Parametro): String {
        modifiche[p.id + p.file]?.let { return it }
        val testo = testi[p.file] ?: return p.diFabbrica
        return MappaParametri.aSchermo(p, ConfigTesto.leggi(testo, p.percorso))
    }

    private fun render() {
        val parametri = MappaParametri.per(mappa)
        val mancanti = MappaParametri.file(mappa).filterNot { testi.containsKey(it) }

        binding.intestazione.text = buildString {
            if (testi.isEmpty()) {
                append("Non trovo i file di ${mappa.nome} sul server.\n\n")
                append("Li scrive ${mappa.nome} stesso al primo avvio dopo l'installazione: ")
                append("se il server non è ancora ripartito da allora, non ci sono ancora. ")
                append("Accendi il server, aspetta che finisca di partire, e riprova.")
            } else {
                append("Sono le impostazioni di ${mappa.nome}, in ")
                append(testi.keys.joinToString(" e ") { "serverfiles/$it" })
                append(". Del resto del file non tocco niente, e prima di scrivere ")
                append("me ne metto da parte una copia.\n\nNiente parte finché non premi Salva.")
                if (mancanti.isNotEmpty()) {
                    append("\n\nNon trovo ${mancanti.joinToString(", ")}: ")
                    append("quelle impostazioni non si possono cambiare da qui.")
                }
            }
        }

        binding.gruppi.removeAllViews()
        parametri.filter { testi.containsKey(it.file) }.forEach {
            binding.gruppi.addView(riga(it))
        }

        binding.altre.text = if (testi.isEmpty()) "" else
            "Nei file ci sono molte altre righe — come disegnare il mondo, quanta memoria " +
                    "usare, i permessi. Restano come sono."

        binding.btnSalva.visible(modifiche.isNotEmpty())
        binding.btnSalva.text =
            if (modifiche.size == 1) "Salva 1 modifica" else "Salva ${modifiche.size} modifiche"
    }

    private fun riga(p: MappaParametri.Parametro): View {
        val item = ItemSettingBinding.inflate(layoutInflater, binding.gruppi, false)
        val valore = valoreDi(p)
        val cambiata = modifiche.containsKey(p.id + p.file)

        item.titolo.text = if (cambiata) "${p.titolo}  •" else p.titolo
        item.spiegazione.text = p.spiegazione

        val avviso = p.avviso?.invoke(valore)
        item.avviso.visible(!avviso.isNullOrBlank())
        item.avviso.text = avviso.orEmpty()

        if (p.booleano) {
            item.valore.visible(false)
            item.interruttore.visible(true)
            item.interruttore.setOnCheckedChangeListener(null)
            item.interruttore.isChecked = valore == "true"
            item.interruttore.setOnCheckedChangeListener { _, acceso ->
                proponi(p, if (acceso) "true" else "false")
            }
            item.card.setOnClickListener { item.interruttore.toggle() }
        } else {
            item.interruttore.visible(false)
            item.valore.visible(true)
            item.valore.text = valore
            item.card.setOnClickListener { chiedi(p, valore) }
        }
        return item.root
    }

    /** In coda, o fuori dalla coda se è tornata com'era: «1 modifica» che non cambia niente è una bugia. */
    private fun proponi(p: MappaParametri.Parametro, valore: String) {
        val nelFile = testi[p.file]?.let { ConfigTesto.leggi(it, p.percorso) }
        val partenza = MappaParametri.aSchermo(p, nelFile)
        if (valore == partenza) modifiche.remove(p.id + p.file) else modifiche[p.id + p.file] = valore
        render()
    }

    private fun chiedi(p: MappaParametri.Parametro, valore: String) {
        val campo = EditText(this).apply {
            setText(valore)
            setSelection(text.length)
            inputType =
                if (p.tipo == Tipo.NUMERO) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
        }
        val contenitore = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 0)
            addView(TextView(this@MappaParamsActivity).apply {
                text = p.spiegazione
                textSize = 13f
                setPadding(0, 0, 0, 16)
            })
            addView(campo)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(p.titolo)
            .setView(contenitore)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Va bene") { _, _ ->
                val scelto = campo.text.toString().trim()
                val male = MappaParametri.controlla(p, scelto)
                if (male != null) showText(p.titolo, male) else proponi(p, scelto)
            }
            .show()
    }

    private fun conferma() {
        val elenco = MappaParametri.per(mappa)
            .filter { modifiche.containsKey(it.id + it.file) }
            .joinToString("\n") { p ->
                "· ${p.titolo}: " + MappaParametri.etichetta(p, modifiche[p.id + p.file].orEmpty())
            }
        MaterialAlertDialogBuilder(this)
            .setTitle("Scrivo sul server")
            .setMessage(
                "$elenco\n\nDi ogni file che tocco resta una copia di sicurezza accanto " +
                        "all'originale.\n\n${mappa.nome} legge la sua configurazione quando " +
                        "parte: perché valga, il server va riavviato."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Scrivi") { _, _ -> salva() }
            .show()
    }

    private fun salva() {
        if (salvando) return
        salvando = true
        binding.swipe.isRefreshing = true
        binding.btnSalva.isEnabled = false

        // Si applicano tutte le modifiche al testo *in memoria*, file per file, e
        // si mandano solo i file che ne hanno almeno una: un file riscritto
        // identico e' una copia di sicurezza in piu' per niente.
        val daScrivere = linkedMapOf<String, String>()
        MappaParametri.per(mappa).forEach { p ->
            val scelto = modifiche[p.id + p.file] ?: return@forEach
            val partenza = daScrivere[p.file] ?: testi[p.file] ?: return@forEach
            daScrivere[p.file] =
                ConfigTesto.scrivi(partenza, p.percorso, MappaParametri.nelFile(p, scelto))
        }

        lifecycleScope.launch {
            val esito = runCatching { McRepository.salvaConfigMappa(daScrivere) }
            salvando = false
            binding.swipe.isRefreshing = false
            binding.btnSalva.isEnabled = true
            esito.fold(
                onSuccess = { quanti ->
                    modifiche.clear()
                    load()
                    chiediRiavvio(quanti)
                },
                onFailure = { showText("Non scritto", it.userMessage()) }
            )
        }
    }

    private fun chiediRiavvio(quanti: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Scritto")
            .setMessage(
                (if (quanti == 1) "Un file cambiato." else "$quanti file cambiati.") +
                        "\n\n${mappa.nome} legge la configurazione quando parte, quindi " +
                        "finché non riavvii il server è tutto come prima. Chi sta " +
                        "giocando viene disconnesso."
            )
            .setNegativeButton("Riavvio dopo", null)
            .setPositiveButton("Riavvia adesso") { _, _ -> riavvia() }
            .show()
    }

    private fun riavvia() {
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            val esito = runCatching { McRepository.restart() }
            binding.swipe.isRefreshing = false
            showText(
                if (esito.isSuccess) "Riavvio mandato" else "Riavvio non riuscito",
                if (esito.isSuccess) {
                    "Il server sta ripartendo. ${mappa.nome} disegna il mondo mentre " +
                            "parte: la prima volta può metterci parecchio."
                } else {
                    esito.exceptionOrNull()?.userMessage().orEmpty()
                }
            )
        }
    }

    private fun showText(titolo: String, testo: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titolo)
            .setMessage(testo)
            .setPositiveButton("Chiudi", null)
            .show()
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

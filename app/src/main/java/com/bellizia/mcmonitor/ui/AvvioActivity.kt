package com.bellizia.mcmonitor.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.ActivityAvvioBinding
import com.bellizia.mcmonitor.lgsm.Avvio
import com.bellizia.mcmonitor.lgsm.Diagnosi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Perché il server non è partito.
 *
 * Prima, quando un server non ripartiva, dall'app non c'era niente da guardare:
 * la scheda Console mostra il log del gioco, che è quello dell'ultimo avvio
 * riuscito, e mostrandolo senza dirlo faceva credere che andasse tutto bene. Il
 * motivo sta in due file che l'app non aveva mai letto — quello che ha deciso
 * LinuxGSM e quello che ha stampato Java prima di morire.
 *
 * In cima c'è la riga con cui il server viene avviato davvero: nove volte su
 * dieci il guasto si vede lì, e finora non era visibile da nessuna parte.
 */
class AvvioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAvvioBinding
    private var diagnosi: Diagnosi? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAvvioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
        applyInsets()

        binding.sottotitolo.text = Prefs.load().displayName
        binding.btnHelp.setOnClickListener { HelpDialog.show(this, Help.AVVIO) }
        binding.swipe.setOnRefreshListener { load() }
        binding.btnCopia.setOnClickListener { copia() }
        load()
    }

    private fun load() {
        binding.swipe.isRefreshing = true
        binding.intestazione.text = "Guardo cosa dicono i log…"
        lifecycleScope.launch {
            val esito = runCatching { McRepository.diagnosiAvvio() }
            binding.swipe.isRefreshing = false
            esito.fold(
                onSuccess = { diagnosi = it; render(it) },
                onFailure = {
                    binding.intestazione.text = it.userMessage()
                    binding.rigaAvvio.text = ""
                    binding.motivo.text = ""
                    binding.elenco.removeAllViews()
                }
            )
        }
    }

    private fun render(d: Diagnosi) {
        binding.intestazione.text = d.stato.frase

        binding.rigaAvvio.text = d.rigaDiAvvio
            ?: "Non riesco a leggere la riga con cui il server viene avviato."
        binding.rigaAvvio.visible(true)

        binding.motivo.text = if (d.haUnMotivo) {
            "${d.motivo}\n\n${d.rimedio}"
        } else {
            "Non ho riconosciuto un motivo noto. Le ultime righe dei log sono qui sotto: " +
                    "quasi sempre la risposta è nelle ultime venti."
        }

        binding.btnRimedio.visible(d.dove.isNotBlank())
        when (d.dove) {
            "ripristino" -> {
                binding.btnRimedio.text = "Rimetti la configurazione di prima"
                binding.btnRimedio.setOnClickListener {
                    startActivity(Intent(this, RestoreActivity::class.java))
                }
            }
            "parametri" -> {
                binding.btnRimedio.text = "Apri le impostazioni tecniche"
                binding.btnRimedio.setOnClickListener {
                    startActivity(Intent(this, ParamsActivity::class.java))
                }
            }
            "impostazioni" -> {
                binding.btnRimedio.text = "Apri le impostazioni del server"
                binding.btnRimedio.setOnClickListener {
                    startActivity(Intent(this, GameSettingsActivity::class.java))
                }
            }
            "mod" -> {
                binding.btnRimedio.text = "Apri la scheda Mod"
                binding.btnRimedio.setOnClickListener { finish() }
            }
        }

        binding.elenco.removeAllViews()
        sezione("Cosa ha deciso LinuxGSM", d.script)
        sezione("Cosa ha detto il server", d.console)
        sezione("Log di gioco", d.gioco)
        sezione("Segnali di stato", d.lock)

        binding.esclusi.text =
            "I log dello script e della console vengono riscritti a ogni avvio, quindi " +
                    "quello che vedi è l'ultimo tentativo."
    }

    private fun sezione(titolo: String, testo: String) {
        binding.elenco.addView(TextView(this).apply {
            text = titolo
            setTextAppearance(com.bellizia.mcmonitor.R.style.CardTitle)
            setPadding(0, 18, 0, 4)
        })
        binding.elenco.addView(TextView(this).apply {
            // Le ultime righe per prime: quando un server muore, il perché sta in fondo.
            text = testo.ifBlank { "(vuoto)" }
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        })
    }

    private fun copia() {
        val d = diagnosi ?: return
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("diagnosi MC Monitor", Avvio.perCopiare(d)))
        MaterialAlertDialogBuilder(this)
            .setTitle("Copiato")
            .setMessage(
                "Il quadro completo è negli appunti: incollalo pure a chi ti sta aiutando.\n\n" +
                        "Dentro ci sono i percorsi del tuo server, non le password."
            )
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

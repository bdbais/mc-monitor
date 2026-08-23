package com.bellizia.mcmonitor.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.ActivityRestoreBinding
import com.bellizia.mcmonitor.databinding.ItemCopiaBinding
import com.bellizia.mcmonitor.lgsm.Copia
import com.bellizia.mcmonitor.lgsm.Restore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Rimettere a posto un file che l'app ha cambiato.
 *
 * Ogni volta che l'app scrive su un file del server ne lascia una copia con la
 * data nel nome. Fin qui c'era sempre stato — ma non si poteva riprendere da
 * nessuna parte: chi si ritrovava il server che non ripartiva doveva collegarsi
 * al computer e sapere cosa cercare. Una rete che non si può tirare non è una
 * rete.
 *
 * Prima di rimettere una copia si vede cosa cambierebbe, riga per riga. E il
 * ripristino a sua volta lascia una copia di com'era: se si torna indietro dalla
 * cosa sbagliata, si deve poter tornare avanti.
 */
class RestoreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRestoreBinding
    private var occupato = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRestoreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
        applyInsets()

        binding.sottotitolo.text = Prefs.load().displayName
        binding.btnHelp.setOnClickListener { HelpDialog.show(this, Help.RESTORE) }
        binding.swipe.setOnRefreshListener { load() }
        load()
    }

    private fun load() {
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            val esito = runCatching { McRepository.copie() }
            binding.swipe.isRefreshing = false
            esito.fold(
                onSuccess = { render(it) },
                onFailure = {
                    binding.intestazione.text = it.userMessage()
                    binding.elenco.removeAllViews()
                    binding.esclusi.text = ""
                }
            )
        }
    }

    private fun render(copie: List<Copia>) {
        binding.intestazione.text = if (copie.isEmpty()) {
            "Nessuna copia: l'app non ha ancora modificato nessun file su questo server.\n\n" +
                    "Quando ne modifica uno, qui compare com'era prima."
        } else {
            "Ogni volta che l'app modifica un file del server ne lascia una copia con la " +
                    "data. Qui le rimetti a posto.\n\n" +
                    "Tocca una copia per vedere cosa cambierebbe prima di decidere."
        }

        binding.elenco.removeAllViews()
        Restore.perFile(copie).forEach { (_, elenco) ->
            elenco.forEachIndexed { i, copia ->
                val item = ItemCopiaBinding.inflate(layoutInflater, binding.elenco, false)
                item.titolo.text = copia.nomeFile
                item.quando.text = copia.quando
                item.dettagli.text = buildString {
                    append(copia.originale)
                    append(" · ").append(copia.sizeBytes).append(" byte")
                }
                // La più recente è quella che serve quasi sempre: le altre sono
                // di giri precedenti e vanno prese con più attenzione.
                item.nota.visible(i == 0)
                item.nota.text = "La più recente per questo file."
                item.card.setOnClickListener { vediDifferenza(copia) }
                binding.elenco.addView(item.root)
            }
        }

        binding.esclusi.text = if (copie.isEmpty()) "" else
            "Le copie restano sul server: non si cancellano da qui, e non occupano quasi niente."
    }

    private fun vediDifferenza(copia: Copia) {
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            val esito = runCatching { McRepository.differenza(copia) }
            binding.swipe.isRefreshing = false
            val testo = esito.getOrElse { it.userMessage() }
            val uguali = testo.contains("UGUALI")

            MaterialAlertDialogBuilder(this@RestoreActivity)
                .setTitle("${copia.nomeFile} · ${copia.quando}")
                .setMessage(
                    when {
                        uguali -> "Il file adesso è identico a questa copia: non cambierebbe niente."
                        testo.isBlank() -> "Non riesco a confrontare i due file."
                        else -> "Rimettendo questa copia, il file tornerebbe così.\n" +
                                "Le righe con - spariscono, quelle con + tornano:\n\n" +
                                testo.take(3000)
                    }
                )
                .setNegativeButton("Lascia stare", null)
                .apply {
                    if (!uguali) setPositiveButton("Rimetti questa") { _, _ -> conferma(copia) }
                }
                .show()
        }
    }

    private fun conferma(copia: Copia) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Rimettere ${copia.nomeFile} com'era?")
            .setMessage(
                "Torna alla copia del ${copia.quando}.\n\n" +
                        "Di com'è adesso resta un'altra copia, quindi si può tornare avanti.\n\n" +
                        "Perché il server usi il file rimesso, va riavviato."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Rimetti") { _, _ -> ripristina(copia) }
            .show()
    }

    private fun ripristina(copia: Copia) {
        if (occupato) return
        occupato = true
        binding.swipe.isRefreshing = true
        lifecycleScope.launch {
            val esito = runCatching { McRepository.ripristina(copia) }
            occupato = false
            binding.swipe.isRefreshing = false
            esito.fold(
                onSuccess = { testo ->
                    MaterialAlertDialogBuilder(this@RestoreActivity)
                        .setTitle("Rimesso")
                        .setMessage("$testo\n\nAdesso riavvia il server perché lo rilegga.")
                        .setPositiveButton("Chiudi", null)
                        .show()
                    load()
                },
                onFailure = {
                    MaterialAlertDialogBuilder(this@RestoreActivity)
                        .setTitle("Non rimesso")
                        .setMessage(it.userMessage())
                        .setPositiveButton("Chiudi", null)
                        .show()
                }
            )
        }
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

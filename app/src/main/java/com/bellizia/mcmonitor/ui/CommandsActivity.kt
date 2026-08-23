package com.bellizia.mcmonitor.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.ActivityCommandsBinding
import com.bellizia.mcmonitor.databinding.ItemComandoBinding
import com.bellizia.mcmonitor.lgsm.ComandoLgsm
import com.bellizia.mcmonitor.lgsm.LgsmCommands
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * I comandi dello script di LinuxGSM, lanciabili con un tocco.
 *
 * Sono gli stessi che si scriverebbero da terminale — `./mcserver backup`,
 * `./mcserver update` — e girano con i valori che l'amministratore ha messo nelle
 * impostazioni: LinuxGSM legge i suoi file a ogni esecuzione, quindi quello che
 * parte da qui è esattamente quello che partirebbe da là.
 *
 * L'elenco è chiuso, e non per prudenza: `console`, `debug` e `install` aspettano
 * una risposta dalla tastiera che da qui non può arrivare, e senza terminale
 * LinuxGSM non si ferma — ripete la domanda all'infinito finché non si stacca.
 */
class CommandsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommandsBinding
    private var inCorso = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommandsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
        applyInsets()

        binding.sottotitolo.text = Prefs.load().displayName
        binding.btnHelp.setOnClickListener { HelpDialog.show(this, Help.COMANDI) }
        binding.swipe.setOnRefreshListener { binding.swipe.isRefreshing = false }
        render()
    }

    private fun render() {
        val cfg = Prefs.load()
        binding.intestazione.text =
            "Gli stessi comandi che si darebbero da terminale con ./${cfg.script}, " +
                    "con i valori che hai messo nelle impostazioni.\n\n" +
                    "Avvia, Ferma e Riavvia stanno nella scheda Stato."

        binding.elenco.removeAllViews()
        LgsmCommands.catalogo.forEach { comando ->
            val item = ItemComandoBinding.inflate(layoutInflater, binding.elenco, false)
            item.titolo.text = comando.titolo
            item.nome.text = "./${cfg.script} ${comando.nome}"
            item.cosaFa.text = comando.cosaFa
            item.conseguenza.visible(!comando.conseguenza.isNullOrBlank())
            item.conseguenza.text = comando.conseguenza.orEmpty()
            item.card.setOnClickListener { chiedi(comando) }
            binding.elenco.addView(item.root)
        }

        binding.esclusi.text =
            "Non ci sono console, debug e install: aspettano una risposta dalla tastiera, " +
                    "e senza un terminale vero LinuxGSM ripete la domanda all'infinito invece " +
                    "di fermarsi. debug in più spegne il server prima di cominciare."
    }

    private fun chiedi(comando: ComandoLgsm) {
        if (inCorso) {
            toast("Aspetta che finisca quello di prima")
            return
        }
        if (!comando.chiedeConferma) {
            esegui(comando)
            return
        }

        val messaggio = buildString {
            append(comando.cosaFa)
            comando.conseguenza?.let { append("\n\n").append(it) }
            if (comando.pubblica) {
                append("\n\nQuesto manda qualcosa fuori dal tuo computer.")
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("${comando.titolo}?")
            .setMessage(messaggio)
            .setNegativeButton("Annulla", null)
            .setPositiveButton(if (comando.pubblica) "Avanti" else "Lancia") { _, _ ->
                // Quello che esce dal computer si conferma due volte: la prima
                // dice cosa fa, la seconda che non si torna indietro.
                if (comando.pubblica) secondaConferma(comando) else esegui(comando)
            }
            .show()
    }

    private fun secondaConferma(comando: ComandoLgsm) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Sicuro?")
            .setMessage(
                "La pagina resta pubblica in rete per un mese e chiunque abbia " +
                        "l'indirizzo può leggerla. Le password vengono nascoste, ma indirizzo, " +
                        "porte e percorsi del tuo server no."
            )
            .setNegativeButton("Lascia stare", null)
            .setPositiveButton("Pubblica") { _, _ -> esegui(comando) }
            .show()
    }

    private fun esegui(comando: ComandoLgsm) {
        inCorso = true
        binding.progress.visible(true)
        toast("${comando.titolo}: in corso…")
        lifecycleScope.launch {
            val esito = runCatching { McRepository.runLgsm(comando) }
            inCorso = false
            binding.progress.visible(false)
            esito.fold(
                onSuccess = { (riassunto, testo) ->
                    mostra(comando.titolo, "$riassunto\n\n$testo")
                },
                onFailure = { mostra(comando.titolo, it.userMessage()) }
            )
        }
    }

    private fun mostra(titolo: String, testo: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titolo)
            .setMessage(testo.take(4000).ifBlank { "Nessuna risposta." })
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

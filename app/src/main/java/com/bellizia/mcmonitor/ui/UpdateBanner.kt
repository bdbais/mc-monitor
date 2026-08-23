package com.bellizia.mcmonitor.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.databinding.ViewUpdateBannerBinding
import com.bellizia.mcmonitor.update.ApkInstaller
import com.bellizia.mcmonitor.update.Update
import com.bellizia.mcmonitor.update.UpdateChecker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Mostra un avviso quando sul repository c'è una release più recente.
 * Il banner resta nascosto se non c'è niente di nuovo o se la rete non risponde.
 */
object UpdateBanner {

    /** Versioni per cui l'utente ha già detto "più tardi" in questa sessione. */
    private var dismissed: String? = null

    fun attach(activity: AppCompatActivity, banner: ViewUpdateBannerBinding) {
        activity.lifecycleScope.launch {
            val update = UpdateChecker.check(activity) ?: return@launch
            if (update.version == dismissed) return@launch
            show(activity, banner, update)
        }
    }

    private fun show(activity: AppCompatActivity, banner: ViewUpdateBannerBinding, update: Update) {
        banner.root.visible(true)
        banner.updateTitle.text = "Disponibile la versione ${update.version} · ${update.sizeLabel}"
        banner.updateNotes.text = plainText(update.notes)
            .ifBlank { "Tocca Aggiorna per scaricare e installare." }

        banner.updateDismiss.setOnClickListener {
            dismissed = update.version
            banner.root.visible(false)
        }

        banner.updateInstall.setOnClickListener {
            // Se questa copia non ha di che consegnare il file all'installer, è
            // inutile scaricare venti megabyte per poi scoprirlo alla fine.
            if (!ApkInstaller.puoInstallare(activity)) {
                variantePerProve(activity, update.version)
                return@setOnClickListener
            }

            banner.updateInstall.isEnabled = false
            banner.updateTitle.text = "Scarico la versione ${update.version}…"
            activity.lifecycleScope.launch {
                val result = runCatching { ApkInstaller.download(activity, update) }
                banner.updateInstall.isEnabled = true
                result.onSuccess { file ->
                    banner.updateTitle.text = "Pronta all'installazione: versione ${update.version}"
                    // Il consenso di sistema si controlla prima: così invece di un
                    // errore si apre la schermata dov'è l'interruttore.
                    if (ApkInstaller.servePermesso(activity)) {
                        chiediPermesso(activity) {
                            runCatching { ApkInstaller.install(activity, file) }
                        }
                        return@onSuccess
                    }
                    runCatching { ApkInstaller.install(activity, file) }.onFailure { error ->
                        chiediPermesso(activity, error.message.orEmpty()) {
                            runCatching { ApkInstaller.install(activity, file) }
                        }
                    }
                }.onFailure { error ->
                    banner.updateTitle.text = "Disponibile la versione ${update.version}"
                    problem(activity, "Download fallito", error.message.orEmpty())
                }
            }
        }
    }

    /**
     * Android non lascia installare app a un'app finché non glielo si dice una
     * volta, e quell'interruttore è sepolto nelle impostazioni di sistema.
     *
     * Spiegarlo a parole e lasciare che se lo cerchi è il modo peggiore: qui c'è
     * il pulsante che ci porta, e tornando indietro si riprova senza riscaricare.
     */
    private fun chiediPermesso(
        activity: AppCompatActivity,
        motivo: String = "",
        riprova: () -> Unit
    ) {
        val messaggio = buildString {
            if (motivo.isNotBlank()) append(motivo).append("\n\n")
            append("Android chiede il tuo consenso una volta sola prima di lasciare che ")
            append("un'app ne installi un'altra. Il pulsante qui sotto apre la schermata ")
            append("giusta: accendi l'interruttore, torna indietro e tocca di nuovo Aggiorna.")
            append("\n\nIl file è già scaricato: non si riscarica niente.")
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Serve il tuo consenso")
            .setMessage(messaggio)
            .setNegativeButton("Più tardi", null)
            .setNeutralButton("Riprova") { _, _ -> riprova() }
            .setPositiveButton("Apri le impostazioni") { _, _ ->
                runCatching { activity.startActivity(ApkInstaller.impostazioniPermesso(activity)) }
                    .onFailure {
                        problem(
                            activity,
                            "Non trovo la schermata",
                            "Aprila a mano: Impostazioni · App · MC Monitor · " +
                                    "Installa app sconosciute."
                        )
                    }
            }
            .show()
    }

    /**
     * La variante di prova non può installare niente, ed è voluto.
     *
     * Era nata per capire quale componente un certo telefono rifiutasse, quindi
     * le sono stati tolti il permesso di installare e il pezzo che consegna il
     * file. Chi ci si trova sopra non lo sa, e finora si prendeva un messaggio di
     * Android in inglese senza nessuna via d'uscita.
     */
    private fun variantePerProve(activity: AppCompatActivity, versione: String) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Questa copia non può aggiornarsi")
            .setMessage(
                "Hai installato la variante di prova di MC Monitor, quella che si chiama " +
                        "\"MC Monitor prova\". È stata fatta apposta senza la parte che " +
                        "consegna il file all'installer, e quindi non può installare niente " +
                        "da sola: non è un guasto.\n\n" +
                        "Scarica la versione $versione dalla pagina delle versioni e " +
                        "installala a mano. È un'app separata, quindi si affianca a questa.\n\n" +
                        "Prima però salva la configurazione: Impostazioni · backup in una " +
                        "cartella. Da lì la riprendi nell'app nuova."
            )
            .setNegativeButton("Chiudi", null)
            .setPositiveButton("Apri la pagina") { _, _ ->
                runCatching { activity.startActivity(ApkInstaller.paginaVersioni()) }
            }
            .show()
    }

    /**
     * Le note di rilascio sono in Markdown: nel banner va mostrato testo semplice,
     * altrimenti si leggono gli asterischi invece del grassetto.
     */
    fun plainText(notes: String): String = notes.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("---") }
        .map { line ->
            line.removePrefix("- ").removePrefix("* ")
                .replace("**", "")
                .replace("`", "")
                .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
        }
        .take(3)
        .joinToString(" ")
        .trim()

    /**
     * Un errore senza una via d'uscita è solo un dispiacere: qui c'è sempre il
     * modo di scaricare il file a mano.
     */
    private fun problem(activity: AppCompatActivity, title: String, message: String) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(message.ifBlank { "Non è riuscito, e non ha detto perché." })
            .setNegativeButton("Chiudi", null)
            .setPositiveButton("Apri la pagina delle versioni") { _, _ ->
                runCatching { activity.startActivity(ApkInstaller.paginaVersioni()) }
            }
            .show()
    }
}

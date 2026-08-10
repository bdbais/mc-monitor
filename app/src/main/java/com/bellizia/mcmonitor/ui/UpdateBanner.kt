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
            banner.updateInstall.isEnabled = false
            banner.updateTitle.text = "Scarico la versione ${update.version}…"
            activity.lifecycleScope.launch {
                val result = runCatching { ApkInstaller.download(activity, update) }
                banner.updateInstall.isEnabled = true
                result.onSuccess { file ->
                    banner.updateTitle.text = "Pronta all'installazione: versione ${update.version}"
                    runCatching { ApkInstaller.install(activity, file) }.onFailure { error ->
                        problem(activity, "Installazione non avviata", error.message.orEmpty())
                    }
                }.onFailure { error ->
                    banner.updateTitle.text = "Disponibile la versione ${update.version}"
                    problem(activity, "Download fallito", error.message.orEmpty())
                }
            }
        }
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

    private fun problem(activity: AppCompatActivity, title: String, message: String) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(
                "$message\n\nSe Android blocca l'installazione, consenti a MC Monitor di " +
                        "installare app sconosciute dalle impostazioni di sistema."
            )
            .setPositiveButton("Chiudi", null)
            .show()
    }
}

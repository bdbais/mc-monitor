package com.bellizia.mcmonitor.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.FragmentStatusBinding
import com.bellizia.mcmonitor.databinding.ItemKeyValueBinding
import com.bellizia.mcmonitor.lgsm.Lgsm
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Scheda "Stato": dettagli LinuxGSM e controlli start/stop/restart. */
class StatusFragment : Fragment() {

    private var _b: FragmentStatusBinding? = null
    private val b get() = _b!!

    private val interesting = listOf(
        "Status", "Server name", "Server IP", "Internet IP", "Game port",
        "Query port", "Version", "Game", "Distro", "Uptime", "Backup"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentStatusBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.swipe.setOnRefreshListener { refresh() }
        b.btnStart.setOnClickListener { control("Avvio del server…") { McRepository.start() } }
        b.btnStop.setOnClickListener {
            confirm("Fermare il server?", "I giocatori online verranno disconnessi.") {
                control("Arresto del server…") { McRepository.stop() }
            }
        }
        b.btnRestart.setOnClickListener {
            confirm("Riavviare il server?", "I giocatori online verranno disconnessi.") {
                control("Riavvio del server…") { McRepository.restart() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.load().isComplete) refresh()
    }

    private fun refresh() {
        b.subtitle.text = Prefs.load().label
        if (!Prefs.load().isComplete) {
            b.swipe.isRefreshing = false
            setStatus("NON CONFIGURATO", Color.parseColor("#9E9E9E"))
            b.output.text = "Apri la scheda Impostazioni e inserisci host, utente e credenziali SSH."
            return
        }
        b.swipe.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { McRepository.details() }
                .onSuccess { render(it) }
                .onFailure {
                    setStatus("ERRORE", Color.parseColor("#EF5350"))
                    b.output.text = it.userMessage()
                    b.fields.removeAllViews()
                }
            _b?.swipe?.isRefreshing = false
        }
    }

    private fun render(details: String) {
        val status = Lgsm.parseStatus(details) ?: "SCONOSCIUTO"
        setStatus(status.uppercase(), colorFor(status))

        val parsed = Lgsm.parseDetails(details)
        b.fields.removeAllViews()
        interesting.forEach { key ->
            val value = parsed[key] ?: return@forEach
            val row = ItemKeyValueBinding.inflate(layoutInflater, b.fields, false)
            row.key.text = key
            row.value.text = value
            b.fields.addView(row.root)
        }
        b.output.text = details.trim().ifBlank { "(nessun output)" }
    }

    private fun setStatus(text: String, color: Int) {
        b.status.text = text
        b.status.setTextColor(color)
        b.statusDot.setBackgroundColor(color)
    }

    private fun colorFor(status: String) = when {
        status.contains("STARTED", true) || status.contains("ONLINE", true) ->
            Color.parseColor("#4CAF50")
        status.contains("STOPPED", true) || status.contains("OFFLINE", true) ->
            Color.parseColor("#EF5350")
        else -> Color.parseColor("#FFB300")
    }

    private fun control(progress: String, block: suspend () -> String) {
        if (!configured()) return
        b.swipe.isRefreshing = true
        b.output.text = progress
        setButtons(false)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { block() }
            _b ?: return@launch
            b.output.text = result.getOrElse { it.userMessage() }.ifBlank { "Comando eseguito." }
            setButtons(true)
            b.swipe.isRefreshing = false
            refresh()
        }
    }

    private fun setButtons(enabled: Boolean) {
        b.btnStart.isEnabled = enabled
        b.btnStop.isEnabled = enabled
        b.btnRestart.isEnabled = enabled
    }

    private fun confirm(title: String, message: String, action: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Conferma") { _, _ -> action() }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}

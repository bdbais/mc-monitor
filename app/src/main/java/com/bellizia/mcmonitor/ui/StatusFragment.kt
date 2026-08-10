package com.bellizia.mcmonitor.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.ModRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.FragmentStatusBinding
import com.bellizia.mcmonitor.databinding.ItemKeyValueBinding
import com.bellizia.mcmonitor.lgsm.Lgsm
import com.bellizia.mcmonitor.lgsm.VersionConfig
import com.bellizia.mcmonitor.mods.MojangVersions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Scheda "Stato": dettagli LinuxGSM e controlli start/stop/restart. */
class StatusFragment : Fragment() {

    private var _b: FragmentStatusBinding? = null
    private val b get() = _b!!
    private var versionConfig: VersionConfig? = null

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
        b.btnChangeVersion.setOnClickListener { chooseVersion() }
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
        loadMods()
        loadVersion()
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { McRepository.details() }
                .onSuccess {
                    render(it)
                    checkRequirementsOnce()
                }
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

    /**
     * Al primo collegamento a un server verifica che gli strumenti necessari ci siano.
     * Senza java o tmux non parte niente, e conviene dirlo subito invece di far
     * fallire i comandi uno per uno.
     */
    private fun checkRequirementsOnce() {
        if (Prefs.load().requirementsChecked) return
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { McRepository.requirements() }.getOrNull() ?: return@launch
            if (!isAdded) return@launch
            val (results, java) = result

            // Segnato come fatto in ogni caso: l'avviso non deve diventare assillante.
            Prefs.save(Prefs.load().copy(requirementsChecked = true))

            val missing = results.filter { !it.present }
            if (missing.isEmpty()) return@launch

            val blocking = missing.filter { it.blocking }
            val message = buildString {
                append("Sul server mancano alcuni strumenti che servono all'app.\n\n")
                missing.forEach {
                    append(if (it.blocking) "· " else "· (facoltativo) ")
                    append(it.requirement.command)
                    it.requirement.alternative?.let { alt -> append(" (né $alt)") }
                    append(" — ${it.requirement.why}\n")
                }
                if (blocking.isNotEmpty()) {
                    append("\nPer installarli serve un utente con privilegi di amministratore:\n")
                    append("sudo apt install ")
                    append(blocking.joinToString(" ") { packageFor(it.requirement.command) })
                }
                if (java != null) append("\n\nJava rilevato: $java")
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(if (blocking.isEmpty()) "Manca qualcosa di facoltativo" else "Requisiti mancanti sul server")
                .setMessage(message)
                .setPositiveButton("Ho capito", null)
                .show()
        }
    }

    /** Il nome del pacchetto non sempre coincide con quello del comando. */
    private fun packageFor(command: String) = when (command) {
        "java" -> "openjdk-21-jre-headless"
        "sha1sum" -> "coreutils"
        "zgrep" -> "gzip"
        else -> command
    }

    /** Versione impostata in LinuxGSM: è quella che il server scaricherà con `update`. */
    private fun loadVersion() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { McRepository.versionConfig() }
            val bind = _b ?: return@launch
            versionConfig = result.getOrNull()
            bind.versionConfig.text = result.fold(
                onSuccess = { "mcversion = ${it.version ?: "?"} · ramo ${it.branch ?: "release"}" },
                onFailure = { "configurazione non leggibile" }
            )
            bind.btnChangeVersion.isEnabled = result.isSuccess
        }
    }

    private fun chooseVersion() {
        val current = versionConfig ?: return
        b.swipe.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { MojangVersions.list(includeSnapshots = false) }
            val bind = _b ?: return@launch
            bind.swipe.isRefreshing = false
            val versions = result.getOrElse {
                showText("Elenco versioni non disponibile", it.userMessage())
                return@launch
            }
            val labels = versions.take(60).map { "${it.id}  ·  ${it.dateLabel}" }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Versioni ufficiali (attuale: ${current.version ?: "?"})")
                .setItems(labels.toTypedArray()) { _, which ->
                    confirmVersion(versions[which].id, current)
                }
                .setNegativeButton("Annulla", null)
                .show()
        }
    }

    private fun confirmVersion(version: String, current: VersionConfig) {
        val downgrade = current.version != null &&
                compareVersions(version, current.version) < 0
        val message = buildString {
            append("Verrà scritto mcversion=\"$version\" nella configurazione LinuxGSM, ")
            append("poi lanciato ./${Prefs.load().script} update: il server si ferma, scarica ")
            append("il jar e riparte.\n\n")
            if (downgrade) {
                append("ATTENZIONE: stai tornando a una versione più vecchia di ")
                append("${current.version}. Un mondo salvato con una versione recente ")
                append("spesso non si apre con una precedente. Fai il backup.")
            } else {
                append("Il mondo verrà convertito al primo avvio e non sarà più apribile ")
                append("con la versione precedente. Un backup resta consigliato.")
            }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Passare a Minecraft $version?")
            .setMessage(message)
            .setNegativeButton("Annulla", null)
            .setNeutralButton("Solo cambio") { _, _ -> applyVersion(version, current, false) }
            .setPositiveButton("Backup e cambio") { _, _ -> applyVersion(version, current, true) }
            .show()
    }

    private fun applyVersion(version: String, current: VersionConfig, backup: Boolean) {
        val view = TextView(requireContext()).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(40, 30, 40, 10)
            text = "Avvio…"
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Passaggio a $version")
            .setView(ScrollView(requireContext()).apply { addView(view) })
            .setCancelable(false)
            .setPositiveButton("Chiudi", null)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val report = runCatching {
                McRepository.changeVersion(
                    version = version,
                    branch = current.branch ?: "release",
                    branchKey = current.branchKey,
                    withBackup = backup
                ) { progress -> view.text = progress }
            }.getOrElse { "Operazione interrotta: ${it.userMessage()}" }
            view.text = report
            dialog.setCancelable(true)
            refresh()
        }
    }

    /** Confronto numerico fra versioni tipo 1.21.10 e 1.20.1. */
    private fun compareVersions(a: String, b: String): Int {
        val x = a.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    private fun showText(title: String, message: String) {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Chiudi", null)
            .show()
    }

    /** Riepilogo dei mod: il dettaglio e le operazioni stanno nella scheda Mod. */
    private fun loadMods() {
        viewLifecycleOwner.lifecycleScope.launch {
            val env = runCatching { ModRepository.environment() }.getOrNull()
            val mods = runCatching { ModRepository.installed() }.getOrDefault(emptyList())
            val bind = _b ?: return@launch

            bind.modsTitle.text = "Mod installati (${mods.count { it.enabled }})"
            bind.modsEnvironment.text = env?.let {
                "Minecraft ${it.minecraftVersion ?: "?"} · ${it.loaderLabel}"
            } ?: "ambiente non rilevato"

            bind.modsList.removeAllViews()
            if (mods.isEmpty()) {
                val row = ItemKeyValueBinding.inflate(layoutInflater, bind.modsList, false)
                row.key.text = "nessun mod"
                row.value.text = if (env?.hasModsDir == true) "cartella mods vuota" else "cartella mods assente"
                bind.modsList.addView(row.root)
                return@launch
            }
            mods.take(12).forEach { mod ->
                val row = ItemKeyValueBinding.inflate(layoutInflater, bind.modsList, false)
                row.key.text = mod.name
                row.value.text = buildString {
                    mod.version?.let { append("v$it ") }
                    append(if (mod.enabled) "" else "· disattivato")
                }
                bind.modsList.addView(row.root)
            }
            if (mods.size > 12) {
                val row = ItemKeyValueBinding.inflate(layoutInflater, bind.modsList, false)
                row.key.text = "…"
                row.value.text = "e altri ${mods.size - 12}"
                bind.modsList.addView(row.root)
            }
        }
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

package com.bellizia.mcmonitor.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.util.Linkify
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.bellizia.mcmonitor.CrashReporter
import com.bellizia.mcmonitor.MainActivity
import com.bellizia.mcmonitor.R
import com.bellizia.mcmonitor.data.PlayerTracker
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.data.ServerConfig
import com.bellizia.mcmonitor.databinding.ActivityServersBinding
import com.bellizia.mcmonitor.databinding.ItemServerBinding
import com.bellizia.mcmonitor.rcon.RconManager
import com.bellizia.mcmonitor.ssh.SshManager
import com.bellizia.mcmonitor.update.UpdateChecker
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Schermata iniziale: l'elenco dei server configurati.
 * Aprirne uno lo rende il server attivo per tutto il resto dell'app.
 */
class ServersActivity : AppCompatActivity() {

    private companion object {
        const val MANUAL_URL = "https://github.com/bdbais/mc-monitor/blob/main/MANUALE.md"
    }

    private lateinit var binding: ActivityServersBinding
    private var selectedId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        applyInsets()

        binding.version.text = "v${UpdateChecker.currentVersion(this)}"
        showLastCrash()
        UpdateBanner.attach(this, binding.updateBanner)
        selectedId = Prefs.activeId().ifBlank { Prefs.servers().firstOrNull()?.id.orEmpty() }

        binding.btnManual.setOnClickListener { openManual() }
        binding.btnHelp.setOnClickListener { showHelp(Help.SERVERS) }
        binding.btnInfo.setOnClickListener { showAbout() }
        binding.btnAdd.setOnClickListener { addServer() }
        binding.btnEdit.setOnClickListener { withSelection { open(it, editing = true) } }
        binding.btnOpen.setOnClickListener { withSelection { open(it, editing = false) } }
        binding.btnClone.setOnClickListener {
            withSelection { server ->
                val copy = Prefs.duplicate(server.id)
                selectedId = copy?.id ?: selectedId
                render()
                toastShort("Duplicato: ${copy?.displayName}")
            }
        }
        binding.btnRemove.setOnClickListener { withSelection { removeServer(it) } }

        // Con un solo server già pronto si va dritti dentro: l'elenco resta comunque
        // dietro, raggiungibile con il tasto indietro.
        if (savedInstanceState == null) {
            val servers = Prefs.servers()
            if (servers.size == 1 && servers[0].isComplete) open(servers[0], editing = false)
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val servers = Prefs.servers()
        if (servers.none { it.id == selectedId }) selectedId = servers.firstOrNull()?.id.orEmpty()

        binding.list.removeAllViews()
        servers.forEach { server ->
            val row = ItemServerBinding.inflate(layoutInflater, binding.list, false)
            row.name.text = server.displayName
            row.subtitle.text = server.label
            row.details.text = buildString {
                append(if (server.isComplete) "pronto" else "configurazione incompleta")
                append(" · LinuxGSM ${server.script}")
                if (server.rconUsable) append(" · RCON ${server.rconPort}")
            }
            row.card.isChecked = server.id == selectedId
            row.card.strokeWidth = if (server.id == selectedId) 3 else 0
            row.card.setOnClickListener {
                selectedId = server.id
                render()
            }
            binding.list.addView(row.root)
        }

        binding.empty.visible(servers.isEmpty())
        val hasSelection = servers.any { it.id == selectedId }
        binding.btnEdit.isEnabled = hasSelection
        binding.btnOpen.isEnabled = hasSelection
        binding.btnClone.isEnabled = hasSelection
        binding.btnRemove.isEnabled = hasSelection
    }

    /**
     * Se l'avvio precedente è finito con un crash, la traccia viene mostrata qui:
     * è l'unico modo perché arrivi a chi può correggerla.
     */
    private fun showLastCrash() {
        val report = CrashReporter.lastCrash(this) ?: return
        val view = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 10f
            setTextIsSelectable(true)
            setPadding(40, 24, 40, 8)
            text = report
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("L'app si è chiusa in modo anomalo")
            .setView(ScrollView(this).apply { addView(view) })
            .setNeutralButton("Copia") { _, _ ->
                getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("crash MC Monitor", report))
                toastShort("Rapporto copiato")
                CrashReporter.clear(this)
            }
            .setPositiveButton("Chiudi") { _, _ -> CrashReporter.clear(this) }
            .show()
    }

    private fun showHelp(page: Help.Page) = HelpDialog.show(this, page)

    /**
     * Riconoscimenti: l'app sta in piedi sul lavoro di altri, e i collegamenti
     * servono anche a chi volesse capire come funziona il proprio server.
     */
    private fun showAbout() {
        val text = """
            MC Monitor ${UpdateChecker.currentVersion(this)}
            Licenza Apache 2.0

            Codice sorgente, release e manuale
            https://github.com/bdbais/mc-monitor

            Costruita insieme a Claude di Anthropic
            https://claude.com/claude-code

            Minecraft è di Mojang Studios. Questa app non è affiliata né approvata da Mojang o Microsoft.
            https://www.minecraft.net

            LinuxGSM, il sistema che gestisce il server di gioco
            https://linuxgsm.com

            Modrinth, da cui arrivano mod e modpack
            https://modrinth.com

            FabricMC, il mod loader installabile dall'app
            https://fabricmc.net

            Elenco ufficiale delle versioni di Minecraft
            https://piston-meta.mojang.com

            Componenti di terze parti
            mwiede/jsch (client SSH, BSD 3-Clause)
            https://github.com/mwiede/jsch
            AndroidX e Material Components (Apache 2.0)
            Font Press Start 2P (SIL Open Font License 1.1)
        """.trimIndent()

        val view = TextView(this).apply {
            setText(text)
            textSize = 13f
            setTextIsSelectable(true)
            autoLinkMask = Linkify.WEB_URLS
            setLinkTextColor(getColor(R.color.grass))
            setPadding(48, 32, 48, 16)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Informazioni")
            .setView(ScrollView(this).apply { addView(view) })
            .setPositiveButton("Chiudi", null)
            .show()
    }

    /** Il manuale sta nel repository: si apre nel browser, sempre aggiornato. */
    private fun openManual() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(MANUAL_URL))
        runCatching { startActivity(intent) }
            .onFailure { toastShort("Nessuna app per aprire i collegamenti") }
    }

    private fun addServer() {
        val fresh = Prefs.add(ServerConfig(name = "Nuovo server"))
        selectedId = fresh.id
        open(fresh, editing = true)
    }

    private fun removeServer(server: ServerConfig) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Rimuovere ${server.displayName}?")
            .setMessage(
                "Viene cancellata solo la configurazione salvata sul telefono, " +
                        "credenziali comprese. Il server non viene toccato."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Rimuovi") { _, _ ->
                val wasActive = Prefs.activeId() == server.id
                Prefs.remove(server.id)
                if (wasActive) disconnectAll()
                render()
            }
            .show()
    }

    /** Cambiare server significa buttare via sessione SSH, RCON e scie in memoria. */
    private fun open(server: ServerConfig, editing: Boolean) {
        if (Prefs.activeId() != server.id) {
            disconnectAll()
            Prefs.setActive(server.id)
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, editing || !server.isComplete)
        )
    }

    private fun disconnectAll() {
        SshManager.disconnect()
        RconManager.disconnect()
        PlayerTracker.clear()
        PlayerTracker.focus = null
    }

    private fun withSelection(block: (ServerConfig) -> Unit) {
        val server = Prefs.servers().firstOrNull { it.id == selectedId }
        if (server == null) toastShort("Seleziona prima un server") else block(server)
    }

    private fun toastShort(message: String) {
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

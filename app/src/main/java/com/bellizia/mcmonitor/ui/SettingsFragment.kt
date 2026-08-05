package com.bellizia.mcmonitor.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.data.ServerConfig
import com.bellizia.mcmonitor.databinding.FragmentSettingsBinding
import com.bellizia.mcmonitor.lgsm.Lgsm
import com.bellizia.mcmonitor.rcon.RconManager
import com.bellizia.mcmonitor.ssh.SshManager
import kotlinx.coroutines.launch
import java.security.SecureRandom

/** Scheda "Impostazioni": credenziali SSH e percorsi LinuxGSM. */
class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fill(Prefs.load())

        b.btnSave.setOnClickListener {
            Prefs.save(collect())
            SshManager.disconnect()
            RconManager.disconnect()
            toast("Impostazioni salvate")
        }

        b.btnTest.setOnClickListener {
            val cfg = collect()
            Prefs.save(cfg)
            SshManager.disconnect()
            if (!cfg.isComplete) {
                toast("Compila host, utente e password o chiave")
                return@setOnClickListener
            }
            b.btnTest.isEnabled = false
            b.testResult.text = "Connessione a ${cfg.label}…"
            viewLifecycleOwner.lifecycleScope.launch {
                val result = runCatching { SshManager.test(cfg) }
                val bind = _b ?: return@launch
                bind.btnTest.isEnabled = true
                bind.testResult.text = result.fold(
                    onSuccess = { "Connesso.\n$it\nFingerprint host salvato." },
                    onFailure = { "Errore: ${it.userMessage()}" }
                )
            }
        }

        b.btnRconPassword.setOnClickListener {
            b.rconPassword.setText(generatePassword())
            toast("Password generata: ricordati di attivare RCON sul server")
        }

        b.btnRconTest.setOnClickListener {
            val cfg = collect().copy(rconEnabled = true)
            Prefs.save(cfg)
            if (cfg.rconPassword.isBlank()) {
                toast("Imposta prima una password RCON")
                return@setOnClickListener
            }
            RconManager.disconnect()
            b.btnRconTest.isEnabled = false
            b.testResult.text = "Prova RCON su porta ${cfg.rconPort}…"
            viewLifecycleOwner.lifecycleScope.launch {
                val result = runCatching { RconManager.test(cfg) }
                val bind = _b ?: return@launch
                bind.btnRconTest.isEnabled = true
                bind.testResult.text = result.fold(
                    onSuccess = { "RCON raggiungibile.\nRisposta a \"list\": $it" },
                    onFailure = { "RCON non raggiungibile: ${it.userMessage()}" }
                )
            }
        }

        b.btnRconSetup.setOnClickListener { setupRcon() }

        b.btnDiagnose.setOnClickListener {
            val cfg = collect()
            Prefs.save(cfg)
            if (cfg.host.isBlank()) {
                toast("Inserisci almeno host e utente")
                return@setOnClickListener
            }
            b.btnDiagnose.isEnabled = false
            b.testResult.text = "Diagnostica in corso…"
            viewLifecycleOwner.lifecycleScope.launch {
                val report = runCatching { SshManager.diagnose(cfg) }
                    .getOrElse { "Diagnostica interrotta: ${it.userMessage()}" }
                val bind = _b ?: return@launch
                bind.btnDiagnose.isEnabled = true
                bind.testResult.text = report
                showReport(report)
            }
        }

        b.btnForgetHostKey.setOnClickListener {
            Prefs.clearHostKey()
            SshManager.disconnect()
            toast("Fingerprint host dimenticato: verrà riappreso al prossimo collegamento")
        }
    }

    /**
     * Attiva RCON lato server. Tocca server.properties e riavvia il gioco, quindi
     * va confermato esplicitamente: i giocatori collegati cadono.
     */
    private fun setupRcon() {
        val cfg = collect()
        Prefs.save(cfg)
        if (!cfg.isComplete) {
            toast("Completa prima i dati di connessione SSH")
            return
        }
        if (cfg.rconPassword.isBlank()) {
            b.rconPassword.setText(generatePassword())
        }
        val password = b.rconPassword.text?.toString().orEmpty()
        if (!Lgsm.PASSWORD_CHARSET.matches(password)) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Password non valida")
                .setMessage(
                    "La password RCON deve avere da 8 a 64 caratteri fra lettere, cifre, " +
                            "punto, trattino e trattino basso. Gli altri simboli sono esclusi " +
                            "perché finirebbero in un file di configurazione sul server."
                )
                .setPositiveButton("Chiudi", null)
                .show()
            return
        }
        val port = b.rconPort.text?.toString()?.trim()?.toIntOrNull() ?: 25575

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Attivare RCON sul server?")
            .setMessage(
                "Verranno modificate le voci enable-rcon, rcon.port, rcon.password e " +
                        "broadcast-rcon-to-ops in server.properties (con copia di sicurezza), " +
                        "poi il server verrà RIAVVIATO: i giocatori online saranno disconnessi."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Procedi") { _, _ -> runRconSetup(port, password) }
            .show()
    }

    private fun runRconSetup(port: Int, password: String) {
        val view = TextView(requireContext()).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(40, 30, 40, 10)
            text = "Avvio…"
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Attivazione RCON")
            .setView(ScrollView(requireContext()).apply { addView(view) })
            .setCancelable(false)
            .setPositiveButton("Chiudi", null)
            .show()

        b.btnRconSetup.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val report = runCatching {
                McRepository.setupRcon(port, password) { progress -> view.text = progress }
            }.getOrElse { "Attivazione interrotta: ${it.userMessage()}" }
            view.text = report
            _b?.let {
                it.btnRconSetup.isEnabled = true
                it.testResult.text = report
                fill(Prefs.load())
            }
            dialog.setCancelable(true)
        }
    }

    /** Password lunga ma con soli caratteri sicuri per server.properties. */
    private fun generatePassword(): String {
        val alphabet = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val random = SecureRandom()
        return (1..20).map { alphabet[random.nextInt(alphabet.size)] }.joinToString("")
    }

    private fun showReport(report: String) {
        val view = TextView(requireContext()).apply {
            text = report
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(40, 30, 40, 10)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Diagnostica")
            .setView(ScrollView(requireContext()).apply { addView(view) })
            .setPositiveButton("Chiudi", null)
            .setNeutralButton("Copia") { _, _ ->
                val cm = requireContext().getSystemService(ClipboardManager::class.java)
                cm?.setPrimaryClip(ClipData.newPlainText("diagnostica", report))
                toast("Report copiato")
            }
            .show()
    }

    /** Accetta "ssh://host:2222", "host:2222" o "host" e separa host e porta. */
    private fun parseHost(raw: String, fallbackPort: Int): Pair<String, Int> {
        var text = raw.trim().removePrefix("ssh://").trim('/')
        if (text.contains('@')) text = text.substringAfterLast('@')
        val port = text.substringAfterLast(':', "").toIntOrNull()
        if (port != null && text.count { it == ':' } == 1) {
            return text.substringBeforeLast(':') to port
        }
        return text to fallbackPort
    }

    private fun fill(cfg: ServerConfig) {
        b.name.setText(cfg.name)
        b.host.setText(cfg.host)
        b.port.setText(cfg.port.toString())
        b.user.setText(cfg.user)
        b.password.setText(cfg.password)
        b.privateKey.setText(cfg.privateKey)
        b.passphrase.setText(cfg.keyPassphrase)
        b.lgsmDir.setText(cfg.lgsmDir)
        b.script.setText(cfg.script)
        b.serverFiles.setText(cfg.serverFilesDir)
        b.tmuxSession.setText(cfg.tmuxSession)
        b.rconEnabled.isChecked = cfg.rconEnabled
        b.rconTunnel.isChecked = cfg.rconTunnel
        b.rconPort.setText(cfg.rconPort.toString())
        b.rconPassword.setText(cfg.rconPassword)
        b.webMapUrl.setText(cfg.webMapUrl)
        b.pollSeconds.setText(cfg.mapPollSeconds.toString())
        b.useLgsmSend.isChecked = cfg.useLgsmSend
    }

    private fun collect(): ServerConfig {
        val (host, port) = parseHost(
            b.host.text?.toString().orEmpty(),
            b.port.text?.toString()?.trim()?.toIntOrNull() ?: 22
        )
        // Se l'utente ha scritto "host:porta" il campo porta viene allineato.
        if (b.host.text?.toString()?.trim() != host) b.host.setText(host)
        if (b.port.text?.toString()?.trim() != port.toString()) b.port.setText(port.toString())
        return build(host, port)
    }

    private fun build(host: String, port: Int) = ServerConfig(
        // id e fingerprint appartengono al profilo, non ai campi del modulo.
        id = Prefs.load().id,
        name = b.name.text?.toString()?.trim().orEmpty(),
        host = host,
        port = port,
        user = b.user.text?.toString()?.trim().orEmpty(),
        password = b.password.text?.toString().orEmpty(),
        privateKey = b.privateKey.text?.toString()?.trim().orEmpty(),
        keyPassphrase = b.passphrase.text?.toString().orEmpty(),
        lgsmDir = b.lgsmDir.text?.toString()?.trim().orEmpty(),
        script = b.script.text?.toString()?.trim().orEmpty(),
        serverFilesDir = b.serverFiles.text?.toString()?.trim().orEmpty(),
        tmuxSession = b.tmuxSession.text?.toString()?.trim().orEmpty(),
        rconEnabled = b.rconEnabled.isChecked,
        rconPort = b.rconPort.text?.toString()?.trim()?.toIntOrNull() ?: 25575,
        rconPassword = b.rconPassword.text?.toString()?.trim().orEmpty(),
        rconTunnel = b.rconTunnel.isChecked,
        webMapUrl = b.webMapUrl.text?.toString()?.trim().orEmpty(),
        mapPollSeconds = b.pollSeconds.text?.toString()?.trim()?.toIntOrNull() ?: 6,
        useLgsmSend = b.useLgsmSend.isChecked,
        hostKeyFingerprint = Prefs.load().hostKeyFingerprint
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}

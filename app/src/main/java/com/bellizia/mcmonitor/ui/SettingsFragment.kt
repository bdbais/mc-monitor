package com.bellizia.mcmonitor.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bellizia.mcmonitor.MainActivity
import com.bellizia.mcmonitor.notify.ServerWatchService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.bellizia.mcmonitor.data.BackupManager
import com.bellizia.mcmonitor.data.ConfigTransfer
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.data.ServerConfig
import com.bellizia.mcmonitor.lgsm.MappaWeb
import java.io.File
import java.text.DateFormat
import java.util.Date
import com.bellizia.mcmonitor.databinding.DialogPasswordBinding
import com.bellizia.mcmonitor.databinding.FragmentSettingsBinding
import com.bellizia.mcmonitor.lgsm.Lgsm
import com.bellizia.mcmonitor.lgsm.Provision
import com.bellizia.mcmonitor.lgsm.RiparaRcon
import com.bellizia.mcmonitor.rcon.RconManager
import com.bellizia.mcmonitor.ssh.SshManager
import kotlinx.coroutines.launch
import java.security.SecureRandom

/** Scheda "Impostazioni": credenziali SSH e percorsi LinuxGSM. */
class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                toast("Senza il permesso alle notifiche il monitoraggio non può avvisarti")
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fill(Prefs.load())

        b.notifyEnabled.setOnCheckedChangeListener { _, checked ->
            updateNotifyFields(checked)
            if (checked) askNotificationPermission()
        }

        b.btnSave.setOnClickListener {
            val chiesto = collect()
            Prefs.save(chiesto)
            // Il nome tecnico può essere stato cambiato salvando: sullo stesso
            // computer due server non possono averlo uguale, perché finisce nei
            // marcatori del crontab e nei percorsi della posta. Se è successo si
            // rimette a schermo quello vero e lo si dice, invece di lasciare nel
            // campo un nome che sul server non esiste.
            val salvato = Prefs.load()
            if (salvato.id == chiesto.id && salvato.slug != chiesto.slug) {
                b.slug.setText(salvato.slug)
                toast("Il nome tecnico «${chiesto.slug}» era già di un altro server su questo computer: l'ho salvato come «${salvato.slug}»")
            }
            SshManager.disconnect()
            RconManager.disconnect()
            // Il servizio parte o si ferma da solo in base ai server da monitorare.
            ServerWatchService.sync(requireContext())
            // Il backup segue le modifiche senza che l'utente debba ricordarsene.
            viewLifecycleOwner.lifecycleScope.launch {
                BackupManager.backupIfEnabled(requireContext())?.let { showBackupFolder() }
            }
            toast(
                if (Prefs.load().watching) "Impostazioni salvate, monitoraggio attivo"
                else "Impostazioni salvate"
            )
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
        b.btnRconRipara.setOnClickListener { cercaGuastoRcon() }
        b.btnMappa.setOnClickListener { scegliMappa() }

        mostraModo()
        b.notaModo.setOnClickListener { HelpDialog.show(requireContext(), Help.MODO) }
        b.modoEsperto.setOnCheckedChangeListener { _, esperto ->
            if (esperto == Prefs.esperto) return@setOnCheckedChangeListener
            Prefs.esperto = esperto
            mostraModo()
            // Le schede si decidono all'apertura: per farle sparire (o tornare)
            // serve far ripartire la pagina. Si riparte da questa stessa
            // scheda, non da dove capita: chi ha appena spento l'esperto deve
            // trovarsi davanti l'interruttore per riaccenderlo, non doverlo
            // cercare. E' cosi' che l'interruttore era diventato a senso unico.
            val a = activity ?: return@setOnCheckedChangeListener
            a.startActivity(
                Intent(a, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
            )
            a.finish()
        }

        b.privacyMode.isChecked = Prefs.privacyMode
        b.privacyMode.setOnCheckedChangeListener { _, checked ->
            Prefs.privacyMode = checked
            toast(if (checked) "Modalità privacy attiva" else "Modalità privacy disattivata")
        }
        b.backupEnabled.isChecked = Prefs.backupEnabled
        b.backupEnabled.setOnCheckedChangeListener { _, checked -> Prefs.backupEnabled = checked }
        b.btnExport.setOnClickListener { exportConfig() }
        b.btnImport.setOnClickListener { pickImportFile.launch(arrayOf("*/*")) }
        b.btnBackupFolder.setOnClickListener { pickBackupFolder.launch(null) }
        b.btnBackupNow.setOnClickListener { backupNow() }
        showBackupFolder()

        b.btnChangePassword.setOnClickListener { changePassword() }
        b.btnPrepare.setOnClickListener { prepareServer() }

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

    // ------------------------------------------- privacy, esporta, importa, backup

    private val pickBackupFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@registerForActivityResult
            // Il permesso va reso duraturo, altrimenti si perde al riavvio.
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            Prefs.backupFolder = uri.toString()
            showBackupFolder()
            toast("Cartella di backup scelta")
        }

    private val pickImportFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) askImportPassword(uri)
        }

    private fun showBackupFolder() {
        val bind = _b ?: return
        val folder = Prefs.backupFolder
        bind.backupFolder.text = when {
            folder.isBlank() -> "Nessuna cartella scelta"
            else -> {
                val name = runCatching {
                    DocumentFile.fromTreeUri(requireContext(), Uri.parse(folder))?.name
                }.getOrNull() ?: "cartella scelta"
                val last = Prefs.backupLast
                if (last > 0) {
                    "In $name · ultimo backup ${DateFormat.getDateTimeInstance().format(Date(last))}"
                } else {
                    "In $name · nessun backup ancora"
                }
            }
        }
    }

    /** Chiede una password e produce il file cifrato da condividere. */
    private fun exportConfig() {
        val servers = Prefs.servers()
        if (servers.isEmpty()) {
            toast("Non c'è nessun server da esportare")
            return
        }
        if (!requireContext().puoCondividereFile()) {
            noSharing()
            return
        }
        askPassword(
            title = "Esporta ${servers.size} server",
            message = "Il file conterrà le credenziali SSH e RCON, protette da questa password. " +
                    "Chi lo riceve potrà aprirlo solo conoscendola: mandagliela per un'altra via.",
            confirm = true
        ) { password ->
            val result = runCatching { ConfigTransfer.export(servers, password) }
            result.onSuccess { content ->
                val file = File(requireContext().cacheDir, "mcmonitor-configurazione.mcm")
                file.writeText(content)
                val uri = FileProvider.getUriForFile(
                    requireContext(), "${requireContext().packageName}.updates", file
                )
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/octet-stream"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Configurazione MC Monitor")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                        "Invia la configurazione"
                    )
                )
            }.onFailure { showText("Esportazione fallita", it.message.orEmpty()) }
        }
    }

    /**
     * La variante di prova non ha il FileProvider, quindi non puo' mandare un
     * file da nessuna parte. Il backup in una cartella pero' funziona: passa dal
     * selettore di sistema e non da lui.
     */
    private fun noSharing() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Questa copia non puo' mandare file")
            .setMessage(
                "Stai usando la variante di prova di MC Monitor, che e' stata fatta " +
                        "apposta senza la parte che consegna i file alle altre app.\n\n" +
                        "Per portare via la configurazione usa \"Fai un backup ora\" qui " +
                        "sotto, scegliendo una cartella: quello funziona, e il file si " +
                        "riprende con \"Importa\" da qualsiasi copia dell'app."
            )
            .setPositiveButton("Ho capito", null)
            .show()
    }

    private fun askImportPassword(uri: Uri) {
        askPassword(
            title = "Importa configurazione",
            message = "Inserisci la password che ti ha dato chi ha esportato il file.",
            confirm = false
        ) { password ->
            val result = runCatching {
                val content = requireContext().contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: error("file illeggibile")
                ConfigTransfer.import(content, password)
            }
            result.onSuccess { servers -> confirmImport(servers) }
                .onFailure { showText("Importazione fallita", it.message.orEmpty()) }
        }
    }

    private fun confirmImport(servers: List<ServerConfig>) {
        val elenco = servers.joinToString("\n") { "· ${it.displayName} (${it.user}@${it.host})" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Importare ${servers.size} server?")
            .setMessage("$elenco\n\nVerranno aggiunti ai tuoi, senza sostituire quelli esistenti.")
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Importa") { _, _ ->
                // add() rimpiazzerebbe nome tecnico e cartella LinuxGSM: qui i
                // dati arrivano già completi e vanno tenuti come sono.
                servers.forEach { Prefs.save(it.copy(id = "")) }
                toast("Importati ${servers.size} server")
                fill(Prefs.load())
            }
            .show()
    }

    private fun backupNow() {
        val folder = Prefs.backupFolder
        if (folder.isBlank()) {
            toast("Scegli prima una cartella")
            return
        }
        askPassword(
            title = "Password del backup",
            message = "Serve a cifrare il file. Verrà ricordata per i backup automatici.",
            confirm = true,
            initial = Prefs.backupPassword
        ) { password ->
            Prefs.backupPassword = password
            viewLifecycleOwner.lifecycleScope.launch {
                val result = runCatching {
                    BackupManager.backupNow(requireContext(), Uri.parse(folder), password)
                }
                if (!isAdded) return@launch
                result.onSuccess {
                    showBackupFolder()
                    toast("Backup salvato: $it")
                }.onFailure { showText("Backup non riuscito", it.message.orEmpty()) }
            }
        }
    }

    /** Dialogo per una password, con eventuale conferma. */
    private fun askPassword(
        title: String,
        message: String,
        confirm: Boolean,
        initial: String = "",
        onReady: (String) -> Unit
    ) {
        val form = DialogPasswordBinding.inflate(layoutInflater)
        form.currentPassword.setText(initial)
        form.currentPassword.hint = "Password"
        form.newPassword.visible(false)
        form.repeatPassword.visible(confirm)
        (form.repeatPassword.parent.parent as? View)?.visible(confirm)
        (form.newPassword.parent.parent as? View)?.visible(false)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setView(form.root)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Continua", null)
            .show()
            .also { dialog ->
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val password = form.currentPassword.text?.toString().orEmpty()
                    val repeat = form.repeatPassword.text?.toString().orEmpty()
                    when {
                        password.length < 8 -> toast("Almeno 8 caratteri")
                        confirm && password != repeat -> toast("Le due password non coincidono")
                        else -> {
                            dialog.dismiss()
                            onReady(password)
                        }
                    }
                }
            }
    }

    /** Le scelte sui singoli eventi hanno senso solo a monitoraggio acceso. */
    private fun updateNotifyFields(enabled: Boolean) {
        val bind = _b ?: return
        listOf(bind.notifyOffline, bind.notifyOnline, bind.notifyJoin, bind.notifyLeave)
            .forEach { it.isEnabled = enabled }
        bind.notifySeconds.isEnabled = enabled
    }

    /** Da Android 13 le notifiche vanno concesse dall'utente. */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Esegue `passwd` sul server per l'utente con cui l'app si collega. */
    private fun changePassword() {
        val cfg = collect()
        Prefs.save(cfg)
        if (!cfg.isComplete) {
            toast("Completa prima i dati di connessione SSH")
            return
        }
        val form = DialogPasswordBinding.inflate(layoutInflater)
        form.currentPassword.setText(cfg.password)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cambia password di ${cfg.user}")
            .setView(form.root)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Cambia", null)
            .show()
            .also { dialog ->
                // Il pulsante non chiude il dialogo finché i campi non sono coerenti.
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val current = form.currentPassword.text?.toString().orEmpty()
                    val fresh = form.newPassword.text?.toString().orEmpty()
                    val repeat = form.repeatPassword.text?.toString().orEmpty()
                    when {
                        current.isBlank() -> toast("Serve la password attuale")
                        fresh.length < 8 -> toast("La nuova password è troppo corta (minimo 8)")
                        fresh != repeat -> toast("Le due nuove password non coincidono")
                        fresh == current -> toast("La nuova password è uguale a quella attuale")
                        else -> {
                            dialog.dismiss()
                            runChangePassword(current, fresh)
                        }
                    }
                }
            }
    }

    private fun runChangePassword(current: String, fresh: String) {
        val view = TextView(requireContext()).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(40, 30, 40, 10)
            text = "Eseguo passwd sul server…"
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cambio password")
            .setView(ScrollView(requireContext()).apply { addView(view) })
            .setCancelable(false)
            .setPositiveButton("Chiudi", null)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val report = runCatching { McRepository.changeSshPassword(current, fresh) }
                .getOrElse { it.userMessage() }
            view.text = report
            dialog.setCancelable(true)
            _b?.let {
                fill(Prefs.load())
                it.testResult.text = report
            }
        }
    }

    /**
     * Installa LinuxGSM su una home vuota. Scarica ed esegue software sul server,
     * quindi va spiegato per intero e confermato.
     */
    private fun prepareServer() {
        val cfg = collect()
        Prefs.save(cfg)
        if (!cfg.isComplete) {
            toast("Completa prima i dati di connessione SSH")
            return
        }
        val settings = Provision.SECURITY_SETTINGS.joinToString("\n") { (key, value, why) ->
            "· $key=$value — $why"
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Preparare il server?")
            .setMessage(
                "In ${cfg.lgsmDir}, come utente ${cfg.user}, verranno eseguiti:\n\n" +
                        "1. controllo dei pacchetti necessari\n" +
                        "2. download di linuxgsm.sh dal sito ufficiale\n" +
                        "3. creazione dell'istanza ${cfg.script}\n" +
                        "4. auto-install del server (diversi minuti)\n" +
                        "5. impostazioni di sicurezza in server.properties:\n\n$settings\n\n" +
                        "Tutto gira senza privilegi di amministratore, dentro la home. " +
                        "Se LinuxGSM è già installato l'operazione si ferma senza toccare nulla."
            )
            .setNegativeButton("Annulla", null)
            .setNeutralButton("Senza sicurezza") { _, _ -> runPrepare(false) }
            .setPositiveButton("Prepara") { _, _ -> runPrepare(true) }
            .show()
    }

    private fun runPrepare(harden: Boolean) {
        val view = TextView(requireContext()).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(40, 30, 40, 10)
            text = "Avvio…"
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Preparazione del server")
            .setView(ScrollView(requireContext()).apply { addView(view) })
            .setCancelable(false)
            .setPositiveButton("Chiudi", null)
            .show()

        b.btnPrepare.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val report = runCatching {
                McRepository.prepareServer(harden) { progress -> view.text = progress }
            }.getOrElse { "Preparazione interrotta:\n${it.userMessage()}" }
            view.text = report
            dialog.setCancelable(true)
            _b?.let {
                it.btnPrepare.isEnabled = true
                it.testResult.text = report
            }
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


    // ------------------------------------------------ la mappa del mondo

    /**
     * Scegliere una mappa.
     *
     * Prima qui c'era solo una casella dove incollare un indirizzo, e per averne
     * uno bisognava gia' sapere cosa sono Dynmap e BlueMap, installarne uno a
     * mano e sapere su che porta si mette: la casella serviva a chi non ne
     * aveva bisogno.
     */
    private fun scegliMappa() {
        val cfg = collect()
        Prefs.save(cfg)
        if (!cfg.isComplete) {
            toast("Completa prima i dati di connessione")
            return
        }
        b.btnMappa.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val gia = runCatching { McRepository.mappaInstallata() }.getOrNull()
            val bind = _b ?: return@launch
            bind.btnMappa.isEnabled = true
            if (gia != null) mappaGiaCE(gia) else scegliDallElenco()
        }
    }

    /**
     * C'e' gia' una mappa: la si apre, non la si reinstalla.
     *
     * Proporre l'elenco a chi ne ha gia' una vorrebbe dire farne installare una
     * seconda per sbaglio, e due mappe sullo stesso server litigano per la porta.
     */
    private fun mappaGiaCE(mappa: MappaWeb.Mappa) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${mappa.nome} c'e' gia'")
            .setMessage("Sul server e' gia' installata ${mappa.nome}. La apro?")
            .setPositiveButton("Apri la mappa") { _, _ -> apriMappa(mappa) }
            .setNeutralButton("Scegline un'altra") { _, _ -> scegliDallElenco() }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun scegliDallElenco() {
        val voci = MappaWeb.CATALOGO.map { "${it.nome}\n${it.comeE}" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Quale mappa")
            .setItems(voci) { _, quale -> confermaInstallazione(MappaWeb.CATALOGO[quale]) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun confermaInstallazione(mappa: MappaWeb.Mappa) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Installare ${mappa.nome}?")
            .setMessage(
                "Scarico ${mappa.nome} e lo metto fra le mod, poi RIAVVIO il server: " +
                        "chi sta giocando viene disconnesso.\n\n" +
                        "La prima volta la mappa deve disegnare tutto il mondo e ci mette " +
                        "un po': se non compare subito non e' andata male, sta lavorando."
            )
            .setPositiveButton("Installa") { _, _ -> installaMappa(mappa) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun installaMappa(mappa: MappaWeb.Mappa) {
        val view = TextView(requireContext()).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(40, 30, 40, 10)
            text = "Avvio…"
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Installazione di ${mappa.nome}")
            .setView(ScrollView(requireContext()).apply { addView(view) })
            .setCancelable(false)
            .setPositiveButton("Chiudi", null)
            .show()

        b.btnMappa.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val esito = runCatching {
                McRepository.installaMappa(mappa) { progresso -> view.text = progresso }
            }
            view.text = esito.fold(
                onSuccess = { it.first },
                onFailure = { "Installazione interrotta: ${it.userMessage()}" }
            )
            dialog.setCancelable(true)
            val bind = _b ?: return@launch
            bind.btnMappa.isEnabled = true
            if (esito.getOrNull()?.second != null) {
                bind.statoMappa.text = "${mappa.nome} installata, porta ${esito.getOrNull()?.second}"
                bind.statoMappa.visible(true)
            }
        }
    }

    /**
     * Apre la mappa dentro il tunnel.
     *
     * L'indirizzo non si salva: la porta locale la sceglie il tunnel ogni volta,
     * e domani quella di oggi non porterebbe da nessuna parte.
     */
    private fun apriMappa(mappa: MappaWeb.Mappa) {
        b.btnMappa.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val esito = runCatching {
                val candidate = McRepository.porteCandidate()
                val porta = MappaWeb.portaDellaMappa(mappa, candidate)
                    ?: throw IllegalStateException(
                        if (candidate.isEmpty()) {
                            "${mappa.nome} non risponde su nessuna porta: il server e' spento, " +
                                    "oppure la mappa sta ancora disegnando il mondo."
                        } else {
                            "Non so quale sia la mappa fra le porte in ascolto " +
                                    "(${candidate.joinToString(", ")}). Scrivi l'indirizzo " +
                                    "nella casella qui sotto."
                        }
                    )
                MappaWeb.indirizzoNelTunnel(SshManager.openTunnel(Prefs.load(), porta))
            }
            val bind = _b ?: return@launch
            bind.btnMappa.isEnabled = true
            esito.fold(
                onSuccess = {
                    startActivity(
                        Intent(requireContext(), WebMapActivity::class.java).putExtra("url", it)
                    )
                },
                onFailure = { showText("${mappa.nome} non si apre", it.userMessage()) }
            )
        }
    }


    // ------------------------------------------------ riparazione di RCON

    /**
     * Cerca il guasto e propone di aggiustarlo.
     *
     * Prima si guarda, poi si chiede, e solo dopo si tocca: la riparazione
     * riscrive un file di configurazione e riavvia il server, che non e' una
     * cosa da far partire premendo un tasto senza sapere cosa fara'.
     */
    private fun cercaGuastoRcon() {
        val cfg = collect()
        Prefs.save(cfg)
        if (!cfg.isComplete) {
            toast("Completa prima i dati di connessione")
            return
        }
        b.btnRconRipara.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val esito = runCatching { McRepository.diagnosiRcon() }
            val bind = _b ?: return@launch
            bind.btnRconRipara.isEnabled = true
            esito.fold(
                onSuccess = { mostraDiagnosi(it) },
                onFailure = { showText("Non sono riuscito a guardare", it.userMessage()) }
            )
        }
    }

    private fun mostraDiagnosi(d: RiparaRcon.Diagnosi) {
        val testo = buildString {
            d.problemi.forEach { p ->
                append(
                    when (p.gravita) {
                        RiparaRcon.Gravita.ROTTO -> "[!] "
                        RiparaRcon.Gravita.SOSPETTO -> "[?] "
                        RiparaRcon.Gravita.BENE -> "[ok] "
                    }
                )
                append(p.cosa).append("\n     ").append(p.rimedio.replace("\n", "\n     "))
                append("\n\n")
            }
            if (d.riparabile) {
                append(
                    "La riparazione riscrive enable-rcon, rcon.port, rcon.password e " +
                            "broadcast-rcon-to-ops da zero (togliendo le righe doppie), tiene " +
                            "una copia del file com'era, e RIAVVIA il server: chi sta giocando " +
                            "viene disconnesso."
                )
            }
        }.trim()

        val view = TextView(requireContext()).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(40, 30, 40, 10)
            text = testo
        }
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (d.rotto) "Ho trovato il guasto" else "Cosa ho trovato")
            .setView(ScrollView(requireContext()).apply { addView(view) })
            .setNegativeButton("Chiudi", null)
        if (d.riparabile) builder.setPositiveButton("Ripara e riavvia") { _, _ -> riparaRcon() }
        builder.show()
    }

    private fun riparaRcon() {
        val view = TextView(requireContext()).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(40, 30, 40, 10)
            text = "Avvio…"
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Riparazione di RCON")
            .setView(ScrollView(requireContext()).apply { addView(view) })
            .setCancelable(false)
            .setPositiveButton("Chiudi", null)
            .show()

        b.btnRconRipara.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val report = runCatching {
                McRepository.riparaRcon { progresso -> view.text = progresso }
            }.getOrElse { "Riparazione interrotta: ${it.userMessage()}" }
            view.text = report
            dialog.setCancelable(true)
            _b?.let {
                it.btnRconRipara.isEnabled = true
                it.testResult.text = report
                fill(Prefs.load())
            }
        }
    }

    /** Password lunga ma con soli caratteri sicuri per server.properties. */
    private fun generatePassword(): String {
        val alphabet = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val random = SecureRandom()
        return (1..20).map { alphabet[random.nextInt(alphabet.size)] }.joinToString("")
    }

    /** Messaggio breve in un dialogo, per errori che vanno letti per intero. */
    private fun showText(title: String, message: String) {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message.ifBlank { "Nessun dettaglio disponibile." })
            .setPositiveButton("Chiudi", null)
            .show()
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

    /** L'interruttore e la riga che spiega cosa cambia. */
    private fun mostraModo() {
        val esperto = Prefs.esperto
        b.modoEsperto.isChecked = esperto
        b.notaModo.text = if (esperto) {
            "Vedi tutto: la console del server, i comandi di LinuxGSM, i parametri " +
                    "tecnici, RCON e il ripristino dei file di configurazione."
        } else {
            "Vedi le cose che servono per far girare il server: accendere e spegnere, " +
                    "chi c'è, whitelist e ban, backup, mod, mappa, sicurezza e posta. " +
                    "Restano nascosti la console, i comandi di LinuxGSM, i parametri " +
                    "tecnici e RCON: si riaccendono da qui quando servono."
        }
        b.sezioneRcon.visible(esperto)
    }

    private fun fill(cfg: ServerConfig) {
        b.name.setText(cfg.name)
        b.slug.setText(cfg.slug)
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
        b.notifyEnabled.isChecked = cfg.notifyEnabled
        b.notifyOffline.isChecked = cfg.notifyOffline
        b.notifyOnline.isChecked = cfg.notifyOnline
        b.notifyJoin.isChecked = cfg.notifyJoin
        b.notifyLeave.isChecked = cfg.notifyLeave
        b.notifySeconds.setText(cfg.notifySeconds.toString())
        updateNotifyFields(cfg.notifyEnabled)
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
        // Il nome tecnico finisce in un percorso: solo caratteri innocui.
        slug = b.slug.text?.toString()?.trim()?.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            ?.ifBlank { null } ?: Prefs.load().slug,
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
        notifyEnabled = b.notifyEnabled.isChecked,
        notifyOffline = b.notifyOffline.isChecked,
        notifyOnline = b.notifyOnline.isChecked,
        notifyJoin = b.notifyJoin.isChecked,
        notifyLeave = b.notifyLeave.isChecked,
        // Sotto i 30 secondi si tempesterebbe il server senza guadagnare nulla.
        notifySeconds = (b.notifySeconds.text?.toString()?.trim()?.toIntOrNull() ?: 60).coerceIn(30, 3600),
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

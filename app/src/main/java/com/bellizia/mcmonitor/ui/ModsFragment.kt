package com.bellizia.mcmonitor.ui

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.ModRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.FragmentModsBinding
import com.bellizia.mcmonitor.databinding.ItemModBinding
import com.bellizia.mcmonitor.databinding.ItemModrinthBinding
import com.bellizia.mcmonitor.lgsm.InstalledMod
import com.bellizia.mcmonitor.lgsm.ServerEnvironment
import com.bellizia.mcmonitor.mods.ModFile
import com.bellizia.mcmonitor.mods.ModProject
import com.bellizia.mcmonitor.mods.Modrinth
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scheda "Mod": cosa è installato sul server, ricerca su Modrinth e installazione.
 * Il download lo fa il server, non il telefono.
 */
class ModsFragment : Fragment() {

    private var _b: FragmentModsBinding? = null
    private val b get() = _b!!

    private var environment: ServerEnvironment? = null
    private val dayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentModsBinding.inflate(inflater, container, false)
        return b.root
    }

    /** Il file .mrpack lo sceglie l'utente con il selettore di sistema. */
    private val pickPack = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadPack(uri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.swipe.setOnRefreshListener { refresh() }
        b.btnSearch.setOnClickListener { search() }
        b.btnPack.setOnClickListener {
            if (configured()) pickPack.launch(arrayOf("*/*"))
        }
        b.btnInstallLoader.setOnClickListener { installLoader() }
        b.query.setOnEditorActionListener { _, _, _ -> search(); true }
        b.btnRestart.setOnClickListener {
            confirm("Riavviare il server?", "I giocatori online verranno disconnessi.") {
                run("Riavvio in corso…") { McRepository.restart() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.load().isComplete && environment == null) refresh()
    }

    private fun refresh() {
        if (!Prefs.load().isComplete) {
            b.swipe.isRefreshing = false
            b.environment.text = "Server non configurato."
            return
        }
        b.swipe.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val env = runCatching { ModRepository.environment() }
            val mods = runCatching { ModRepository.installed() }
            val bind = _b ?: return@launch

            env.onSuccess { e ->
                environment = e
                bind.environment.text = buildString {
                    append("Minecraft ${e.minecraftVersion ?: "versione ignota"}")
                    append(" · ${e.loaderLabel}")
                    append(if (e.hasModsDir) " · cartella mods presente" else " · nessuna cartella mods")
                }
                bind.btnInstallLoader.visible(e.isVanilla)
                bind.warning.visible(e.isVanilla || !e.hasModsDir)
                bind.warning.text = when {
                    e.isVanilla -> "Questo è un server vanilla: i mod non verranno caricati finché " +
                            "non installi un mod loader (Fabric, Forge o NeoForge). Puoi comunque " +
                            "scaricarli: resteranno pronti nella cartella mods."
                    !e.hasModsDir -> "La cartella mods non esiste ancora: verrà creata alla prima installazione."
                    else -> ""
                }
                // I campi restano modificabili: il rilevamento è un punto di partenza.
                if (bind.gameVersion.text.isNullOrBlank()) bind.gameVersion.setText(e.minecraftVersion.orEmpty())
                if (bind.loader.text.isNullOrBlank() && !e.isVanilla) bind.loader.setText(e.loader, false)
            }.onFailure {
                bind.environment.text = "Rilevamento fallito: ${it.userMessage()}"
            }

            mods.onSuccess { renderInstalled(it) }
                .onFailure {
                    bind.installedTitle.text = "Mod installati — errore"
                    bind.installedList.removeAllViews()
                }
            bind.swipe.isRefreshing = false
        }
    }

    private fun renderInstalled(mods: List<InstalledMod>) {
        val bind = _b ?: return
        bind.installedTitle.text = "Mod installati (${mods.count { it.enabled }} attivi di ${mods.size})"
        bind.installedList.removeAllViews()
        if (mods.isEmpty()) {
            val row = ItemModBinding.inflate(layoutInflater, bind.installedList, false)
            row.name.text = "nessun mod nella cartella"
            row.subtitle.visible(false)
            row.btnToggle.visible(false)
            row.btnRemove.visible(false)
            bind.installedList.addView(row.root)
            return
        }
        mods.forEach { mod ->
            val row = ItemModBinding.inflate(layoutInflater, bind.installedList, false)
            row.name.text = mod.name
            row.subtitle.text = buildString {
                mod.version?.let { append("v$it · ") }
                append(mod.sizeLabel)
                if (mod.modifiedEpoch > 0) append(" · ${dayFormat.format(Date(mod.modifiedEpoch * 1000))}")
                if (!mod.enabled) append(" · DISATTIVATO")
            }
            row.btnToggle.text = if (mod.enabled) "Disattiva" else "Attiva"
            row.btnToggle.setOnClickListener {
                run("${mod.name}: aggiornamento…") { ModRepository.toggle(mod) }
            }
            row.btnRemove.setOnClickListener {
                confirm("Rimuovere ${mod.name}?", "Il file ${mod.fileName} verrà cancellato dal server.") {
                    run("Rimozione di ${mod.name}…") { ModRepository.remove(mod) }
                }
            }
            bind.installedList.addView(row.root)
        }
    }

    // ------------------------------------------------------------- ricerca

    private fun search() {
        val query = b.query.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) {
            toast("Scrivi il nome di un mod")
            return
        }
        b.searchProgress.visible(true)
        b.results.removeAllViews()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                Modrinth.search(query, gameVersion(), loader())
            }
            val bind = _b ?: return@launch
            bind.searchProgress.visible(false)
            result.onSuccess { renderResults(it) }
                .onFailure { showText("Ricerca fallita", it.userMessage()) }
        }
    }

    private fun renderResults(projects: List<ModProject>) {
        val bind = _b ?: return
        bind.results.removeAllViews()
        if (projects.isEmpty()) {
            val row = ItemModrinthBinding.inflate(layoutInflater, bind.results, false)
            row.title.text = "Nessun risultato"
            row.meta.text = "Prova senza filtro di versione o loader"
            row.description.visible(false)
            row.btnInstall.visible(false)
            bind.results.addView(row.root)
            return
        }
        projects.forEach { project ->
            val row = ItemModrinthBinding.inflate(layoutInflater, bind.results, false)
            row.title.text = project.title
            row.meta.text = "${project.author} · ${project.downloadsLabel} download"
            row.description.text = project.description
            row.btnInstall.setOnClickListener { chooseVersion(project) }
            row.root.setOnClickListener { chooseVersion(project) }
            bind.results.addView(row.root)
        }
    }

    private fun chooseVersion(project: ModProject) {
        b.searchProgress.visible(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { Modrinth.versions(project.id, gameVersion(), loader()) }
            val bind = _b ?: return@launch
            bind.searchProgress.visible(false)
            result.onFailure { showText("Versioni non disponibili", it.userMessage()) }
            val versions = result.getOrNull().orEmpty()
            if (versions.isEmpty()) {
                showText(
                    project.title,
                    "Nessuna versione compatibile con Minecraft ${gameVersion() ?: "?"} " +
                            "e loader ${loader() ?: "?"}.\n\nProva a cambiare i due campi in cima alla scheda."
                )
                return@launch
            }
            val labels = versions.take(20).map {
                "${it.versionNumber} · ${it.dateLabel} · ${it.gameVersions.joinToString(", ").take(40)}"
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(project.title)
                .setItems(labels.toTypedArray()) { _, which -> confirmInstall(project, versions[which]) }
                .setNegativeButton("Annulla", null)
                .show()
        }
    }

    private fun confirmInstall(project: ModProject, file: ModFile) {
        b.searchProgress.visible(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val deps = runCatching {
                ModRepository.requiredDependencies(file, gameVersion(), loader())
            }.getOrDefault(emptyList())
            val bind = _b ?: return@launch
            bind.searchProgress.visible(false)

            val message = buildString {
                append("File: ${file.fileName}\n")
                append("Verrà scaricato dal server da cdn.modrinth.com e verificato con sha1.")
                if (deps.isNotEmpty()) {
                    append("\n\nRichiede anche:\n")
                    deps.forEach { (title, dep) -> append("· $title ${dep.versionNumber}\n") }
                }
            }
            val builder = MaterialAlertDialogBuilder(requireContext())
                .setTitle("Installare ${project.title} ${file.versionNumber}?")
                .setMessage(message)
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Installa") { _, _ -> install(listOf(file)) }
            if (deps.isNotEmpty()) {
                builder.setNeutralButton("Con le dipendenze") { _, _ ->
                    install(listOf(file) + deps.map { it.second })
                }
            }
            builder.show()
        }
    }

    private fun install(files: List<ModFile>) {
        run("Installazione di ${files.size} file…") {
            // Un file per volta: se una dipendenza fallisce si vede quale.
            buildString {
                files.forEach { file ->
                    append(ModRepository.install(file))
                    append('\n')
                }
            }.trim()
        }
    }

    /** Su un server vanilla i mod non partono: qui si installa Fabric. */
    private fun installLoader() {
        val version = gameVersion() ?: environment?.minecraftVersion
        if (version.isNullOrBlank()) {
            showText(
                "Versione sconosciuta",
                "Scrivi la versione di Minecraft nel campo in cima alla scheda: serve per " +
                        "scegliere il Fabric giusto."
            )
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Installare Fabric per Minecraft $version?")
            .setMessage(
                "Il server scaricherà il jar di avvio di Fabric da meta.fabricmc.net e la " +
                        "riga startparameters di LinuxGSM verrà modificata per usarlo (con copia " +
                        "di sicurezza del file). Il mondo non viene toccato, ma il server va " +
                        "riavviato per applicare il cambio."
            )
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Installa") { _, _ -> runLoaderInstall(version) }
            .show()
    }

    private fun runLoaderInstall(version: String) {
        val view = TextView(requireContext()).apply {
            setPadding(48, 32, 48, 16)
            textSize = 12f
            setTextIsSelectable(true)
            text = "Avvio…"
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Installazione di Fabric")
            .setView(ScrollView(requireContext()).apply { addView(view) })
            .setCancelable(false)
            .setPositiveButton("Chiudi", null)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val report = runCatching {
                ModRepository.installLoader(version) { step -> view.text = step }
            }.getOrElse { "Installazione interrotta:\n${it.userMessage()}" }
            view.text = report
            dialog.setCancelable(true)
            _b?.btnRestart?.visible(true)
            refresh()
        }
    }

    // -------------------------------------------------------------- modpack

    private fun loadPack(uri: Uri) {
        val name = displayName(uri)
        if (!name.endsWith(".mrpack", ignoreCase = true)) {
            showText(
                "File non compatibile",
                "Serve un pacchetto .mrpack esportato da Modrinth.\n\nHai scelto: $name"
            )
            return
        }
        b.swipe.isRefreshing = true
        toast("Carico $name sul server…")
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val stream = requireContext().contentResolver.openInputStream(uri)
                    ?: error("file non leggibile")
                stream.use { ModRepository.uploadPack(it, name) }
            }
            val bind = _b ?: return@launch
            bind.swipe.isRefreshing = false
            result.onSuccess { confirmPack(it) }
                .onFailure { showText("Modpack non caricato", it.userMessage()) }
        }
    }

    private fun confirmPack(pack: com.bellizia.mcmonitor.mods.Modpack) {
        val env = environment
        val mismatch = pack.mismatch(env?.minecraftVersion, env?.loader)
        val serverFiles = pack.serverFiles
        val message = buildString {
            append("Minecraft ${pack.minecraftVersion ?: "?"}")
            pack.loader?.let { append(" · $it ${pack.loaderVersion.orEmpty()}") }
            append("\n\n${serverFiles.size} file da scaricare")
            if (serverFiles.size != pack.files.size) {
                append(" (${pack.files.size - serverFiles.size} solo per il client, esclusi)")
            }
            append("\nPiù le configurazioni contenute nel pacchetto.")
            if (mismatch != null) {
                append("\n\nATTENZIONE\n$mismatch\n")
                append("I mod verranno copiati lo stesso, ma il server non li caricherà finché ")
                append("versione e loader non coincidono.")
            }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(pack.label.ifBlank { "Modpack" })
            .setMessage(message)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Installa") { _, _ -> applyPack(pack) }
            .show()
    }

    private fun applyPack(pack: com.bellizia.mcmonitor.mods.Modpack) {
        val view = TextView(requireContext()).apply {
            setPadding(48, 32, 48, 16)
            text = "Avvio…"
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Installazione di ${pack.label}")
            .setView(view)
            .setCancelable(false)
            .setPositiveButton("Chiudi", null)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                ModRepository.applyPack(pack) { step -> view.text = step }
            }
            view.text = result.getOrElse { "Installazione interrotta:\n${it.userMessage()}" }
            dialog.setCancelable(true)
            _b?.btnRestart?.visible(true)
            refresh()
        }
    }

    private fun displayName(uri: Uri): String {
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index) ?: ""
        }
        return uri.lastPathSegment.orEmpty()
    }

    // -------------------------------------------------------------- utilita'

    private fun gameVersion() = b.gameVersion.text?.toString()?.trim()?.takeIf { it.isNotBlank() }

    private fun loader() = b.loader.text?.toString()?.trim()?.takeIf { it.isNotBlank() }

    /** Esegue un'operazione sul server e poi ricarica lo stato della cartella mods. */
    private fun run(progress: String, block: suspend () -> String) {
        if (!configured()) return
        b.swipe.isRefreshing = true
        toast(progress)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { block() }
            val bind = _b ?: return@launch
            bind.swipe.isRefreshing = false
            result.onSuccess {
                bind.btnRestart.visible(true)
                showText("Fatto", it.ifBlank { "operazione completata" })
                refresh()
            }.onFailure { showText("Operazione fallita", it.userMessage()) }
        }
    }

    private fun showText(title: String, message: String) {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Chiudi", null)
            .show()
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

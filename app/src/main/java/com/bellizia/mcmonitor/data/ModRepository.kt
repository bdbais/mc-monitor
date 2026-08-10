package com.bellizia.mcmonitor.data

import com.bellizia.mcmonitor.lgsm.InstalledMod
import com.bellizia.mcmonitor.lgsm.Lgsm
import com.bellizia.mcmonitor.lgsm.Mods
import com.bellizia.mcmonitor.lgsm.ServerEnvironment
import com.bellizia.mcmonitor.mods.FabricMeta
import com.bellizia.mcmonitor.mods.ModFile
import com.bellizia.mcmonitor.mods.Modpack
import com.bellizia.mcmonitor.mods.Modrinth
import com.bellizia.mcmonitor.ssh.SshException
import com.bellizia.mcmonitor.ssh.SshManager
import java.io.InputStream

/** Lettura e scrittura della cartella mods del server. */
object ModRepository {

    private fun cfg() = Prefs.load()

    suspend fun environment(): ServerEnvironment {
        val c = cfg()
        val r = SshManager.exec(c, Mods.detectEnvironment(c), 30_000)
        return Mods.parseEnvironment(r.text, c)
    }

    suspend fun installed(): List<InstalledMod> {
        val c = cfg()
        return Mods.parseMods(SshManager.exec(c, Mods.listMods(c), 30_000).text)
    }

    /**
     * Scarica il mod sul server e ne verifica lo sha1. Il download non passa dal
     * telefono: è il server a contattare la CDN di Modrinth.
     */
    suspend fun install(file: ModFile): String {
        val c = cfg()
        if (file.url.isBlank() || file.sha1.isBlank()) {
            throw SshException("Modrinth non ha fornito URL o hash per questa versione.")
        }
        if (!Mods.isTrustedUrl(file.url)) {
            throw SshException("Il file non arriva dalla CDN di Modrinth: installazione annullata.")
        }
        if (!Mods.safeFileName(file.fileName)) {
            throw SshException("Nome file non ammesso: ${file.fileName}")
        }

        val r = SshManager.exec(c, Mods.install(c, file.url, file.fileName, file.sha1), 300_000)
        val text = Lgsm.clean(r.text).trim()
        when (r.exitCode) {
            0 -> return text
            Mods.EXIT_NO_TOOL -> throw SshException(
                "Sul server mancano sia curl sia wget: installane uno per scaricare i mod."
            )
            Mods.EXIT_HASH_MISMATCH -> throw SshException(
                "Il file scaricato non corrisponde all'hash dichiarato da Modrinth ed è stato " +
                        "eliminato.\n\n$text"
            )
            Mods.EXIT_DOWNLOAD_FAILED -> throw SshException(
                "Download fallito: il server non raggiunge cdn.modrinth.com.\n\n$text"
            )
            else -> throw SshException(text.ifBlank { "Installazione fallita (uscita ${r.exitCode})" })
        }
    }

    suspend fun remove(mod: InstalledMod): String {
        val c = cfg()
        val r = SshManager.exec(c, Mods.remove(c, mod.fileName), 30_000)
        if (!r.ok) throw SshException(Lgsm.clean(r.text).trim().ifBlank { "Rimozione fallita" })
        return Lgsm.clean(r.text).trim()
    }

    suspend fun toggle(mod: InstalledMod): String {
        val c = cfg()
        val r = SshManager.exec(c, Mods.toggle(c, mod.fileName), 30_000)
        if (!r.ok) throw SshException(Lgsm.clean(r.text).trim().ifBlank { "Operazione fallita" })
        return Lgsm.clean(r.text).trim()
    }

    /**
     * Installa Fabric: scarica il jar di avvio e fa puntare LinuxGSM a quello.
     * Il download lo fa il server con curl, come per i mod.
     */
    suspend fun installLoader(gameVersion: String, onStep: (String) -> Unit): String {
        val c = cfg()
        val report = StringBuilder()
        fun log(line: String) {
            report.append(line).append('\n')
            onStep(report.toString())
        }

        log("1/3 · Cerco l'ultima versione stabile di Fabric per $gameVersion…")
        val build = FabricMeta.latestBuild(gameVersion)
        log("     ${build.label} (installer ${build.installerVersion})")

        log("\n2/3 · Il server scarica il jar di avvio…")
        val download = SshManager.exec(c, Mods.installLoader(c, build.serverJarUrl), 300_000)
        val downloadText = Lgsm.clean(download.text).trim()
        if (!download.ok) {
            log("     FALLITO: $downloadText")
            return report.toString()
        }
        log("     $downloadText")

        log("\n3/3 · Configuro LinuxGSM per avviare Fabric…")
        val patch = SshManager.exec(c, Mods.useLoaderInConfig(c), 45_000)
        Lgsm.clean(patch.text).trim().lineSequence().forEach { log("     $it") }
        if (!patch.ok) {
            log("\nIl jar è pronto ma la riga di avvio non è stata modificata: " +
                    "controlla startparameters nella configurazione LinuxGSM.")
            return report.toString()
        }

        log("\nFatto. Riavvia il server per partire con Fabric.")
        return report.toString()
    }

    // ------------------------------------------------------------- modpack

    private var uploadedPack: String? = null

    /**
     * Carica un .mrpack sul server e ne legge l'indice, senza installare nulla:
     * prima di toccare la cartella mods l'utente deve poter vedere cosa contiene.
     */
    suspend fun uploadPack(input: InputStream, fileName: String): Modpack {
        val c = cfg()
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._+-]"), "_").ifBlank { "modpack.mrpack" }

        val prepare = SshManager.exec(c, Mods.preparePackDir(c), 30_000)
        if (!prepare.ok) {
            throw SshException(Lgsm.clean(prepare.text).trim().ifBlank { "Preparazione fallita" })
        }

        SshManager.upload(c, input, "${Mods.packDir(c)}/$safeName")
        uploadedPack = safeName

        val index = SshManager.exec(c, Mods.readPackIndex(c, safeName), 60_000)
        if (!index.ok) {
            throw SshException(Lgsm.clean(index.text).trim().ifBlank { "Indice del modpack illeggibile" })
        }
        return runCatching { Modpack.parse(index.stdout) }
            .getOrElse { throw SshException("modrinth.index.json non interpretabile: ${it.message}") }
    }

    /**
     * Installa il pacchetto: scarica i file elencati nell'indice e copia gli
     * overrides. [onStep] riceve l'avanzamento file per file.
     */
    suspend fun applyPack(pack: Modpack, onStep: (String) -> Unit): String {
        val c = cfg()
        val packName = uploadedPack ?: throw SshException("Nessun modpack caricato.")
        val files = pack.serverFiles
        val report = StringBuilder()
        var installed = 0
        val failed = mutableListOf<String>()

        files.forEachIndexed { index, file ->
            onStep("Scarico ${index + 1} di ${files.size}: ${file.path.substringAfterLast('/')}")
            if (!Mods.isTrustedUrl(file.url) || !Mods.safeRelativePath(file.path)) {
                failed += "${file.path} (percorso o URL non ammesso)"
                return@forEachIndexed
            }
            val r = runCatching {
                SshManager.exec(c, Mods.installPackFile(c, file.url, file.path, file.sha1), 300_000)
            }.getOrNull()
            if (r != null && r.ok) installed++ else failed += file.path
        }

        onStep("Copio configurazioni e mod inclusi nel pacchetto…")
        val overrides = runCatching { SshManager.exec(c, Mods.extractOverrides(c, packName), 300_000) }
            .getOrNull()
        report.append("Installati $installed file di ${files.size}.\n")
        overrides?.let { report.append(Lgsm.clean(it.text).trim()).append('\n') }
        if (failed.isNotEmpty()) {
            report.append("\nNon riusciti:\n").append(failed.joinToString("\n") { "· $it" })
        }

        runCatching { SshManager.exec(c, Mods.cleanupPack(c), 30_000) }
        uploadedPack = null
        return report.toString().trim()
    }

    /**
     * Dipendenze obbligatorie di una versione, già risolte nella versione
     * compatibile più recente: senza Fabric API metà dei mod non parte.
     */
    suspend fun requiredDependencies(
        file: ModFile,
        gameVersion: String?,
        loader: String?
    ): List<Pair<String, ModFile>> = file.requiredDependencies.mapNotNull { projectId ->
        val title = runCatching { Modrinth.projectTitle(projectId) }.getOrDefault(projectId)
        val version = runCatching { Modrinth.versions(projectId, gameVersion, loader).firstOrNull() }
            .getOrNull() ?: return@mapNotNull null
        title to version
    }
}

package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.update.UpdateChecker
import org.json.JSONObject

/**
 * Aggiornamento di LinuxGSM stesso, non del gioco.
 *
 * LinuxGSM è un insieme di script che vive dentro la cartella del server: quando
 * resta indietro, comandi come `update` o `details` smettono di funzionare bene
 * con le versioni nuove di Minecraft, e l'errore che ne esce non dice mai che la
 * causa è quella. Il controllo confronta la versione scritta nello script con
 * l'ultima pubblicata, e l'aggiornamento è il comando che LinuxGSM offre da sé.
 */
object LgsmUpdate {

    const val LATEST_URL =
        "https://api.github.com/repos/GameServerManagers/LinuxGSM/releases/latest"

    /** Dal JSON di GitHub serve solo l'etichetta della release, tipo "v23.5.3". */
    fun parseTag(json: String): String? = runCatching {
        JSONObject(json).optString("tag_name").trim().takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * Indietro solo se sappiamo entrambe le versioni e quella installata è
     * davvero più vecchia: nel dubbio non si segnala niente, perché un avviso
     * sbagliato spinge ad aggiornare un server che sta benissimo.
     */
    fun outdated(installed: String?, latest: String?): Boolean {
        if (installed.isNullOrBlank() || latest.isNullOrBlank()) return false
        return UpdateChecker.isNewer(latest, installed)
    }

    /** Le istanze da aggiornare, nell'ordine in cui sono elencate. */
    fun outdatedServers(servers: List<DiscoveredServer>, latest: String?): List<DiscoveredServer> =
        servers.filter { outdated(it.lgsmVersion, latest) }

    /**
     * `update-lgsm` aggiorna gli script; le versioni più vecchie lo chiamavano
     * `update-functions`, quindi si prova il secondo se il primo non esiste.
     */
    fun updateCommand(server: DiscoveredServer): String {
        val dir = Lgsm.path(server.directory)
        val script = "./${Lgsm.sq(server.script)}"
        return "cd $dir && ($script update-lgsm 2>&1 || $script update-functions 2>&1)"
    }

    /** Riepilogo per l'avviso: quante istanze e da quale versione a quale. */
    fun banner(outdated: List<DiscoveredServer>, latest: String?): String {
        if (outdated.isEmpty() || latest == null) return ""
        val versions = outdated.mapNotNull { it.lgsmVersion }.distinct()
        val from = if (versions.size == 1) versions.first() else "versioni diverse"
        val quanti = if (outdated.size == 1) {
            "Il server ${outdated.first().displayName} usa"
        } else {
            "${outdated.size} server usano"
        }
        return "$quanti una versione vecchia di LinuxGSM ($from → $latest). " +
                "Aggiornarla non tocca il mondo né le mod."
    }
}

package com.bellizia.mcmonitor.data

import com.bellizia.mcmonitor.lgsm.Blueprint
import com.bellizia.mcmonitor.lgsm.Blueprints
import com.bellizia.mcmonitor.lgsm.GameVersion
import com.bellizia.mcmonitor.lgsm.Lgsm
import com.bellizia.mcmonitor.lgsm.ServerParams
import com.bellizia.mcmonitor.mods.Modrinth
import com.bellizia.mcmonitor.ssh.SshException
import com.bellizia.mcmonitor.ssh.SshManager

/** Cosa applicare del progetto ricevuto: si spunta prima, non si scopre dopo. */
data class BlueprintChoice(
    val lgsm: Boolean = true,
    val properties: Boolean = true,
    val mods: Boolean = true,
    val players: Boolean = false
)

/** Legge il progetto di questo server e applica quello di un altro. */
object BlueprintRepository {

    private fun cfg() = Prefs.load()

    /**
     * Raccoglie tutto in un colpo solo. La versione e il loader arrivano da
     * ModRepository, che li sa dedurre meglio di quanto si legga dal file.
     */
    suspend fun read(includePlayers: Boolean): Blueprint {
        val c = cfg()
        val r = SshManager.exec(c, Blueprints.read(c, includePlayers), 120_000)
        if (r.exitCode == Blueprints.EXIT_NO_CONFIG) {
            throw SshException("File di configurazione non trovato: ${GameVersion.configPath(c)}")
        }
        val env = runCatching { ModRepository.environment() }.getOrNull()
        return Blueprints.parse(r.text, c, env)
    }

    /**
     * Scrive il progetto su questo server, dicendo passo per passo cosa sta
     * facendo: sono decine di modifiche a file altrui e, se qualcosa va storto,
     * bisogna sapere dov'era arrivato.
     *
     * Ogni file toccato viene copiato prima, con la data nel nome.
     */
    suspend fun apply(
        blueprint: Blueprint,
        choice: BlueprintChoice,
        onStep: (String) -> Unit
    ): String {
        val c = cfg()
        var scritti = 0
        val problemi = mutableListOf<String>()

        if (choice.lgsm) {
            val da = Blueprints.transferable(blueprint.lgsm)
            onStep("Impostazioni di LinuxGSM: ${da.size} da scrivere")
            da.forEach { (chiave, valore) ->
                if (!ServerParams.isValidKey(chiave)) {
                    problemi += "$chiave: nome non ammesso, saltato"
                    return@forEach
                }
                val r = SshManager.exec(c, ServerParams.set(c, chiave, valore), 30_000)
                if (ServerParams.written(r.text)) {
                    scritti++
                    onStep("  $chiave = $valore")
                } else {
                    problemi += "$chiave: ${Lgsm.clean(r.text).trim().take(80)}"
                }
            }
        }

        if (choice.properties) {
            val da = Blueprints.transferable(blueprint.properties)
            onStep("Impostazioni di Minecraft: ${da.size} da scrivere")
            da.forEach { (chiave, valore) ->
                if (!Blueprints.isValidPropertyKey(chiave)) {
                    problemi += "$chiave: nome non ammesso, saltato"
                    return@forEach
                }
                val r = SshManager.exec(c, Blueprints.setProperty(c, chiave, valore), 30_000)
                if (Blueprints.written(r.text)) {
                    scritti++
                    onStep("  $chiave = $valore")
                } else {
                    problemi += "$chiave: ${Lgsm.clean(r.text).trim().take(80)}"
                }
            }
        }

        if (choice.mods && blueprint.mods.isNotEmpty()) {
            onStep("Mod: ne cerco ${blueprint.mods.size} su Modrinth dall'impronta")
            val trovati = runCatching { Modrinth.byHashes(blueprint.mods.map { it.sha1 }) }
                .getOrElse {
                    problemi += "Modrinth non raggiungibile: ${it.message}"
                    emptyMap()
                }
            val gia = runCatching { ModRepository.installed().map { it.jarName }.toSet() }
                .getOrDefault(emptySet())

            blueprint.mods.forEach { mod ->
                if (mod.fileName in gia) {
                    onStep("  ${mod.fileName}: c'era già")
                    return@forEach
                }
                val file = trovati[mod.sha1]
                if (file == null) {
                    // Un mod scaricato da GitHub o da CurseForge non ha un
                    // corrispondente su Modrinth: si dice quale è, e lo si mette
                    // a mano.
                    problemi += "${mod.fileName}: non è su Modrinth, va copiato a mano"
                    return@forEach
                }
                val esito = runCatching { ModRepository.install(file) }
                if (esito.isSuccess) {
                    scritti++
                    onStep("  ${file.fileName}: installato")
                } else {
                    problemi += "${mod.fileName}: ${esito.exceptionOrNull()?.message}"
                }
            }
        }

        if (choice.players) {
            val nomi = blueprint.whitelist + blueprint.ops
            onStep("Giocatori: ${nomi.size} da registrare")
            // whitelist e op passano dalla console: servono gli UUID, e a
            // procurarseli è il server, non l'app.
            blueprint.whitelist.forEach { nome ->
                val esito = runCatching { McRepository.send("whitelist add $nome") }
                if (esito.isSuccess) scritti++ else problemi += "whitelist $nome: ${esito.exceptionOrNull()?.message}"
            }
            blueprint.ops.forEach { nome ->
                val esito = runCatching { McRepository.send("op $nome") }
                if (esito.isSuccess) scritti++ else problemi += "op $nome: ${esito.exceptionOrNull()?.message}"
            }
        }

        return buildString {
            append("$scritti modifiche scritte.")
            if (problemi.isNotEmpty()) {
                append("\n\nRimaste indietro:\n")
                append(problemi.joinToString("\n") { "· $it" })
            }
            append("\n\nRiavvia il server perché le impostazioni valgano.")
        }
    }
}

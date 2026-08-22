package com.bellizia.mcmonitor.data

import com.bellizia.mcmonitor.lgsm.Blueprint
import com.bellizia.mcmonitor.lgsm.BlueprintMod
import org.json.JSONArray
import org.json.JSONObject

/**
 * Il progetto di un server, chiuso in un file da passare a qualcun altro.
 *
 * Serve a chi vuole rifare lo stesso server a casa propria: dentro ci sono le
 * impostazioni, l'elenco dei mod con la loro impronta e, se chi esporta ha detto
 * di sì, i nomi di chi può entrare. Non ci sono credenziali: quelle restano
 * nell'altro file, quello dei collegamenti.
 *
 * Cifrato lo stesso, e per un motivo diverso dai collegamenti: la password serve
 * a decidere chi può rifare il tuo server, non a nascondere una password.
 */
object BlueprintTransfer {

    const val MAGIC = "MCMPROGETTO1"

    fun export(blueprint: Blueprint, password: String): String {
        if (blueprint.isEmpty) throw TransferException("Non c'è niente da esportare in questo server.")

        val payload = JSONObject().apply {
            put("versione", 1)
            put("creato", System.currentTimeMillis())
            put("nome", blueprint.serverName)
            put("minecraft", blueprint.minecraft)
            put("loader", blueprint.loader)
            put("script", blueprint.script)
            put("lgsm", JSONObject(blueprint.lgsm))
            put("properties", JSONObject(blueprint.properties))
            put("mod", JSONArray().also { array ->
                blueprint.mods.forEach { mod ->
                    array.put(JSONObject().put("file", mod.fileName).put("sha1", mod.sha1))
                }
            })
            put("whitelist", JSONArray(blueprint.whitelist))
            put("ops", JSONArray(blueprint.ops))
        }.toString().toByteArray(Charsets.UTF_8)

        // Qui la compressione conta: fra impostazioni ripetute e nomi di mod il
        // testo si riduce di parecchio, e il file deve stare in un allegato.
        return SealedBox.seal(MAGIC, payload, password, compress = true)
    }

    fun import(content: String, password: String): Blueprint {
        val plain = SealedBox.open(
            magic = MAGIC,
            content = content,
            password = password,
            wrongKind = "Questo è un file di collegamenti, non il progetto di un server: " +
                    "si importa dalle Impostazioni."
        )

        val o = runCatching { JSONObject(String(plain, Charsets.UTF_8)) }
            .getOrElse { throw TransferException("Contenuto illeggibile dopo la decifratura.") }

        val progetto = Blueprint(
            serverName = o.optString("nome"),
            minecraft = o.optString("minecraft"),
            loader = o.optString("loader", "vanilla"),
            script = o.optString("script", "mcserver"),
            lgsm = mappa(o.optJSONObject("lgsm")),
            properties = mappa(o.optJSONObject("properties")),
            mods = elencoMod(o.optJSONArray("mod")),
            whitelist = elenco(o.optJSONArray("whitelist")),
            ops = elenco(o.optJSONArray("ops")),
            createdAt = o.optLong("creato")
        )
        if (progetto.isEmpty) throw TransferException("Il file non contiene nessuna configurazione.")
        return progetto
    }

    private fun mappa(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        return o.keys().asSequence().associateWith { o.optString(it) }
    }

    private fun elenco(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }
    }

    private fun elencoMod(a: JSONArray?): List<BlueprintMod> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val file = o.optString("file")
            val sha1 = o.optString("sha1").lowercase()
            if (file.isBlank() || !sha1.matches(Regex("^[a-f0-9]{40}$"))) return@mapNotNull null
            BlueprintMod(file, sha1)
        }
    }
}

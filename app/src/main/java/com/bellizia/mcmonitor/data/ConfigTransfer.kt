package com.bellizia.mcmonitor.data

import org.json.JSONArray
import org.json.JSONObject

class TransferException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Esporta e importa la configurazione dei server in un file cifrato con una
 * password.
 *
 * Il file contiene credenziali SSH e RCON: viaggia su chat e email, quindi è
 * chiuso da [SealedBox]. Non è compresso perché il formato è nato così e le
 * versioni precedenti dell'app devono continuare a leggere i backup di questa.
 */
object ConfigTransfer {

    const val MAGIC = "MCMONITOR1"

    fun export(servers: List<ServerConfig>, password: String): String {
        if (servers.isEmpty()) throw TransferException("Nessun server da esportare.")

        val payload = JSONObject().apply {
            put("versione", 1)
            put("creato", System.currentTimeMillis())
            put("servers", JSONArray().also { array -> servers.forEach { array.put(it.toJson()) } })
        }.toString().toByteArray(Charsets.UTF_8)

        return SealedBox.seal(MAGIC, payload, password)
    }

    /** Restituisce i server contenuti nel file, senza inserirli: decide il chiamante. */
    fun import(content: String, password: String): List<ServerConfig> {
        val plain = SealedBox.open(
            magic = MAGIC,
            content = content,
            password = password,
            wrongKind = "Questo è il progetto di un server, non un file di collegamenti: " +
                    "si importa dalla scheda Stato, con il pulsante Progetto."
        )

        val payload = runCatching { JSONObject(String(plain, Charsets.UTF_8)) }
            .getOrElse { throw TransferException("Contenuto illeggibile dopo la decifratura.") }
        val array = payload.optJSONArray("servers") ?: JSONArray()
        val servers = (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { ServerConfig.fromJson(it) }
        }
        if (servers.isEmpty()) throw TransferException("Il file non contiene nessun server.")
        return servers
    }
}

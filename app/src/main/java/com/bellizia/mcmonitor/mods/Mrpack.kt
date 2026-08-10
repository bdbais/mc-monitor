package com.bellizia.mcmonitor.mods

import org.json.JSONObject

/** Un file elencato nell'indice di un modpack Modrinth. */
data class PackFile(
    val path: String,
    val url: String,
    val sha1: String,
    val sizeBytes: Long,
    val serverSide: String
) {
    /** I file marcati "unsupported" lato server sono roba solo per il client. */
    val neededOnServer: Boolean get() = serverSide != "unsupported"
}

/**
 * Contenuto di `modrinth.index.json`, il manifesto dentro un file .mrpack.
 * Il pacchetto è uno zip: l'indice elenca i file da scaricare dalla CDN, mentre
 * la cartella `overrides/` contiene mod e configurazioni da copiare così come sono.
 */
data class Modpack(
    val name: String,
    val versionId: String,
    val minecraftVersion: String?,
    val loader: String?,
    val loaderVersion: String?,
    val files: List<PackFile>
) {
    val serverFiles: List<PackFile> get() = files.filter { it.neededOnServer }

    val label: String get() = listOfNotNull(name.ifBlank { null }, versionId.ifBlank { null }).joinToString(" ")

    /** Il pacchetto è pensato per un altro server se versione o loader non coincidono. */
    fun mismatch(serverVersion: String?, serverLoader: String?): String? {
        val problems = buildList {
            if (!minecraftVersion.isNullOrBlank() && !serverVersion.isNullOrBlank() &&
                minecraftVersion != serverVersion
            ) {
                add("il modpack è per Minecraft $minecraftVersion, il server è $serverVersion")
            }
            if (!loader.isNullOrBlank() && !serverLoader.isNullOrBlank() && loader != serverLoader) {
                add("il modpack richiede $loader, il server risulta $serverLoader")
            }
        }
        return problems.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    companion object {

        private val LOADER_KEYS = mapOf(
            "fabric-loader" to "fabric",
            "forge" to "forge",
            "neoforge" to "neoforge",
            "quilt-loader" to "quilt"
        )

        fun parse(json: String): Modpack {
            val root = JSONObject(json)
            val dependencies = root.optJSONObject("dependencies") ?: JSONObject()
            val loaderKey = LOADER_KEYS.keys.firstOrNull { dependencies.has(it) }

            val filesArray = root.optJSONArray("files")
            val files = (0 until (filesArray?.length() ?: 0)).mapNotNull { i ->
                val o = filesArray?.optJSONObject(i) ?: return@mapNotNull null
                val downloads = o.optJSONArray("downloads")
                val url = (0 until (downloads?.length() ?: 0))
                    .mapNotNull { downloads?.optString(it) }
                    .firstOrNull { it.isNotBlank() } ?: return@mapNotNull null
                PackFile(
                    path = o.optString("path"),
                    url = url,
                    sha1 = o.optJSONObject("hashes")?.optString("sha1").orEmpty(),
                    sizeBytes = o.optLong("fileSize"),
                    serverSide = o.optJSONObject("env")?.optString("server").orEmpty()
                )
            }

            return Modpack(
                name = root.optString("name"),
                versionId = root.optString("versionId"),
                minecraftVersion = dependencies.optString("minecraft").takeIf { it.isNotBlank() },
                loader = loaderKey?.let { LOADER_KEYS[it] },
                loaderVersion = loaderKey?.let { dependencies.optString(it) }?.takeIf { it.isNotBlank() },
                files = files
            )
        }
    }
}

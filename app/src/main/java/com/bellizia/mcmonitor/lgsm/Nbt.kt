package com.bellizia.mcmonitor.lgsm

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * Un pezzo di NBT letto da un file di Minecraft.
 *
 * Ce n'è uno per ogni tipo che il formato prevede. Non è una gerarchia elegante:
 * è quello che serve per tirare fuori un inventario senza portarsi dietro una
 * libreria intera.
 */
sealed interface Tag {
    data class Numero(val valore: Long) : Tag
    data class Decimale(val valore: Double) : Tag
    data class Testo(val valore: String) : Tag
    data class Byte(val valori: ByteArray) : Tag {
        override fun equals(other: Any?) = other is Byte && valori.contentEquals(other.valori)
        override fun hashCode() = valori.contentHashCode()
    }

    data class Numeri(val valori: LongArray) : Tag {
        override fun equals(other: Any?) = other is Numeri && valori.contentEquals(other.valori)
        override fun hashCode() = valori.contentHashCode()
    }

    data class Elenco(val voci: List<Tag>) : Tag
    data class Gruppo(val campi: Map<String, Tag>) : Tag
}

/** Scorciatoie per leggere un gruppo senza scrivere ogni volta lo stesso `as?`. */
fun Tag.Gruppo.numero(nome: String): Long? = (campi[nome] as? Tag.Numero)?.valore
fun Tag.Gruppo.decimale(nome: String): Double? = when (val t = campi[nome]) {
    is Tag.Decimale -> t.valore
    is Tag.Numero -> t.valore.toDouble()
    else -> null
}

fun Tag.Gruppo.testo(nome: String): String? = (campi[nome] as? Tag.Testo)?.valore
fun Tag.Gruppo.elenco(nome: String): List<Tag> = (campi[nome] as? Tag.Elenco)?.voci.orEmpty()
fun Tag.Gruppo.gruppo(nome: String): Tag.Gruppo? = campi[nome] as? Tag.Gruppo

/**
 * Legge il formato NBT di Minecraft.
 *
 * Serve perché i dati di un giocatore — inventario, armatura, vita, esperienza —
 * stanno in `world/playerdata/<uuid>.dat`, che è NBT compresso con gzip. Non
 * esiste un modo di leggerlo con quello che c'è già in Android, e portarsi
 * dietro una libreria per un solo file sarebbe sproporzionato: il formato è
 * piccolo e non cambia da anni.
 *
 * Due cose su cui è facile sbagliare, e che qui sono esplicite:
 *
 * - **tutto è big-endian**, anche su un telefono che non lo è;
 * - **le stringhe sono in UTF-8 modificato**, che è quello che legge
 *   `DataInputStream.readUTF`: non è UTF-8 e basta, e usare il decoder normale
 *   rompe i nomi con caratteri fuori dall'ASCII.
 *
 * Il file arriva dal server come base64 e viene aperto qui: un file di
 * playerdata sono decine di kilobyte, non megabyte, quindi tenerlo in memoria
 * va bene. Il tetto c'è lo stesso, perché un file rotto o inventato non deve
 * poter far finire la memoria all'app.
 */
object Nbt {

    /** Oltre questo non si legge: un playerdata vero sta in poche decine di KB. */
    const val MAX_BYTE = 8 * 1024 * 1024

    /** Quanto in profondità può annidarsi: oltre, è un file costruito apposta. */
    private const val MAX_PROFONDITA = 64

    class NbtRotto(messaggio: String) : IOException(messaggio)

    /**
     * Apre un file NBT, compresso o no.
     *
     * Si guarda il numero magico del gzip invece di fidarsi del nome: LinuxGSM e
     * i backup passano i file di mano in mano, e un `.dat` non compresso capita.
     */
    fun leggi(byte: ByteArray): Tag.Gruppo {
        if (byte.isEmpty()) throw NbtRotto("file vuoto")
        if (byte.size > MAX_BYTE) throw NbtRotto("file troppo grande: ${byte.size} byte")

        val compresso = byte.size > 1 &&
                (byte[0].toInt() and 0xFF) == 0x1F &&
                (byte[1].toInt() and 0xFF) == 0x8B

        val dentro = if (compresso) {
            GZIPInputStream(ByteArrayInputStream(byte)).use { it.readBytes() }
        } else {
            byte
        }
        if (dentro.size > MAX_BYTE) throw NbtRotto("file troppo grande una volta aperto")

        DataInputStream(ByteArrayInputStream(dentro)).use { d ->
            val tipo = try {
                d.readByte().toInt()
            } catch (e: EOFException) {
                throw NbtRotto("file troncato")
            }
            if (tipo != 10) throw NbtRotto("non comincia con un gruppo (tipo $tipo)")
            d.readUTF() // il nome della radice, che in un playerdata è vuoto
            return leggiGruppo(d, 0)
        }
    }

    private fun leggiGruppo(d: DataInputStream, profondita: Int): Tag.Gruppo {
        if (profondita > MAX_PROFONDITA) throw NbtRotto("troppi livelli annidati")
        val campi = LinkedHashMap<String, Tag>()
        while (true) {
            val tipo = try {
                d.readByte().toInt()
            } catch (e: EOFException) {
                throw NbtRotto("gruppo non chiuso")
            }
            if (tipo == 0) return Tag.Gruppo(campi)
            val nome = d.readUTF()
            campi[nome] = leggiValore(d, tipo, profondita + 1)
        }
    }

    private fun leggiValore(d: DataInputStream, tipo: Int, profondita: Int): Tag {
        if (profondita > MAX_PROFONDITA) throw NbtRotto("troppi livelli annidati")
        return when (tipo) {
            1 -> Tag.Numero(d.readByte().toLong())
            2 -> Tag.Numero(d.readShort().toLong())
            3 -> Tag.Numero(d.readInt().toLong())
            4 -> Tag.Numero(d.readLong())
            5 -> Tag.Decimale(d.readFloat().toDouble())
            6 -> Tag.Decimale(d.readDouble())
            7 -> Tag.Byte(ByteArray(quanti(d)).also { d.readFully(it) })
            8 -> Tag.Testo(d.readUTF())
            9 -> {
                val tipoVoci = d.readByte().toInt()
                val n = quanti(d)
                // Un elenco vuoto ha tipo 0: è normale, non è un errore.
                if (n == 0) return Tag.Elenco(emptyList())
                Tag.Elenco(List(n) { leggiValore(d, tipoVoci, profondita + 1) })
            }
            10 -> leggiGruppo(d, profondita)
            11 -> Tag.Numeri(LongArray(quanti(d)) { d.readInt().toLong() })
            12 -> Tag.Numeri(LongArray(quanti(d)) { d.readLong() })
            else -> throw NbtRotto("tipo sconosciuto: $tipo")
        }
    }

    /**
     * La lunghezza di un elenco o di un array.
     *
     * Va controllata prima di allocare: un file costruito apposta può dichiarare
     * due miliardi di voci, e `List(n)` ci proverebbe.
     */
    private fun quanti(d: DataInputStream): Int {
        val n = d.readInt()
        if (n < 0) throw NbtRotto("lunghezza negativa: $n")
        if (n > MAX_BYTE) throw NbtRotto("lunghezza assurda: $n")
        return n
    }
}

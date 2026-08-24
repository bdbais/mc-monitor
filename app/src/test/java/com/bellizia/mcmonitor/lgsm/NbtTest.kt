package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Il lettore NBT, provato contro file scritti byte per byte.
 *
 * Non c'è un file di esempio da copiare: lo si costruisce qui con le stesse
 * regole con cui lo scrive Minecraft (tutto big-endian, stringhe in UTF-8
 * modificato), così quello che si prova è il formato e non un file fortunato.
 */
class NbtTest {

    // ------------------------------------------------- come si scrive un NBT

    private class Scrittore {
        val out = ByteArrayOutputStream()
        val d = DataOutputStream(out)

        fun apriRadice() {
            d.writeByte(10)
            d.writeUTF("")
        }

        fun campo(tipo: Int, nome: String) {
            d.writeByte(tipo)
            d.writeUTF(nome)
        }

        fun byte(nome: String, v: Int) = campo(1, nome).also { d.writeByte(v) }
        fun intero(nome: String, v: Int) = campo(3, nome).also { d.writeInt(v) }
        fun decimale(nome: String, v: Float) = campo(5, nome).also { d.writeFloat(v) }
        fun testo(nome: String, v: String) = campo(8, nome).also { d.writeUTF(v) }
        fun fine() = d.writeByte(0)

        fun byteArray(compresso: Boolean = false): ByteArray {
            fine()
            val grezzo = out.toByteArray()
            if (!compresso) return grezzo
            val z = ByteArrayOutputStream()
            GZIPOutputStream(z).use { it.write(grezzo) }
            return z.toByteArray()
        }
    }

    private fun oggetto(s: Scrittore, casella: Int, id: String, quanti: Int) {
        // Dentro un elenco le voci sono solo il contenuto: niente byte del tipo
        // e niente nome, perché il tipo è dichiarato una volta sola all'inizio
        // dell'elenco. Scriverlo lo stesso è il primo errore che si fa.
        s.byte("Slot", casella)
        s.testo("id", id)
        s.byte("Count", quanti)
        s.fine()
    }

    private fun conInventario(compresso: Boolean, vararg voci: Triple<Int, String, Int>): ByteArray {
        val s = Scrittore()
        s.apriRadice()
        s.decimale("Health", 16f)
        s.intero("foodLevel", 18)
        s.intero("XpLevel", 30)
        s.intero("SelectedItemSlot", 2)
        s.testo("Dimension", "minecraft:the_nether")
        s.campo(9, "Inventory")
        s.d.writeByte(10)
        s.d.writeInt(voci.size)
        voci.forEach { (c, id, q) -> oggetto(s, c, id, q) }
        return s.byteArray(compresso)
    }

    // ------------------------------------------------------------ il formato

    @Test
    fun `legge un file compresso e uno no`() {
        // Un .dat vero è gzip, ma passando di mano in mano capita non compresso:
        // si guarda il numero magico invece di fidarsi del nome.
        listOf(true, false).forEach { compresso ->
            val g = Nbt.leggi(conInventario(compresso, Triple(0, "minecraft:stone", 64)))
            assertEquals(18L, g.numero("foodLevel"))
        }
    }

    @Test
    fun `i numeri sono big-endian`() {
        val s = Scrittore()
        s.apriRadice()
        s.intero("grande", 0x01020304)
        val g = Nbt.leggi(s.byteArray())
        assertEquals(0x01020304L, g.numero("grande"))
    }

    @Test
    fun `le stringhe con accenti non si rompono`() {
        // NBT usa UTF-8 modificato: leggerle con il decoder normale storpia
        // tutto quello che non è ASCII.
        val s = Scrittore()
        s.apriRadice()
        s.testo("nome", "Spada di Anna — perché sì ✦")
        assertEquals("Spada di Anna — perché sì ✦", Nbt.leggi(s.byteArray()).testo("nome"))
    }

    @Test
    fun `un elenco vuoto non e' un errore`() {
        val s = Scrittore()
        s.apriRadice()
        s.campo(9, "Inventory")
        s.d.writeByte(0) // tipo delle voci: 0, come lo scrive Minecraft
        s.d.writeInt(0)
        assertTrue(Nbt.leggi(s.byteArray()).elenco("Inventory").isEmpty())
    }

    // ------------------------------------------------- file rotti o cattivi

    @Test
    fun `un file vuoto non fa esplodere niente`() {
        runCatching { Nbt.leggi(ByteArray(0)) }.fold(
            onSuccess = { throw AssertionError("ha letto un file vuoto") },
            onFailure = { assertTrue(it is Nbt.NbtRotto) }
        )
    }

    @Test
    fun `un file troncato viene rifiutato`() {
        val intero = conInventario(false, Triple(0, "minecraft:stone", 1))
        runCatching { Nbt.leggi(intero.copyOfRange(0, intero.size / 2)) }.fold(
            onSuccess = { throw AssertionError("ha letto un file troncato") },
            onFailure = { assertTrue(it.toString(), it is Nbt.NbtRotto || it is java.io.IOException) }
        )
    }

    @Test
    fun `un elenco che dichiara due miliardi di voci non alloca niente`() {
        // Un file costruito apposta puo' dire quello che vuole: se ci si fida,
        // List(n) ci prova e l'app muore.
        val s = Scrittore()
        s.apriRadice()
        s.campo(9, "cattivo")
        s.d.writeByte(1)
        s.d.writeInt(Int.MAX_VALUE)
        runCatching { Nbt.leggi(s.byteArray()) }.fold(
            onSuccess = { throw AssertionError("ha accettato una lunghezza assurda") },
            onFailure = { assertTrue(it is Nbt.NbtRotto) }
        )
    }

    @Test
    fun `una lunghezza negativa viene rifiutata`() {
        val s = Scrittore()
        s.apriRadice()
        s.campo(7, "cattivo")
        s.d.writeInt(-5)
        runCatching { Nbt.leggi(s.byteArray()) }.fold(
            onSuccess = { throw AssertionError("ha accettato una lunghezza negativa") },
            onFailure = { assertTrue(it is Nbt.NbtRotto) }
        )
    }

    @Test
    fun `un tipo che non esiste viene rifiutato`() {
        val s = Scrittore()
        s.apriRadice()
        s.campo(99, "boh")
        runCatching { Nbt.leggi(s.byteArray()) }.fold(
            onSuccess = { throw AssertionError("ha accettato un tipo inventato") },
            onFailure = { assertTrue(it is Nbt.NbtRotto) }
        )
    }

    // ---------------------------------------------------- e l'inventario

    @Test
    fun `tira fuori l'inventario`() {
        val dati = conInventario(
            true,
            Triple(0, "minecraft:diamond_sword", 1),
            Triple(2, "minecraft:torch", 64),
            Triple(9, "minecraft:cobblestone", 32),
            Triple(103, "minecraft:diamond_helmet", 1),
            Triple(-106, "minecraft:shield", 1)
        )
        val g = Inventario.leggi(Nbt.leggi(dati))

        assertEquals("minecraft:diamond_sword", g.cintura[0]?.id)
        assertEquals(64, g.cintura[2]?.quantita)
        assertNull(g.cintura[1])
        assertEquals("minecraft:cobblestone", g.zaino[0]?.id)
        assertEquals("minecraft:diamond_helmet", g.equipaggiamento.testa?.id)
        assertEquals("minecraft:shield", g.equipaggiamento.manoSecondaria?.id)
        assertEquals(2, g.inMano)
        assertEquals("8.0", g.cuori)
        assertEquals(18, g.fame)
        assertEquals(30, g.livelli)
        assertEquals("Nether", g.dimensione)
    }

    @Test
    fun `l'aria non e' un oggetto`() {
        // Occupa una casella nel file, ma per chi guarda quella casella e' vuota.
        val g = Inventario.leggi(Nbt.leggi(conInventario(false, Triple(0, "minecraft:air", 0))))
        assertNull(g.cintura[0])
    }

    @Test
    fun `il nome corto e' leggibile`() {
        val g = Inventario.leggi(
            Nbt.leggi(conInventario(false, Triple(0, "minecraft:diamond_pickaxe", 1)))
        )
        assertEquals("diamond pickaxe", g.cintura[0]?.nomeCorto)
    }

    @Test
    fun `una casella fuori dai numeri conosciuti non fa saltare niente`() {
        // I mod aggiungono caselle: devono essere ignorate, non far cadere tutto.
        // 50 e non 9999: nel file la casella e' un byte, e 9999 diventerebbe 15,
        // cioe' una casella vera dello zaino.
        val g = Inventario.leggi(Nbt.leggi(conInventario(false, Triple(50, "minecraft:stone", 1))))
        assertTrue(g.cintura.all { it == null })
        assertTrue(g.zaino.all { it == null })
    }
}

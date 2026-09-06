package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Lo scambio fra due giocatori nel registro.
 *
 * Queste prove **non contengono le frasi vere**, e non e' una dimenticanza: il
 * codice di questo progetto e' pubblico, e una frase scritta in una prova e'
 * scritta esattamente quanto una scritta nel programma. Si prova il meccanismo
 * con una coppia inventata qui dentro, e le impronte se le calcola la prova da
 * sola con le stesse funzioni del programma.
 *
 * Quello che resta fuori dalle prove — che le costanti vere siano quelle giuste
 * — si verifica una volta sola, a mano, fuori dal repository.
 */
class AscoltoTest {

    // Una coppia qualunque, inventata per queste prove.
    private val domandaFinta = "Chi va la'?"
    private val rispostaFinta = "Sono io, apri!"

    private val nudaA = Ascolto.nudo(domandaFinta)
    private val nudaB = Ascolto.nudo(rispostaFinta)
    private val improntaA = Ascolto.impronta(nudaA)
    private val improntaB = Ascolto.impronta(nudaB)

    /** Cifra un saluto come fa il programma, per poter provare che lo riapre. */
    private fun chiudi(testo: String): String {
        val chiave = MessageDigest.getInstance("SHA-256")
            .digest((nudaA + nudaB).toByteArray(Charsets.UTF_8))
        return testo.toByteArray(Charsets.UTF_8)
            .mapIndexed { i, b -> (b.toInt() xor chiave[i % chiave.size].toInt()) and 0xFF }
            .joinToString("") { "%02x".format(it) }
    }

    private val salutoFinto = "CIAO DAL BANCO DI PROVA"
    private val chiusa = chiudi(salutoFinto)

    private fun cerca(registro: String) =
        Ascolto.cerca(registro, improntaA, improntaB, chiusa)

    private fun riga(chi: String, cosa: String) =
        "[12:00:00] [Server thread/INFO]: <$chi> $cosa"

    @Test
    fun `due persone diverse aprono lo scambio`() {
        val scambio = cerca(
            listOf(
                riga("Anna", "buonasera"),
                riga("Anna", domandaFinta),
                riga("Bruno", rispostaFinta),
            ).joinToString("\n")
        )
        assertNotNull(scambio)
        assertEquals("Anna", scambio!!.primo)
        assertEquals("Bruno", scambio.secondo)
        assertEquals("la risposta si ricompone solo con le due frasi", salutoFinto, scambio.risposta)
    }

    @Test
    fun `da soli non si apre`() {
        // E' la regola che rende la cosa quello che deve essere: si tramanda,
        // non si scopre stando seduti da soli a provare frasi.
        assertNull(
            cerca(
                listOf(
                    riga("Anna", domandaFinta),
                    riga("Anna", rispostaFinta),
                ).joinToString("\n")
            )
        )
    }

    @Test
    fun `lo stesso nome con le maiuscole diverse resta la stessa persona`() {
        // Il registro non e' coerente sulle maiuscole: senza questo controllo
        // bastava riscrivere il proprio nome in un altro modo.
        assertNull(
            cerca(
                listOf(
                    riga("Anna", domandaFinta),
                    riga("ANNA", rispostaFinta),
                ).joinToString("\n")
            )
        )
    }

    @Test
    fun `l'ordine conta`() {
        assertNull(
            cerca(
                listOf(
                    riga("Bruno", rispostaFinta),
                    riga("Anna", domandaFinta),
                ).joinToString("\n")
            )
        )
    }

    @Test
    fun `la risposta deve arrivare mentre si sta ancora parlando`() {
        // Due battute a due giorni di distanza non sono uno scambio: sono una
        // coincidenza, e non deve valere.
        val lontane = buildList {
            add(riga("Anna", domandaFinta))
            repeat(15) { add(riga("Carla", "chiacchiera numero $it")) }
            add(riga("Bruno", rispostaFinta))
        }.joinToString("\n")
        assertNull(cerca(lontane))
    }

    @Test
    fun `qualche battuta in mezzo non rovina niente`() {
        val vicine = buildList {
            add(riga("Anna", domandaFinta))
            repeat(3) { add(riga("Carla", "che state dicendo")) }
            add(riga("Bruno", rispostaFinta))
        }.joinToString("\n")
        assertNotNull(cerca(vicine))
    }

    @Test
    fun `punteggiatura, maiuscole e spazi non contano`() {
        val storpiate = listOf(
            riga("Anna", "   chi   VA   LA'???   "),
            riga("Bruno", "sono io... APRI!!!"),
        ).joinToString("\n")
        assertNotNull("le frasi devono valere comunque si scrivano", cerca(storpiate))
    }

    @Test
    fun `una frase sbagliata non apre niente`() {
        assertNull(
            cerca(
                listOf(
                    riga("Anna", "chi va li'"),
                    riga("Bruno", rispostaFinta),
                ).joinToString("\n")
            )
        )
    }

    @Test
    fun `un registro senza chat non fa saltare niente`() {
        assertNull(cerca(""))
        assertNull(cerca("[12:00:00] [Server thread/INFO]: Done (0.783s)!"))
    }

    @Test
    fun `si trova anche in mezzo a un registro vero`() {
        // Non arriva mai una chat pulita: arriva un registro con dentro tutto.
        val registro = """
            [12:00:00] [Server thread/INFO]: Done (0.783s)! For help, type "help"
            [12:00:05] [Server thread/INFO]: Anna joined the game
            [12:00:09] [Server thread/INFO]: ${'<'}Anna> $domandaFinta
            [12:00:11] [Server thread/INFO]: Bruno[/1.2.3.4:1234] logged in
            [12:00:12] [Server thread/INFO]: ${'<'}Bruno> $rispostaFinta
            [12:00:20] [Server thread/INFO]: Anna has made the advancement [Taking Inventory]
        """.trimIndent()
        val scambio = cerca(registro)
        assertNotNull(scambio)
        assertEquals("Bruno", scambio!!.secondo)
    }

    // ----------------------------------------------- quello che non deve esserci

    @Test
    fun `le costanti vere non si leggono da nessuna parte`() {
        // Quello che il programma tiene sono impronte: numeri da cui non si
        // torna indietro. Se un giorno qualcuno ci scrivesse una frase vera al
        // posto di un'impronta, questa prova lo direbbe.
        val vere = Ascolto.javaClass.declaredFields
            .filter { it.type == String::class.java }
            .mapNotNull { it.isAccessible = true; it.get(Ascolto) as? String }
        vere.forEach { valore ->
            val soloEsadecimale = valore.all { it in "0123456789abcdef" }
            assertTrue(
                "una costante non e' un'impronta: «$valore»",
                soloEsadecimale || valore.length < 24
            )
        }
    }
}

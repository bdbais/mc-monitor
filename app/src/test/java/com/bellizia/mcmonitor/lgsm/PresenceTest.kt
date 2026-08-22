package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Presenza e chat passano da file scritti sul server: quello che torna può essere
 * vecchio, incompleto o scritto da una versione diversa dell'app.
 */
class PresenceTest {

    private val elenco = """
        ora=1700000300
        {"id":"aaa111","nome":"Federico","ts":1700000280,"azione":""}
        {"id":"bbb222","nome":"Vale","ts":1700000250,"azione":"riavvio"}
        {"id":"ccc333","nome":"Nonno","ts":1699990000,"azione":""}
    """.trimIndent()

    @Test
    fun `legge chi c'e' e da quanto`() {
        val admins = Presence.parseAdmins(elenco)
        assertEquals(3, admins.size)

        // Ordinati dal più recente: chi si è fatto vivo per ultimo sta in cima.
        assertEquals("Federico", admins[0].name)
        assertEquals(20L, admins[0].secondsAgo)
        assertTrue(admins[0].active)
        assertFalse(admins[0].busy)

        val vale = admins[1]
        assertEquals("riavvio", vale.doing)
        assertTrue(vale.busy)
        assertTrue(vale.active)

        // Vecchio di tre ore: non c'è più.
        val nonno = admins[2]
        assertFalse(nonno.active)
        assertEquals("2 ore fa", nonno.whenLabel)
    }

    @Test
    fun `senza l'ora del server non si conclude niente`() {
        // I secondi trascorsi si calcolano solo sull'orologio del server: senza
        // quello, meglio dire che non si sa piuttosto che usare quello del telefono.
        val senzaOra = """{"id":"aaa111","nome":"Federico","ts":1700000280,"azione":""}"""
        assertTrue(Presence.parseAdmins(senzaOra).isEmpty())
    }

    @Test
    fun `righe rotte non fanno saltare l'elenco`() {
        val sporco = """
            ora=1700000300
            {"id":"aaa111","nome":"Federico","ts":1700000280,"azione":""}
            questa non e' una riga json
            {"id":"","nome":"senza id","ts":1700000290}
            {rotta
        """.trimIndent()
        val admins = Presence.parseAdmins(sporco)
        assertEquals(1, admins.size)
        assertEquals("Federico", admins[0].name)
    }

    @Test
    fun `lo stesso telefono compare una volta sola`() {
        val doppio = elenco + "\n" + """{"id":"aaa111","nome":"Federico","ts":1700000100,"azione":""}"""
        assertEquals(3, Presence.parseAdmins(doppio).size)
    }

    @Test
    fun `il riassunto parla solo degli altri`() {
        val admins = Presence.parseAdmins(elenco)
        // Da soli non si dice niente.
        assertEquals("", Presence.summary(admins.filter { it.id == "aaa111" }, "aaa111"))
        // Chi sta facendo qualcosa viene prima di chi guarda e basta.
        assertEquals("Vale sta facendo: riavvio", Presence.summary(admins, "aaa111"))

        val soloGuardanti = Presence.parseAdmins(
            """
            ora=1700000300
            {"id":"aaa111","nome":"Federico","ts":1700000280,"azione":""}
            {"id":"bbb222","nome":"Vale","ts":1700000250,"azione":""}
            """.trimIndent()
        )
        assertEquals("Anche Vale è collegato", Presence.summary(soloGuardanti, "aaa111"))
    }

    @Test
    fun `nomi e identificativi non possono rompere il comando`() {
        assertEquals("Federico", Presence.safeName("Federico"))
        assertEquals("admin", Presence.safeName(""))
        assertEquals("rm -rf", Presence.safeName("rm -rf /"))
        // Quello che resta e' innocuo: niente apici, punti e virgola o tilde da
        // far leggere alla shell. Che sia brutto da vedere e' un altro discorso.
        val ripulito = Presence.safeName("Vale'; rm -rf ~; echo '")
        assertEquals("Vale rm -rf echo", ripulito)
        assertFalse(ripulito.any { it in "'\";~$`&|<>()" })
        assertEquals(20, Presence.safeName("a".repeat(50)).length)

        assertEquals("abc123", Presence.safeId("abc-123"))
        assertEquals("sconosciuto", Presence.safeId(";;;"))
    }

    @Test
    fun `il battito scrive un file con l'ora del server`() {
        val comando = Presence.heartbeat("abc123", "Federico", "riavvio")
        assertTrue(comando.contains("mkdir -p"))
        assertTrue(comando.contains("date +%s"))
        assertTrue(comando.contains("abc123.json"))
        assertTrue(comando.contains("'Federico'"))
        assertTrue(comando.contains("'riavvio'"))
    }

    @Test
    fun `il messaggio viene ripulito prima di entrare nel json`() {
        val comando = Presence.send("abc123", "Vale", "primo\nsecondo \"tra virgolette\"")
        // Niente a capo: spezzerebbe la riga in due messaggi.
        assertFalse(comando.contains("primo\nsecondo"))
        assertTrue(comando.contains("primo secondo"))
        // Le virgolette diventano apostrofi: il JSON resta valido.
        assertFalse(comando.contains("\\\"tra virgolette\\\""))
        assertTrue(comando.contains("chat.log"))
    }

    @Test
    fun `legge i messaggi e salta quelli vuoti`() {
        val raw = """
            ora=1700000300
            {"id":"aaa111","nome":"Federico","ts":1700000100,"testo":"riavvio fra cinque minuti"}
            {"id":"bbb222","nome":"Vale","ts":1700000200,"testo":""}
            {"id":"bbb222","nome":"Vale","ts":1700000250,"testo":"ok, aspetto"}
        """.trimIndent()
        val messaggi = Presence.parseMessages(raw)
        assertEquals(2, messaggi.size)
        assertEquals("riavvio fra cinque minuti", messaggi[0].text)
        assertEquals("Vale", messaggi[1].name)
        assertEquals(1700000250L, messaggi[1].epochSeconds)
    }
}

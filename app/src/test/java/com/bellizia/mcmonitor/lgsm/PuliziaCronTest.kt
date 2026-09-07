package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cosa resta sul computer quando un server sparisce.
 *
 * Cancellare l'istanza e lasciare in crontab una riga che ogni notte prova a
 * fare il backup di una cartella che non c'è più è il modo di lasciare in casa
 * d'altri una cosa che non si spegne da sola e che nessuno sa da dove arrivi —
 * perché l'app quel server non lo elenca nemmeno più.
 *
 * Queste prove tengono ferme due cose: che i nostri blocchi se ne vadano tutti,
 * e che di quello che non è nostro non si tocchi niente.
 */
class PuliziaCronTest {

    private fun blocco(slug: String, lavoro: String, riga: String) = buildString {
        append(Cron.inizio(slug, lavoro)).append('\n')
        append(riga).append('\n')
        append(Cron.fine(slug, lavoro)).append('\n')
    }

    private val altrui = """
        # il cron di qualcun altro
        0 5 * * * /usr/local/bin/aggiorna.sh
        @reboot /home/mc/avvia-tutto.sh
    """.trimIndent() + "\n"

    @Test
    fun `i nostri blocchi se ne vanno tutti`() {
        val crontab = altrui +
                blocco("server1", "backup", "0 4 * * * cd ~/server1 && ./mcserver backup") +
                blocco("server1", "macro", "30 3 * * * cd ~/server1 && ./mcserver send riavvia")

        val dopo = Cron.senzaBlocchi(crontab, "server1")
        assertFalse("e' rimasto un blocco nostro", dopo.contains("MC Monitor"))
        assertFalse(dopo.contains("mcserver backup"))
        assertFalse(dopo.contains("mcserver send"))
    }

    @Test
    fun `quello che non e' nostro resta intatto`() {
        // E' la meta' piu' importante: un crontab non e' solo nostro, e portare
        // via la riga di qualcun altro e' un danno che nessuno collega a noi.
        val crontab = altrui + blocco("server1", "backup", "0 4 * * * qualcosa")
        val dopo = Cron.senzaBlocchi(crontab, "server1")
        assertTrue(dopo.contains("/usr/local/bin/aggiorna.sh"))
        assertTrue(dopo.contains("@reboot /home/mc/avvia-tutto.sh"))
        assertTrue(dopo.contains("# il cron di qualcun altro"))
    }

    @Test
    fun `i blocchi degli altri server restano`() {
        // Si cancella un mondo, non tutti quelli che vivono sulla stessa
        // macchina.
        val crontab = blocco("server1", "backup", "0 4 * * * primo") +
                blocco("server2", "backup", "0 5 * * * secondo")
        val dopo = Cron.senzaBlocchi(crontab, "server1")
        assertFalse(dopo.contains("primo"))
        assertTrue("ha portato via anche l'altro server", dopo.contains("secondo"))
        assertEquals(1, Cron.quantiBlocchi(dopo, "server2"))
    }

    @Test
    fun `funziona anche per lavori che questa versione non conosce`() {
        // I marcatori si riconoscono dalla forma, non dall'elenco dei lavori:
        // cosi' vale anche per quelli che aggiungeremo dopo, e per quelli
        // scritti da una versione piu' nuova dell'app.
        val crontab = blocco("server1", "qualcosachenonesiste", "0 1 * * * boh")
        assertEquals(1, Cron.quantiBlocchi(crontab, "server1"))
        assertFalse(Cron.senzaBlocchi(crontab, "server1").contains("boh"))
    }

    @Test
    fun `un crontab senza niente di nostro non cambia`() {
        assertEquals(altrui.trimEnd() + "\n", Cron.senzaBlocchi(altrui, "server1"))
    }

    @Test
    fun `un crontab vuoto resta vuoto`() {
        assertEquals("", Cron.senzaBlocchi("", "server1"))
        assertEquals("", Cron.senzaBlocchi("\n\n\n", "server1"))
    }

    @Test
    fun `le righe vuote in fondo non si accumulano`() {
        // A ogni giro se ne aggiungerebbe una, e dopo un anno il crontab e'
        // fatto per meta' di niente.
        val crontab = altrui + blocco("server1", "backup", "0 4 * * * x") + "\n\n\n"
        val dopo = Cron.senzaBlocchi(crontab, "server1")
        assertFalse(dopo.endsWith("\n\n"))
        assertTrue(dopo.endsWith("\n"))
    }

    @Test
    fun `contare i blocchi dice quanto c'e' da pulire`() {
        val crontab = blocco("s", "backup", "a") + blocco("s", "macro", "b")
        assertEquals(2, Cron.quantiBlocchi(crontab, "s"))
        assertEquals(0, Cron.quantiBlocchi(crontab, "altro"))
        assertEquals(0, Cron.quantiBlocchi("", "s"))
    }

    // ------------------------------------------- i file lasciati in giro

    @Test
    fun `il comando nomina i file della posta di quel server`() {
        val c = Discovery.rimuoviTracce("server1")
        assertTrue(c.contains("server1.txt"))
        assertTrue(c.contains("server1.sh"))
        assertTrue(c.contains("server1-consegnati.log"))
    }

    @Test
    fun `non tocca i file di un altro server`() {
        val c = Discovery.rimuoviTracce("server1")
        assertFalse("porterebbe via anche gli altri", c.contains("*"))
        assertFalse(c.contains("server2"))
    }

    @Test
    fun `senza nome non cancella niente`() {
        // Uno slug vuoto con un `rm` di mezzo e' il modo di cancellare la
        // cartella intera invece di un file.
        listOf("", "   ", "///", "...").forEach { vuoto ->
            val c = Discovery.rimuoviTracce(vuoto)
            assertFalse("un rm costruito da «$vuoto»", c.contains("rm -f"))
        }
    }

    @Test
    fun `un nome vuoto non diventa il nome di un altro server`() {
        // `normalizzaSlug("")` torna «server»: costruire il comando su quel
        // ripiego vorrebbe dire cancellare i file di un server che si chiama
        // davvero cosi'.
        assertFalse(Discovery.rimuoviTracce("").contains("server.txt"))
    }

    @Test
    fun `dice quanti ne ha tolti`() {
        // Un comando che non dice cosa ha fatto si comporta uguale sia che
        // abbia funzionato sia che non abbia trovato niente.
        assertTrue(Discovery.rimuoviTracce("server1").contains("TOLTI"))
    }

    @Test
    fun `il nome del server si normalizza come quando si scrive`() {
        // Se leggendo si normalizzasse diversamente da come si scrive, i
        // blocchi diventerebbero irrimovibili: ci sono, ma non si trovano.
        val crontab = blocco("Server Uno", "backup", "0 4 * * * x")
        assertEquals(1, Cron.quantiBlocchi(crontab, "Server Uno"))
        assertFalse(Cron.senzaBlocchi(crontab, "Server Uno").contains("0 4"))
    }
}

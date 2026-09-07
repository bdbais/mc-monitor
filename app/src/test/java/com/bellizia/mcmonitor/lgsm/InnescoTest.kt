package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Far partire una macro da una riga del registro.
 *
 * Queste prove esistono per una ragione sola: **un innesco è un modo di
 * eseguire comandi sul server**, e se la riga che lo fa scattare può scriverla
 * un giocatore, allora quel giocatore può eseguire quei comandi. In chat può
 * scrivere chiunque riesca a entrare.
 */
class InnescoTest {

    private fun chat(chi: String, cosa: String) =
        "[12:00:00] [Server thread/INFO]: <$chi> $cosa"

    private fun server(cosa: String) =
        "[12:00:00] [Server thread/INFO]: $cosa"

    private fun regola(
        testo: String,
        provenienza: Innesco.Provenienza,
        pausa: Int = 60,
    ) = Innesco.Regola("m1", provenienza, testo, pausa)

    // --------------------------------------------- la cosa che conta davvero

    @Test
    fun `un giocatore non puo' fabbricare una riga del server`() {
        // Scrive in chat le parole esatte che il server userebbe. Se un innesco
        // «quando entra qualcuno» ci cascasse, il primo che ci prova
        // comanderebbe il server.
        val finta = chat("Marco", "Anna joined the game")
        val vera = server("Anna joined the game")
        val r = regola("joined the game", Innesco.Provenienza.SERVER)

        assertFalse("una riga di chat ha fatto scattare un innesco del server", Innesco.combacia(finta, r))
        assertTrue(Innesco.combacia(vera, r))
    }

    @Test
    fun `nemmeno mettendoci dentro delle parentesi angolari`() {
        // Il tentativo successivo di chi ci ha provato una volta.
        val piuFurba = chat("Marco", "]: <Server> Anna joined the game")
        assertFalse(
            Innesco.combacia(piuFurba, regola("joined the game", Innesco.Provenienza.SERVER))
        )
    }

    @Test
    fun `una riga del server non fa scattare un innesco della chat`() {
        // Simmetrico: chi aspetta una parola detta da una persona non deve
        // partire perche' quella parola compare in un messaggio di sistema.
        assertFalse(
            Innesco.combacia(
                server("Anna has made the advancement [Ciao]"),
                regola("ciao", Innesco.Provenienza.CHAT)
            )
        )
    }

    @Test
    fun `una macro delicata non parte da una frase in chat`() {
        // Chi imposta la regola pensa a se' che scrive la parola, non al
        // ragazzino che la legge da sopra la spalla.
        val daChat = regola("riavvia", Innesco.Provenienza.CHAT)
        assertFalse(Innesco.ammessa(daChat, macroDelicata = true))
        assertTrue(Innesco.ammessa(daChat, macroDelicata = false))
    }

    @Test
    fun `una macro delicata da una riga del server invece si`() {
        val daServer = regola("Can't keep up", Innesco.Provenienza.SERVER)
        assertTrue(Innesco.ammessa(daServer, macroDelicata = true))
    }

    @Test
    fun `quando si rifiuta si dice perche'`() {
        val testo = Innesco.perche(regola("x", Innesco.Provenienza.CHAT), macroDelicata = true)
        assertTrue(testo.contains("chat"))
        assertTrue("non spiega il rischio: $testo", testo.contains("chiunque"))
        assertEquals("", Innesco.perche(regola("x", Innesco.Provenienza.CHAT), false))
    }

    // ------------------------------------------------------ il testo di chat

    @Test
    fun `dalla riga di chat si prende solo quello che ha scritto la persona`() {
        assertEquals("ciao a tutti", Innesco.testoDiChat(chat("Baisso", "ciao a tutti")))
        assertNull(Innesco.testoDiChat(server("Done (0.783s)!")))
    }

    @Test
    fun `l'innesco di chat guarda il messaggio, non il nome`() {
        // Uno che si chiama «riavvia» non deve far partire la macro solo
        // entrando e salutando.
        assertFalse(
            Innesco.combacia(chat("riavvia", "buonasera"), regola("riavvia", Innesco.Provenienza.CHAT))
        )
        assertTrue(
            Innesco.combacia(chat("Anna", "riavvia per favore"), regola("riavvia", Innesco.Provenienza.CHAT))
        )
    }

    @Test
    fun `maiuscole e minuscole non contano`() {
        assertTrue(
            Innesco.combacia(chat("Anna", "RIAVVIA"), regola("riavvia", Innesco.Provenienza.CHAT))
        )
    }

    @Test
    fun `una regola spenta o vuota non scatta mai`() {
        assertFalse(Innesco.combacia(server("qualsiasi cosa"), regola("", Innesco.Provenienza.SERVER)))
        assertFalse(
            Innesco.combacia(
                server("Done"),
                Innesco.Regola("m1", Innesco.Provenienza.SERVER, "Done", attiva = false)
            )
        )
    }

    // ------------------------------------------------------------- la pausa

    @Test
    fun `non riscatta prima della pausa`() {
        // Senza, una macro che parla in chat mentre l'innesco guarda la chat si
        // richiama all'infinito, alla velocita' con cui si rilegge il registro.
        val r = regola("x", Innesco.Provenienza.CHAT, pausa = 60)
        val ora = 1_000_000L
        assertFalse(Innesco.puoScattare(r, ultimoScatto = ora - 30_000, adesso = ora))
        assertTrue(Innesco.puoScattare(r, ultimoScatto = ora - 61_000, adesso = ora))
    }

    @Test
    fun `la prima volta scatta subito`() {
        assertTrue(Innesco.puoScattare(regola("x", Innesco.Provenienza.CHAT), 0L, 1_000L))
    }

    @Test
    fun `un orologio spostato indietro non blocca tutto per sempre`() {
        val ora = 1_000_000L
        assertTrue(Innesco.puoScattare(regola("x", Innesco.Provenienza.CHAT), ora + 999_999, ora))
    }

    // ------------------------------------------------------- le righe nuove

    @Test
    fun `alla prima lettura non si guarda il passato`() {
        // Il registro contiene ore di passato: far partire adesso tutto quello
        // che e' successo stanotte e' il modo di svegliarsi col server
        // riavviato otto volte.
        val registro = listOf(server("uno"), server("due"), server("tre")).joinToString("\n")
        assertTrue(Innesco.righeNuove(registro, ultimaVista = null).isEmpty())
    }

    @Test
    fun `poi si guardano solo le righe arrivate dopo`() {
        val prima = listOf(server("uno"), server("due")).joinToString("\n")
        val segno = Innesco.segnaposto(prima)
        val dopo = prima + "\n" + server("tre") + "\n" + server("quattro")
        val nuove = Innesco.righeNuove(dopo, segno)
        assertEquals(2, nuove.size)
        assertTrue(nuove[0].contains("tre"))
    }

    @Test
    fun `se il registro e' stato ruotato si riparte senza impazzire`() {
        // Con `latest.log` appena ricreato la riga di prima non c'e' piu': si
        // prendono le righe che ci sono, non si va in confusione.
        val nuovo = listOf(server("alfa"), server("beta")).joinToString("\n")
        val nuove = Innesco.righeNuove(nuovo, ultimaVista = "una riga che non esiste piu'")
        assertEquals(2, nuove.size)
    }

    @Test
    fun `senza registro non succede niente`() {
        assertTrue(Innesco.righeNuove("", "qualcosa").isEmpty())
        assertNull(Innesco.segnaposto(""))
        assertNull(Innesco.segnaposto("\n\n  \n"))
    }
}

package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Il crontab dell'utente.
 *
 * È l'unico posto in cui l'app scrive su una cosa che non è sua: lì dentro
 * possono esserci righe di qualcun altro, scritte anni fa, che non c'entrano
 * niente con Minecraft. Perderle sarebbe un danno vero, silenzioso e definitivo.
 * Questi non sono controlli di forma: sono la rete.
 */
class CronTest {

    private val cfg = ServerConfig(slug = "server1", lgsmDir = "~/server1", script = "mcserver")
    private val piano = PianoBackup(Cadenza.GIORNO, ora = 4, minuto = 30)

    private val altrui = """
        # backup della posta, scritto anni fa
        MAILTO=io@example.com
        17 3 * * * /usr/local/bin/salva-posta.sh
        @reboot /home/io/avvia.sh
    """.trimIndent() + "\n"

    private fun risposta(rc: Int, out: String, err: String): String {
        fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.ISO_8859_1))
        return """
            benvenuto sul server!
            @@INIZIO
            @@RC $rc
            @@OUT
            ${b64(out)}
            @@ERR
            ${b64(err)}
            @@FINE
        """.trimIndent()
    }

    // ---------------------------------------------------------------- lettura

    @Test
    fun `legge il crontab`() {
        val esito = Cron.parseRead(risposta(0, altrui, ""))
        assertTrue(esito is Crontab.Letto)
        assertEquals(altrui, (esito as Crontab.Letto).testo)
    }

    @Test
    fun `nessun crontab non e' un errore`() {
        val esito = Cron.parseRead(risposta(1, "", "no crontab for mcserver"))
        assertTrue(esito is Crontab.Letto)
        assertEquals("", (esito as Crontab.Letto).testo)
    }

    @Test
    fun `un errore vero blocca tutto`() {
        // Se non si e' capito cosa c'era, scrivere vuol dire cancellare: qui si
        // deve fermare, non "ripartire da zero".
        val esito = Cron.parseRead(risposta(1, "", "crontab: you are not allowed to use this program"))
        assertTrue(esito is Crontab.Illeggibile)
    }

    @Test
    fun `una risposta senza marcatori non si usa`() {
        assertTrue(Cron.parseRead("robaccia") is Crontab.Illeggibile)
        assertTrue(Cron.parseRead("") is Crontab.Illeggibile)
        assertTrue(Cron.parseRead("@@INIZIO\nsenza rc\n@@FINE") is Crontab.Illeggibile)
    }

    @Test
    fun `il saluto della shell non entra nel crontab`() {
        // Un echo in .bashrc finirebbe dentro il crontab a ogni scrittura.
        val esito = Cron.parseRead(risposta(0, altrui, "")) as Crontab.Letto
        assertFalse(esito.testo.contains("benvenuto"))
    }

    @Test
    fun `i byte che non sono UTF-8 tornano identici`() {
        // Un commento in latino-1 di qualcun altro, decodificato male e riscritto,
        // tornerebbe indietro storpiato.
        val strano = "# provaèÿ\n0 1 * * * /bin/true\n"
        val esito = Cron.parseRead(risposta(0, strano, "")) as Crontab.Letto
        assertEquals(strano, esito.testo)
    }

    // -------------------------------------------------------------- comporre

    @Test
    fun `le righe di qualcun altro non si toccano`() {
        val nuovo = Cron.componi(altrui, cfg, Cron.blocco(cfg, piano))
        listOf("salva-posta.sh", "MAILTO=io@example.com", "@reboot", "17 3 * * *").forEach {
            assertTrue(it, nuovo.contains(it))
        }
        assertTrue(nuovo.contains("30 4 * * *"))
    }

    @Test
    fun `riscrivere due volte non accumula niente`() {
        val uno = Cron.componi(altrui, cfg, Cron.blocco(cfg, piano))
        val due = Cron.componi(uno, cfg, Cron.blocco(cfg, piano))
        assertEquals(uno, due)
        assertEquals(1, uno.split(Cron.inizio(cfg.slug)).size - 1)
    }

    @Test
    fun `togliere il backup lascia il resto com'era`() {
        val con = Cron.componi(altrui, cfg, Cron.blocco(cfg, piano))
        val senza = Cron.componi(con, cfg, null)
        assertEquals(altrui, senza)
        assertFalse(Cron.programmato(senza, cfg))
    }

    @Test
    fun `due server diversi non si pestano i piedi`() {
        val altro = cfg.copy(slug = "server2", lgsmDir = "~/server2")
        val uno = Cron.componi(altrui, cfg, Cron.blocco(cfg, piano))
        val due = Cron.componi(uno, altro, Cron.blocco(altro, PianoBackup(Cadenza.SETTIMANA, 5, 0)))
        assertTrue(Cron.programmato(due, cfg))
        assertTrue(Cron.programmato(due, altro))
        // Togliendo il secondo, il primo resta.
        val tolto = Cron.componi(due, altro, null)
        assertTrue(Cron.programmato(tolto, cfg))
        assertFalse(Cron.programmato(tolto, altro))
    }

    @Test
    fun `su un crontab vuoto si parte da zero`() {
        val nuovo = Cron.componi("", cfg, Cron.blocco(cfg, piano))
        assertTrue(nuovo.startsWith(Cron.inizio(cfg.slug)))
        assertTrue(nuovo.endsWith("\n"))
    }

    @Test
    fun `c'e' sempre l'a capo finale`() {
        // Senza, crontab rifiuta il file e il vecchio resta al suo posto: l'app
        // direbbe "programmato" senza aver programmato niente.
        listOf("", altrui, "0 1 * * * /bin/true").forEach { partenza ->
            val nuovo = Cron.componi(partenza, cfg, Cron.blocco(cfg, piano))
            assertTrue(nuovo.endsWith("\n"))
            assertTrue(Cron.problemi(nuovo).isEmpty())
        }
    }

    // ---------------------------------------------------------------- danni

    @Test
    fun `un ritorno a capo di Windows viene visto`() {
        // cron lo accetta in silenzio e il comando non parte mai piu'.
        val problemi = Cron.problemi("0 4 * * * /bin/true\r\n")
        assertTrue(problemi.any { it.contains("Windows") })
    }

    @Test
    fun `manca l'a capo finale`() {
        assertTrue(Cron.problemi("0 4 * * * /bin/true").any { it.contains("a capo finale") })
    }

    @Test
    fun `la percentuale viene protetta`() {
        // Nel comando di cron la percentuale vuol dire "a capo": taglierebbe il
        // comando a meta'.
        assertEquals("date +\\%s", Cron.protezione("date +%s"))
        val blocco = Cron.blocco(cfg.copy(lgsmDir = "~/server%1"), piano)
        assertFalse(blocco.lines().first { it.startsWith("30") }.contains(Regex("(?<!\\\\)%")))
    }

    @Test
    fun `un fuso scritto da altri viene segnalato`() {
        val avvisi = Cron.avvertenze("CRON_TZ=UTC\n0 1 * * * /bin/true\n")
        assertTrue(avvisi.isNotEmpty())
        assertTrue(Cron.avvertenze("#CRON_TZ=UTC\n").isEmpty())
        assertTrue(Cron.avvertenze(altrui).isEmpty())
    }

    // --------------------------------------------------------------- orari

    @Test
    fun `gli orari diventano i campi giusti`() {
        assertEquals("30 4 * * *", PianoBackup(Cadenza.GIORNO, 4, 30).quando())
        assertEquals("0 5 * * 1", PianoBackup(Cadenza.SETTIMANA, 5, 0, giornoSettimana = 1).quando())
        // Per cron la domenica e' 0, per una persona e' il settimo giorno.
        assertEquals("0 5 * * 0", PianoBackup(Cadenza.SETTIMANA, 5, 0, giornoSettimana = 7).quando())
        assertEquals("15 3 1 * *", PianoBackup(Cadenza.MESE, 3, 15, giornoMese = 1).quando())
    }

    @Test
    fun `il giorno del mese si ferma al 28`() {
        // Il 29, 30 e 31 salterebbero mesi interi senza dire niente.
        assertFalse(PianoBackup(Cadenza.MESE, 3, 0, giornoMese = 31).valido)
        assertTrue(PianoBackup(Cadenza.MESE, 3, 0, giornoMese = 28).valido)
        assertFalse(PianoBackup(Cadenza.GIORNO, 24, 0).valido)
        assertFalse(PianoBackup(Cadenza.GIORNO, 4, 60).valido)
    }

    @Test
    fun `si legge in italiano`() {
        assertEquals("ogni giorno alle 04:30", PianoBackup(Cadenza.GIORNO, 4, 30).descrizione())
        assertEquals(
            "ogni domenica alle 05:00",
            PianoBackup(Cadenza.SETTIMANA, 5, 0, giornoSettimana = 7).descrizione()
        )
        assertEquals(
            "il 3 di ogni mese alle 23:05",
            PianoBackup(Cadenza.MESE, 23, 5, giornoMese = 3).descrizione()
        )
    }

    @Test
    fun `quello che si scrive si rilegge uguale`() {
        listOf(
            PianoBackup(Cadenza.GIORNO, 4, 30),
            PianoBackup(Cadenza.SETTIMANA, 5, 0, giornoSettimana = 7),
            PianoBackup(Cadenza.SETTIMANA, 22, 15, giornoSettimana = 3),
            PianoBackup(Cadenza.MESE, 1, 0, giornoMese = 28)
        ).forEach { p ->
            val crontab = Cron.componi(altrui, cfg, Cron.blocco(cfg, p))
            assertEquals(p.descrizione(), Cron.leggiPiano(crontab, cfg)?.descrizione())
        }
    }

    @Test
    fun `senza blocco non c'e' nessun piano`() {
        assertNull(Cron.leggiPiano(altrui, cfg))
        assertNull(Cron.leggiPiano("", cfg))
    }

    // ------------------------------------------------------------- il comando

    @Test
    fun `il backup parte come lo lancerebbe una persona`() {
        // LinuxGSM riconosce il proprio backup in corso cercando esattamente
        // "/bin/bash ./mcserver backup" fra i processi: con il percorso assoluto
        // non si riconosce e il suo guardiano riavvia il server mentre l'archivio
        // si sta ancora scrivendo.
        val riga = Cron.blocco(cfg, piano).lines().first { it.startsWith("30") }
        assertTrue(riga, riga.contains("cd \"\$HOME\"/"))
        assertTrue(riga, riga.contains("&& ./mcserver backup"))
        assertFalse(riga, riga.contains("/server1/mcserver backup"))
    }

    @Test
    fun `il comando non riempie la posta dell'utente`() {
        val riga = Cron.blocco(cfg, piano).lines().first { it.startsWith("30") }
        assertTrue(riga.contains(">>"))
        assertTrue(riga.contains("2>&1"))
    }

    @Test
    fun `il blocco si spiega da solo`() {
        val blocco = Cron.blocco(cfg, piano)
        assertTrue(blocco.contains("MC Monitor"))
        assertTrue(blocco.contains("ogni giorno alle 04:30"))
        assertTrue(blocco.endsWith("\n"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un orario impossibile non diventa una riga`() {
        Cron.blocco(cfg, PianoBackup(Cadenza.GIORNO, 99, 0))
    }

    @Test
    fun `il comando di lettura non usa una pipeline`() {
        // "crontab -l | filtro | crontab -" installa un crontab VUOTO se la
        // lettura fallisce, e restituisce successo.
        val cmd = Cron.read()
        assertFalse(cmd.contains("| crontab"))
        assertTrue(cmd.contains("base64"))
        assertTrue(cmd.contains("@@RC"))
    }

    @Test
    fun `prima di installare si controlla il file`() {
        val cmd = Cron.install()
        assertTrue(cmd.contains("@@CR"))
        assertTrue(cmd.contains("crontab \"\$f\""))
        // Dopo l'installazione si rilegge: crontab puo' uscire con zero senza
        // aver scritto niente.
        assertTrue(cmd.contains("@@INIZIO"))
    }
}

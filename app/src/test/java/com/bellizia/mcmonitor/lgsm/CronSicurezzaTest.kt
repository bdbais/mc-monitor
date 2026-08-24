package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * I due modi in cui il crontab di qualcun altro poteva sparire.
 *
 * Sono venuti fuori da una revisione fatta prima di installare la consegna della
 * posta su un server vero. Non erano ipotesi di scuola: il primo si innesca su
 * qualsiasi computer dove `crontab` non c'è o viene ucciso, il secondo su
 * qualsiasi crontab modificato a mano.
 */
class CronSicurezzaTest {

    private val cfg = ServerConfig(slug = "server1", lgsmDir = "~/server1", script = "mcserver")
    private val piano = PianoBackup(Cadenza.GIORNO, 4, 30)

    private fun risposta(rc: Int, out: String = "", err: String = ""): String {
        fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
        return "@@INIZIO\n@@RC $rc\n@@OUT\n${b64(out)}\n@@ERR\n${b64(err)}\n@@FINE"
    }

    // -------------------------------------------- uscita male, e muto

    @Test
    fun `uscito male e senza dire niente non vuol dire che non ha un crontab`() {
        // Era il buco piu' grave: con un'uscita qualsiasi diversa da zero e
        // nessun output l'app concludeva "non ha nessun crontab", ripartiva da
        // una base vuota, e installava un file che conteneva soltanto il blocco
        // nostro. Tutto il resto spariva.
        listOf(127, 126, 137, 2, -1).forEach { rc ->
            val esito = Cron.parseRead(risposta(rc))
            assertTrue("uscita $rc", esito is Crontab.Illeggibile)
        }
    }

    @Test
    fun `uscita 1 e muta vuol dire che non ha un crontab`() {
        // Su diversi sistemi `crontab -l` senza crontab esce con 1 e non stampa
        // niente: quello e' il caso vero, e va accettato.
        val esito = Cron.parseRead(risposta(1))
        assertTrue(esito is Crontab.Letto)
        assertEquals("", (esito as Crontab.Letto).testo)
    }

    @Test
    fun `il messaggio no crontab vale a qualunque uscita`() {
        val esito = Cron.parseRead(risposta(1, err = "no crontab for mcserver"))
        assertTrue(esito is Crontab.Letto)
    }

    @Test
    fun `un crontab vero non viene scambiato per vuoto`() {
        val esito = Cron.parseRead(risposta(0, out = "0 5 * * * /usr/bin/cosa-mia\n"))
        assertTrue(esito is Crontab.Letto)
        assertTrue((esito as Crontab.Letto).testo.contains("cosa-mia"))
    }

    // ------------------------------------------- marcatori spaiati

    @Test
    fun `un marcatore aperto e mai chiuso non porta via il resto del file`() {
        // Basta che qualcuno apra il crontab e cancelli la riga di chiusura:
        // da li' in giu' era tutto considerato roba nostra, e alla prima
        // modifica se ne andava.
        val rotto = buildString {
            append(Cron.inizio(cfg.slug)).append('\n')
            append("30 4 * * * roba-nostra\n")
            append("0 5 * * * IL BACKUP DEL DATABASE DI QUALCUN ALTRO\n")
        }
        assertFalse(Cron.marcatoriInOrdine(rotto, cfg))
        runCatching { Cron.componi(rotto, cfg, Cron.blocco(cfg, piano)) }.fold(
            onSuccess = { throw AssertionError("ha riscritto un crontab spaiato:\n$it") },
            onFailure = { assertTrue(it is IllegalStateException) }
        )
    }

    @Test
    fun `una chiusura senza apertura non passa`() {
        assertFalse(Cron.marcatoriInOrdine("qualcosa\n${Cron.fine(cfg.slug)}\naltro", cfg))
    }

    @Test
    fun `due aperture di fila non passano`() {
        val doppio = "${Cron.inizio(cfg.slug)}\n${Cron.inizio(cfg.slug)}\n${Cron.fine(cfg.slug)}"
        assertFalse(Cron.marcatoriInOrdine(doppio, cfg))
    }

    @Test
    fun `un crontab sano passa`() {
        assertTrue(Cron.marcatoriInOrdine("", cfg))
        assertTrue(Cron.marcatoriInOrdine("0 5 * * * roba-di-altri", cfg))
        assertTrue(Cron.marcatoriInOrdine(Cron.componi("roba", cfg, Cron.blocco(cfg, piano)), cfg))
    }

    @Test
    fun `il blocco di un altro server non conta come nostro`() {
        // I marcatori portano lo slug: quello di server2 non deve far pensare a
        // un blocco di server1 rimasto aperto.
        val altro = ServerConfig(slug = "server2", lgsmDir = "~/server2", script = "mcserver")
        val soloAltro = Cron.componi("", altro, Cron.blocco(altro, piano))
        assertTrue(Cron.marcatoriInOrdine(soloAltro, cfg))
    }

    // ---------------------------------------- nomi tecnici che si pestano

    @Test
    fun `due nomi tecnici diversi possono diventare lo stesso marcatore`() {
        // "abc/def" e "abcdef" dopo la ripulitura sono la stessa cosa: chi salva
        // un profilo deve poterlo sapere prima, non scoprirlo quando un blocco
        // di cron cancella quello dell'altro server.
        assertEquals(Cron.normalizzaSlug("abc/def"), Cron.normalizzaSlug("abcdef"))
        assertEquals("server", Cron.normalizzaSlug("!!!"))
        assertEquals(24, Cron.normalizzaSlug("a".repeat(50)).length)
        assertEquals("mio-server_2", Cron.normalizzaSlug("mio-server_2"))
    }

    // ------------------------------------------------- blocchi orfani

    @Test
    fun `un blocco di un server cancellato si fa vedere`() {
        // Rinominando il nome tecnico o cancellando il profilo, il blocco resta
        // nel crontab e continua a girare, ma l'app non lo riconosce piu'.
        val vecchio = ServerConfig(slug = "vecchio", lgsmDir = "~/vecchio", script = "mcserver")
        val crontab = Cron.componi(
            Cron.componi("# roba mia", cfg, Cron.blocco(cfg, piano)),
            vecchio, Cron.blocco(vecchio, piano)
        )
        val orfani = Cron.orfani(crontab, listOf(cfg.slug))
        assertEquals(listOf("backup" to "vecchio"), orfani)
    }

    @Test
    fun `i blocchi dei server ancora configurati non sono orfani`() {
        val crontab = Cron.componi("", cfg, Cron.blocco(cfg, piano))
        assertTrue(Cron.orfani(crontab, listOf("server1")).isEmpty())
        // Anche scritto con caratteri che poi vengono ripuliti.
        assertTrue(Cron.orfani(crontab, listOf("server 1")).isEmpty())
    }

    @Test
    fun `le righe di altri programmi non vengono scambiate per nostre`() {
        val altrui = "# >>> qualcun altro: backup di roba (scritto dall'app)\n0 5 * * * cosa"
        assertTrue(Cron.orfani(altrui, emptyList()).isEmpty())
    }

    @Test
    fun `anche la posta orfana si vede`() {
        val vecchio = ServerConfig(slug = "andato", lgsmDir = "~/andato", script = "mcserver")
        val crontab = Cron.componi(
            "", vecchio,
            Cron.bloccoOgniMinuto(vecchio, "sh x.sh", Posta.LAVORO, "consegna"),
            Posta.LAVORO
        )
        assertEquals(listOf("posta" to "andato"), Cron.orfani(crontab, listOf("server1")))
    }

    @Test
    fun `posta e backup si contano separatamente`() {
        val conPosta = Cron.componi(
            "", cfg,
            Cron.bloccoOgniMinuto(cfg, "sh qualcosa.sh", Posta.LAVORO, "consegna"),
            Posta.LAVORO
        )
        assertTrue(Cron.marcatoriInOrdine(conPosta, cfg, Posta.LAVORO))
        assertTrue(Cron.marcatoriInOrdine(conPosta, cfg))
    }
}

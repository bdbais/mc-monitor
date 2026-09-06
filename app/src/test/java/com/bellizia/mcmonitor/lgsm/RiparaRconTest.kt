package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La diagnosi di RCON.
 *
 * Quasi tutti i modi in cui RCON si rompe sono invisibili guardando il file:
 * una chiave scritta due volte, un a capo di Windows, uno spazio in fondo, la
 * password vuota. Se la diagnosi non li vede, dice «e' tutto a posto» a chi ha
 * il server rotto — che e' peggio che non dire niente.
 */
class RiparaRconTest {

    private val cfg = ServerConfig(host = "h", user = "u", lgsmDir = "~/server2")
    private val impronta = Lgsm.impronta("segretissima123")

    /** La risposta del server quando va tutto bene. */
    private fun risposta(
        esiste: String = "si",
        quante: String = "enable-rcon=1\nrcon.port=1\nrcon.password=1\nbroadcast-rcon-to-ops=1",
        valori: String = "enable-rcon=true$\nrcon.port=25575$\nbroadcast-rcon-to-ops=false$",
        lunghezza: Int = 15,
        improntaServer: String = impronta,
        altriFile: String = "",
    ) = """
        ===ESISTE===
        $esiste
        ===QUANTE===
        $quante
        ===VALORI===
        $valori
        ===PASSWORD===
        lunghezza=$lunghezza
        $improntaServer
        ===ALTRIFILE===
        $altriFile
        ===ASCOLTO===
        LISTEN 0 50 *:25575
        ===FINE===
    """.trimIndent()

    @Test
    fun `quando e' tutto a posto non inventa problemi`() {
        val d = RiparaRcon.leggi(risposta(), 25575, impronta)
        assertFalse(d.rotto)
        assertFalse(d.riparabile)
        assertEquals(RiparaRcon.Gravita.BENE, d.problemi.single().gravita)
    }

    @Test
    fun `una chiave scritta due volte e' un guasto, non un dettaglio`() {
        // Minecraft tiene l'ultima: il file puo' mostrare enable-rcon=true in
        // cima e averne un altro venti righe sotto. E' il caso che nessuno vede.
        val d = RiparaRcon.leggi(
            risposta(quante = "enable-rcon=2\nrcon.port=1\nrcon.password=1"),
            25575, impronta
        )
        assertTrue(d.rotto)
        assertTrue(d.riparabile)
        assertTrue(d.problemi.any { it.cosa.contains("scritta 2 volte") })
    }

    @Test
    fun `l'a capo di Windows viene visto`() {
        // `true\r` per Minecraft non e' `true`, e il file sembra giusto.
        val d = RiparaRcon.leggi(
            risposta(valori = "enable-rcon=true\\r$\nrcon.port=25575$"),
            25575, impronta
        )
        assertTrue(d.rotto)
        assertTrue(d.problemi.any { it.cosa.contains("a capo di Windows") })
    }

    @Test
    fun `la password vuota spegne RCON e va detto`() {
        // Minecraft con la password vuota non avvia RCON e non lo scrive da
        // nessuna parte: senza questo controllo si cerca per ore.
        val d = RiparaRcon.leggi(risposta(lunghezza = 0, improntaServer = ""), 25575, impronta)
        assertTrue(d.rotto)
        assertTrue(d.problemi.any { it.cosa.contains("vuota") })
    }

    @Test
    fun `la password diversa da quella dell'app viene vista`() {
        val d = RiparaRcon.leggi(risposta(improntaServer = "deadbeef"), 25575, impronta)
        assertTrue(d.rotto)
        assertTrue(d.problemi.any { it.cosa.contains("non è quella che usa l'app") })
    }

    @Test
    fun `la porta diversa viene vista e si dice quale e' quale`() {
        val d = RiparaRcon.leggi(risposta(), portaApp = 25580, improntaApp = impronta)
        assertTrue(d.rotto)
        val p = d.problemi.first { it.cosa.contains("porta") }
        assertTrue("non dice quella del server: ${p.cosa}", p.cosa.contains("25575"))
        assertTrue("non dice quella dell'app: ${p.cosa}", p.cosa.contains("25580"))
    }

    @Test
    fun `RCON spento nel file viene visto`() {
        val d = RiparaRcon.leggi(risposta(valori = "enable-rcon=false$"), 25575, impronta)
        assertTrue(d.rotto)
        assertTrue(d.problemi.any { it.cosa.contains("spento") })
    }

    @Test
    fun `il file che non c'e' ferma tutto e non e' riparabile`() {
        // Riscrivere quattro righe in un file che non esiste ne creerebbe uno
        // che il server non legge: prima si capisce dov'e' quello vero.
        val d = RiparaRcon.leggi(risposta(esiste = "no"), 25575, impronta)
        assertTrue(d.rotto)
        assertFalse(d.riparabile)
        assertEquals(1, d.problemi.size)
    }

    @Test
    fun `un secondo server properties e' un sospetto, non una certezza`() {
        // Puo' essere del tutto normale (una copia di sicurezza), ma e' anche
        // l'unica spiegazione quando tutto riesce e non cambia niente.
        val d = RiparaRcon.leggi(
            risposta(altriFile = "/home/u/server2/server.properties"),
            25575, impronta
        )
        assertFalse(d.rotto)
        assertTrue(d.daFareAMano.any { it.cosa.contains("altri") })
    }

    @Test
    fun `il modello di LinuxGSM non e' un secondo file di configurazione`() {
        // Sotto lgsm/config-default/ c'e' il modello da cui LinuxGSM copia
        // quando prepara un server nuovo: sta li' su ogni installazione e non lo
        // legge nessun Minecraft. Segnalarlo vorrebbe dire dare lo stesso falso
        // allarme a chiunque usi LinuxGSM, cioe' a tutti — e un allarme che
        // suona sempre si impara a ignorarlo.
        val d = RiparaRcon.leggi(
            risposta(
                altriFile = "/mnt/10g/minecraft/server2/lgsm/config-default/" +
                        "config-game/server.properties"
            ),
            25575, impronta
        )
        assertFalse(d.rotto)
        assertTrue("segnala il modello di LinuxGSM", d.daFareAMano.isEmpty())
        assertEquals(RiparaRcon.Gravita.BENE, d.problemi.single().gravita)
    }

    @Test
    fun `un secondo file vero viene segnalato lo stesso`() {
        // Il controllo serve ancora: e' l'unica spiegazione quando tutto riesce
        // e non cambia niente.
        val d = RiparaRcon.leggi(
            risposta(altriFile = "/mnt/10g/minecraft/server2/serverfiles2/server.properties"),
            25575, impronta
        )
        assertTrue(d.daFareAMano.any { it.cosa.contains("altri") })
    }

    @Test
    fun `i problemi arrivano in ordine di gravita'`() {
        val d = RiparaRcon.leggi(
            risposta(
                valori = "enable-rcon=false$",
                altriFile = "/altrove/server.properties"
            ),
            25575, impronta
        )
        assertEquals(RiparaRcon.Gravita.ROTTO, d.problemi.first().gravita)
    }

    // ------------------------------------------------------- la riparazione

    private val riparazione = RiparaRcon.comandoRiparazione(cfg, 25575, "segretissima123")

    @Test
    fun `la riparazione toglie tutte le righe, non corregge la prima`() {
        // Correggendo la prima, una seconda copia piu' sotto resterebbe li' a
        // vincere: e' proprio il caso che si sta cercando di aggiustare.
        assertTrue("non toglie le righe vecchie", riparazione.contains("grep -v -E"))
        assertTrue(riparazione.contains("rcon.password"))
    }

    @Test
    fun `toglie gli a capo di Windows da tutto il file`() {
        // Se ce n'e' uno su enable-rcon ce ne sono ovunque, e il prossimo a
        // rompersi sarebbe un'altra impostazione.
        assertTrue(riparazione.contains("tr -d"))
    }

    @Test
    fun `fa una copia di sicurezza prima di toccare`() {
        assertTrue(riparazione.contains("cp -p"))
    }

    @Test
    fun `riversa nel file esistente invece di sostituirlo`() {
        // Un `mv` porterebbe dentro proprietario e permessi del file temporaneo:
        // su un server dove Minecraft gira con un utente suo, il server non
        // ripartirebbe piu'.
        assertTrue("usa mv: perderebbe i permessi", riparazione.contains("cat \"\$t\" > \"\$f\""))
        assertFalse(riparazione.contains("mv \"\$t\""))
    }

    @Test
    fun `rifiuta una password che non puo' stare nel file`() {
        listOf("corta", "con spazi dentro", "con=uguale", "").forEach { cattiva ->
            runCatching { RiparaRcon.comandoRiparazione(cfg, 25575, cattiva) }.fold(
                onSuccess = { throw AssertionError("ha accettato «$cattiva»") },
                onFailure = { assertTrue(it is IllegalArgumentException) }
            )
        }
    }

    @Test
    fun `rifiuta una porta che non esiste`() {
        listOf(0, -1, 70000).forEach { porta ->
            runCatching { RiparaRcon.comandoRiparazione(cfg, porta, "segretissima123") }.fold(
                onSuccess = { throw AssertionError("ha accettato la porta $porta") },
                onFailure = { assertTrue(it is IllegalArgumentException) }
            )
        }
    }

    @Test
    fun `la tilde della cartella viene espansa`() {
        // Dentro apici singoli resta una tilde e il file non si trova mai.
        listOf(riparazione, RiparaRcon.comandoDiagnosi(cfg, 25575)).forEach {
            assertFalse("tilde cruda: $it", it.contains("'~/"))
            assertTrue(it.contains("\$HOME"))
        }
    }
}

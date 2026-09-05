package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RCON scritto insieme alle impostazioni di sicurezza, prima del primo avvio.
 *
 * Il punto di tutto questo è che la console risponda dal primo avvio: se RCON
 * venisse attivato dopo, servirebbe un riavvio, e su un server appena nato quel
 * riavvio non serve a nient'altro che a far aspettare.
 */
class RconDaSubitoTest {

    private val cfg = ServerConfig(
        id = "s1", host = "10.0.0.5", user = "mcserver",
        lgsmDir = "~/server1", script = "mcserver"
    )

    // ------------------------------------------------------ la porta libera

    @Test
    fun `senza altri server si parte dalla porta di partenza`() {
        assertEquals(25575, Provision.portaRconLibera(emptyList()))
    }

    @Test
    fun `una porta gia' presa viene saltata`() {
        // Due istanze sulla stessa porta non possono stare accese insieme: la
        // seconda muore appena parte, e chi la guarda vede un server che "non va".
        assertEquals(25576, Provision.portaRconLibera(listOf(25575)))
        assertEquals(25578, Provision.portaRconLibera(listOf(25575, 25576, 25577)))
    }

    @Test
    fun `le porte prese non devono essere in fila`() {
        assertEquals(25576, Provision.portaRconLibera(listOf(25575, 25577, 25580)))
    }

    @Test
    fun `si puo' partire da una porta diversa`() {
        assertEquals(30000, Provision.portaRconLibera(listOf(25575), dalla = 30000))
        assertEquals(30001, Provision.portaRconLibera(listOf(30000), dalla = 30000))
    }

    // ------------------------------------------------------- la password

    @Test
    fun `la password generata e' accettata da server properties`() {
        repeat(200) {
            val p = Lgsm.nuovaPasswordRcon()
            assertTrue("password rifiutata: $p", Lgsm.PASSWORD_CHARSET.matches(p))
            assertEquals(20, p.length)
        }
    }

    @Test
    fun `due password di fila non sono uguali`() {
        val viste = (1..50).map { Lgsm.nuovaPasswordRcon() }.toSet()
        assertEquals("password ripetute: non e' casuale", 50, viste.size)
    }

    @Test
    fun `la password non contiene caratteri che la shell interpreterebbe`() {
        // sed, le virgolette e la barra verticale userebbero il comando per
        // qualcos'altro: l'alfabeto e' ristretto apposta.
        val vietati = "'\"\\|$`&;<>()* \t\n"
        repeat(200) {
            val p = Lgsm.nuovaPasswordRcon()
            vietati.forEach { c -> assertFalse("«$c» dentro $p", p.contains(c)) }
        }
    }

    // ---------------------------------------------- cosa finisce nel comando

    private fun comando(
        rcon: Provision.RconDaSubito? = Provision.RconDaSubito(25575, "AbCdEfGhIjKlMnOpQrSt"),
        sicurezza: Boolean = true,
    ) = Provision.harden(cfg, rcon, sicurezza)

    @Test
    fun `scrive enable-rcon, porta e password`() {
        val c = comando()
        assertTrue(c.contains("enable-rcon=true"))
        assertTrue(c.contains("rcon.port=25575"))
        assertTrue(c.contains("rcon.password=AbCdEfGhIjKlMnOpQrSt"))
    }

    @Test
    fun `crea server properties se non c'e' ancora`() {
        // LinuxGSM puo' aver scaricato il server senza mai farlo partire: senza
        // questa riga le impostazioni varrebbero solo dal secondo avvio.
        val c = comando()
        assertTrue("manca la creazione del file", c.contains("umask 077"))
        assertTrue(c.contains(": >"))
    }

    @Test
    fun `la password non viene ristampata alla fine`() {
        // Il registro di questa schermata si copia negli appunti e si incolla in
        // una segnalazione: una password li' dentro finirebbe fuori.
        val c = comando()
        val riletturaFinale = c.substringAfterLast("grep -E")
        // Nel comando la chiave è scritta con la barra — `rcon\.password` — perché
        // dentro una regex il punto vale qualsiasi carattere. Cercare solo la forma
        // senza barra faceva passare il test anche con la password nell'elenco:
        // il difetto era nel test, non nel codice, ed è saltato fuori rimettendocelo.
        listOf("rcon.password", "rcon\\.password", "AbCdEfGhIjKlMnOpQrSt").forEach {
            assertFalse("la rilettura finale mostrerebbe «$it»", riletturaFinale.contains(it))
        }
        assertTrue(riletturaFinale.contains("enable-rcon"))
        assertTrue(riletturaFinale.contains("rcon\\.port"))
    }

    @Test
    fun `senza RCON il comando resta quello di prima`() {
        val c = Provision.harden(cfg)
        assertFalse(c.contains("enable-rcon"))
        assertFalse(c.contains("rcon.password"))
        assertTrue(c.contains("online-mode=true"))
    }

    @Test
    fun `senza sicurezza scrive solo RCON`() {
        // Chi rifiuta la messa in sicurezza deve comunque avere la console che
        // funziona: sono due scelte diverse e non vanno legate.
        val c = comando(sicurezza = false)
        assertTrue(c.contains("enable-rcon=true"))
        assertFalse("non doveva toccare la whitelist", c.contains("white-list=true"))
        assertFalse(c.contains("online-mode=true"))
    }

    @Test
    fun `non si puo' chiedere un comando che non scrive niente`() {
        runCatching { Provision.harden(cfg, rcon = null, conSicurezza = false) }.fold(
            onSuccess = { throw AssertionError("ha prodotto un comando vuoto") },
            onFailure = { assertTrue(it is IllegalArgumentException) }
        )
    }

    // -------------------------------------------------------- cosa rifiuta

    @Test
    fun `una password con caratteri strani viene rifiutata`() {
        listOf("corta", "con spazio dentro", "apostrofo'qui", "pipe|qui", "dollaro\$qui").forEach { p ->
            runCatching { Provision.harden(cfg, Provision.RconDaSubito(25575, p)) }.fold(
                onSuccess = { throw AssertionError("ha accettato la password «$p»") },
                onFailure = { assertTrue(it is IllegalArgumentException) }
            )
        }
    }

    @Test
    fun `una porta fuori scala viene rifiutata`() {
        listOf(0, -1, 65536, 99999).forEach { porta ->
            runCatching {
                Provision.harden(cfg, Provision.RconDaSubito(porta, "AbCdEfGhIjKlMnOpQrSt"))
            }.fold(
                onSuccess = { throw AssertionError("ha accettato la porta $porta") },
                onFailure = { assertTrue(it is IllegalArgumentException) }
            )
        }
    }

    @Test
    fun `il percorso con la tilde viene espanso dalla shell`() {
        // Dentro apici singoli la tilde resta un carattere e il file non si trova
        // mai: e' un errore gia' costato una volta.
        val c = comando()
        assertFalse(c.contains("'~"))
        assertTrue(c.contains("\"\$HOME\""))
    }

    @Test
    fun `la porta scelta non e' quella di un altro server`() {
        val altri = listOf(
            cfg.copy(id = "s2", rconPort = 25575),
            cfg.copy(id = "s3", rconPort = 25576),
        )
        val scelta = Provision.portaRconLibera(altri.map { it.rconPort })
        assertNotEquals(25575, scelta)
        assertNotEquals(25576, scelta)
        assertEquals(25577, scelta)
    }
}

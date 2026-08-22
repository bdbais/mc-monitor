package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le impostazioni del gioco, cioè server.properties.
 *
 * Il server non protesta per un valore sbagliato: `difficulty=medio` non fa
 * scattare nessun errore, il server usa il valore di fabbrica, e chi l'ha scritto
 * resta convinto di aver cambiato qualcosa. Il controllo deve stare qui.
 */
class GameSettingsTest {

    private val cfg = ServerConfig(
        lgsmDir = "~/server1",
        script = "mcserver",
        serverFilesDir = "~/server1/serverfiles"
    )

    private val file = """
        #Minecraft server properties
        #Sat Aug 23 10:00:00 CEST 2026
        difficulty=easy
        motd=Casa di Vale = benvenuti! #1
        max-players=20
        view-distance=10
        rcon-port=99999
        rcon.port=25575
        #pause-when-empty-seconds=60
        riga-senza-uguale
    """.trimIndent()

    @Test
    fun `legge le righe e salta i commenti`() {
        val p = GameSettings.parse(file)
        assertEquals("easy", p["difficulty"])
        assertEquals("20", p["max-players"])
        assertEquals("25575", p["rcon.port"])
        assertEquals("99999", p["rcon-port"])
        // Commentata vuol dire spenta: mostrarla come attiva sarebbe una bugia.
        assertFalse(p.containsKey("pause-when-empty-seconds"))
        assertFalse(p.containsKey("riga-senza-uguale"))
    }

    @Test
    fun `uguale e cancelletto dentro il valore restano`() {
        // In server.properties il cancelletto commenta solo a inizio riga, e
        // l'uguale separa solo la prima volta.
        assertEquals("Casa di Vale = benvenuti! #1", GameSettings.parse(file)["motd"])
    }

    @Test
    fun `l'interruttore accetta solo si e no`() {
        val pvp = GameSettings.byKey("pvp")!!
        assertNull(GameSettings.validate(pvp, "true"))
        assertNull(GameSettings.validate(pvp, "false"))
        assertNotNull(GameSettings.validate(pvp, "si"))
        assertNotNull(GameSettings.validate(pvp, ""))
    }

    @Test
    fun `la difficolta accetta solo le sue quattro parole`() {
        val d = GameSettings.byKey("difficulty")!!
        assertNull(GameSettings.validate(d, "hard"))
        // È il caso vero: il server la ignora in silenzio.
        assertNotNull(GameSettings.validate(d, "medio"))
        assertNotNull(GameSettings.validate(d, "HARD"))
    }

    @Test
    fun `i numeri stanno nel loro intervallo`() {
        val v = GameSettings.byKey("view-distance")!!
        assertNull(GameSettings.validate(v, "7"))
        assertNotNull(GameSettings.validate(v, "100"))
        assertNotNull(GameSettings.validate(v, "2"))
        assertNotNull(GameSettings.validate(v, "sette"))
    }

    @Test
    fun `il messaggio di benvenuto sta su una riga e non e' infinito`() {
        val motd = GameSettings.byKey("motd")!!
        assertNull(GameSettings.validate(motd, "Benvenuti a casa"))
        assertNotNull(GameSettings.validate(motd, "x".repeat(80)))
        assertNotNull(GameSettings.validate(motd, "due\nrighe"))
    }

    @Test
    fun `ogni valore di fabbrica passa il proprio controllo`() {
        // Se un valore di fabbrica non passasse, la schermata rifiuterebbe di
        // salvare quello che il server sta già usando.
        GameSettings.tutte.forEach { imp ->
            assertNull(imp.key, GameSettings.validate(imp, imp.diFabbrica))
            assertTrue(imp.key, GameSettings.isValidKey(imp.key))
            assertTrue(imp.key, imp.spiegazione.length > 20)
        }
        val chiavi = GameSettings.tutte.map { it.key }
        assertEquals(chiavi.size, chiavi.distinct().size)
    }

    @Test
    fun `una scelta ha le sue voci e un numero il suo intervallo`() {
        GameSettings.tutte.forEach { imp ->
            when (imp.tipo) {
                Tipo.SCELTA -> assertTrue(imp.key, imp.scelte.size >= 2)
                Tipo.NUMERO -> assertTrue(imp.key, imp.max > imp.min)
                else -> Unit
            }
        }
    }

    // ------------------------------------------------------------ scrittura

    @Test
    fun `scrive tutto in un colpo solo`() {
        val cmd = GameSettings.setProperties(
            cfg,
            linkedMapOf("difficulty" to "hard", "view-distance" to "7")
        )
        // Una copia sola e uno spostamento solo: scrivendone una per volta si
        // aprirebbero N connessioni e resterebbero N copie del file.
        assertEquals(1, cmd.split("cp \"${'$'}f\"").size - 1)
        assertEquals(1, cmd.split("mv \"${'$'}f.mcmonitor.tmp\"").size - 1)
        assertTrue(cmd.contains("MCM_1="))
        assertTrue(cmd.contains("MCM_2="))
    }

    @Test
    fun `la riga passa ad awk dall'ambiente`() {
        val cmd = GameSettings.setProperties(cfg, mapOf("motd" to "Ciao"))
        assertTrue(cmd.contains("ENVIRON"))
        assertFalse(cmd.contains("awk -v"))
    }

    @Test
    fun `la chiave si confronta come testo e non come modello`() {
        // Con un confronto a modello il punto di "rcon.port" varrebbe per
        // qualsiasi carattere, e scriverebbe anche su "rcon-port".
        val cmd = GameSettings.setProperties(cfg, mapOf("rcon.port" to "25575"))
        assertTrue(cmd.contains("c == k[i]"))
    }

    @Test
    fun `senza il file esce con il suo codice`() {
        val cmd = GameSettings.setProperties(cfg, mapOf("motd" to "Ciao"))
        assertTrue(cmd.contains("exit ${GameSettings.EXIT_NO_PROPERTIES}"))
        assertTrue(GameSettings.readAll(cfg).contains("exit ${GameSettings.EXIT_NO_PROPERTIES}"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `una chiave inventata viene rifiutata`() {
        GameSettings.setProperties(cfg, mapOf("motd; rm -rf ~" to "Ciao"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `senza niente da scrivere non si scrive`() {
        GameSettings.setProperties(cfg, emptyMap())
    }

    @Test
    fun `il valore resta su una riga sola`() {
        assertEquals("uno due", GameSettings.cleanValue("uno\ndue"))
        assertEquals("uno due", GameSettings.cleanValue("  uno\r\ndue  "))
    }

    // -------------------------------------------------------------- versione

    @Test
    fun `le versioni si confrontano numero per numero`() {
        // Confrontate come testo, "1.21.10" verrebbe prima di "1.21.9".
        assertEquals(true, GameSettings.atLeast("1.21.10", "1.21.9"))
        assertEquals(false, GameSettings.atLeast("1.21.8", "1.21.9"))
        assertEquals(true, GameSettings.atLeast("1.21.9", "1.21.9"))
        assertEquals(true, GameSettings.atLeast("1.22", "1.21.9"))
        assertEquals(false, GameSettings.atLeast("1.20", "1.21.9"))
    }

    @Test
    fun `una versione che non si sa leggere non risponde`() {
        assertNull(GameSettings.atLeast("latest", "1.21.9"))
        assertNull(GameSettings.atLeast("25w35a", "1.21.9"))
        assertNull(GameSettings.atLeast(null, "1.21.9"))
        assertNull(GameSettings.atLeast("", "1.21.9"))
    }

    @Test
    fun `dal 1 21 9 il pvp e' una regola di gioco`() {
        val pvp = GameSettings.byKey("pvp")!!
        assertTrue(GameSettings.isGamerule(pvp, "1.21.9"))
        assertFalse(GameSettings.isGamerule(pvp, "1.21.8"))
        // Nel dubbio si scrive il file e basta.
        assertFalse(GameSettings.isGamerule(pvp, "latest"))
        assertEquals("gamerule pvp false", GameSettings.commandFor(pvp, "false", "1.21.9"))
        assertNull(GameSettings.commandFor(pvp, "false", "1.20.4"))
    }

    @Test
    fun `difficolta e whitelist si applicano anche al mondo che sta girando`() {
        val d = GameSettings.byKey("difficulty")!!
        assertEquals("difficulty hard", GameSettings.commandFor(d, "hard", "1.20.4"))
        val w = GameSettings.byKey("white-list")!!
        assertEquals("whitelist on", GameSettings.commandFor(w, "true", "1.20.4"))
        assertEquals("whitelist off", GameSettings.commandFor(w, "false", "1.20.4"))
    }

    @Test
    fun `le impostazioni delicate avvisano`() {
        val online = GameSettings.byKey("online-mode")!!
        assertNotNull(online.attenzione?.invoke("false"))
        assertNull(online.attenzione?.invoke("true"))
        val white = GameSettings.byKey("white-list")!!
        assertNotNull(white.attenzione?.invoke("false"))
    }

    @Test
    fun `il valore si mostra in italiano`() {
        assertEquals("difficile", GameSettings.byKey("difficulty")!!.etichetta("hard"))
        assertEquals("sì", GameSettings.byKey("pvp")!!.etichetta("true"))
        assertEquals("no", GameSettings.byKey("pvp")!!.etichetta("false"))
        assertEquals("7 chunk", GameSettings.byKey("view-distance")!!.etichetta("7"))
    }
}

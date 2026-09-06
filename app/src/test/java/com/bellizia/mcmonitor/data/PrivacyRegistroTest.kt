package com.bellizia.mcmonitor.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il mascheramento sul registro del server.
 *
 * Nasce da uno screenshot vero: la privacy era accesa, e nella stessa schermata
 * un nome era coperto in una riga e in chiaro in altre quattro, con
 * l'identificativo Mojang per esteso. Uno screenshot cosi' era gia' stato
 * mandato a qualcuno prima che qualcuno se ne accorgesse.
 *
 * Le righe qui sotto sono quelle vere, ricopiate.
 */
class PrivacyRegistroTest {

    private val log = """
        [18:46:00] [Server thread/INFO]: FifthColumnMC (/213.0.113.9:13627) lost connection: Disconnected
        [19:03:33] [RCON Listener #2/INFO]: Thread RCON Client /127.0.0.1 started
        [19:08:19] [User Authenticator #12/INFO]: UUID of player Baisso is eb284064-0863-41ad-aea3-4dd3ef18e830
        [19:08:20] [Server thread/INFO]: Baisso[/146.0.113.7:23081] logged in with entity id 15375 at (1489.4, 66.0, -489.4)
        [19:08:21] [Server thread/INFO]: Baisso joined the game
        [19:09:06] [Server thread/INFO]: Baisso has made the advancement [Not Today, Thank You]
        [19:09:15] [User Authenticator #13/INFO]: UUID of player notursosa is 7041d39f-979c-4158-9442-591a20351f00
        [19:09:16] [Server thread/INFO]: notursosa[/101.0.113.4:1150] logged in with entity id 15810 at (3.5, 65.0, 10.5)
        [19:09:25] [Server thread/INFO]: <notursosa> hola
    """.trimIndent()

    /**
     * Si chiama [Privacy.maschera] e non [Privacy.text]: la seconda legge le
     * preferenze, che in un test unitario lanciano. La prima versione di queste
     * verifiche passava proprio per quello -- usciva al primo rigo e dichiarava
     * tutto a posto.
     */
    private fun mascherato(): String = Privacy.maschera(log)

    @Test
    fun `nessun nome resta in chiaro, in nessuna delle sue righe`() {
        val fuori = mascherato()
        listOf("Baisso", "notursosa", "FifthColumnMC").forEach { nome ->
            assertFalse(
                "«$nome» e' rimasto in chiaro da qualche parte",
                Regex("\\b${Regex.escape(nome)}\\b").containsMatchIn(fuori)
            )
        }
    }

    @Test
    fun `l'identificativo Mojang non resta per intero`() {
        val fuori = mascherato()
        // Si cambia nome, non identificativo: e' il dato che dura.
        assertFalse(fuori.contains("eb284064-0863-41ad-aea3-4dd3ef18e830"))
        assertFalse(fuori.contains("7041d39f-979c-4158-9442-591a20351f00"))
    }

    @Test
    fun `gli indirizzi restano coperti`() {
        val fuori = mascherato()
        listOf("213.0.113.9", "146.0.113.7", "101.0.113.4").forEach {
            assertFalse("«$it» in chiaro", fuori.contains(it))
        }
    }

    @Test
    fun `il registro resta leggibile`() {
        val fuori = mascherato()
        // Coprire i nomi non deve voler dire buttare via l'informazione: chi
        // guarda deve ancora capire chi e' entrato, chi ha scritto, e quando.
        assertTrue(fuori.contains("joined the game"))
        assertTrue(fuori.contains("has made the advancement"))
        assertTrue(fuori.contains("lost connection"))
        assertTrue(fuori.contains("[19:08:21]"))
        assertTrue("gli orari non sono indirizzi", fuori.contains("[19:03:33]"))
    }

    @Test
    fun `due giocatori restano distinguibili`() {
        val fuori = mascherato()
        // Il mascheramento e' parziale apposta: se tutti diventassero «•••» non
        // si capirebbe piu' chi ha fatto cosa, e la schermata sarebbe inutile.
        assertTrue(fuori.contains("Ba"))
        assertTrue(fuori.contains("no"))
    }
}

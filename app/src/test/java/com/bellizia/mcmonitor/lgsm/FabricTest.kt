package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'installazione di Fabric.
 *
 * È la funzione che ha rotto il server di un utente: scriveva dentro
 * `startparameters` invece che dentro `executable`, e su un file di istanza vuoto
 * finiva per aggiungere un secondo `-jar` dopo il primo. Java smette di leggere
 * opzioni al primo `-jar <file>`: partiva il vanilla, tutto il resto gli arrivava
 * come argomenti, e il server moriva subito.
 */
class FabricTest {

    private val cfg = ServerConfig(lgsmDir = "~/server1", script = "mcserver")

    @Test
    fun `scrive executable e non startparameters`() {
        val cmd = Mods.useLoaderInConfig(cfg)
        assertTrue(cmd, cmd.contains("executable=\"./fabric-server-launch.jar\""))
        // executable decide quale jar parte; startparameters viene accodato DOPO
        // il nome del jar, quindi lì un -jar non serve a niente.
        assertFalse(cmd.contains("startparameters=\"-Xmx"))
    }

    @Test
    fun `commenta la riga sbagliata lasciata dalla versione rotta`() {
        val cmd = Mods.useLoaderInConfig(cfg)
        assertTrue(cmd.contains("startparameters=.*-jar"))
        assertTrue(cmd.contains("tolta da MC Monitor"))
    }

    @Test
    fun `senza il jar non tocca niente`() {
        // Scrivere executable puntando a un file che non c'è lascerebbe il server
        // incapace di partire: LinuxGSM si ferma con "executable was not found".
        val cmd = Mods.useLoaderInConfig(cfg)
        assertTrue(cmd.contains("IL JAR DI FABRIC NON CE"))
        assertTrue(cmd.contains("exit ${Mods.EXIT_NO_LOADER}"))
    }

    @Test
    fun `prima di scrivere fa la copia, e se non riesce si ferma`() {
        val cmd = Mods.useLoaderInConfig(cfg)
        assertTrue(cmd.contains("COPIA DI SICUREZZA NON RIUSCITA"))
        assertTrue(cmd.contains("exit ${Lgsm.EXIT_NO_BACKUP}"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un nome di jar inventato viene rifiutato`() {
        Mods.useLoaderInConfig(cfg, "; rm -rf ~ .jar")
    }

    @Test
    fun `si torna al programma di serie`() {
        val cmd = Mods.useVanillaInConfig(cfg)
        assertTrue(cmd.contains("executable="))
        assertTrue(cmd.contains("tolta da MC Monitor"))
        assertTrue(cmd.contains("COPIA DI SICUREZZA NON RIUSCITA"))
    }

    // -------------------------------------------------- la riga di avvio

    private val sana = """
        Command-line Parameters
        java -Xmx1024M -jar ./fabric-server-launch.jar nogui
    """.trimIndent()

    private val rotta = """
        Command-line Parameters
        java -Xmx1024M -jar ./minecraft_server.jar -Xmx1024M -Xms1024M -jar fabric-server-launch.jar nogui
    """.trimIndent()

    @Test
    fun `legge la riga di avvio dai dettagli`() {
        assertEquals(
            "java -Xmx1024M -jar ./fabric-server-launch.jar nogui",
            Mods.parseLaunchLine(sana)
        )
        assertNull(Mods.parseLaunchLine("niente di utile"))
    }

    @Test
    fun `riconosce la riga sana e quella rotta`() {
        assertTrue(Mods.launchLineOk(Mods.parseLaunchLine(sana)))
        // Due -jar: è esattamente il guasto capitato all'utente.
        assertFalse(Mods.launchLineOk(Mods.parseLaunchLine(rotta)))
        assertFalse(Mods.launchLineOk(null))
        // Un solo -jar, ma punta al vanilla: Fabric non è stato configurato.
        assertFalse(Mods.launchLineOk("java -Xmx1024M -jar ./minecraft_server.jar nogui"))
    }
}

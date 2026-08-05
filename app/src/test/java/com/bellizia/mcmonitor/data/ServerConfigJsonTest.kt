package com.bellizia.mcmonitor.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L'elenco dei server viene salvato come JSON: se un campo si perdesse nel giro di
 * serializzazione, l'utente si ritroverebbe una configurazione monca senza accorgersene.
 */
class ServerConfigJsonTest {

    private val full = ServerConfig(
        id = "abc-123",
        name = "Server di casa",
        host = "mc.esempio.it",
        port = 2222,
        user = "mcserver",
        password = "segreta",
        privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nxyz\n-----END OPENSSH PRIVATE KEY-----",
        keyPassphrase = "frase",
        lgsmDir = "/home/mcserver",
        script = "mcserver",
        serverFilesDir = "/srv/minecraft",
        useLgsmSend = true,
        tmuxSession = "mc",
        rconEnabled = true,
        rconPort = 25580,
        rconPassword = "RconPass_123",
        rconTunnel = false,
        webMapUrl = "http://mc.esempio.it:8123",
        mapPollSeconds = 12,
        hostKeyFingerprint = "aa:bb:cc"
    )

    @Test
    fun `il giro json conserva ogni campo`() {
        assertEquals(full, ServerConfig.fromJson(full.toJson()))
    }

    @Test
    fun `i default reggono un json vuoto`() {
        val restored = ServerConfig.fromJson(org.json.JSONObject())
        assertEquals(22, restored.port)
        assertEquals("mcserver", restored.user)
        assertEquals(25575, restored.rconPort)
        assertEquals(true, restored.rconTunnel)
        assertEquals(6, restored.mapPollSeconds)
    }

    @Test
    fun `il nome mostrato ricade su host e poi su un segnaposto`() {
        assertEquals("Server di casa", full.displayName)
        assertEquals("mc.esempio.it", full.copy(name = "").displayName)
        assertEquals("Nuovo server", ServerConfig().displayName)
    }
}

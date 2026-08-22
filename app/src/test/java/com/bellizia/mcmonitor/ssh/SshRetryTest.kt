package com.bellizia.mcmonitor.ssh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Quando è lecito rieseguire un comando remoto.
 *
 * Non è pignoleria: il secondo tentativo scriveva davvero una seconda riga in
 * fondo a chat.log, e in chat comparivano due volte lo stesso messaggio.
 */
class SshRetryTest {

    @Test
    fun `il canale mai agganciato si puo ripetere`() {
        val mai = SshManager.ChannelNotStarted(IOException("session is down"))
        assertTrue(SshManager.safeToRetry(reused = true, error = mai))
    }

    @Test
    fun `un comando gia partito non si ripete`() {
        // È il caso che faceva danno: il comando gira sul server, si perde la
        // risposta, e ripeterlo lo esegue due volte.
        assertFalse(
            SshManager.safeToRetry(
                reused = true,
                error = SshException("Il comando non è terminato entro 20s.")
            )
        )
        assertFalse(SshManager.safeToRetry(reused = true, error = SocketTimeoutException("read timed out")))
        assertFalse(SshManager.safeToRetry(reused = true, error = IOException("connection reset")))
    }

    @Test
    fun `una sessione appena aperta non si ritenta`() {
        // Se era nuova e ha fallito subito, il secondo tentativo aspetta il doppio
        // per fallire uguale.
        val mai = SshManager.ChannelNotStarted(IOException("session is down"))
        assertFalse(SshManager.safeToRetry(reused = false, error = mai))
    }

    @Test
    fun `l'involucro conserva il motivo vero`() {
        // Serve a friendly(): a schermo deve finire il motivo, non il nome
        // dell'involucro interno.
        val causa = IOException("Auth fail")
        val mai = SshManager.ChannelNotStarted(causa)
        assertTrue(mai.cause === causa)
        assertTrue(mai.message == "Auth fail")
    }
}

package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rimettere un backup: l'unica operazione dell'app che butta via il lavoro di
 * qualcuno.
 *
 * Lo script vero gira davvero, contro un albero di server finto e un archivio
 * tar.gz vero, in `tools/prova-ripristino.sh`: lì si controlla che si rifiuti
 * quando deve e che rimetta tutto com'era quando l'estrazione fallisce. Qui si
 * controlla quello che ci mette dentro l'app.
 */
class RipristinoBackupTest {

    private val cfg = ServerConfig(
        slug = "server1",
        lgsmDir = "~/server1",
        script = "mcserver",
        tmuxSession = "mcserver"
    )

    private val nome = "mcserver-2026-08-23-231000.tar.gz"

    // ------------------------------------------------- il nome dell'archivio

    @Test(expected = IllegalArgumentException::class)
    fun `un nome con una barra non passa`() {
        // Senza questo, un nome come "../../.ssh/authorized_keys" uscirebbe
        // dalla cartella dei backup.
        Backups.ripristina(cfg, "../../etc/passwd")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un nome con due punti non passa`() {
        Backups.ripristina(cfg, "..tar.gz")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un nome vuoto non passa`() {
        Backups.contenuto(cfg, "  ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un nome con caratteri di shell non passa`() {
        Backups.ripristina(cfg, "x;rm -rf ~.tar.gz")
    }

    @Test
    fun `un nome vero di LinuxGSM passa`() {
        assertTrue(Backups.ripristina(cfg, nome).contains(nome))
    }

    // ------------------------------------------------------- lo script vero

    @Test
    fun `lo script esce dal pacchetto con tutti i buchi riempiti`() {
        val s = Backups.ripristina(cfg, nome)
        assertFalse(s, s.contains("@@DIR@@"))
        assertFalse(s, s.contains("@@EXIT"))
        assertTrue(s.startsWith("#!/bin/sh"))
        assertTrue(s.contains("'mcserver'"))
    }

    @Test
    fun `lo script non ha ritorni a capo di Windows`() {
        assertFalse(Backups.ripristina(cfg, nome).contains('\r'))
    }

    @Test
    fun `si rifiuta se il server e' acceso`() {
        // Estrarre sopra un mondo in esecuzione lo rovina, e Minecraft
        // riscriverebbe sopra quello appena tornato.
        val s = Backups.ripristina(cfg, nome)
        assertTrue(s.contains("has-session"))
        assertTrue(s.contains("SERVER ACCESO"))
        assertTrue(s.contains("exit ${Backups.EXIT_ACCESO}"))
    }

    @Test
    fun `il controllo del server viene prima di qualsiasi modifica`() {
        val s = Backups.ripristina(cfg, nome)
        assertTrue(s.indexOf("SERVER ACCESO") < s.indexOf("mv \"\$SF\""))
    }

    @Test
    fun `il mondo di adesso non viene mai cancellato`() {
        val s = Backups.ripristina(cfg, nome)
        // Si sposta con una rinomina: istantanea, e non occupa un byte in piu'.
        assertTrue(s.contains("mv \"\$SF\" \"\$DAPARTE\""))
        assertTrue(s.contains("prima-del-ripristino"))
        // L'unico rm -rf sta nel recupero, dopo un'estrazione fallita.
        assertEquals(1, Regex("rm -rf").findAll(s).count())
    }

    @Test
    fun `se l'estrazione fallisce rimette tutto com'era`() {
        val s = Backups.ripristina(cfg, nome)
        val recupero = s.substringAfter("ESTRAZIONE FALLITA", "")
        assertTrue(s.contains("mv \"\$DAPARTE\" \"\$SF\""))
        assertTrue(recupero.isNotBlank() || s.contains("mv \"\$DAPARTE\" \"\$SF\""))
    }

    @Test
    fun `si estrae solo il mondo, non la configurazione`() {
        // Chi chiede di rimettere un backup vuole il mondo di quel giorno, non
        // il server di quel giorno: le impostazioni e i mod restano quelli di
        // adesso, comprese le riparazioni fatte dopo.
        val s = Backups.ripristina(cfg, nome)
        assertTrue(s.contains("serverfiles"))
        assertTrue(s.contains("tar -xzf \"\$A\" -C \"\$D\" \"\$MEMBRO\""))
        assertFalse(s, s.contains("tar -xzf \"\$A\" -C \"\$D\" 2>"))
    }

    @Test
    fun `controlla lo spazio prima di toccare`() {
        val s = Backups.ripristina(cfg, nome)
        assertTrue(s.contains("SPAZIO INSUFFICIENTE"))
        assertTrue(s.indexOf("SPAZIO INSUFFICIENTE") < s.indexOf("mv \"\$SF\""))
    }

    @Test
    fun `si rifiuta se dentro non c'e' il mondo`() {
        // Un archivio rimasto a meta' per il disco pieno sembra un backup buono
        // dall'elenco.
        val s = Backups.ripristina(cfg, nome)
        assertTrue(s.contains("NIENTE MONDO"))
        assertTrue(s.contains("exit ${Backups.EXIT_NIENTE_MONDO}"))
    }

    // ---------------------------------------------------------- le risposte

    @Test
    fun `si legge dove e' finito il mondo di prima`() {
        val raw = "@@RIMESSO /home/mc/server1/serverfiles.prima-del-ripristino.20260824041500"
        assertTrue(Backups.rimesso(raw))
        assertEquals(
            "/home/mc/server1/serverfiles.prima-del-ripristino.20260824041500",
            Backups.messoDaParte(raw)
        )
    }

    @Test
    fun `senza cartella di prima non si inventa un percorso`() {
        assertTrue(Backups.rimesso("@@RIMESSO "))
        assertNull(Backups.messoDaParte("@@RIMESSO "))
    }

    @Test
    fun `un fallimento non viene scambiato per riuscita`() {
        assertFalse(Backups.rimesso("ESTRAZIONE FALLITA"))
        assertFalse(Backups.rimesso("SERVER ACCESO"))
    }

    @Test
    fun `si legge quante voci del mondo ci sono nell'archivio`() {
        val raw = "peso=983000000\n### elenco\nvoci=4000\nmondo=3821\n### prime\n./serverfiles/world/level.dat"
        assertEquals(3821, Backups.vociMondo(raw))
        assertTrue(Backups.anteprima(raw).contains("./serverfiles/world/level.dat"))
    }

    @Test
    fun `un archivio senza mondo si riconosce`() {
        assertEquals(0, Backups.vociMondo("mondo=0\n### prime\n./lgsm/config"))
    }
}

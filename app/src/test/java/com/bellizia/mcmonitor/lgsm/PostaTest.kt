package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * La posta per chi non c'e'.
 *
 * Lo script di consegna vero gira davvero, contro un server finto, in
 * `tools/prova-posta.sh`: uno script sh si prova lanciandolo, non riscrivendolo
 * in Kotlin per finta. Qui si controlla quello che ci mette dentro l'app, cioe'
 * che il testo dell'admin resti testo e che il crontab del backup non venga
 * travolto.
 */
class PostaTest {

    private val cfg = ServerConfig(
        slug = "server1",
        lgsmDir = "~/server1",
        script = "mcserver",
        tmuxSession = "mcserver"
    )

    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())

    // ----------------------------------------------------------- il testo

    @Test
    fun `un a capo nel messaggio non diventa una seconda riga di console`() {
        // Dentro send-keys manderebbe mezzo comando, e l'altra meta' finirebbe
        // in console come se l'avesse scritta l'admin.
        val pulito = Posta.ripulisci("prima\nseconda\r\nterza\tquarta")
        assertFalse(pulito.contains('\n'))
        assertFalse(pulito.contains('\r'))
        assertFalse(pulito.contains('\t'))
        assertEquals("prima seconda terza quarta", pulito)
    }

    @Test
    fun `un messaggio lunghissimo viene tagliato`() {
        assertEquals(Posta.LIMITE_TESTO, Posta.ripulisci("a".repeat(500)).length)
    }

    @Test
    fun `i nomi di giocatore fuori regola non passano`() {
        assertTrue(Posta.nomeValido("Pippo_99"))
        assertFalse(Posta.nomeValido(""))
        assertFalse(Posta.nomeValido("nome con spazi"))
        assertFalse(Posta.nomeValido("Pippo; rm -rf ~"))
        assertFalse(Posta.nomeValido("a".repeat(17)))
    }

    // ---------------------------------------------------------- la riga

    @Test
    fun `quello che si scrive e' quello che si rilegge`() {
        val m = Messaggio(1_700_000_000, "Pippo", "ciao, ci vediamo domani!")
        val riletti = Posta.parseLeggi("@@INIZIO\n${b64(m.riga())}\n@@FINE")
        assertEquals(listOf(m), riletti)
    }

    @Test
    fun `apici e accenti sopravvivono al giro`() {
        val testo = "l'ho messo li', pero' e' \"strano\" — 100% sicuro"
        val m = Messaggio(1, "Pippo", testo)
        assertEquals(testo, Posta.parseLeggi("@@INIZIO\n${b64(m.riga())}\n@@FINE").single().testo)
    }

    @Test
    fun `il saluto del bashrc non entra fra i messaggi`() {
        // Senza marcatori, un neofetch nel .bashrc finirebbe nella cassetta.
        val m = Messaggio(1, "Pippo", "ciao")
        val raw = "Benvenuto su questo computer!\n@@INIZIO\n${b64(m.riga())}\n@@FINE\narrivederci"
        assertEquals(listOf(m), Posta.parseLeggi(raw))
    }

    @Test
    fun `le righe rotte vengono saltate e non fanno cadere il resto`() {
        val buono = Messaggio(2, "Pippo", "ciao")
        val contenuto = listOf(
            "questa non ha i campi giusti",
            "abc\tPippo\t${b64("quando non e' un numero")}",
            "3\tnome non valido\t${b64("ciao")}",
            "4\tPluto\tnon-e-base64-@@@",
            buono.riga()
        ).joinToString("\n")
        assertEquals(listOf(buono), Posta.parseLeggi("@@INIZIO\n${b64(contenuto)}\n@@FINE"))
    }

    @Test
    fun `cassetta assente o vuota non e' un errore`() {
        assertTrue(Posta.parseLeggi("@@INIZIO\n@@FINE").isEmpty())
        assertTrue(Posta.parseLeggi("").isEmpty())
    }

    @Test
    fun `i messaggi tornano in ordine di arrivo`() {
        val tardi = Messaggio(200, "Pippo", "dopo")
        val presto = Messaggio(100, "Pippo", "prima")
        val raw = b64(tardi.riga() + "\n" + presto.riga())
        assertEquals(listOf(presto, tardi), Posta.parseLeggi("@@INIZIO\n$raw\n@@FINE"))
    }

    // -------------------------------------------------------- i comandi

    @Test
    fun `nel comando che accoda non finisce mai il testo dell'admin`() {
        // Il testo viaggia in base64: nella riga non puo' esserci un apice, e
        // quindi non puo' chiudere le virgolette del comando.
        val cattivo = "'; rm -rf ~; echo '"
        val cmd = Posta.accoda(cfg, Messaggio(1, "Pippo", cattivo))
        // Il comando un "rm -rf" ce l'ha di suo, per rompere un lucchetto morto:
        // quello che non deve esserci è il pezzo scritto dall'admin.
        assertFalse(cmd, cmd.contains("rm -rf ~"))
        assertFalse(cmd, cmd.contains("; echo '"))
        assertTrue(cmd.contains(b64(cattivo)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un nome inventato viene rifiutato prima di partire`() {
        Posta.accoda(cfg, Messaggio(1, "Pippo; stop", "ciao"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un messaggio vuoto viene rifiutato`() {
        Posta.accoda(cfg, Messaggio(1, "Pippo", "   "))
    }

    @Test
    fun `la coda ha un tetto`() {
        val cmd = Posta.accoda(cfg, Messaggio(1, "Pippo", "ciao"))
        assertTrue(cmd.contains("CASSETTA PIENA"))
        assertTrue(cmd.contains("-lt ${Posta.MAX_IN_ATTESA}"))
    }

    @Test
    fun `cancellare usa ENVIRON e non -v`() {
        // Con -v awk rileggerebbe le sequenze di escape e la riga non
        // corrisponderebbe piu' a se stessa.
        val cmd = Posta.cancella(cfg, Messaggio(1, "Pippo", "ciao"))
        assertTrue(cmd.contains("ENVIRON"))
        assertFalse(cmd.contains("awk -v"))
    }

    @Test
    fun `ogni server ha la sua cassetta`() {
        val altro = cfg.copy(slug = "server2")
        assertFalse(Posta.fileCassetta(cfg) == Posta.fileCassetta(altro))
        assertTrue(Posta.fileCassetta(cfg).endsWith("server1.txt"))
    }

    // --------------------------------------------------- lo script vero

    @Test
    fun `lo script esce dal pacchetto con tutti i buchi riempiti`() {
        val s = Posta.script(cfg)
        assertFalse(s, s.contains("@@"))
        assertTrue(s.startsWith("#!/bin/sh"))
        assertTrue(s.contains("'mcserver'"))
        assertTrue(s.contains("latest.log"))
        assertTrue(s.contains("mcserver-console.log"))
    }

    @Test
    fun `lo script non ha ritorni a capo di Windows`() {
        // Basta un \r dopo #!/bin/sh perche' il server risponda "interprete non
        // trovato", e un checkout con autocrlf ce li metterebbe in silenzio.
        assertFalse(Posta.script(cfg).contains('\r'))
    }

    @Test
    fun `quando la cassetta e' vuota lo script non tocca il server`() {
        val s = Posta.script(cfg)
        assertTrue(s.contains("[ ! -s \"\$POSTA\" ]"))
        // Prima di quel controllo non deve esserci nessun comando al server.
        assertFalse(s.substringBefore("[ ! -s \"\$POSTA\" ]").contains("send-keys"))
    }

    @Test
    fun `non chiede piu' al server chi e' collegato`() {
        // Era il difetto grave: la risposta al comando 'list' finisce nello
        // stesso log della chat, e un giocatore che scriveva "players online:
        // Tizio" decideva chi risultava presente. Da li' si arrivava a far dare
        // per consegnata, e quindi cancellare, la posta di chiunque.
        // Si guardano i comandi, non i commenti: il perché è scritto lì sopra e
        // deve poter nominare la cosa che non si fa più.
        val comandi = Posta.script(cfg)
            .lineSequence()
            .filterNot { it.trimStart().startsWith("#") }
            .joinToString("\n")
        assertFalse(comandi, comandi.contains("list"))
        assertFalse(comandi, comandi.contains("players online"))
        // E non aspetta piu' nessuna risposta prima di decidere chi c'e':
        // l'unica attesa rimasta e' dopo la consegna, per vedere se il server
        // si e' lamentato.
        assertFalse(comandi.substringBefore("while IFS").contains("sleep"))
    }

    @Test
    fun `la prova della consegna e' positiva e la da' il server`() {
        // tmux che accetta i tasti non e' una prova: la sessione puo' esistere
        // senza che dentro ci sia la console del gioco. La prova buona e' che il
        // server rieccheggi il sussurro dicendo a chi.
        val s = Posta.script(cfg)
        assertTrue(s, s.contains("You whisper to \$canonico:"))
        // E si guardano anche le parole con cui i vari server dicono "non c'e'".
        assertTrue(s.contains("No player was found"))
        assertTrue(s, s.contains("There's no player by that name online"))
    }

    @Test
    fun `quando nessuno conferma, il testo resta nel registro`() {
        // Server non standard, in un'altra lingua o troppo lento: si considera
        // partito per non consegnarlo all'infinito, ma si scrive che nessuno
        // l'ha confermato, con il testo per intero.
        val s = Posta.script(cfg)
        assertTrue(s, s.contains("NON CONFERMATO"))
        assertTrue(s, s.contains("nonconfermato"))
    }

    @Test
    fun `le prove sul log pretendono il prefisso del server`() {
        // Senza, basterebbe che un giocatore scrivesse in chat "No player was
        // found" per far tornare in coda una consegna gia' andata a buon fine.
        val s = Posta.script(cfg)
        assertTrue(s, s.contains("grep -aE \"\$PREFISSO"))
    }

    @Test
    fun `il segno nel log non viene usato se cade a meta' riga`() {
        // E' l'attacco vero: da meta' riga, quello che resta di un messaggio di
        // chat puo' sembrare una riga del server.
        val s = Posta.script(cfg)
        assertTrue(s, s.contains("dd if=\"\$LOG\""))
        assertTrue(s, s.contains("PRIMA"))
    }

    @Test
    fun `il segno dice anche di quale log parla`() {
        // Ci sono due log possibili, e un numero di byte preso su uno non vuol
        // dire niente sull'altro.
        val s = Posta.script(cfg)
        assertTrue(s, s.contains("QUALE"))
        assertTrue(s, s.contains("segna()"))
    }

    @Test
    fun `il modello delle righe del server non passa da awk -v`() {
        // Con -v awk rilegge le sequenze di escape: il \\[ diventa un [ che apre
        // una classe di caratteri, il modello non corrisponde piu' a niente e
        // nessuno riceve piu' la posta. E' successo davvero.
        val s = Posta.script(cfg)
        assertTrue(s, s.contains("MCM_PRE"))
        assertFalse(s, s.contains("awk -v pre="))
    }

    @Test
    fun `conta come ingresso solo quello che scrive il server`() {
        val s = Posta.script(cfg)
        assertTrue(s.contains("joined the game"))
        assertTrue(s.contains("left the game"))
        // chat, /say e /me: le scrive un giocatore e non valgono.
        assertTrue(s, s.contains("c == \"<\" || c == \"[\" || c == \"*\""))
    }

    @Test
    fun `riprende da dove era arrivato invece di rileggere tutto`() {
        val s = Posta.script(cfg)
        assertTrue(s.contains("OFFSET"))
        assertTrue(s.contains("tail -c"))
        // Log ripartito da capo: si riparte da zero invece di leggere spazzatura.
        assertTrue(s.contains("[ \"\$FINE\" -lt \"\$DA\" ] && DA=0"))
    }

    @Test
    fun `il lucchetto ha una scadenza`() {
        // Un lucchetto rimasto dopo un kill fermerebbe la consegna per sempre,
        // in silenzio, mentre l'app continua a dire che e' attiva.
        assertTrue(Posta.script(cfg).contains("-mmin +5"))
        assertTrue(Posta.accoda(cfg, Messaggio(1, "Pippo", "ciao")).contains("-mmin +5"))
    }

    @Test
    fun `il file di lavoro sta accanto alla cassetta`() {
        // Da /tmp la mv finale sarebbe una copia fra filesystem diversi, e
        // un'interruzione a meta' lascerebbe una cassetta troncata.
        assertTrue(Posta.script(cfg).contains("mktemp \"\$POSTA.XXXXXX\""))
    }

    @Test
    fun `anche l'app prende il lucchetto prima di scrivere`() {
        // Senza, un messaggio accodato dal telefono mentre lo script riscrive la
        // cassetta sparirebbe nella riscrittura.
        listOf(
            Posta.accoda(cfg, Messaggio(1, "Pippo", "ciao")),
            Posta.cancella(cfg, Messaggio(1, "Pippo", "ciao")),
            Posta.svuota(cfg)
        ).forEach {
            assertTrue(it, it.contains("mkdir \"\$L\""))
            assertTrue(it, it.contains("CASSETTA OCCUPATA"))
        }
    }

    @Test
    fun `cancellare dice quante righe ha tolto davvero`() {
        // "Tolto" anche quando non c'era piu' niente lasciava l'admin convinto
        // di aver fermato in tempo un messaggio che invece era gia' partito.
        assertTrue(Posta.cancella(cfg, Messaggio(1, "Pippo", "ciao")).contains("@@TOLTE"))
        assertEquals(0, Posta.tolte("@@TOLTE 0"))
        assertEquals(3, Posta.tolte("roba\n@@TOLTE 3\naltro"))
        assertNull(Posta.tolte("niente"))
    }

    @Test
    fun `il registro delle consegne si rilegge`() {
        val righe = listOf(
            "2026-08-24 10:00:00\tPippo\t1000\t${b64("ciao")}",
            "2026-08-24 10:01:00\tILLEGGIBILE\tAnna\t${b64("boh")}"
        ).joinToString("\n")
        val letti = Posta.parseConsegnati("@@INIZIO\n${b64(righe)}\n@@FINE")
        // Dal piu' recente.
        assertEquals("Anna", letti[0].giocatore)
        assertTrue(letti[0].illeggibile)
        assertEquals("Pippo", letti[1].giocatore)
        assertEquals("ciao", letti[1].testo)
    }

    // ------------------------------------------------------- il crontab

    @Test
    fun `la consegna gira ogni minuto`() {
        val b = Cron.bloccoOgniMinuto(cfg, Posta.comandoCron(cfg), Posta.LAVORO, "consegna")
        assertTrue(b.lines().any { it.startsWith("* * * * * ") })
        assertTrue(b.contains("server1.sh"))
        assertTrue(b.endsWith("\n"))
    }

    @Test
    fun `posta e backup stanno nello stesso crontab senza pestarsi`() {
        val piano = PianoBackup(Cadenza.GIORNO, 4, 30)
        val conBackup = Cron.componi("# roba mia\n", cfg, Cron.blocco(cfg, piano))
        val conTutt = Cron.componi(
            conBackup, cfg,
            Cron.bloccoOgniMinuto(cfg, Posta.comandoCron(cfg), Posta.LAVORO, "consegna"),
            Posta.LAVORO
        )
        assertTrue(Cron.programmato(conTutt, cfg))
        assertTrue(Cron.programmato(conTutt, cfg, Posta.LAVORO))
        assertTrue(conTutt.contains("# roba mia"))

        // Spegnere la posta non deve portarsi via il backup della notte.
        val senzaPosta = Cron.componi(conTutt, cfg, null, Posta.LAVORO)
        assertTrue(Cron.programmato(senzaPosta, cfg))
        assertFalse(Cron.programmato(senzaPosta, cfg, Posta.LAVORO))
        assertTrue(senzaPosta.contains("# roba mia"))
    }

    @Test
    fun `il marcatore del backup non e' cambiato di un carattere`() {
        // Sta scritto nel crontab di chi usa l'app da prima: cambiarlo
        // renderebbe orfano il backup gia' installato, e nessuno se ne
        // accorgerebbe fino alla notte in cui serve.
        assertEquals(
            "# >>> MC Monitor: backup di server1 (scritto dall'app)",
            Cron.inizio("server1")
        )
        assertEquals("# <<< MC Monitor: backup di server1", Cron.fine("server1"))
    }

    @Test
    fun `i due lavori hanno marcatori diversi`() {
        assertFalse(Cron.inizio("server1") == Cron.inizio("server1", Posta.LAVORO))
    }
}

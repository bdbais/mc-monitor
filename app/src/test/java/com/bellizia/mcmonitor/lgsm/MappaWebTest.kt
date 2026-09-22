package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trovare la porta su cui si è messa la mappa.
 *
 * I tre programmi scrivono la configurazione in tre formati diversi: leggerla
 * vorrebbe dire conoscerli tutti e tre per sempre. Si guardano invece le porte
 * in ascolto, che sono vere comunque sia scritta la configurazione — ma allora
 * bisogna saper togliere quelle che sono di altri, o si finisce per aprire SSH
 * in un browser.
 */
class MappaWebTest {

    /** Come risponde `ss -ltn` su una macchina vera. */
    private val ascolto = """
        State  Recv-Q Send-Q Local Address:Port Peer Address:Port
        LISTEN 0      128          0.0.0.0:22
        LISTEN 0      50              *:25565
        LISTEN 0      50              *:25575
        LISTEN 0      128             *:8100
    """.trimIndent()

    @Test
    fun `resta la porta della mappa e non quelle che sappiamo gia'`() {
        val candidate = MappaWeb.porteCandidate(ascolto, gioco = 25565, rcon = 25575)
        assertEquals(listOf(8100), candidate)
    }

    @Test
    fun `la porta di SSH non finisce mai fra le candidate`() {
        // Aprire la 22 in un browser non mostra una mappa: mostra niente, e chi
        // ha premuto pensa che l'installazione sia fallita.
        assertFalse(22 in MappaWeb.porteCandidate(ascolto, 25565, 25575))
    }

    @Test
    fun `le porte del gioco e di RCON dipendono dal server, non sono fisse`() {
        // Chi ha spostato il gioco sulla 25566 non deve vederselo proposto come
        // se fosse una mappa.
        val altro = ascolto.replace("25565", "25566").replace("25575", "25576")
        assertEquals(listOf(8100), MappaWeb.porteCandidate(altro, gioco = 25566, rcon = 25576))
    }

    @Test
    fun `un sito sulla 80 non e' la mappa di Minecraft`() {
        // Su una macchina che fa anche altro c'e' quasi sempre un nginx. Il
        // server di Minecraft non gira da amministratore e una porta sotto la
        // 1024 non la puo' aprire: quindi quelle non sono sue, e proporle
        // vorrebbe dire aprire il sito di qualcun altro credendo di aprire la
        // mappa.
        val conNginx = ascolto + """

            LISTEN 0      511          0.0.0.0:80
            LISTEN 0      511          0.0.0.0:443
        """.trimIndent()
        assertEquals(listOf(8100), MappaWeb.porteCandidate(conNginx, 25565, 25575))
    }

    @Test
    fun `senza niente in ascolto non si inventa una porta`() {
        assertTrue(MappaWeb.porteCandidate("NESSUNA", 25565, 25575).isEmpty())
        assertTrue(MappaWeb.porteCandidate("", 25565, 25575).isEmpty())
    }

    // ------------------------------------------------------- quale proporre

    private val bluemap = MappaWeb.perSlug("bluemap")!!

    @Test
    fun `la porta predefinita vince quando c'e'`() {
        assertEquals(8100, MappaWeb.portaDellaMappa(bluemap, listOf(8100, 9000)))
    }

    @Test
    fun `se ne resta una sola e' quella, anche se non e' la predefinita`() {
        // Chi ha spostato la mappa su un'altra porta non deve rifare tutto a mano.
        assertEquals(9000, MappaWeb.portaDellaMappa(bluemap, listOf(9000)))
    }

    @Test
    fun `con piu' porte e nessuna predefinita non si tira a indovinare`() {
        // Sbagliare porta mostra una pagina bianca, che si legge come
        // «l'installazione e' fallita» anche quando e' andata bene.
        assertNull(MappaWeb.portaDellaMappa(bluemap, listOf(9000, 9001)))
        assertNull(MappaWeb.portaDellaMappa(bluemap, emptyList()))
    }

    // ------------------------------------------------------ cosa c'e' gia'

    @Test
    fun `riconosce una mappa gia' installata dal nome del file`() {
        val mod = listOf("fabric-api-0.159.0.jar", "BlueMap-5.4-fabric.jar", "jei.jar")
        assertEquals("BlueMap", MappaWeb.installata(mod)?.nome)
    }

    @Test
    fun `la riconosce anche spenta`() {
        // LinuxGSM e le mod si spengono rinominando il file: «ce l'hai gia', e'
        // solo spenta» e' una risposta diversa da «non ce l'hai».
        assertEquals("Dynmap", MappaWeb.installata(listOf("Dynmap-3.7-fabric.jar.disabled"))?.nome)
    }

    @Test
    fun `senza nessuna mappa non ne inventa una`() {
        assertNull(MappaWeb.installata(listOf("fabric-api.jar", "journeymap-26.2.jar")))
    }

    @Test
    fun `journeymap non e' una mappa web`() {
        // Sta sul server ma disegna sul client: proporre di aprirla in un
        // browser sarebbe una promessa che nessuno puo' mantenere.
        assertNull(MappaWeb.installata(listOf("journeymap-26.2-6.0.7.jar")))
    }

    // --------------------------------------------------------- il tunnel

    @Test
    fun `l'indirizzo del tunnel non si salva`() {
        // La porta locale la sceglie il tunnel ogni volta: salvarla vorrebbe
        // dire ritrovarsi domani un indirizzo che non porta da nessuna parte.
        assertTrue(MappaWeb.daNonSalvare(MappaWeb.indirizzoNelTunnel(41337)))
        assertTrue(MappaWeb.daNonSalvare("http://localhost:8100"))
        assertFalse(MappaWeb.daNonSalvare("https://mappa.example.com"))
    }

    @Test
    fun `il catalogo non ha due mappe sulla stessa porta`() {
        val porte = MappaWeb.CATALOGO.map { it.portaPredefinita }
        assertEquals(porte.size, porte.toSet().size)
    }

    @Test
    fun `ogni mappa e' spiegata a chi non ne conosce nessuna`() {
        MappaWeb.CATALOGO.forEach {
            assertTrue("${it.nome} non e' spiegata", it.comeE.length > 40)
            assertNull("${it.nome}: due mappe con lo stesso slug",
                MappaWeb.CATALOGO.firstOrNull { altra -> altra !== it && altra.slug == it.slug })
        }
    }

    // ------------------------------------------- la porta che si e' ricordata

    /**
     * Il caso che prima restava senza risposta.
     *
     * Mappa su una porta sua, due porte in ascolto e nessuna predefinita:
     * l'app rinunciava ogni volta, anche dopo che una volta si era capito
     * benissimo qual era. Adesso quella che ha funzionato l'ultima volta vale
     * piu' di tutto il resto.
     */
    @Test
    fun `la porta ricordata vince su tutto, se e' ancora in ascolto`() {
        val candidate = listOf(9000, 9001)
        assertNull(MappaWeb.portaDellaMappa(bluemap, candidate))
        assertEquals(
            9001,
            MappaWeb.portaDellaMappa(bluemap, candidate, ricordata = 9001)
        )
    }

    @Test
    fun `la porta ricordata vince anche sulla predefinita`() {
        // Chi ha spostato la mappa l'ha spostata apposta, e la predefinita in
        // ascolto e' un'altra cosa -- magari un secondo mondo.
        val candidate = listOf(bluemap.portaPredefinita, 9001)
        assertEquals(
            9001,
            MappaWeb.portaDellaMappa(bluemap, candidate, ricordata = 9001)
        )
    }

    @Test
    fun `una porta ricordata che non risponde piu' si scarta`() {
        // E' quello che rende automatica la riscoperta: la mappa reinstallata
        // altrove, o spenta, esce da sola dalle candidate e la regola
        // ricomincia da capo. Senza questo, in configurazione resterebbe un
        // numero sbagliato da correggere a mano.
        val candidate = listOf(bluemap.portaPredefinita)
        assertEquals(
            bluemap.portaPredefinita,
            MappaWeb.portaDellaMappa(bluemap, candidate, ricordata = 9001)
        )
    }

    @Test
    fun `senza niente in ascolto la porta ricordata non basta`() {
        // Il server e' spento: aprire un tunnel verso una porta che non c'e'
        // mostrerebbe una pagina bianca, che si legge come «installazione
        // fallita».
        assertNull(MappaWeb.portaDellaMappa(bluemap, emptyList(), ricordata = 9001))
    }

    @Test
    fun `zero vuol dire che non si e' mai trovata`() {
        // E' il valore di partenza in configurazione: non deve diventare una
        // porta da provare.
        assertNull(MappaWeb.portaDellaMappa(bluemap, listOf(9000, 9001), ricordata = 0))
    }

    // ------------------------------------------- chiedere alle porte chi sono

    /** La pagina che manda BlueMap: si nomina nel titolo e nei file che carica. */
    private val paginaBlueMap = """
        <!DOCTYPE html><html><head><title>BlueMap</title>
        <link rel="icon" href="assets/bluemap.png">
        <script src="assets/bluemap.js"></script>
        </head><body><div id="map-container"></div></body></html>
    """.trimIndent()

    private val paginaDynmap = """
        <!DOCTYPE html><html><head><title>Dynmap</title>
        <link rel="stylesheet" href="css/dynmap.css">
        <script src="js/dynmap.js"></script>
        </head><body class="dynmap"></body></html>
    """.trimIndent()

    private fun sonda(vararg pezzi: Pair<Int, String>): MappaWeb.Sondaggio =
        MappaWeb.leggiSonda(pezzi.joinToString("\n") { (porta, corpo) ->
            "=== PORTA $porta\n$corpo"
        })

    @Test
    fun `la sonda chiede a tutte le porte candidate`() {
        val comando = MappaWeb.comandoSonda(listOf(8100, 9090))
        assertTrue(comando.contains("=== PORTA 8100"))
        assertTrue(comando.contains("=== PORTA 9090"))
        // Dal server verso se stesso: la mappa e' quasi sempre in ascolto solo
        // sul locale, e chiederlo dal telefono non funzionerebbe.
        assertTrue(comando.contains("http://127.0.0.1:8100/"))
        // Su una macchina spoglia c'e' l'uno o l'altro, quasi mai tutti e due.
        assertTrue(comando.contains("curl") && comando.contains("wget"))
    }

    @Test
    fun `le porte si chiedono tutte insieme, non una dopo l'altra`() {
        // In fila, su una macchina con quindici porte in ascolto, il comando
        // scadeva prima di rispondere: la sonda non c'era e non si vedeva che
        // non c'era, l'app tornava semplicemente a chiedere. E' il difetto che
        // si e' visto solo su un server vero.
        val comando = MappaWeb.comandoSonda((9000..9016).toList())
        assertEquals(17, Regex("""=== PORTA""").findAll(comando).count())
        assertEquals(17, Regex("""} &${'$'}""", RegexOption.MULTILINE).findAll(comando).count())
        assertTrue(comando.lineSequence().any { it.trim() == "wait" })
        // Si aspetta prima di leggere, o si leggono file ancora vuoti.
        assertTrue(comando.indexOf("\nwait") < comando.indexOf("=== PORTA"))
    }

    @Test
    fun `oltre una certa soglia non si bussa a tutte le porte della macchina`() {
        val comando = MappaWeb.comandoSonda((9000..9100).toList())
        assertEquals(
            MappaWeb.QUANTE_AL_MASSIMO,
            Regex("""=== PORTA""").findAll(comando).count()
        )
    }

    @Test
    fun `nessuno dei due programmi puo' riprovare`() {
        // Il difetto che ha fatto scadere la sonda su un server vero: wget
        // riprova venti volte da solo, e una porta che accetta e non risponde
        // diventa quaranta secondi. Il tempo massimo di una richiesta deve
        // essere detto per intero, non meta' lasciata ai valori di fabbrica.
        val comando = MappaWeb.comandoSonda(listOf(8100))
        assertTrue("wget senza -t 1 riprova venti volte", comando.contains("-t 1"))
        assertTrue("curl senza -m si ferma solo quando vuole lui", comando.contains("-m 2"))
    }

    @Test
    fun `senza porte da chiedere non si manda un comando a vuoto`() {
        // Un for su una lista vuota e' un comando che gira per niente: qui
        // arriva anche col server spento, e un giro di SSH costa.
        assertEquals("echo", MappaWeb.comandoSonda(emptyList()))
    }

    @Test
    fun `la mappa si riconosce da come si presenta`() {
        val s = sonda(9090 to paginaBlueMap)
        assertEquals("bluemap", s.riconosciute[9090])
        assertTrue(9090 in s.web)
    }

    @Test
    fun `una porta che risponde ma non si nomina resta un sito qualunque`() {
        // Un pannello di controllo, un proxy, una pagina di errore: e' un sito,
        // quindi puo' essere una mappa travestita, ma non lo sappiamo.
        val s = sonda(9090 to "<html><body>401 Unauthorized</body></html>")
        assertNull(s.riconosciute[9090])
        assertTrue(9090 in s.web)
    }

    @Test
    fun `una porta muta non e' un sito`() {
        // E' il caso che risolve il dilemma da solo: fra le porte comparse dopo
        // l'installazione, quelle che non parlano HTTP escono di scena senza
        // che nessuno debba scegliere.
        val s = sonda(9090 to "")
        assertTrue(s.web.isEmpty())
    }

    @Test
    fun `vince chi si nomina di piu'`() {
        // Dynmap cita BlueMap una volta sola, in un commento. Fermarsi alla
        // prima trovata farebbe vincere quella sbagliata per un'inezia.
        val s = sonda(9090 to (paginaDynmap + "\n<!-- niente a che vedere con bluemap -->"))
        assertEquals("dynmap", s.riconosciute[9090])
    }

    @Test
    fun `due a pari merito non riconoscono nessuna`() {
        // Meglio non sapere che sapere male: aprire la porta sbagliata mostra
        // una pagina bianca e si legge come «installazione fallita».
        val s = sonda(9090 to "<html>bluemap dynmap</html>")
        assertNull(s.riconosciute[9090])
    }

    @Test
    fun `chi si e' nominato vince su tutto il resto`() {
        // Le altre tre risposte sono supposizioni. Questa e' la mappa che
        // risponde e dice di essere lei.
        val candidate = listOf(bluemap.portaPredefinita, 9090, 9091)
        val s = sonda(
            bluemap.portaPredefinita to "<html><body>pannello</body></html>",
            9090 to paginaBlueMap,
            9091 to "<html><body>pannello</body></html>",
        )
        assertEquals(
            9090,
            MappaWeb.portaDellaMappa(bluemap, candidate, ricordata = 9091, sondaggio = s)
        )
    }

    @Test
    fun `le porte mute non si propongono nemmeno da scegliere`() {
        // Due porte in ascolto, nessuna predefinita, niente di ricordato: prima
        // qui l'app chiedeva. Adesso una delle due non risponde nemmeno, e la
        // domanda non ha piu' motivo di esistere.
        val candidate = listOf(9090, 9091)
        val s = sonda(9090 to "", 9091 to "<html><body>pannello</body></html>")
        assertEquals(listOf(9091), MappaWeb.restano(bluemap, candidate, s))
        assertEquals(9091, MappaWeb.portaDellaMappa(bluemap, candidate, sondaggio = s))
    }

    @Test
    fun `la porta di un'altra mappa non si propone per questa`() {
        // Due mappe installate insieme e' raro ma non assurdo, e li' la porta
        // dell'una non e' mai la risposta per l'altra.
        val candidate = listOf(9090, 9091)
        val s = sonda(9090 to paginaDynmap, 9091 to "<html><body>pannello</body></html>")
        assertEquals(listOf(9091), MappaWeb.restano(bluemap, candidate, s))
    }

    @Test
    fun `una sonda che non ha trovato niente vale come non fatta`() {
        // Ne' curl ne' wget sulla macchina: non e' la prova che le porte siano
        // mute. Si torna a ragionare sulle sole porte in ascolto, com'era
        // prima, invece di dire che non c'e' niente.
        val muta = MappaWeb.leggiSonda("")
        assertFalse(muta.utile)
        val candidate = listOf(bluemap.portaPredefinita, 9090)
        assertEquals(candidate, MappaWeb.restano(bluemap, candidate, muta))
        assertEquals(
            bluemap.portaPredefinita,
            MappaWeb.portaDellaMappa(bluemap, candidate, sondaggio = muta)
        )
    }

    @Test
    fun `la risposta vera del comando si legge`() {
        // Non un esempio inventato: queste righe sono quelle che il comando ha
        // stampato davvero, girato contro due porte -- una che serve la pagina
        // di una mappa e una dove non c'e' niente in ascolto. Il formato lo
        // decide il comando li' sopra, e un test scritto a mano non si
        // accorgerebbe se cambiasse.
        val vera = """
            === PORTA 9090
            <!DOCTYPE html><html><head><title>BlueMap</title>
            <link rel="icon" href="assets/bluemap.png"><script src="assets/bluemap.js"></script>
            </head><body><div id="map-container">bluemap</div></body></html>

            === PORTA 9091

        """.trimIndent()
        val s = MappaWeb.leggiSonda(vera)
        assertEquals("bluemap", s.riconosciute[9090])
        assertEquals(setOf(9090), s.web)
        assertEquals(9090, MappaWeb.portaDellaMappa(bluemap, listOf(9090, 9091), sondaggio = s))
    }

    @Test
    fun `senza sonda la regola e' quella di prima`() {
        // La sonda puo' non partire: server irraggiungibile, comando in
        // timeout. Quando non c'e', le vecchie risposte devono valere ancora.
        val candidate = listOf(bluemap.portaPredefinita, 9090)
        assertEquals(
            bluemap.portaPredefinita,
            MappaWeb.portaDellaMappa(bluemap, candidate, sondaggio = null)
        )
    }

    // -------------------------------- piu' server Minecraft nello stesso Linux

    @Test
    fun `la porta scritta a mano vince su tutto`() {
        // Chi l'ha scritta sa qualcosa che l'app non puo' sapere: qui due
        // BlueMap sulla stessa macchina, e solo lui sa quale dei due mondi e'
        // il suo.
        val s = sonda(8100 to paginaBlueMap, 8123 to paginaBlueMap)
        assertEquals(
            MappaWeb.Scelta.Aprila(8123),
            MappaWeb.scegliLaPorta(
                bluemap, listOf(8100, 8123), ricordata = 8100, fissata = 8123, sondaggio = s
            )
        )
    }

    @Test
    fun `una porta scritta a mano che non risponde non si aggira`() {
        // Ripiegare su un'altra porta sarebbe la cosa peggiore che l'app possa
        // fare qui: mostrerebbe la mappa di un mondo diverso spacciandola per
        // la tua. Meglio dire che non si apre.
        assertEquals(
            MappaWeb.Scelta.FissataMuta(8123),
            MappaWeb.scegliLaPorta(bluemap, listOf(8100), fissata = 8123)
        )
    }

    @Test
    fun `due mappe uguali sulla stessa macchina non si tirano a indovinare`() {
        // Due server Minecraft nello stesso Linux: le mappe si presentano tutte
        // e due come BlueMap, e una delle due e' sulla porta predefinita. Se
        // vincesse la predefinita sarebbe testa o croce, e una delle due facce
        // mostra il mondo sbagliato dicendo che e' il tuo.
        val candidate = listOf(bluemap.portaPredefinita, 8101)
        val s = sonda(bluemap.portaPredefinita to paginaBlueMap, 8101 to paginaBlueMap)
        assertEquals(
            MappaWeb.Scelta.Chiedi(candidate, sondato = true),
            MappaWeb.scegliLaPorta(bluemap, candidate, sondaggio = s)
        )
        // E durante l'installazione, dove non si puo' chiedere niente, non si
        // sceglie affatto.
        assertNull(MappaWeb.portaDellaMappa(bluemap, candidate, sondaggio = s))
    }

    @Test
    fun `fra due mappe uguali la porta di ieri decide senza chiedere`() {
        // La domanda si fa una volta sola: e' il motivo per cui la risposta si
        // ricorda.
        val candidate = listOf(bluemap.portaPredefinita, 8101)
        val s = sonda(bluemap.portaPredefinita to paginaBlueMap, 8101 to paginaBlueMap)
        assertEquals(
            MappaWeb.Scelta.Aprila(8101),
            MappaWeb.scegliLaPorta(bluemap, candidate, ricordata = 8101, sondaggio = s)
        )
    }

    @Test
    fun `chi si e' nominato esclude chi non ha detto niente`() {
        // Una porta muta non puo' battere una che ha detto di essere lei,
        // nemmeno quando la muta e' quella predefinita.
        val candidate = listOf(bluemap.portaPredefinita, 8101)
        val s = sonda(
            bluemap.portaPredefinita to "<html><body>pannello</body></html>",
            8101 to paginaBlueMap,
        )
        assertEquals(listOf(8101), MappaWeb.restano(bluemap, candidate, s))
        assertEquals(
            MappaWeb.Scelta.Aprila(8101),
            MappaWeb.scegliLaPorta(bluemap, candidate, sondaggio = s)
        )
    }

    @Test
    fun `senza niente in ascolto lo si dice, invece di chiedere a vuoto`() {
        assertEquals(
            MappaWeb.Scelta.NienteDaAprire,
            MappaWeb.scegliLaPorta(bluemap, emptyList())
        )
    }

    @Test
    fun `chiedere dopo aver sondato non e' come chiedere senza`() {
        // Senza questa distinzione la domanda mente: «nessuna ha detto di
        // essere BlueMap» e «non sono riuscito a chiederlo» finiscono nella
        // stessa finestra, ma la seconda e' un guasto da aggiustare -- sul
        // server mancano curl e wget -- e chi legge deve poterlo sapere.
        val senza = MappaWeb.scegliLaPorta(bluemap, listOf(9000, 9001))
        assertEquals(MappaWeb.Scelta.Chiedi(listOf(9000, 9001), sondato = false), senza)
        val con = MappaWeb.scegliLaPorta(
            bluemap,
            listOf(9000, 9001),
            sondaggio = sonda(
                9000 to "<html><body>uno</body></html>",
                9001 to "<html><body>due</body></html>",
            ),
        )
        assertEquals(MappaWeb.Scelta.Chiedi(listOf(9000, 9001), sondato = true), con)
    }
}

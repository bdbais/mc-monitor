package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il controllo di sicurezza che si rifà da solo.
 *
 * Le regole che contano sono due, e sono tutte e due sul *non* disturbare: non
 * girare più spesso del dovuto, e non parlare quando non c'è niente da dire. Un
 * avviso che una volta a settimana dice «va tutto bene» è un avviso che si
 * impara a scartare senza leggerlo — e il giorno che dice qualcosa di serio
 * finisce nello stesso gesto.
 */
class ControlloSettimanaleTest {

    private val ora = 1_757_000_000_000L
    private val giorno = 24L * 60 * 60 * 1000
    private val settimana = 7 * giorno

    private fun controllo(esito: Esito, peso: Peso = Peso.ALTO) =
        Controllo("un controllo", esito, "spiegazione", "rimedio", peso)

    private fun rapporto(livello: Livello, quanti: Int = 1): Rapporto {
        val controlli = when (livello) {
            Livello.BASSO -> List(quanti) { controllo(Esito.MALE) }
            Livello.MEDIO -> List(quanti) { controllo(Esito.ATTENZIONE) }
            Livello.ALTO -> List(quanti) { controllo(Esito.BENE) }
        }
        return Rapporto(livello, controlli)
    }

    // ------------------------------------------------------------ quando

    @Test
    fun `la prima volta si fa subito`() {
        assertTrue(ControlloSettimanale.deveGirare(esperto = true, ultimoControllo = 0, adesso = ora))
    }

    @Test
    fun `dopo una settimana si rifa`() {
        assertTrue(
            ControlloSettimanale.deveGirare(true, ultimoControllo = ora - settimana, adesso = ora)
        )
    }

    @Test
    fun `il giorno dopo no`() {
        assertFalse(
            ControlloSettimanale.deveGirare(true, ultimoControllo = ora - giorno, adesso = ora)
        )
    }

    @Test
    fun `un orologio spostato indietro non congela i controlli per sempre`() {
        // Se l'ultimo controllo risulta nel futuro, aspettando non arriverebbe
        // mai il momento: si rifa' e basta.
        assertTrue(
            ControlloSettimanale.deveGirare(true, ultimoControllo = ora + settimana, adesso = ora)
        )
    }

    @Test
    fun `senza modalita' esperto non gira`() {
        // E' quello che e' stato chiesto: il rapporto parla di whitelist, di
        // online-mode e di password RCON, e a chi non le conosce direbbe che
        // qualcosa non va senza dargli modo di capire cosa.
        assertFalse(ControlloSettimanale.deveGirare(esperto = false, ultimoControllo = 0, adesso = ora))
        assertFalse(
            ControlloSettimanale.deveGirare(false, ultimoControllo = ora - settimana * 10, adesso = ora)
        )
    }

    @Test
    fun `si sa quanto manca alla prossima`() {
        // Arrotondato per eccesso: «fra 0 giorni» quando mancano sei ore e'
        // peggio che «domani».
        assertEquals(7, ControlloSettimanale.giorniAllaProssima(ora, ora))
        assertEquals(1, ControlloSettimanale.giorniAllaProssima(ora - settimana + giorno / 4, ora))
        assertEquals(0, ControlloSettimanale.giorniAllaProssima(ora - settimana, ora))
        assertEquals(0, ControlloSettimanale.giorniAllaProssima(0, ora))
    }

    // ------------------------------------------------------------ se parlare

    @Test
    fun `quando va tutto bene non si dice niente`() {
        assertFalse(ControlloSettimanale.vaSegnalato(rapporto(Livello.ALTO)))
    }

    @Test
    fun `se c'e' qualcosa si parla`() {
        assertTrue(ControlloSettimanale.vaSegnalato(rapporto(Livello.MEDIO)))
        assertTrue(ControlloSettimanale.vaSegnalato(rapporto(Livello.BASSO)))
    }

    @Test
    fun `solo le cose gravi valgono per tutti`() {
        assertTrue(ControlloSettimanale.soloSeGrave(rapporto(Livello.BASSO)))
        assertFalse(ControlloSettimanale.soloSeGrave(rapporto(Livello.MEDIO)))
        assertFalse(ControlloSettimanale.soloSeGrave(rapporto(Livello.ALTO)))
    }

    // ------------------------------------------------------------ cosa dire

    @Test
    fun `il titolo dice subito quanto e' grave`() {
        assertTrue(ControlloSettimanale.titolo("casa", rapporto(Livello.BASSO)).contains("aperto"))
        assertTrue(ControlloSettimanale.titolo("casa", rapporto(Livello.BASSO)).contains("casa"))
    }

    @Test
    fun `il testo elenca le cose, non le conta`() {
        // «3 problemi» non dice niente e non fa aprire l'avviso. I titoli si'.
        val r = Rapporto(
            Livello.BASSO,
            listOf(
                Controllo("Chiunque puo' entrare", Esito.MALE, "", "", Peso.ALTO),
                Controllo("Password RCON debole", Esito.MALE, "", "", Peso.ALTO),
            )
        )
        val testo = ControlloSettimanale.testo(r)
        assertTrue(testo.contains("Chiunque puo' entrare"))
        assertTrue(testo.contains("Password RCON debole"))
    }

    @Test
    fun `con tante cose se ne dicono tre e si dice quante restano`() {
        // In una notifica ci stanno tre titoli. Le altre si contano, cosi' chi
        // legge sa che aprendo trova di piu'.
        val r = Rapporto(Livello.BASSO, List(6) { Controllo("problema $it", Esito.MALE, "", "") })
        val testo = ControlloSettimanale.testo(r)
        assertTrue("non dice quante ne restano: $testo", testo.contains("altre 3"))
        assertTrue(testo.contains("problema 0"))
        assertFalse("ne elenca troppe: $testo", testo.contains("problema 4"))
    }

    @Test
    fun `i problemi gravi vengono prima delle attenzioni`() {
        val r = Rapporto(
            Livello.BASSO,
            listOf(
                Controllo("solo da guardare", Esito.ATTENZIONE, "", ""),
                Controllo("grave davvero", Esito.MALE, "", ""),
            )
        )
        val testo = ControlloSettimanale.testo(r)
        assertTrue(
            "l'attenzione viene prima del problema: $testo",
            testo.indexOf("grave davvero") < testo.indexOf("solo da guardare")
        )
    }

    @Test
    fun `un rapporto senza rilievi non produce un testo vuoto`() {
        assertTrue(ControlloSettimanale.testo(rapporto(Livello.ALTO)).isNotBlank())
    }
}

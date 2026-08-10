package com.bellizia.mcmonitor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Le note di GitHub sono in Markdown: nel banner deve arrivarci testo leggibile. */
class UpdateNotesTest {

    @Test
    fun `toglie titoli, grassetti, elenchi e collegamenti`() {
        val notes = """
            ### Novità

            **Preparazione di un server vuoto** — installa `LinuxGSM` nella home.

            - Requisiti verificati alla prima connessione
            - Vedi il [manuale](https://esempio.it/manuale.md)
        """.trimIndent()

        val plain = UpdateBanner.plainText(notes)
        assertFalse(plain.contains("*"))
        assertFalse(plain.contains("`"))
        assertFalse(plain.contains("#"))
        assertFalse(plain.contains("http"))
        assertEquals(
            "Preparazione di un server vuoto — installa LinuxGSM nella home. " +
                    "Requisiti verificati alla prima connessione Vedi il manuale",
            plain
        )
    }

    @Test
    fun `note vuote non producono spazzatura`() {
        assertEquals("", UpdateBanner.plainText("### Solo un titolo\n\n---\n"))
    }
}

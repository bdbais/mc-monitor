package com.bellizia.mcmonitor.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Una release può contenere più di un APK: quello buono e le varianti di prova.
 * Sbagliare file significa installare un'app diversa accanto a questa invece di
 * aggiornarla, e chi tocca il banner non se ne accorge finché non si ritrova due
 * icone uguali.
 */
class PickApkTest {

    private fun asset(name: String) = JSONObject().put("name", name)

    @Test
    fun preferisceIlFileConIlNomeDellaVersione() {
        val assets = listOf(
            asset("MC-Monitor-1.17-prova.apk"),
            asset("MC-Monitor-1.17.apk"),
            asset("MC-Monitor-1.17.aab")
        )
        assertEquals(
            "MC-Monitor-1.17.apk",
            UpdateChecker.pickApk(assets, "1.17")?.optString("name")
        )
    }

    @Test
    fun conUnNomeDiversoScartaLeVarianti() {
        val assets = listOf(
            asset("MC-Monitor-prova.apk"),
            asset("MC-Monitor-1.17-NON-FIRMATO.apk"),
            asset("mc-monitor-release.apk")
        )
        assertEquals(
            "mc-monitor-release.apk",
            UpdateChecker.pickApk(assets, "1.17")?.optString("name")
        )
    }

    @Test
    fun senzaApkNonSiProponeNiente() {
        val assets = listOf(asset("MC-Monitor-1.17.aab"), asset("note.txt"))
        assertNull(UpdateChecker.pickApk(assets, "1.17"))
    }

    @Test
    fun soloVariantiDiProvaNonSiPropongono() {
        val assets = listOf(asset("MC-Monitor-1.17-prova.apk"), asset("app-diag.apk"))
        assertNull(UpdateChecker.pickApk(assets, "1.17"))
    }
}

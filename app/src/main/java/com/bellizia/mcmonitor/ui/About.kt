package com.bellizia.mcmonitor.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import com.bellizia.mcmonitor.databinding.DialogAboutBinding
import com.bellizia.mcmonitor.update.UpdateChecker
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Manuale e riconoscimenti: gli stessi in ogni schermata che li offre, così non
 * si sdoppiano quando si aggiunge un componente.
 */
object About {

    const val MANUAL_URL = "https://github.com/bdbais/mc-monitor/blob/main/MANUALE.md"

    /** Il manuale sta nel repository: si apre nel browser, sempre aggiornato. */
    fun manual(activity: Activity) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(MANUAL_URL))
        runCatching { activity.startActivity(intent) }
            .onFailure {
                Toast.makeText(activity, "Nessuna app per aprire i collegamenti", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * L'app sta in piedi sul lavoro di altri, e i collegamenti servono anche a
     * chi volesse capire come funziona il proprio server.
     */
    fun show(activity: Activity) {
        val text = """
            MC Monitor ${UpdateChecker.currentVersion(activity)}
            Licenza Apache 2.0

            Codice sorgente, release e manuale
            https://github.com/bdbais/mc-monitor

            Costruita insieme a Claude di Anthropic
            https://claude.com/claude-code

            Minecraft è di Mojang Studios. Questa app non è affiliata né approvata da Mojang o Microsoft.
            https://www.minecraft.net

            Dove cercare quando qualcosa non torna
            Wiki di Minecraft (comandi, oggetti, meccaniche)
            https://minecraft.wiki
            Elenco dei comandi della console
            https://minecraft.wiki/w/Commands
            server.properties, riga per riga
            https://minecraft.wiki/w/Server.properties
            Documentazione di LinuxGSM per il server Minecraft
            https://docs.linuxgsm.com/game-servers/minecraft
            Configurazione di LinuxGSM (mcserver.cfg)
            https://docs.linuxgsm.com/configuration/game-server-config

            LinuxGSM, il sistema che gestisce il server di gioco
            https://linuxgsm.com

            Modrinth, da cui arrivano mod e modpack
            https://modrinth.com

            FabricMC, il mod loader installabile dall'app
            https://fabricmc.net

            Elenco ufficiale delle versioni di Minecraft
            https://piston-meta.mojang.com

            Componenti di terze parti
            mwiede/jsch (client SSH, BSD 3-Clause)
            https://github.com/mwiede/jsch
            AndroidX e Material Components (Apache 2.0)
            Font Press Start 2P (SIL Open Font License 1.1)
        """.trimIndent()

        val about = DialogAboutBinding.inflate(LayoutInflater.from(activity))
        about.credits.text = text
        MaterialAlertDialogBuilder(activity)
            .setTitle("Informazioni")
            .setView(about.root)
            .setPositiveButton("Chiudi", null)
            .show()
    }
}

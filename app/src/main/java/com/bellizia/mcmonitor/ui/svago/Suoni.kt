package com.bellizia.mcmonitor.ui.svago

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Suona quello che [Sintesi] produce.
 *
 * Ogni suono ha la sua traccia, riempita una volta sola alla prima richiesta e
 * poi riusata: farne una nuova a ogni picconata vorrebbe dire allocare e
 * liberare memoria audio dieci volte al secondo, e su un telefono lento si
 * sentirebbe come un ritardo fra il dito e il colpo.
 *
 * Chiudendo la schermata **si liberano**: una traccia audio è una risorsa di
 * sistema, e lasciarne sette aperte per una partita finita è il genere di cosa
 * che si nota solo quando il telefono comincia a scaldarsi.
 */
class Suoni {

    private val tracce = HashMap<String, AudioTrack>()

    /** Spento vuol dire spento: non si genera nemmeno l'onda. */
    var acceso: Boolean = true

    private fun traccia(nome: String, onda: () -> ShortArray): AudioTrack? = runCatching {
        tracce.getOrPut(nome) {
            val dati = onda()
            val byte = dati.size * 2
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(Sintesi.CAMPIONI_AL_SECONDO)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(byte)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
                .apply { write(dati, 0, dati.size) }
        }
    }.getOrNull()

    /**
     * Fa partire un suono dall'inizio, anche se stava già suonando.
     *
     * Un audio che non riparte fa perdere il colpo: si danno tre picconate in
     * fretta e se ne sente una. Meglio troncare quella prima.
     */
    private fun suona(nome: String, onda: () -> ShortArray) {
        if (!acceso) return
        val t = traccia(nome, onda) ?: return
        runCatching {
            t.stop()
            t.reloadStaticData()
            t.play()
        }
    }

    fun picconata(durezza: Int) = suona("picconata$durezza") { Sintesi.picconata(durezza) }
    fun rotto() = suona("rotto") { Sintesi.rotto() }
    fun raccolto() = suona("raccolto") { Sintesi.raccolto() }
    fun vinto() = suona("vinto") { Sintesi.vinto() }
    fun rifiutato() = suona("rifiutato") { Sintesi.rifiutato() }
    fun persa() = suona("persa") { Sintesi.persa() }

    /**
     * La scivolata dura quanto la strada: una traccia per lunghezza, tenute da
     * parte come le altre. Le lunghezze possibili sono poche — quante sono le
     * caselle di un campo — quindi non diventano mai tante.
     */
    fun scivolata(caselle: Int) {
        if (caselle <= 0) return
        suona("scivolata$caselle") { Sintesi.scivolata(caselle) }
    }

    fun chiudi() {
        tracce.values.forEach { runCatching { it.stop(); it.release() } }
        tracce.clear()
    }
}

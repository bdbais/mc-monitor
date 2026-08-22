package com.bellizia.mcmonitor.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.PlayerTracker
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.databinding.FragmentMapBinding
import com.bellizia.mcmonitor.lgsm.PlayerPos
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scheda "Mappa": piano X/Z navigabile con la posizione dei giocatori e le loro
 * scie. Se è configurata una mappa web (Dynmap/BlueMap) la apre a tutto schermo.
 */
class MapFragment : Fragment() {

    private var _b: FragmentMapBinding? = null
    private val b get() = _b!!
    private var dimension = "minecraft:overworld"
    private var busy = false
    private var firstFit = true
    private var syncingChips = false
    private var focusZoomApplied: String? = null

    private val clock = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /** Ultimo conteggio ricevuto: serve a spiegare una mappa vuota. */
    private var onlineCount = 0
    private var positionCount = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentMapBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.btnFit.setOnClickListener { b.map.fitAll() }
        b.btnZoomIn.setOnClickListener { b.map.zoomBy(1.5f) }
        b.btnZoomOut.setOnClickListener { b.map.zoomBy(1 / 1.5f) }
        b.btnRefresh.setOnClickListener { poll(force = true) }
        b.btnWebMap.setOnClickListener { openWebMap() }
        b.btnClearTrails.setOnClickListener {
            PlayerTracker.clear()
            render(emptyList())
            toast("Scie azzerate")
        }

        b.btnUnfollow.setOnClickListener {
            PlayerTracker.focus = null
            focusZoomApplied = null
            b.btnUnfollow.visible(false)
            b.selection.text = "Tocca un giocatore per i dettagli"
        }

        b.dimensions.setOnCheckedStateChangeListener { group, _ ->
            if (syncingChips) return@setOnCheckedStateChangeListener
            dimension = when (group.checkedChipId) {
                b.chipNether.id -> "minecraft:the_nether"
                b.chipEnd.id -> "minecraft:the_end"
                else -> "minecraft:overworld"
            }
            render(PlayerTracker.online)
            // Inquadrare tutti annullerebbe l'aggancio al giocatore seguito.
            if (PlayerTracker.focus == null) b.map.fitAll()
        }

        b.map.onPlayerTap = { player ->
            b.selection.text = "${player.name} · X ${player.x.toInt()} Y ${player.y.toInt()} " +
                    "Z ${player.z.toInt()} · ${player.shortDimension}"
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                render(PlayerTracker.online)
                while (isActive) {
                    if (b.autoTrack.isChecked) poll(force = false)
                    delay(Prefs.load().mapPollSeconds.coerceIn(3, 120) * 1000L)
                }
            }
        }
    }

    private fun poll(force: Boolean) {
        if (busy) return
        if (!Prefs.load().isComplete) {
            b.info.text = "Server non configurato."
            return
        }
        busy = true
        if (force) b.progress.visible(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { McRepository.online(withPositions = true) }
            busy = false
            val bind = _b ?: return@launch
            bind.progress.visible(false)
            result.onSuccess { snap ->
                onlineCount = snap.names.size
                positionCount = snap.positions.size
                render(snap.positions)
                val missing = snap.names.size - snap.positions.size
                bind.info.text = buildString {
                    append("${snap.names.size} online")
                    snap.maxPlayers?.let { append("/$it") }
                    append(" · aggiornato ${clock.format(Date())}")
                    if (missing > 0) append(" · $missing senza posizione")
                }
                if (firstFit && snap.positions.isNotEmpty()) {
                    firstFit = false
                    bind.map.fitAll()
                }
            }.onFailure {
                bind.info.text = it.userMessage()
            }
        }
    }

    private fun render(all: List<PlayerPos>) {
        val bind = _b ?: return
        val followed = PlayerTracker.focus?.let { name ->
            all.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
        // Seguire un giocatore che è passato nel Nether senza cambiare filtro
        // mostrerebbe una mappa vuota: la dimensione si adegua da sola.
        if (followed != null && followed.dimension != dimension) selectDimension(followed.dimension)

        val visible = all.filter { it.dimension == dimension }
        val trails = visible.associate { it.name to PlayerTracker.trail(it.name) }
        bind.map.setData(visible, trails)
        bind.empty.visible(visible.isEmpty())
        // Una mappa vuota ha tre cause diverse, e dirle evita di dare la colpa
        // all'app: nessuno collegato, nessuna posizione, o tutti altrove.
        bind.empty.text = when {
            onlineCount == 0 ->
                "Nessuno sta giocando adesso.\nLa mappa mostra i giocatori: senza di loro resta vuota."
            positionCount == 0 ->
                "Ci sono $onlineCount giocatori, ma il server non ha dato le posizioni.\n" +
                        "Succede quando qualcuno è appena entrato o sta ancora caricando: riprova fra qualche secondo."
            else -> "Nessun giocatore in questa dimensione: prova gli altri filtri qui sopra."
        }

        bind.btnUnfollow.visible(PlayerTracker.focus != null)
        if (followed != null) {
            // Zoom ravvicinato solo al primo aggancio: dopo comanda l'utente.
            val minScale = if (focusZoomApplied == followed.name) 0f else 0.9f
            focusZoomApplied = followed.name
            bind.map.centerOn(followed.x, followed.z, minScale)
            bind.selection.text = "Segue ${followed.name} · X ${followed.x.toInt()} " +
                    "Y ${followed.y.toInt()} Z ${followed.z.toInt()} · ${followed.shortDimension}"
        } else if (PlayerTracker.focus != null) {
            bind.selection.text = "${PlayerTracker.focus} non è più raggiungibile: posizione sconosciuta"
        }
    }

    private fun selectDimension(target: String) {
        dimension = target
        val bind = _b ?: return
        syncingChips = true
        bind.dimensions.check(
            when (target) {
                "minecraft:the_nether" -> bind.chipNether.id
                "minecraft:the_end" -> bind.chipEnd.id
                else -> bind.chipOverworld.id
            }
        )
        syncingChips = false
    }

    override fun onResume() {
        super.onResume()
        // Arrivando qui dalla scheda Giocatori il giocatore va inquadrato subito.
        if (PlayerTracker.focus != null) {
            render(PlayerTracker.online)
            poll(force = true)
        }
    }

    private fun openWebMap() {
        val url = Prefs.load().webMapUrl.trim()
        if (url.isBlank()) {
            toast("Imposta l'URL di Dynmap/BlueMap nelle Impostazioni")
            return
        }
        startActivity(Intent(requireContext(), WebMapActivity::class.java).putExtra("url", url))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}

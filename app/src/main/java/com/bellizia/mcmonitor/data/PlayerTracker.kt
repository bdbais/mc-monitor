package com.bellizia.mcmonitor.data

import com.bellizia.mcmonitor.lgsm.PlayerPos

/**
 * Tiene in memoria l'ultima posizione nota di ogni giocatore e la scia dei suoi
 * spostamenti, così la mappa può mostrare il movimento e non solo il punto attuale.
 */
object PlayerTracker {

    private const val MAX_TRAIL = 240
    private const val MIN_STEP = 0.75 // blocchi: sotto questa soglia è solo jitter

    private val trails = LinkedHashMap<String, MutableList<PlayerPos>>()

    @Volatile
    var lastUpdate: Long = 0L
        private set

    @Volatile
    var online: List<PlayerPos> = emptyList()
        private set

    /** Giocatore che la mappa deve inseguire, impostato dalla scheda Giocatori. */
    @Volatile
    var focus: String? = null

    @Synchronized
    fun update(positions: List<PlayerPos>) {
        online = positions
        lastUpdate = System.currentTimeMillis()
        positions.forEach { pos ->
            val trail = trails.getOrPut(pos.name) { mutableListOf() }
            val last = trail.lastOrNull()
            val moved = last == null ||
                    last.dimension != pos.dimension ||
                    Math.hypot(last.x - pos.x, last.z - pos.z) >= MIN_STEP
            if (moved) {
                trail.add(pos)
                while (trail.size > MAX_TRAIL) trail.removeAt(0)
            }
        }
    }

    @Synchronized
    fun trail(name: String): List<PlayerPos> = trails[name]?.toList() ?: emptyList()

    @Synchronized
    fun clear() {
        trails.clear()
        online = emptyList()
        lastUpdate = 0L
    }
}

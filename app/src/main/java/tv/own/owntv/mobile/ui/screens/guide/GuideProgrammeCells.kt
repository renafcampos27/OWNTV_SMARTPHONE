package tv.own.owntv.mobile.ui.screens.guide

import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import java.util.TreeMap
import java.util.TreeSet

/** Display bounds are separate from the original programme used for replay/download requests. */
internal data class GuideProgrammeCell(val programme: EpgProgrammeEntity, val startMs: Long, val endMs: Long)

internal fun guideProgrammeCells(rows: List<EpgProgrammeEntity>, window: GuideWindow): List<GuideProgrammeCell> {
    if (window.end <= window.start) return emptyList()
    data class Edge(val entering: MutableList<Int> = mutableListOf(), val leaving: MutableList<Int> = mutableListOf())
    val edges = TreeMap<Long, Edge>()
    rows.forEachIndexed { index, programme ->
        val start = maxOf(window.start, programme.startMs)
        val end = minOf(window.end, programme.stopMs)
        if (end > start) {
            edges.getOrPut(start) { Edge() }.entering.add(index)
            edges.getOrPut(end) { Edge() }.leaving.add(index)
        }
    }
    // A later start overrides a long enclosing event. Equal starts retain the reader's source priority.
    val active = TreeSet<Int>(compareBy<Int> { rows[it].startMs }.thenByDescending { it })
    val result = mutableListOf<GuideProgrammeCell>()
    var previous: Long? = null
    edges.forEach { (time, edge) ->
        val from = previous
        if (from != null && time > from && active.isNotEmpty()) {
            val programme = rows[active.last()]
            val last = result.lastOrNull()
            if (last?.programme === programme && last.endMs == from) result[result.lastIndex] = last.copy(endMs = time)
            else result.add(GuideProgrammeCell(programme, from, time))
        }
        edge.leaving.forEach(active::remove)
        edge.entering.forEach(active::add)
        previous = time
    }
    return result
}

internal fun guideVisibleCells(cells: List<GuideProgrammeCell>, from: Long, until: Long): List<GuideProgrammeCell> =
    cells.filter { it.endMs > from && it.startMs < until }

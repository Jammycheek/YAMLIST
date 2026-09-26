package com.example.yamlist.domain.progress

/**
 * Display-order policy for tasks within one hierarchy level (spec §2.5–§2.8).
 *
 * Ordering is scoped per level: root tasks of a project are numbered among
 * themselves, and children are numbered among siblings sharing the same
 * parentTaskId. Numbers are allocated in steps of [STEP] so that a task can
 * later be dropped between two neighbours without renumbering the whole level.
 */
object TaskOrdering {

    /** Gap between consecutive siblings, leaving room for manual insertion. */
    const val STEP = 1000L

    /**
     * Next order value for a new sibling appended at the end of a level.
     *
     * [currentMax] is the largest existing order in that level, or a negative
     * value when the level is still empty; the first task then gets [STEP].
     */
    fun nextOrder(currentMax: Long): Long =
        (if (currentMax < 0) 0L else currentMax) + STEP

    /**
     * Order values for [count] tasks appended one after another to a level whose
     * current maximum is [currentMax]. Used by bulk creation (spec §1.9).
     */
    fun appendOrders(currentMax: Long, count: Int): List<Long> {
        val base = if (currentMax < 0) 0L else currentMax
        return (1..count).map { base + it * STEP }
    }

    /**
     * Renumbers a level after a manual reorder (spec §2.8): the given ids, in
     * their new visual order, are re-spaced to STEP, 2*STEP, 3*STEP...
     */
    fun renumber(orderedIds: List<Long>): List<Pair<Long, Long>> =
        orderedIds.mapIndexed { index, id -> id to (index + 1) * STEP }
}

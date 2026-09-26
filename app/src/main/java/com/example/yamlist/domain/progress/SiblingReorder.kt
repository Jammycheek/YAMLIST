package com.example.yamlist.domain.progress

/**
 * Index arithmetic for moving a task up or down among its same-level siblings
 * (spec addendum: order changes within a level).
 *
 * Kept pure so the "what should happen" question is answered without a database:
 * the caller supplies the sibling ids in their current displayOrder, and this
 * says which two should swap places.
 */
object SiblingReorder {

    /** -1 = toward the front (earlier/shallower position), +1 = toward the back. */
    const val UP = -1
    const val DOWN = 1

    /**
     * Returns the (currentIndex, targetIndex) pair to swap, or null when
     * [taskId] is missing from [siblingIds] or already at that end of the list.
     */
    fun swapIndices(siblingIds: List<Long>, taskId: Long, direction: Int): Pair<Int, Int>? {
        val from = siblingIds.indexOf(taskId)
        if (from < 0) return null
        val to = from + direction
        if (to !in siblingIds.indices) return null
        return from to to
    }

    /**
     * [siblingIds] with [id] moved one step in [direction], or null for a no-op.
     * Callers renumber the whole result, so ties in stored order can't block a move.
     */
    fun moved(siblingIds: List<Long>, id: Long, direction: Int): List<Long>? {
        val (from, to) = swapIndices(siblingIds, id, direction) ?: return null
        return siblingIds.toMutableList().apply { add(to, removeAt(from)) }
    }
}

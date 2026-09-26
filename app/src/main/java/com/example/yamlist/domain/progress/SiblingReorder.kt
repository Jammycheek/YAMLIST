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
}

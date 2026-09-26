package com.example.yamlist.domain.progress

/**
 * Orders self-referencing rows so every row comes after its parent, which is
 * what a row-by-row insert under an immediate foreign key needs.
 */
object ParentFirstOrder {

    /**
     * Returns each item paired with `detached`: true when its parent is missing
     * from [items] or it sits in a parent cycle, meaning the caller must insert
     * it with no parent. Siblings keep their input order.
     */
    fun <T> sort(items: List<T>, id: (T) -> Long, parentId: (T) -> Long?): List<Pair<T, Boolean>> {
        val ids = items.mapTo(HashSet()) { id(it) }
        val children = items.groupBy { parentId(it) }
        val visited = HashSet<Long>()
        val out = ArrayList<Pair<T, Boolean>>(items.size)

        fun visit(item: T, detached: Boolean) {
            if (!visited.add(id(item))) return
            out.add(item to detached)
            children[id(item)]?.forEach { visit(it, false) }
        }

        items.filter { parentId(it).let { p -> p == null || p !in ids } }
            .forEach { visit(it, parentId(it) != null) }
        // Whatever is left can only be reached through a cycle.
        items.forEach { visit(it, true) }
        return out
    }
}

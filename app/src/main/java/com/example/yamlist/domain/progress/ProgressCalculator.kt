package com.example.yamlist.domain.progress

import com.example.yamlist.domain.model.ParentDisplayState
import com.example.yamlist.domain.model.TaskNode
import com.example.yamlist.domain.model.TaskStatus
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Aggregated progress over a subtree or a whole project (spec §8).
 *
 * Invariants enforced by [ProgressCalculator]:
 *  - Only leaf tasks that are progress targets count (spec §8.1).
 *  - Parent tasks never contribute their own weight/count (no double counting).
 *  - weight <= 0 targets are excluded from BOTH numerator and denominator (§8.4).
 *  - HOLD tasks are in the denominator but never the numerator (§8.6).
 *  - Non-target and deleted tasks are excluded (§8.1). Deletion is handled upstream
 *    by [TaskTreeBuilder]; this engine additionally skips non-targets.
 */
data class ProgressResult(
    val countTotal: Int,
    val countDone: Int,
    val weightTotal: Double,
    val weightDone: Double,
) {
    val hasTargets: Boolean get() = countTotal > 0

    /** Raw fraction 0.0..1.0, or 0.0 when there is nothing to measure (§8.5). */
    val countFraction: Double get() = if (countTotal == 0) 0.0 else countDone.toDouble() / countTotal
    val weightFraction: Double get() = if (weightTotal <= 0.0) 0.0 else weightDone / weightTotal

    /** Integer percent, rounded half-up for display (§8.7). */
    val countPercent: Int get() = percentOf(countFraction)
    val weightPercent: Int get() = percentOf(weightFraction)

    /** One-decimal percent for the detail screen (§8.7). */
    val countPercentPrecise: Double get() = round1(countFraction * 100.0)
    val weightPercentPrecise: Double get() = round1(weightFraction * 100.0)

    operator fun plus(other: ProgressResult) = ProgressResult(
        countTotal + other.countTotal,
        countDone + other.countDone,
        weightTotal + other.weightTotal,
        weightDone + other.weightDone,
    )

    companion object {
        val EMPTY = ProgressResult(0, 0, 0.0, 0.0)

        private fun percentOf(fraction: Double): Int =
            BigDecimal(fraction * 100.0).setScale(0, RoundingMode.HALF_UP).toInt()

        private fun round1(value: Double): Double =
            BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toDouble()
    }
}

object ProgressCalculator {

    /** Progress of a whole project given its resolved forest. */
    fun forForest(forest: List<TaskNode>): ProgressResult =
        forest.fold(ProgressResult.EMPTY) { acc, node -> acc + forSubtree(node) }

    /**
     * Progress contributed by a single node's subtree.
     * A leaf contributes iff it is a progress target with weight > 0.
     * A parent contributes only what its descendants contribute.
     */
    fun forSubtree(node: TaskNode): ProgressResult {
        if (node.isLeaf) return leafContribution(node)
        return node.children.fold(ProgressResult.EMPTY) { acc, child -> acc + forSubtree(child) }
    }

    private fun leafContribution(node: TaskNode): ProgressResult {
        val task = node.task
        if (!task.isProgressTarget) return ProgressResult.EMPTY
        // weight <= 0 => excluded from denominator and numerator (§8.4).
        if (task.weight <= 0.0) return ProgressResult.EMPTY
        val done = task.status == TaskStatus.DONE
        return ProgressResult(
            countTotal = 1,
            countDone = if (done) 1 else 0,
            weightTotal = task.weight,
            weightDone = if (done) task.weight else 0.0,
        )
    }

    /**
     * Derived display state for a parent task (spec §7.4).
     * Based on the count of leaf targets in the subtree, not on the parent's own status.
     */
    fun parentDisplayState(node: TaskNode): ParentDisplayState {
        val p = forSubtree(node)
        return when {
            p.countTotal == 0 -> ParentDisplayState.NO_TARGET
            p.countDone == 0 -> ParentDisplayState.NOT_STARTED
            p.countDone < p.countTotal -> ParentDisplayState.IN_PROGRESS
            else -> ParentDisplayState.DONE
        }
    }
}

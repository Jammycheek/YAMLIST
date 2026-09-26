package com.example.yamlist.ui.weightsheet

/** The same range as single-task editing and YAML import. Blank is an error here. */
object WeightSheetRules {
    fun parse(text: String): Double? = text.trim().toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0.0 && it <= 9999.0 }

    fun display(weight: Double): String =
        if (weight.isFinite() && weight % 1.0 == 0.0) weight.toLong().toString()
        else weight.toString()

    fun total(weights: List<Double>): Double = weights.filter { it.isFinite() && it > 0.0 }.sum()

    fun share(weight: Double, total: Double): Double =
        if (weight <= 0.0 || total <= 0.0) 0.0 else weight / total * 100.0
}

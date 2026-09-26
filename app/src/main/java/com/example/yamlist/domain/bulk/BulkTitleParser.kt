package com.example.yamlist.domain.bulk

/**
 * Parses the multi-line title box of the bulk child creation screen (spec §1.4)
 * and reports the validation problems listed in spec §1.8.
 *
 * Kept free of Android and database types so the whole line-handling contract
 * can be unit-tested directly.
 */
object BulkTitleParser {

    /** Maximum tasks creatable in one go (spec §1.8). */
    const val MAX_COUNT = 100

    /** Maximum length of a single title (spec §1.8). */
    const val MAX_TITLE_LENGTH = 200

    /** Bullet prefixes optionally stripped from the head of a line (spec §1.4). */
    private val BULLET_PREFIXES = listOf("- ", "-\t", "・", "* ", "＊")

    /**
     * Characters treated as blank. Beyond normal whitespace this covers the
     * ideographic space, which is easy to leave behind when editing on a phone
     * and would otherwise create an invisible, untitled task.
     */
    private const val BLANK_CHARS = " \t\u3000\u00A0"

    sealed interface Problem {
        /** No usable title line at all. */
        data object Empty : Problem

        /** [lineNumber] is 1-based as shown to the user. */
        data class TooLong(val lineNumber: Int, val length: Int) : Problem

        data class TooMany(val count: Int) : Problem
    }

    data class Result(
        val titles: List<String>,
        val problems: List<Problem>,
    ) {
        val isValid: Boolean get() = problems.isEmpty()
        val count: Int get() = titles.size
    }

    /**
     * Splits [text] into titles.
     *
     * Blank lines (including tab / ideographic-space-only lines) are dropped,
     * surrounding whitespace is trimmed, and when [stripBullets] is true a
     * leading "- " or "・" is removed. Duplicate titles are allowed on purpose:
     * repeated check items are normal on a site checklist.
     *
     * Leading numbers are intentionally left alone in this version, since
     * "1." may legitimately be part of an equipment tag.
     */
    fun parse(text: String, stripBullets: Boolean = true): Result {
        val problems = ArrayList<Problem>()
        val titles = ArrayList<String>()

        text.split('\n').forEachIndexed { index, rawLine ->
            var line = rawLine.trim { it in BLANK_CHARS || it == '\r' }
            if (line.isEmpty()) return@forEachIndexed

            if (stripBullets) {
                val hit = BULLET_PREFIXES.firstOrNull { line.startsWith(it) }
                if (hit != null) {
                    line = line.removePrefix(hit).trim { it in BLANK_CHARS }
                    if (line.isEmpty()) return@forEachIndexed
                }
            }

            if (line.length > MAX_TITLE_LENGTH) {
                problems.add(Problem.TooLong(index + 1, line.length))
                return@forEachIndexed
            }
            titles.add(line)
        }

        if (titles.isEmpty() && problems.isEmpty()) problems.add(Problem.Empty)
        if (titles.size > MAX_COUNT) problems.add(Problem.TooMany(titles.size))

        return Result(titles, problems)
    }
}

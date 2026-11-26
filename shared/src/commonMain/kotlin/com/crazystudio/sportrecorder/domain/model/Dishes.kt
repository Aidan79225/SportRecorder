package com.crazystudio.sportrecorder.domain.model

/**
 * What a meal had on the plate: plain text the user typed, never classified or scored. A meal's dishes
 * are a short list of clean names; this object is the one place that decides what "clean" means, so the
 * editor, the database column and a restored snapshot all agree.
 *
 * Stored as one dish per line ([encode] / [decode]); [normalize] guarantees a name never holds a newline.
 */
object Dishes {
    const val MAX_PER_MEAL = 10
    const val MAX_LENGTH = 30

    private const val SEPARATOR = '\n'

    /**
     * [name] trimmed, every run of whitespace (U+3000, newlines and tabs included) folded to one space,
     * other control characters dropped, and cut to [MAX_LENGTH]; `null` when nothing is left.
     */
    fun normalize(name: String): String? {
        val folded = StringBuilder()
        var pendingSpace = false
        for (c in name) {
            when {
                c.isWhitespace() -> pendingSpace = folded.isNotEmpty()
                c.isISOControl() -> Unit
                else -> {
                    if (pendingSpace) folded.append(' ')
                    pendingSpace = false
                    folded.append(c)
                }
            }
        }
        return folded.toString().take(MAX_LENGTH).trimEnd().ifEmpty { null }
    }

    /**
     * [dishes] cleaned: each [normalize]d, blanks dropped, repeats dropped case-insensitively (the first
     * spelling wins), and only the first [MAX_PER_MEAL] kept.
     */
    fun sanitize(dishes: List<String>): List<String> {
        val seen = HashSet<String>()
        val kept = ArrayList<String>()
        for (raw in dishes) {
            if (kept.size >= MAX_PER_MEAL) break
            val name = normalize(raw) ?: continue
            if (seen.add(name.lowercase())) kept += name
        }
        return kept
    }

    /** The column value for [dishes]: one per line, or `null` when there are none (the column's "none"). */
    fun encode(dishes: List<String>): String? =
        sanitize(dishes).takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR.toString())

    /** The dishes in [stored], sanitized: a hand-edited or future value never reaches the UI unclean. */
    fun decode(stored: String?): List<String> =
        if (stored.isNullOrEmpty()) emptyList() else sanitize(stored.split(SEPARATOR))

    const val DEFAULT_SUGGESTION_LIMIT = 6

    /**
     * Earlier dishes worth offering while the user types: distinct (case-insensitive) across [records],
     * most used first then most recently eaten, minus those already in [chosen], and only those that
     * contain [typed] (case-insensitive; blank [typed] matches all). The names come from the user's own
     * history; no count is ever returned.
     */
    fun suggestions(
        records: List<EatRecord>,
        typed: String,
        chosen: List<String>,
        limit: Int = DEFAULT_SUGGESTION_LIMIT,
    ): List<String> {
        val needle = normalize(typed)?.lowercase().orEmpty()
        val taken = chosen.mapNotNullTo(HashSet()) { normalize(it)?.lowercase() }
        class Seen(val name: String, var uses: Int, var latest: Long)
        val seen = LinkedHashMap<String, Seen>()
        for (record in records) {
            for (dish in sanitize(record.dishes)) {
                val key = dish.lowercase()
                val entry = seen.getOrPut(key) { Seen(dish, 0, record.time) }
                entry.uses++
                if (record.time > entry.latest) entry.latest = record.time
            }
        }
        return seen.values
            .filter { it.name.lowercase() !in taken && it.name.lowercase().contains(needle) }
            .sortedWith(compareByDescending<Seen> { it.uses }.thenByDescending { it.latest })
            .take(limit)
            .map { it.name }
    }
}

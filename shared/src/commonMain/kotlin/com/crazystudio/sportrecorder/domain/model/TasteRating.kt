package com.crazystudio.sportrecorder.domain.model

/**
 * How a meal tasted to the person who ate it, in the moment: five levels, [MIN] (not tasty) to [MAX]
 * (very tasty). Rating is optional and nothing asks for it, so a meal with no rating carries `null` —
 * "not rated", never a middle value. The number is only how a level is stored; the UI shows a face and
 * a word, never a score, and nothing is derived from it.
 */
object TasteRating {
    const val MIN = 1
    const val MAX = 5

    /** The levels, lowest first: the order the editor lays the faces out in. */
    val LEVELS: List<Int> = (MIN..MAX).toList()

    /**
     * [value] when it is one of the [LEVELS], otherwise `null`. A stored row or a snapshot is data from
     * outside, so a value off the scale reads as "not rated" instead of reaching the UI.
     */
    fun sanitize(value: Int?): Int? = value?.takeIf { it in MIN..MAX }
}

package com.crazystudio.sportrecorder.domain.diet

import com.crazystudio.sportrecorder.domain.model.FastingWindow

/** Built-in fasting windows. Single source of truth for the dedup rule in CreateCustomFastingTypeUseCase. */
@Suppress("MagicNumber") // declarative reference data; naming each hour as a const adds no clarity
object DefaultFastingWindows {
    val all: List<FastingWindow> = listOf(
        FastingWindow(14, 10),
        FastingWindow(16, 8),
        FastingWindow(20, 4),
        FastingWindow(23, 1),
        FastingWindow(47, 1),
    )
}

package com.crazystudio.sportrecorder.ui.insights

import com.crazystudio.sportrecorder.domain.insights.InsightsResult
import com.crazystudio.sportrecorder.domain.insights.Period

data class InsightsUiState(
    val period: Period = Period.MONTH,
    val monthAnchor: Long = 0L,
    val result: InsightsResult = InsightsResult.EMPTY,
)

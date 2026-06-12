package com.crazystudio.sportrecorder.domain.model

/** A fasting/eating window in hours. Reused for built-in defaults, custom types, and the selection. */
data class FastingWindow(val fastingHours: Long, val eatingHours: Long)

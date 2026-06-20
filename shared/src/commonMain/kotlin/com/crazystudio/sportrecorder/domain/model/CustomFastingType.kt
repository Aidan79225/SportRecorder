package com.crazystudio.sportrecorder.domain.model

/** A stored custom fasting window plus its optional, user-given name. */
data class CustomFastingType(
    val fastingHours: Long,
    val eatingHours: Long,
    val name: String? = null,
)

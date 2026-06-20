package com.crazystudio.sportrecorder.data.mapper

import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.entity.FastingType

fun FastingType.toCustomType(): CustomFastingType =
    CustomFastingType(fastingHours = fastingHours, eatingHours = eatingHours, name = name)

package com.crazystudio.sportrecorder.data.mapper

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.entity.FastingType

fun FastingType.toDomain(): FastingWindow = FastingWindow(fastingHours = fastingHours, eatingHours = eatingHours)

package com.crazystudio.sportrecorder.ui.diet.create.fasting

import androidx.lifecycle.ViewModel
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.usecase.CreateCustomFastingTypeUseCase

class CreateFastingTypeViewModel constructor(
    private val createCustomType: CreateCustomFastingTypeUseCase,
) : ViewModel() {

    suspend fun createCustomFastingType(
        fastingHours: Long,
        eatingHours: Long,
        name: String = "",
    ): Boolean = createCustomType(FastingWindow(fastingHours, eatingHours), name.ifBlank { null })
}

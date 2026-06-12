package com.crazystudio.sportrecorder.ui.diet.create.fasting

import androidx.lifecycle.ViewModel
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.usecase.CreateCustomFastingTypeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CreateFastingTypeViewModel @Inject constructor(
    private val createCustomType: CreateCustomFastingTypeUseCase,
) : ViewModel() {

    suspend fun createCustomFastingType(fastingHours: Long, eatingHours: Long): Boolean =
        createCustomType(FastingWindow(fastingHours, eatingHours))
}

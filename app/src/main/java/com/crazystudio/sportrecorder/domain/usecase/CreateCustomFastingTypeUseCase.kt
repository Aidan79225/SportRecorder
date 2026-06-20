package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.diet.DefaultFastingWindows
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository

class CreateCustomFastingTypeUseCase constructor(
    private val repository: FastingTypeRepository,
) {
    /** Adds a custom window unless it duplicates a built-in default or an existing custom one. */
    suspend operator fun invoke(window: FastingWindow, name: String? = null): Boolean {
        if (window in DefaultFastingWindows.all || repository.exists(window)) return false
        repository.add(window, name)
        return true
    }
}

package com.crazystudio.sportrecorder.ui.diet.create.eating

data class CreateEatTimeUiState(
    val date: java.util.Calendar,
    val foods: List<com.crazystudio.sportrecorder.entity.FoodRecord> = emptyList(),
)

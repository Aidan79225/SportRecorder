package com.crazystudio.sportrecorder.ui.nav

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Diet : Route
    @Serializable data object Record : Route
    @Serializable data object Notifications : Route

    @Serializable data object SelectFastingType : Route
    @Serializable data object CreateFastingType : Route
    @Serializable data object CreateEatTime : Route
    @Serializable data class CreateFoodRecord(val eatTimeId: Long) : Route
}

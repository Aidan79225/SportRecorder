package com.crazystudio.sportrecorder.ui.nav

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Diet : Route

    @Serializable data object Record : Route

    @Serializable data object Insights : Route

    @Serializable data object SelectFastingType : Route

    @Serializable data object CreateFastingType : Route

    @Serializable data class EatTimeEditor(val eatTimeId: Int = 0) : Route
}

package com.crazystudio.sportrecorder.ui.nav

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Diet : Route

    @Serializable data object Record : Route

    @Serializable data object Insights : Route

    @Serializable data object Settings : Route

    @Serializable data object Backup : Route

    @Serializable data object SelectFastingType : Route

    @Serializable data object CreateFastingType : Route

    /** [sharedText]: what another app (Google Maps) shared into a new meal (#84); null otherwise. */
    @Serializable data class EatTimeEditor(val eatTimeId: Int = 0, val sharedText: String? = null) : Route

    /** One eating day's meals, read-only, as a sheet over Insights. [dayStart] is local midnight millis. */
    @Serializable data class DayRecords(val dayStart: Long) : Route
}

package com.crazystudio.sportrecorder.ui.diet.editor

import com.crazystudio.sportrecorder.domain.model.EatPhoto

data class EatTimeEditorUiState(
    val dateMillis: Long = 0L, // the meal's date+time as epoch millis; formatted for display
    val isEditMode: Boolean = false,
    val note: String = "",
    val existingPhotos: List<EatPhoto> = emptyList(), // already-saved photos (edit mode)
    val pendingPhotos: List<String> = emptyList(), // newly captured webp file names
    val location: LatLng? = null,
    val locationStatus: LocationStatus = LocationStatus.IDLE,
) {
    data class LatLng(val lat: Double, val lng: Double)
    enum class LocationStatus { IDLE, LOADING, AVAILABLE, UNAVAILABLE }
}

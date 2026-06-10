package com.crazystudio.sportrecorder.ui.diet.editor

import com.crazystudio.sportrecorder.entity.Photo
import java.util.Calendar

data class EatTimeEditorUiState(
    val date: Calendar,
    val isEditMode: Boolean = false,
    val note: String = "",
    val existingPhotos: List<Photo> = emptyList(),   // already-saved photos (edit mode)
    val pendingPhotos: List<String> = emptyList(),    // newly captured webp file names
    val location: LatLng? = null,
    val locationStatus: LocationStatus = LocationStatus.IDLE,
) {
    data class LatLng(val lat: Double, val lng: Double)
    enum class LocationStatus { IDLE, LOADING, AVAILABLE, UNAVAILABLE }
}

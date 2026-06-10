package com.crazystudio.sportrecorder.ui.diet.create.eating

import java.util.Calendar

data class CreateEatTimeUiState(
    val date: Calendar,
    val pendingPhotos: List<String> = emptyList(), // webp file names already written to photosDir
    val location: LatLng? = null,
    val locationStatus: LocationStatus = LocationStatus.IDLE,
) {
    data class LatLng(val lat: Double, val lng: Double)
    enum class LocationStatus { IDLE, LOADING, AVAILABLE, UNAVAILABLE }
}

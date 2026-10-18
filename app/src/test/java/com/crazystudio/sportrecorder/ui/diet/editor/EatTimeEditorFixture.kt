package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.lifecycle.SavedStateHandle
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.usecase.LoadEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveEatRecordUseCase
import com.crazystudio.sportrecorder.domain.venue.VenueAddressFiller
import com.crazystudio.sportrecorder.fake.FakeAddressLookup
import com.crazystudio.sportrecorder.fake.FakeClipboardReader
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.fake.FakeLocationProvider
import com.crazystudio.sportrecorder.fake.FakeMapsLinkResolver
import com.crazystudio.sportrecorder.fake.FakePhotoFileStore
import com.crazystudio.sportrecorder.fake.FakePhotoImageSource
import com.crazystudio.sportrecorder.fake.FakePhotoImporter
import com.crazystudio.sportrecorder.fake.FakeRemindersRescheduler
import com.crazystudio.sportrecorder.fake.FakeVenueRepository

// What the editor's venue tests share: the Maps texts, the places, and an editor built on fakes.

// What Google Maps hands over when a place is shared: name, address, and a short link (#84).
internal const val SHARE_TEXT = "鼎泰豐 信義店\n106臺北市大安區信義路二段194號\nhttps://maps.app.goo.gl/3oUGbkxLYE6jt7L29"

// The full URL that short link redirects to: pin and camera both at the restaurant.
internal const val FULL_URL = "https://www.google.com/maps/place/" +
    "%E9%BC%8E%E6%B3%B0%E8%B1%90+%E4%BF%A1%E7%BE%A9%E5%BA%97/" +
    "@25.0335035,121.530118,17z/data=!4m6!3m5!1s0x3442a9821392326d:0x93faef9e2359365c!8m2!3d25.0335035" +
    "!4d121.530118!16s%2Fg%2F1td2dltc?entry=tts"

// Another place, as a full link: opening it needs no resolver call.
internal const val ICHIRAN_URL = "https://www.google.com/maps/place/Ichiran/@25.0418,121.5503,17z"

private const val PAST_MEAL = 1_700_000_000_000L
internal val home = GeoPoint(24.0, 120.0)
internal val daHuWu = Venue(id = 1, name = "大戶屋", lat = 25.0, lng = 121.0, lastUsedAt = 1L)

// Where FULL_URL puts 鼎泰豐; the saved one below is about 53.5 km away from it.
internal val mapsPosition = GeoPoint(25.0335035, 121.530118)
internal val dinTaiFung = Venue(id = 1, name = "鼎泰豐 信義店", lat = 25.0, lng = 121.0, lastUsedAt = 1L)

internal fun mealAt(venue: Venue?, location: GeoPoint? = home) =
    EatRecord(id = 1, time = PAST_MEAL, location = location, note = "午餐", photos = emptyList(), venue = venue)

/** An editor over fakes; every short link resolves to [FULL_URL] unless a test says otherwise. */
internal class Editor(records: List<EatRecord> = emptyList(), venues: List<Venue> = emptyList()) {
    val records = FakeEatRecordRepository(records)
    val venues = FakeVenueRepository(venues)
    val locationProvider = FakeLocationProvider()
    val resolver = FakeMapsLinkResolver(location = FULL_URL)
    val lookup = FakeAddressLookup()
    val clipboard = FakeClipboardReader()

    fun viewModel(
        eatTimeId: Int? = null,
        sharedText: String? = null,
        handle: SavedStateHandle = SavedStateHandle(
            listOfNotNull(eatTimeId?.let { "eatTimeId" to it }, sharedText?.let { "sharedText" to it }).toMap(),
        ),
    ) = EatTimeEditorViewModel(
        loadEatRecord = LoadEatRecordUseCase(records),
        saveEatRecord = SaveEatRecordUseCase(records, FakeRemindersRescheduler()),
        locationProvider = locationProvider,
        venueRepository = venues,
        photoImporter = FakePhotoImporter(),
        photoFileStore = FakePhotoFileStore(),
        photoImageSource = FakePhotoImageSource(),
        savedStateHandle = handle,
        mapsLinkResolver = resolver,
        venueAddressFiller = VenueAddressFiller(lookup, venues),
        clipboardReader = clipboard,
    )
}

package com.crazystudio.sportrecorder.quickrecord

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.domain.usecase.ObserveDietStateUseCase
import com.crazystudio.sportrecorder.domain.usecase.QuickRecordMealUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 「記一餐」in the notification shade: one tap logs the moment without opening the app.
 *
 * The shade is a glance, not a dashboard, so the tile says only what it needs to — the last meal's
 * time, and the time it just recorded. No elapsed fasting clock and no progress: a tile sitting in
 * the shade counting down at someone would be the pressure this app exists to avoid.
 */
class QuickRecordTileService : TileService(), KoinComponent {

    private val quickRecordMeal: QuickRecordMealUseCase by inject()
    private val observeDietState: ObserveDietStateUseCase by inject()

    @Suppress("InjectDispatcher") // a service has no injected scope; Default suits these short reads
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val last = lastMealTime()
            setSubtitle(
                if (last == null) {
                    getString(R.string.quick_tile_none)
                } else {
                    getString(R.string.quick_tile_last, clockLabel(last))
                }
            )
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            // The shade usually closes on the tap, and System UI unbinds the service right after —
            // cancelling this scope. A meal the user already tapped for must still be written, so
            // the save itself is not cancellable.
            val at = withContext(NonCancellable) { quickRecordMeal() }
            setSubtitle(
                if (at == null) {
                    getString(R.string.quick_tile_failed)
                } else {
                    getString(R.string.quick_tile_saved, clockLabel(at))
                }
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun lastMealTime(): Long? =
        observeDietState(Clock.System.now().toEpochMilliseconds()).first().eatTimesAsc.lastOrNull()

    /** Tile changes are a binder call back to System UI; make them from the main thread. */
    private suspend fun setSubtitle(text: String) = withContext(Dispatchers.Main) {
        val tile = qsTile ?: return@withContext
        tile.state = Tile.STATE_ACTIVE
        // Tile.subtitle landed in Android 10; below that the label alone carries the tile.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = text
        }
        tile.updateTile()
    }

    private fun clockLabel(epochMillis: Long): String {
        val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
        return "%02d:%02d".format(local.hour, local.minute)
    }
}

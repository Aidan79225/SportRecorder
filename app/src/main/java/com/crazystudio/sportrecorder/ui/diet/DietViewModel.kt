package com.crazystudio.sportrecorder.ui.diet

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.ui.diet.select.FastingItem
import com.crazystudio.sportrecorder.util.Constants
import com.crazystudio.sportrecorder.util.DietPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@HiltViewModel
class DietViewModel @Inject constructor(
    private val eatTimeDao: EatTimeDao,
    private val dietPreference: DietPreference,
) : ViewModel() {

    /** Last merged eating interval (start, end). Null until first DB emission. */
    private data class DietData(
        val lastEatTime: Pair<Long, Long>?,
        val history: List<Pair<Long, Float>>,
    )

    /** Per-second ticker so elapsedText / progress recompute every second. */
    private val tickerFlow: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TimeUnit.SECONDS.toMillis(1))
        }
    }

    /** Emits whenever the diet SharedPreferences change (plus an initial emission). */
    private val prefsFlow: Flow<Unit> = callbackFlow {
        trySend(Unit)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(Unit)
        }
        dietPreference.preference.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            dietPreference.preference.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    /** DAO history, mapped into last-eat-time + per-day ratios (logic preserved verbatim). */
    private val dietDataFlow: Flow<DietData> = historyFlow().map { data ->
        DietData(
            lastEatTime = lastEatingTime(data),
            history = computeHistory(data),
        )
    }

    val uiState: StateFlow<DietUiState> =
        combine(dietDataFlow, prefsFlow, tickerFlow) { dietData, _, _ ->
            buildState(dietData)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DietUiState(),
        )

    private fun buildState(dietData: DietData): DietUiState {
        val eatingHours = dietPreference.preference.getLong(Constants.DIET_EATING_TIME_INTERVAL, 8)
        val fastingHours = dietPreference.preference.getLong(Constants.DIET_FASTING_TIME_INTERVAL, 16)

        val fastingLabel = "%d : %d".format(fastingHours, eatingHours)
        val selectedFastingItem = FastingItem.defaultFastingItems.firstOrNull {
            it.fastingHours == fastingHours && it.eatingHours == eatingHours
        }

        val history = dietData.history.map { (date, ratio) ->
            DietUiState.HistoryBar(dateMillis = date, ratio = ratio)
        }

        val eatTime = dietData.lastEatTime
        if (eatTime == null) {
            return DietUiState(
                fastingLabel = fastingLabel,
                history = history,
                selectedFastingItem = selectedFastingItem,
            )
        }

        val remainTime = System.currentTimeMillis() - eatTime.first
        val eatingTimeMillis = TimeUnit.HOURS.toMillis(eatingHours)
        val fastingTimeMillis = TimeUnit.HOURS.toMillis(fastingHours)
        val fastingTime =
            Calendar.getInstance().timeInMillis - min(eatTime.second, Calendar.getInstance().timeInMillis)

        val progress = min(100.0, fastingTime * 100.0 / fastingTimeMillis).toFloat()

        val statusIcon: Int
        val statusTextRes: Int
        val elapsedText: String
        val promptTextRes: Int
        when {
            remainTime < eatingTimeMillis -> {
                statusIcon = R.drawable.ic_baseline_fastfood_24
                statusTextRes = R.string.diet_status_eating
                elapsedText = formatElapsed(eatingTimeMillis - remainTime)
                promptTextRes = R.string.diet_remaining_time
            }
            fastingTime > fastingTimeMillis -> {
                statusIcon = R.drawable.ic_baseline_no_food_24
                statusTextRes = R.string.diet_status_success
                elapsedText = formatElapsed(System.currentTimeMillis() - eatTime.second)
                promptTextRes = R.string.diet_fasting_time
            }
            else -> {
                statusIcon = R.drawable.ic_baseline_no_food_24
                statusTextRes = R.string.diet_status_fasting
                elapsedText = formatElapsed(System.currentTimeMillis() - eatTime.second)
                promptTextRes = R.string.diet_fasting_time
            }
        }

        return DietUiState(
            elapsedText = elapsedText,
            progress = progress,
            fastingLabel = fastingLabel,
            history = history,
            selectedFastingItem = selectedFastingItem,
            statusIcon = statusIcon,
            statusTextRes = statusTextRes,
            promptTextRes = promptTextRes,
        )
    }

    private fun formatElapsed(timestamp: Long): String {
        var temp = timestamp
        val hours = TimeUnit.MILLISECONDS.toHours(temp)
        temp -= TimeUnit.HOURS.toMillis(hours)
        val mins = TimeUnit.MILLISECONDS.toMinutes(temp)
        temp -= TimeUnit.MINUTES.toMillis(mins)
        val ses = TimeUnit.MILLISECONDS.toSeconds(temp)
        // Matches R.string.diet_time_format = "%02d:%02d:%02d"
        return "%02d:%02d:%02d".format(hours, mins, ses)
    }

    private fun historyFlow(): Flow<List<EatTime>> {
        val before = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR) + 1)
        }.timeInMillis

        val after = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR) - 8)
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return eatTimeDao.flowByTimeInterval(before, after)
    }

    private fun lastEatingTime(data: List<EatTime>): Pair<Long, Long>? {
        val timeInterval = mergeInterval(data)
        return timeInterval.lastOrNull()
    }

    private fun computeHistory(data: List<EatTime>): List<Pair<Long, Float>> {
        val timeInterval = mergeIntervalWithFourHours(data)
        return (0..4).map { i ->
            val before = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR) - i - 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val after = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR) - i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            before to effectiveProgress(timeInterval, before, after)
        }.asReversed()
    }

    private fun mergeInterval(data: List<EatTime>): List<Pair<Long, Long>> {
        val eight = TimeUnit.HOURS.toMillis(8)
        return mutableListOf<Pair<Long, Long>>().apply {
            if (data.isEmpty()) {
                return@apply
            }
            var start = data[0]
            var end = data[0]
            data.forEach {
                if (end.time + eight > it.time) {
                    end = it
                } else {
                    add(Pair(start.time, end.time))
                    start = it
                    end = it
                }
            }
            add(Pair(start.time, end.time))
        }
    }

    private fun mergeIntervalWithFourHours(data: List<EatTime>): List<Pair<Long, Long>> {
        val eight = TimeUnit.HOURS.toMillis(8)
        val four = TimeUnit.HOURS.toMillis(4)
        return mutableListOf<Pair<Long, Long>>().apply {
            if (data.isEmpty()) {
                return@apply
            }
            var start = data[0]
            var end = data[0]
            data.forEach {
                if (end.time + eight > it.time) {
                    end = it
                } else {
                    add(Pair(start.time, max(end.time, start.time + four)))
                    start = it
                    end = it
                }
            }
            add(Pair(start.time, max(end.time, start.time + four)))
        }
    }

    private fun effectiveProgress(
        timeInterval: List<Pair<Long, Long>>,
        before: Long,
        after: Long,
    ): Float {
        var sum = 0f
        timeInterval.forEach {
            val max = min(after, it.second)
            val min = max(before, it.first)
            sum += max(0, max - min)
        }
        return 1.0f - (sum / (after - before).toFloat())
    }

    companion object {
        val HISTORY_DATE_FORMAT = SimpleDateFormat("MM/dd", Locale.getDefault())

        fun formatHistoryDate(dateMillis: Long): String =
            HISTORY_DATE_FORMAT.format(Date(dateMillis))
    }
}

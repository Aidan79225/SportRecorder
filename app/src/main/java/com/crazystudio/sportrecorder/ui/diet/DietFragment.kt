package com.crazystudio.sportrecorder.ui.diet

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.FragmentDietBinding
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.ui.base.BaseFragment
import com.crazystudio.sportrecorder.util.Constants
import com.crazystudio.sportrecorder.util.SharedPreferenceUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.sql.Timestamp
import java.util.concurrent.TimeUnit
import kotlin.math.min

class DietFragment : BaseFragment(R.layout.fragment_diet) {
    private val viewModel by viewModels<DietViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        FragmentDietBinding.bind(view).apply {
            val preference = SharedPreferenceUtils.getDietPreference()
            fun getTimeString(timestamp: Long): String {
                var temp = timestamp
                val hours = TimeUnit.MILLISECONDS.toHours(temp)
                temp -= TimeUnit.HOURS.toMillis(hours)
                val mins = TimeUnit.MILLISECONDS.toMinutes(temp)
                temp -= TimeUnit.MINUTES.toMillis(mins)
                val ses = TimeUnit.MILLISECONDS.toSeconds(temp)
                return getString(R.string.diet_time_format).format(hours, mins, ses)
            }
            fun updateTime(eatTime: Pair<EatTime, EatTime>) {
                val remainTime = System.currentTimeMillis() - eatTime.first.time
                val eatingHours = preference.getLong(Constants.DIET_EATING_TIME_INTERVAL, 8)
                val fastingHours = preference.getLong(Constants.DIET_FASTING_TIME_INTERVAL, 16)
                val totalTime = TimeUnit.HOURS.toMillis((fastingHours + eatingHours))

                val progress = min(100.0, remainTime * 100.0 / totalTime)
                circularProgressBar.progress = progress
                when {
                    remainTime < TimeUnit.HOURS.toMillis(eatingHours) -> {
                        statusTextView.text = getString(R.string.diet_status_eating)
                    }
                    remainTime > totalTime -> {
                        statusTextView.text = getString(R.string.diet_status_success)
                    }
                    else -> {
                        statusTextView.text = getString(R.string.diet_status_fasting)
                    }
                }

                intermittentTextView.text = getTimeString(totalTime - remainTime)
                fastingTextView.text = getTimeString(System.currentTimeMillis() - eatTime.second.time)
            }

            createEatTimeFloatActionButton.setOnClickListener {
                viewModel.createEatTime()
            }

            viewModel.lastEatTimeLiveData.observe(viewLifecycleOwner) {
                updateTime(it)
            }

            lifecycleScope.launch {
                while (true) {
                    delay(TimeUnit.SECONDS.toMillis(1))
                    val eatTimeList = viewModel.lastEatTimeLiveData.value ?: continue
                    updateTime(eatTimeList)
                }
            }
        }

    }
}
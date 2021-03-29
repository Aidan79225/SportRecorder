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
import java.util.concurrent.TimeUnit
import kotlin.math.min

class DietFragment: BaseFragment(R.layout.fragment_diet) {
    private val viewModel by viewModels<DietViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        FragmentDietBinding.bind(view).apply {
            fun updateTime(eatTime: Pair<EatTime, EatTime>) {
                var remainTime = System.currentTimeMillis() - eatTime.first.time
                val preference = SharedPreferenceUtils.getDietPreference()
                val eatingHours = preference.getLong(Constants.DIET_EATING_TIME_INTERVAL, 8)
                val fastingHours = preference.getLong(Constants.DIET_FASTING_TIME_INTERVAL, 16)
                val progress = min(100.0, remainTime * 100.0 / TimeUnit.HOURS.toMillis((fastingHours + eatingHours).toLong()))

                circularProgressBar.progress = progress
                when {
                    remainTime < TimeUnit.HOURS.toMillis(eatingHours) -> {
                        statusTextView.text = getString(R.string.diet_status_eating)
                        circularProgressBar.updateColor(R.color.dark_green)
                    }
                    remainTime > TimeUnit.HOURS.toMillis((fastingHours + eatingHours)) -> {
                        statusTextView.text = getString(R.string.diet_status_success)
                        circularProgressBar.updateColor(R.color.light_green)
                    }
                    else -> {
                        statusTextView.text = getString(R.string.diet_status_fasting)
                        circularProgressBar.updateColor(R.color.light_green)
                    }
                }

                remainTime -= TimeUnit.HOURS.toMillis(eatingHours)
                val hours = TimeUnit.MILLISECONDS.toHours(remainTime)
                remainTime -= TimeUnit.HOURS.toMillis(hours)
                val mins = TimeUnit.MILLISECONDS.toMinutes(remainTime)
                remainTime -= TimeUnit.MINUTES.toMillis(mins)
                val ses = TimeUnit.MILLISECONDS.toSeconds(remainTime)
                intermittentTextView.text = getString(R.string.diet_time_format).format(hours, mins, ses)
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
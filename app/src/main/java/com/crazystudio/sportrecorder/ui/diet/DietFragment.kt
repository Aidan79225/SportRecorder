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
            fun updateTime(eatTime: EatTime) {
                var remainTime = System.currentTimeMillis() - eatTime.time
                val preference = SharedPreferenceUtils.getDietPreference()
                val target = preference.getInt(Constants.DIET_TIME_INTERVAL, 16)
                val progress = min(100.0, remainTime * 100.0 / TimeUnit.HOURS.toMillis(target.toLong()))
                circularProgressBar.progress = progress
                when {
                    remainTime < TimeUnit.HOURS.toMillis(6) -> {
                        statusTextView.text = getString(R.string.diet_status_eating)
                        circularProgressBar.updateColor(R.color.dark_green)
                    }
                    remainTime > TimeUnit.HOURS.toMillis(target.toLong()) -> {
                        statusTextView.text = getString(R.string.diet_status_success)
                        circularProgressBar.updateColor(R.color.light_green)
                    }
                    else -> {
                        statusTextView.text = getString(R.string.diet_status_fasting)
                        circularProgressBar.updateColor(R.color.light_green)
                    }
                }


                val hours = TimeUnit.MILLISECONDS.toHours(remainTime)
                remainTime -= TimeUnit.HOURS.toMillis(hours)
                val mins = TimeUnit.MILLISECONDS.toMinutes(remainTime)
                remainTime -= TimeUnit.MINUTES.toMillis(mins)
                val ses = TimeUnit.MILLISECONDS.toSeconds(remainTime)
                intermittentTextView.text = "%02d:%02d:%02d".format(hours, mins, ses)
            }

            createEatTimeFloatActionButton.setOnClickListener {
                viewModel.createEatTime()
            }

            viewModel.lastEatTimeLiveData.observe(viewLifecycleOwner) {
                if (it.isEmpty()) {
                    return@observe
                }
                updateTime(it[0])
            }

            lifecycleScope.launch {
                while (true) {
                    delay(TimeUnit.SECONDS.toMillis(1))
                    val eatTimeList = viewModel.lastEatTimeLiveData.value ?: continue
                    if (eatTimeList.isEmpty()) {
                        continue
                    }
                    updateTime(eatTimeList[0])
                }
            }
        }

    }
}
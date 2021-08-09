package com.crazystudio.sportrecorder.ui.diet

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.FragmentDietBinding
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.ui.base.BaseFragment
import com.crazystudio.sportrecorder.util.Constants
import com.crazystudio.sportrecorder.util.DietPreference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.min

@AndroidEntryPoint
class DietFragment : BaseFragment(R.layout.fragment_diet) {
    private val viewModel by viewModels<DietViewModel>()
    @Inject lateinit var dietPreference: DietPreference

    private lateinit var onSharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        FragmentDietBinding.bind(view).apply {

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
                val eatingHours = dietPreference.preference.getLong(Constants.DIET_EATING_TIME_INTERVAL, 8)
                val eatingTimeMillis = TimeUnit.HOURS.toMillis(eatingHours)

                val fastingHours = dietPreference.preference.getLong(Constants.DIET_FASTING_TIME_INTERVAL, 16)
                val fastingTimeMillis = TimeUnit.HOURS.toMillis(fastingHours)
                val fastingTime = System.currentTimeMillis() - eatTime.second.time

                val progress = min(100.0, fastingTime * 100.0 / fastingTimeMillis)
                circularProgressBar.progress = progress

                when {
                    remainTime < eatingTimeMillis -> {
                        statusImageView.setImageResource(R.drawable.ic_baseline_fastfood_24)
                        statusTextView.text = getString(R.string.diet_status_eating)
                        timerTextView.text = getTimeString(eatingTimeMillis - remainTime)
                        dietPromptTextView.setText(R.string.diet_remaining_time)
                    }
                    fastingTime > fastingTimeMillis -> {
                        statusImageView.setImageResource(R.drawable.ic_baseline_no_food_24)
                        statusTextView.text = getString(R.string.diet_status_success)
                        timerTextView.text = getTimeString(System.currentTimeMillis() - eatTime.second.time)
                        dietPromptTextView.setText(R.string.diet_fasting_time)
                    }
                    else -> {
                        statusImageView.setImageResource(R.drawable.ic_baseline_no_food_24)
                        statusTextView.text = getString(R.string.diet_status_fasting)
                        timerTextView.text = getTimeString(System.currentTimeMillis() - eatTime.second.time)
                        dietPromptTextView.setText(R.string.diet_fasting_time)
                    }
                }
            }

            fun updateTime() {
                val eatTimeList = viewModel.lastEatTimeLiveData.value ?: return
                updateTime(eatTimeList)
            }

            fun updateInfo() {
                val eatingHours = dietPreference.preference.getLong(Constants.DIET_EATING_TIME_INTERVAL, 8)
                val fastingHours =dietPreference.preference.getLong(Constants.DIET_FASTING_TIME_INTERVAL, 16)
                dietInfoTextView.text = "%d : %d".format(fastingHours, eatingHours)
            }

            createEatTimeFloatActionButton.setOnClickListener {
                viewModel.createEatTime()
            }
            dietInfoContainer.setOnClickListener {
                findNavController().navigate(DietFragmentDirections.gotoSelectFastingTypeFragment())
            }
            viewModel.lastEatTimeLiveData.observe(viewLifecycleOwner) {
                updateTime(it)
            }
            viewModel.historyLiveData.observe(viewLifecycleOwner) {
                verticalBar1.progress = it[0]
                verticalBar2.progress = it[1]
                verticalBar3.progress = it[2]
                verticalBar4.progress = it[3]
                verticalBar5.progress = it[4]
                verticalBar6.progress = it[5]
                verticalBar7.progress = it[6]
            }
            onSharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                updateInfo()
                updateTime()
            }
            dietPreference.preference.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
            viewModel.selectFastingItemLiveData.observe(viewLifecycleOwner) {
                dietPreference.preference.edit().apply {
                    putLong(Constants.DIET_EATING_TIME_INTERVAL, it.eatingHours)
                    putLong(Constants.DIET_FASTING_TIME_INTERVAL, it.fastingHours)
                }.apply()
                updateInfo()
                updateTime()
            }
            updateInfo()

            lifecycleScope.launch {
                while (true) {
                    delay(TimeUnit.SECONDS.toMillis(1))
                    updateTime()
                }
            }
        }

    }

    override fun onDestroyView() {
        dietPreference.preference.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
        super.onDestroyView()
    }
}
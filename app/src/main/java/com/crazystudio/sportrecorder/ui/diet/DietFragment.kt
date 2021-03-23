package com.crazystudio.sportrecorder.ui.diet

import android.os.Bundle
import android.text.style.TtsSpan
import android.view.View
import androidx.core.util.TimeUtils
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.FragmentDietBinding
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.ui.base.BaseFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.sql.Time
import java.util.concurrent.TimeUnit

class DietFragment: BaseFragment(R.layout.fragment_diet) {
    private val viewModel by viewModels<DietViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        FragmentDietBinding.bind(view).apply {
            fun updateTime(eatTime: EatTime) {
                var remainTime = System.currentTimeMillis() - eatTime.time
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
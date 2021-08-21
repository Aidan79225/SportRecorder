package com.crazystudio.sportrecorder.ui.diet.daily

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.crazystudio.sportrecorder.databinding.FragmentDailyBinding
import com.crazystudio.sportrecorder.ui.diet.record.DietRecordViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DailyFragment: Fragment() {

    private lateinit var binding: FragmentDailyBinding
    private val viewModel: DailyViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentDailyBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding
    }
}
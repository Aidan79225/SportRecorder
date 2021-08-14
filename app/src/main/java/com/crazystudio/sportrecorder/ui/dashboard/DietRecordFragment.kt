package com.crazystudio.sportrecorder.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.crazystudio.sportrecorder.databinding.FragmentDietRecordBinding

class DietRecordFragment : Fragment() {

    private val dietRecordViewModel: DietRecordViewModel by viewModels()
    private lateinit var binding: FragmentDietRecordBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentDietRecordBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }
}
package com.crazystudio.sportrecorder.ui.diet.create.food

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.crazystudio.sportrecorder.databinding.FragmentCreateFoodRecordBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CreateFoodRecordDialogFragment: DialogFragment() {
    private val viewModel by viewModels<CreateFoodRecordViewModel>()
    lateinit var binding: FragmentCreateFoodRecordBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return FragmentCreateFoodRecordBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
}
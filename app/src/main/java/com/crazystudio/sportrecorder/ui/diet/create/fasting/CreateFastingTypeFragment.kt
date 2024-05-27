package com.crazystudio.sportrecorder.ui.diet.create.fasting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.crazystudio.sportrecorder.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CreateFastingTypeFragment : BottomSheetDialogFragment() {
    private val viewModel by viewModels<CreateFastingTypeViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return ComposeView(requireContext()).apply {
            setContent {
                CreateFastingTypeScreen(onDismissRequest = {
                    dismiss()
                }, onConfirmRequest = { fastingTime, eatingTime ->
                    val fastingHours = fastingTime.toLong()
                    val eatingHours = eatingTime.toLong()
                    lifecycleScope.launch {
                        if (viewModel.createCustomFastingType(fastingHours, eatingHours)) {
                            dismiss()
                        } else {
                            Toast.makeText(
                                requireContext(),
                                R.string.diet_fasting_type_create_duplicate,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                })
            }
        }
    }
}
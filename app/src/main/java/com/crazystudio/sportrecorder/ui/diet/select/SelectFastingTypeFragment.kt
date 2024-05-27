package com.crazystudio.sportrecorder.ui.diet.select

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHost
import androidx.navigation.fragment.findNavController
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import com.crazystudio.sportrecorder.util.Constants
import com.crazystudio.sportrecorder.util.DietPreference
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SelectFastingTypeFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var dietPreference: DietPreference
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        return ComposeView(requireContext()).apply {
            setContent {
                val vm: SelectFastingTypeViewModel = viewModel()
                val fastingItem by vm.fastingItemFlow.collectAsState(emptyList())
                SportRecorderTheme {
                    SelectFastingTypeScreen(
                        fastingItem,
                        {
                            findNavController().navigate(SelectFastingTypeFragmentDirections.gotoCreateFastingTypeFragment())
                        },
                        { fastingHours, eatingHours ->
                            val preferenceEdit = dietPreference.preference.edit()
                            preferenceEdit.putLong(Constants.DIET_FASTING_TIME_INTERVAL, fastingHours)
                            preferenceEdit.putLong(Constants.DIET_EATING_TIME_INTERVAL, eatingHours)
                            preferenceEdit.apply()
                            findNavController().popBackStack()
                        }
                    )
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setOnShowListener {
                (it as? BottomSheetDialog)?.run {
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
    }
}
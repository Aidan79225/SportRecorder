package com.crazystudio.sportrecorder.ui.diet.create.fasting

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.ListPopupWindow
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.FragmentCreateFastingTypeBinding
import com.crazystudio.sportrecorder.databinding.FragmentCreateFastingTypeComposeBinding
import com.crazystudio.sportrecorder.databinding.ItemFastingTypeTitleBinding
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
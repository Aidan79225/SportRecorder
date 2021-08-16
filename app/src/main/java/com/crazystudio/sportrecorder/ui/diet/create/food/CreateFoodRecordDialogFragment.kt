package com.crazystudio.sportrecorder.ui.diet.create.food

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.FragmentCreateFoodRecordBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CreateFoodRecordDialogFragment : BottomSheetDialogFragment() {
    private val viewModel by viewModels<CreateFoodRecordViewModel>()
    lateinit var binding: FragmentCreateFoodRecordBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return FragmentCreateFoodRecordBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.nameContainer.titleTextView.setText(R.string.create_food_name)
        binding.nameContainer.contentTextView.setText("")
        binding.nameContainer.contentTextView.inputType = InputType.TYPE_CLASS_TEXT

        binding.carbohydrateContainer.titleTextView.setText(R.string.create_food_carbohydrate)
        binding.proteinContainer.titleTextView.setText(R.string.create_food_protein)
        binding.fatContainer.titleTextView.setText(R.string.create_food_fat)

        binding.cancelTextView.setOnClickListener {
            dismiss()
        }
        binding.createTextView.setOnClickListener {
            lifecycleScope.launch {
                viewModel.createFoodRecord(0,
                    binding.nameContainer.contentTextView.text.toString(),
                    binding.carbohydrateContainer.contentTextView.text.toString().toFloat(),
                    binding.proteinContainer.contentTextView.text.toString().toFloat(),
                    binding.fatContainer.contentTextView.text.toString().toFloat()
                )
                dismiss()
            }
        }
    }
}
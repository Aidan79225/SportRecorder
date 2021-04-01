package com.crazystudio.sportrecorder.ui.diet.create.fasting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.crazystudio.sportrecorder.databinding.FragmentCreateFastingTypeBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class CreateFastingTypeFragment: BottomSheetDialogFragment() {
    private val viewModel by viewModels<CreateFastingTypeViewModel>()
    private lateinit var bind: FragmentCreateFastingTypeBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        bind = FragmentCreateFastingTypeBinding.inflate(inflater)
        return bind.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.apply {
            cancelTextView.setOnClickListener {
                dismiss()
            }
            createTextView.setOnClickListener {
                val fastingHours = fastingTextView.text.toString().toLongOrNull() ?: return@setOnClickListener
                val eatingHours = eatingTextView.text.toString().toLongOrNull() ?: return@setOnClickListener
                lifecycleScope.launch {
                    viewModel.createCustomFastingType(fastingHours, eatingHours).join()
                    dismiss()
                }
            }
        }
    }
}
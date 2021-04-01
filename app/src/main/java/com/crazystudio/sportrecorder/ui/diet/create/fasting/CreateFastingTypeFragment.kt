package com.crazystudio.sportrecorder.ui.diet.create.fasting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.crazystudio.sportrecorder.databinding.FragmentCreateFastingTypeBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CreateFastingTypeFragment: BottomSheetDialogFragment() {

    private lateinit var bind: FragmentCreateFastingTypeBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        bind = FragmentCreateFastingTypeBinding.inflate(inflater)
        return bind.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.apply {

        }
    }
}
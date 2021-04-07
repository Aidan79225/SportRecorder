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
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.FragmentCreateFastingTypeBinding
import com.crazystudio.sportrecorder.databinding.ItemFastingTypeTitleBinding
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
                    if (viewModel.createCustomFastingType(fastingHours, eatingHours)) {
                        dismiss()
                    } else {
                        Toast.makeText(requireContext(), R.string.diet_fasting_type_create_duplicate , Toast.LENGTH_LONG).show()
                    }
                }
            }
            eatingImageView.setOnClickListener {
                showSelectMenu(eatingTextView, (1..11).map { it.toString() }.toList())
            }
            eatingTextView.setOnClickListener {
                showSelectMenu(eatingTextView, (1..11).map { it.toString() }.toList())
            }
            fastingTextView.setOnClickListener {
                showSelectMenu(fastingTextView, (16..49).map { it.toString() }.toList())
            }
            fastingImageView.setOnClickListener {
                showSelectMenu(fastingTextView, (16..49).map { it.toString() }.toList())
            }
        }
    }

    fun showSelectMenu(textView: TextView, values: List<String>) {
        ListPopupWindow(textView.context).apply {
            setAdapter(ArrayAdapter(requireContext(), R.layout.item_select, R.id.select_text_view, values))
            setOnItemClickListener { parent, view, position, id ->
                textView.text = values[position]
                dismiss()
            }
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(ContextCompat.getColor(requireContext(), R.color.black))
            })
            isModal = true
            height = textView.height * 10
            anchorView = textView
        }.show()
    }
}
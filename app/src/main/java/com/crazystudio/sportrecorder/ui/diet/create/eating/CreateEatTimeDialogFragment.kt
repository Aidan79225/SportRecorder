package com.crazystudio.sportrecorder.ui.diet.create.eating

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import android.widget.TimePicker
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.FragmentCreateEatTimeBinding
import com.crazystudio.sportrecorder.ui.diet.create.fasting.CreateFastingTypeViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class CreateEatTimeDialogFragment : BottomSheetDialogFragment() {
    private val viewModel by viewModels<CreateEatTimeViewModel>()
    private lateinit var binding: FragmentCreateEatTimeBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return FragmentCreateEatTimeBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.calendarLiveData.observe(viewLifecycleOwner) {
            binding.dateTextView.text = SimpleDateFormat("yyyy/MM/dd").format(it.time)
            binding.timeTextView.text = SimpleDateFormat("HH:mm").format(it.time)
        }

        binding.dateImageView.setOnClickListener {
            val currentCalendar = viewModel.currentCalendar
            DatePickerDialog(
                it.context,
                R.style.DatePicker,
                { view, year, month, dayOfMonth ->
                    viewModel.updateDate(year, month, dayOfMonth)
                }, currentCalendar.get(Calendar.YEAR),
                currentCalendar.get(Calendar.MONTH),
                currentCalendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.timeImageView.setOnClickListener {
            val currentCalendar = viewModel.currentCalendar
            TimePickerDialog(
                it.context,
                { view, hourOfDay, minute -> viewModel.updateTime(hourOfDay, minute) },
                currentCalendar.get(Calendar.HOUR_OF_DAY),
                currentCalendar.get(Calendar.MINUTE),
                true
            ).show()
        }


        binding.createTextView.setOnClickListener {
            lifecycleScope.launch {
                if (viewModel.createEatingTime()) {
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), R.string.diet_create_eating_error , Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.cancelTextView.setOnClickListener {
            dismiss()
        }

    }
}
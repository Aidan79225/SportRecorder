package com.crazystudio.sportrecorder.ui.diet.create.eating

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.FragmentCreateEatTimeBinding
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
        val adapter = CreateEatTimeAdapter(object : CreateEatTimeClickListener {
            override fun onDateClickListener() {
                val currentCalendar = viewModel.currentCalendar
                DatePickerDialog(
                    view.context,
                    R.style.DialogStyle,
                    { view, year, month, dayOfMonth ->
                        viewModel.updateDate(year, month, dayOfMonth)
                    }, currentCalendar.get(Calendar.YEAR),
                    currentCalendar.get(Calendar.MONTH),
                    currentCalendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }

            override fun onTimeClickListener() {
                val currentCalendar = viewModel.currentCalendar
                TimePickerDialog(
                    view.context,
                    R.style.TimeDialogStyle,
                    { view, hourOfDay, minute -> viewModel.updateTime(hourOfDay, minute) },
                    currentCalendar.get(Calendar.HOUR_OF_DAY),
                    currentCalendar.get(Calendar.MINUTE),
                    true
                ).apply {
                    getView()?.setBackgroundColor(ContextCompat.getColor(view.context, R.color.bg_black))
                }.show()
            }
        })

        viewModel.calendarLiveData.observe(viewLifecycleOwner) {
            adapter.calendar = it
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


        binding.recyclerView.apply {
            this.adapter = adapter
            layoutManager = LinearLayoutManager(view.context)
        }
    }
}
package com.crazystudio.sportrecorder.ui.diet.record

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.FragmentDietRecordBinding
import com.crazystudio.sportrecorder.entity.EatTime
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DietRecordFragment : Fragment() {

    private val viewModel: DietRecordViewModel by viewModels()
    private lateinit var binding: FragmentDietRecordBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentDietRecordBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = DietRecordAdapter(object : DietRecordClickListener {
            override fun onLongClick(eatTime: EatTime) {
                AlertDialog.Builder(view.context, R.style.DialogStyle)
                    .setTitle(R.string.diet_eat_time_delete_title)
                    .setMessage(R.string.diet_eat_time_delete_message)
                    .setPositiveButton(R.string.yes) { dialog, _ ->
                        viewModel.deleteEatTime(eatTime)
                        dialog.dismiss()
                    }.setNegativeButton(R.string.no) { dialog, _ ->
                        dialog.dismiss()
                    }.show()
            }
        })
        viewModel.eatTimeLiveData.observe(viewLifecycleOwner) {
            adapter.data = it
        }
        binding.recyclerView.apply {
            this.adapter = adapter
            layoutManager = LinearLayoutManager(view.context)
        }
    }
}
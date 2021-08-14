package com.crazystudio.sportrecorder.ui.dashboard

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.crazystudio.sportrecorder.databinding.ItemDietRecordBinding
import com.crazystudio.sportrecorder.entity.EatTime

class DietRecordAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var data: List<EatTime> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        TODO("Not yet implemented")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        TODO("Not yet implemented")
    }

    override fun getItemCount(): Int = data.size

}

class DietRecordViewHolder(private val binding: ItemDietRecordBinding): RecyclerView.ViewHolder(binding.root) {

}
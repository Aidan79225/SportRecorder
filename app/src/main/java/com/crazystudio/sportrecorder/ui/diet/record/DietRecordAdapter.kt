package com.crazystudio.sportrecorder.ui.diet.record

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.crazystudio.sportrecorder.databinding.ItemDietRecordBinding
import com.crazystudio.sportrecorder.entity.EatTime
import java.text.SimpleDateFormat
import java.util.*

class DietRecordAdapter(private val clickListener: DietRecordClickListener): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var data: List<EatTime> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return DietRecordViewHolder(ItemDietRecordBinding.inflate(LayoutInflater.from(parent.context)))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when(holder) {
            is DietRecordViewHolder -> holder.onBind(data[position], clickListener)
        }
    }

    override fun getItemCount(): Int = data.size

}

interface DietRecordClickListener {
    fun onLongClick(eatTime: EatTime)
}

class DietRecordViewHolder(private val binding: ItemDietRecordBinding): RecyclerView.ViewHolder(binding.root) {
    fun onBind(data: EatTime, clickListener: DietRecordClickListener) {
        binding.idTextView.text = data.id.toString()
        binding.timeTextView.text = dateFormat.format(Date(data.time))
        binding.root.setOnLongClickListener {
            clickListener.onLongClick(data)
            return@setOnLongClickListener true
        }
    }

    companion object {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd")
    }
}
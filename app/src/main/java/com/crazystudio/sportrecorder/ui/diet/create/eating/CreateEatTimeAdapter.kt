package com.crazystudio.sportrecorder.ui.diet.create.eating

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.ItemCreateEatHeaderBinding
import com.crazystudio.sportrecorder.entity.FoodRecord
import java.text.SimpleDateFormat
import java.util.*

class CreateEatTimeAdapter(private val createEatTimeClickListener: CreateEatTimeClickListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var calendar = Calendar.getInstance()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var data: List<FoodRecord> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_EMPTY -> EmptyViewHolder(View(parent.context))
            TYPE_TIME -> TimeViewHolder(ItemCreateEatHeaderBinding.inflate(inflater, parent, false), createEatTimeClickListener)
            TYPE_DATE -> DateViewHolder(ItemCreateEatHeaderBinding.inflate(inflater, parent, false), createEatTimeClickListener)
            TYPE_CREATE_FOOD_RECORD -> CreateFoodRecordViewHolder(ItemCreateEatHeaderBinding.inflate(inflater, parent, false), createEatTimeClickListener)
            TYPE_FOOD_RECORD -> FoodRecordViewHolder(ItemCreateEatHeaderBinding.inflate(inflater, parent, false))
            else -> EmptyViewHolder(View(parent.context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when(holder) {
            is DateViewHolder -> holder.onBind(calendar)
            is TimeViewHolder -> holder.onBind(calendar)
            is FoodRecordViewHolder -> holder.onBind(data[position-3], createEatTimeClickListener)
        }
    }

    override fun getItemCount(): Int = 3 + data.size

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> TYPE_DATE
            1 -> TYPE_TIME
            2 -> TYPE_CREATE_FOOD_RECORD
            else -> TYPE_FOOD_RECORD
        }
    }

    companion object {
        val TYPE_DATE = 0
        val TYPE_TIME = 1
        val TYPE_EMPTY = 2
        val TYPE_CREATE_FOOD_RECORD = 3
        val TYPE_FOOD_RECORD = 4
    }
}

interface CreateEatTimeClickListener {
    fun onDateClick()
    fun onTimeClick()
    fun onCreateFoodRecordClick()
    fun onFoodRecordDeleteClick(foodRecord: FoodRecord)
}

private class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view)

private class DateViewHolder(private val binding: ItemCreateEatHeaderBinding, createEatTimeClickListener: CreateEatTimeClickListener) : RecyclerView.ViewHolder(binding.root) {
    init {
        binding.titleTextView.setText(R.string.diet_create_eating_date_title)
        binding.iconImageView.setImageResource(R.drawable.ic_baseline_date_range_24)
        binding.actionImageView.setOnClickListener {
            createEatTimeClickListener.onDateClick()
        }
    }

    fun onBind(calendar: Calendar) {
        binding.contentTextView.text = SimpleDateFormat("yyyy/MM/dd").format(calendar.time)
    }
}

class TimeViewHolder(private val binding: ItemCreateEatHeaderBinding, createEatTimeClickListener: CreateEatTimeClickListener) : RecyclerView.ViewHolder(binding.root) {
    init {
        binding.titleTextView.setText(R.string.diet_create_eating_time_title)
        binding.iconImageView.setImageResource(R.drawable.ic_baseline_access_time_24)
        binding.actionImageView.setOnClickListener {
            createEatTimeClickListener.onTimeClick()
        }
    }

    fun onBind(calendar: Calendar) {
        binding.contentTextView.text = SimpleDateFormat("HH:mm").format(calendar.time)
    }
}

class CreateFoodRecordViewHolder(private val binding: ItemCreateEatHeaderBinding, createEatTimeClickListener: CreateEatTimeClickListener) : RecyclerView.ViewHolder(binding.root) {
    init {
        binding.titleTextView.setText(R.string.diet_create_food_title)
        binding.contentTextView.text = ""
        binding.iconImageView.setImageResource(R.drawable.ic_baseline_fastfood_24)
        binding.actionImageView.setOnClickListener {
            createEatTimeClickListener.onCreateFoodRecordClick()
        }
        binding.actionImageView.setImageResource(R.drawable.ic_baseline_add_24)
    }

}

class FoodRecordViewHolder(private val binding: ItemCreateEatHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
    init {
        binding.titleTextView.setText(R.string.diet_food_record_title)
        binding.contentTextView.text = ""
        binding.iconImageView.setImageResource(R.drawable.ic_baseline_fastfood_24)
        binding.actionImageView.setImageResource(R.drawable.ic_baseline_delete_24)
    }

    fun onBind(foodRecord: FoodRecord, createEatTimeClickListener: CreateEatTimeClickListener) {
        binding.actionImageView.setOnClickListener {
            createEatTimeClickListener.onFoodRecordDeleteClick(foodRecord)
        }
        binding.contentTextView.text = foodRecord.name
    }
}
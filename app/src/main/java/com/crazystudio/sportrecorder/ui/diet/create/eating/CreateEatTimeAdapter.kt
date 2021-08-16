package com.crazystudio.sportrecorder.ui.diet.create.eating

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.ItemCreateEatHeaderBinding
import java.text.SimpleDateFormat
import java.util.*

class CreateEatTimeAdapter(private val createEatTimeClickListener: CreateEatTimeClickListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var calendar = Calendar.getInstance()
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
            else -> EmptyViewHolder(View(parent.context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when(holder) {
            is DateViewHolder -> holder.onBind(calendar)
            is TimeViewHolder -> holder.onBind(calendar)
        }
    }

    override fun getItemCount(): Int = 2

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> TYPE_DATE
            1 -> TYPE_TIME
            else -> TYPE_EMPTY
        }
    }

    companion object {
        val TYPE_DATE = 0
        val TYPE_TIME = 1
        val TYPE_EMPTY = 2
    }
}

interface CreateEatTimeClickListener {
    fun onDateClickListener()
    fun onTimeClickListener()
}

private class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view)

private class DateViewHolder(private val binding: ItemCreateEatHeaderBinding, createEatTimeClickListener: CreateEatTimeClickListener) : RecyclerView.ViewHolder(binding.root) {
    init {
        binding.titleTextView.setText(R.string.diet_create_eating_date_title)
        binding.iconImageView.setImageResource(R.drawable.ic_baseline_date_range_24)
        binding.actionImageView.setOnClickListener {
            createEatTimeClickListener.onDateClickListener()
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
            createEatTimeClickListener.onTimeClickListener()
        }
    }

    fun onBind(calendar: Calendar) {
        binding.contentTextView.text = SimpleDateFormat("HH:mm").format(calendar.time)
    }
}
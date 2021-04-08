package com.crazystudio.sportrecorder.ui.diet.select

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.ItemFastingTypeCustomBinding
import com.crazystudio.sportrecorder.databinding.ItemFastingTypeDefaultBinding

class SelectFastingTypeAdapter(
    private val clickListener: FastingItemClickListener,
    private val createFastingClickListener: CreateFastingClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val data = mutableListOf<FastingItem>()

    private val backgroundColors = listOf(
        R.color.google_red,
        R.color.google_blue,
        R.color.google_yellow,
        R.color.google_green,
    )

    val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            return data[position].spanSize
        }
    }

    fun setData(data: List<FastingItem>) {
        this.data.clear()
        this.data.addAll(data)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (data[position]) {
            FastingItem.TitleFastingItem -> R.layout.item_fasting_type_title
            FastingItem.AddFastingItem -> R.layout.item_fasting_type_add
            is FastingItem.CustomFastingItem -> R.layout.item_fasting_type_custom
            else -> R.layout.item_fasting_type_default
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            R.layout.item_fasting_type_title -> TitleViewHolder(LayoutInflater.from(parent.context).inflate(viewType, parent, false))
            R.layout.item_fasting_type_add -> AddViewHolder(LayoutInflater.from(parent.context).inflate(viewType, parent, false))
            R.layout.item_fasting_type_custom -> CustomViewHolder(LayoutInflater.from(parent.context).inflate(viewType, parent, false))
            else -> DefaultFastingViewHolder(LayoutInflater.from(parent.context).inflate(viewType, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DefaultFastingViewHolder -> {
                val data = data[position] as? FastingItem.DefaultFastingItem ?: return
                holder.onBind(data)
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, backgroundColors[position % backgroundColors.size]))
                holder.itemView.setOnClickListener {
                    clickListener.onClick(data.fastingHours, data.eatingHours)
                }
            }
            is AddViewHolder -> {
                holder.itemView.setOnClickListener {
                    createFastingClickListener.onClick()
                }
            }
            is CustomViewHolder -> {
                val data = data[position] as? FastingItem.CustomFastingItem ?: return
                holder.onBind(data)
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, backgroundColors[position % backgroundColors.size]))
                holder.itemView.setOnClickListener {
                    clickListener.onClick(data.fastingHours, data.eatingHours)
                }
            }
        }
    }

    override fun getItemCount() = data.size

    interface FastingItemClickListener {
        fun onClick(fastingHours: Long, eatingHours: Long)
    }

    interface CreateFastingClickListener {
        fun onClick()
    }
}

class TitleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
class AddViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
class DefaultFastingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val binding = ItemFastingTypeDefaultBinding.bind(itemView)

    fun onBind(data: FastingItem.DefaultFastingItem) {
        binding.apply {
            nameTextView.setText(data.nameResId)
            timeTextView.text = "%d : %d".format(data.fastingHours, data.eatingHours)
        }
    }
}

class CustomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val binding = ItemFastingTypeCustomBinding.bind(itemView)

    fun onBind(data: FastingItem.CustomFastingItem) {
        binding.apply {
            timeTextView.text = "%d : %d".format(data.fastingHours, data.eatingHours)
        }
    }
}

sealed class FastingItem(val spanSize: Int) {
    object TitleFastingItem : FastingItem(2)

    data class DefaultFastingItem(
        val nameResId: Int,
        val fastingHours: Long,
        val eatingHours: Long
    ) : FastingItem(1)

    data class CustomFastingItem(
        val fastingHours: Long,
        val eatingHours: Long
    ) : FastingItem(1)

    object AddFastingItem : FastingItem(1)
    companion object {
        val defaultFastingItems = listOf(
            TitleFastingItem,
            DefaultFastingItem(
                R.string.diet_fasting_type_trainee,
                14,
                10
            ),
            DefaultFastingItem(
                R.string.diet_fasting_type_normal,
                16,
                8
            ),
            DefaultFastingItem(
                R.string.diet_fasting_type_expert,
                20,
                4
            ),
            DefaultFastingItem(R.string.diet_fasting_type_master,
                23,
                1
            ),
            DefaultFastingItem(R.string.diet_fasting_type_monk,
                47,
                1
            )
        )
    }
}

package com.crazystudio.sportrecorder.ui.diet.select

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.ItemSelectFastingTypeBinding

class SelectFastingTypeAdapter(private val data: List<SelectFastingItem>, private val clickListener: FastingItemClickListener): RecyclerView.Adapter<SelectFastingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectFastingViewHolder {
        return SelectFastingViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_select_fasting_type, parent, false))
    }

    override fun onBindViewHolder(holder: SelectFastingViewHolder, position: Int) {
        holder.onBind(data[position])
        holder.itemView.setOnClickListener {
            clickListener.onClick(data[position])
        }
    }

    override fun getItemCount() = data.size

    interface FastingItemClickListener {
        fun onClick(data: SelectFastingItem)
    }
}

class SelectFastingViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
    private val binding = ItemSelectFastingTypeBinding.bind(itemView)

    fun onBind(data: SelectFastingItem) {
        binding.apply {
            root.setBackgroundColor(ContextCompat.getColor(itemView.context, data.backgroundColorResId))
            nameTextView.setText(data.nameResId)
            timeTextView.text = "%d : %d".format(data.fastingHours, data.eatingHours)
        }
    }
}

data class SelectFastingItem(
    val nameResId: Int,
    val fastingHours: Long,
    val eatingHours: Long,
    val backgroundColorResId: Int
)
package com.crazystudio.sportrecorder.ui.diet.select

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.FragmentSelectFastingTypeBinding
import com.crazystudio.sportrecorder.ui.base.BaseFragment
import com.crazystudio.sportrecorder.ui.diet.DietViewModel
import com.crazystudio.sportrecorder.util.dpToPx

class SelectFastingTypeFragment: BaseFragment(R.layout.fragment_select_fasting_type) {
    private val viewModel by activityViewModels<DietViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        FragmentSelectFastingTypeBinding.bind(view).apply {
            val selectFastingItems = listOf(
                SelectFastingItem(
                    R.string.diet_fasting_type_normal,
                    14,
                    10,
                    R.color.google_green
                ),
                SelectFastingItem(
                    R.string.diet_fasting_type_advance,
                    16,
                    8,
                    R.color.google_blue
                ),
                SelectFastingItem(R.string.diet_fasting_type_master,
                    23,
                    1,
                    R.color.google_yellow
                ),
                SelectFastingItem(R.string.diet_fasting_type_monk,
                    47,
                    1,
                    R.color.google_red
                )
            )
            val selectFastingTypeAdapter = SelectFastingTypeAdapter(selectFastingItems, object : SelectFastingTypeAdapter.FastingItemClickListener {
                override fun onClick(data: SelectFastingItem) {
                    viewModel.selectFastingItemLiveData.value = data
                    findNavController().popBackStack()
                }
            })
            recyclerView.apply {
                layoutManager = GridLayoutManager(context, 2)
                adapter = selectFastingTypeAdapter
                addItemDecoration(SpaceItemDecoration(context.dpToPx(10f).toInt(), 2))
            }
        }
    }
}
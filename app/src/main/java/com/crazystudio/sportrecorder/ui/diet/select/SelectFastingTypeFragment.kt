package com.crazystudio.sportrecorder.ui.diet.select

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.FragmentSelectFastingTypeBinding
import com.crazystudio.sportrecorder.ui.base.BaseFragment
import com.crazystudio.sportrecorder.ui.diet.DietViewModel
import com.crazystudio.sportrecorder.util.dpToPx

class SelectFastingTypeFragment: BaseFragment(R.layout.fragment_select_fasting_type) {
    private val activityViewModel by activityViewModels<DietViewModel>()
    private val viewModel by viewModels<SelectFastingTypeViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        FragmentSelectFastingTypeBinding.bind(view).apply {

            val selectFastingTypeAdapter = SelectFastingTypeAdapter(object : SelectFastingTypeAdapter.FastingItemClickListener {
                override fun onClick(data: FastingItem.DefaultFastingItem) {
                    activityViewModel.selectFastingItemLiveData.value = data
                    findNavController().popBackStack()
                }
            })

            viewModel.selectFastingItemLiveData.observe(viewLifecycleOwner, {
                selectFastingTypeAdapter.setData(it)
            })

            recyclerView.apply {
                layoutManager = GridLayoutManager(context, 2).apply {
                    spanSizeLookup = selectFastingTypeAdapter.spanSizeLookup
                }
                adapter = selectFastingTypeAdapter
                addItemDecoration(SpaceItemDecoration(context.dpToPx(10f).toInt(), 2))
            }
        }
    }
}
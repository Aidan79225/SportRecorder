package com.crazystudio.sportrecorder.ui.diet.select

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.FragmentSelectFastingTypeBinding
import com.crazystudio.sportrecorder.ui.base.BaseFragment
import com.crazystudio.sportrecorder.util.Constants
import com.crazystudio.sportrecorder.util.DietPreference
import com.crazystudio.sportrecorder.util.dpToPx
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SelectFastingTypeFragment : BaseFragment(R.layout.fragment_select_fasting_type) {
    private val viewModel by viewModels<SelectFastingTypeViewModel>()
    @Inject lateinit var dietPreference: DietPreference

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        FragmentSelectFastingTypeBinding.bind(view).apply {

            val selectFastingTypeAdapter = SelectFastingTypeAdapter(
                object : SelectFastingTypeAdapter.FastingItemClickListener {
                    override fun onClick(fastingHours: Long, eatingHours: Long) {
                        val preferenceEdit = dietPreference.preference.edit()
                        preferenceEdit.putLong(Constants.DIET_FASTING_TIME_INTERVAL, fastingHours)
                        preferenceEdit.putLong(Constants.DIET_EATING_TIME_INTERVAL, eatingHours)
                        preferenceEdit.apply()
                        findNavController().popBackStack()
                    }
                },
                object : SelectFastingTypeAdapter.CreateFastingClickListener {
                    override fun onClick() {
                        findNavController().navigate(SelectFastingTypeFragmentDirections.gotoCreateFastingTypeFragment())
                    }
                }
            )

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
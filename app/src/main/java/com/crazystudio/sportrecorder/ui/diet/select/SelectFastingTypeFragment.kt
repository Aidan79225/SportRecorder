package com.crazystudio.sportrecorder.ui.diet.select

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.FragmentSelectFastingTypeBinding
import com.crazystudio.sportrecorder.ui.base.BaseFragment
import com.crazystudio.sportrecorder.util.Constants
import com.crazystudio.sportrecorder.util.DietPreference
import com.crazystudio.sportrecorder.util.dpToPx
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SelectFastingTypeFragment : BottomSheetDialogFragment() {
    private val viewModel by viewModels<SelectFastingTypeViewModel>()
    @Inject lateinit var dietPreference: DietPreference
    lateinit var binding: FragmentSelectFastingTypeBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return FragmentSelectFastingTypeBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setOnShowListener {
                (it as? BottomSheetDialog)?.run {
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
    }
}
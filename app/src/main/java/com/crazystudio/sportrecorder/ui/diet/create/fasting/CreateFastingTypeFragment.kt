package com.crazystudio.sportrecorder.ui.diet.create.fasting

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.ListPopupWindow
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.databinding.FragmentCreateFastingTypeBinding
import com.crazystudio.sportrecorder.databinding.FragmentCreateFastingTypeComposeBinding
import com.crazystudio.sportrecorder.databinding.ItemFastingTypeTitleBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CreateFastingTypeFragment : BottomSheetDialogFragment() {
    private val viewModel by viewModels<CreateFastingTypeViewModel>()
    private lateinit var bind: FragmentCreateFastingTypeComposeBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        bind = FragmentCreateFastingTypeComposeBinding.inflate(inflater)
        return bind.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind.apply {
            cancelTextView.setOnClickListener {
                dismiss()
            }
            createTextView.setOnClickListener {
                val fastingHours =
                    fastingTextView.text.toString().toLongOrNull() ?: return@setOnClickListener
                val eatingHours =
                    eatingTextView.text.toString().toLongOrNull() ?: return@setOnClickListener
                lifecycleScope.launch {
                    if (viewModel.createCustomFastingType(fastingHours, eatingHours)) {
                        dismiss()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            R.string.diet_fasting_type_create_duplicate,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            eatingImageView.setOnClickListener {
                showSelectMenu(eatingTextView, (1..11).map { it.toString() }.toList())
            }
            eatingTextView.setOnClickListener {
                showSelectMenu(eatingTextView, (1..11).map { it.toString() }.toList())
            }
            fastingTextView.setOnClickListener {
                showSelectMenu(fastingTextView, (16..49).map { it.toString() }.toList())
            }
            fastingImageView.setOnClickListener {
                showSelectMenu(fastingTextView, (16..49).map { it.toString() }.toList())
            }
            composeView.apply {
                setContent {
                    Test()
                }
            }
        }
    }

    fun showSelectMenu(textView: TextView, values: List<String>) {
        ListPopupWindow(textView.context).apply {
            setAdapter(
                ArrayAdapter(
                    requireContext(),
                    R.layout.item_select,
                    R.id.select_text_view,
                    values
                )
            )
            setOnItemClickListener { parent, view, position, id ->
                textView.text = values[position]
                dismiss()
            }
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(ContextCompat.getColor(requireContext(), R.color.black))
            })
            isModal = true
            height = textView.height * 10
            anchorView = textView
        }.show()
    }

    @Preview(showBackground = true)
    @Composable
    fun Test() {
        MaterialTheme {
            Column(
                Modifier
                    .width(IntrinsicSize.Max)
                    .background(colorResource(id = R.color.bg_black))
                    .padding(20.dp)
            ) {
                Row(modifier = Modifier) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_baseline_no_food_24),
                        contentDescription = ""
                    )
                    Text(
                        text = stringResource(id = R.string.diet_status_fasting),
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "16",
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ic_baseline_arrow_drop_down),
                        contentDescription = ""
                    )
                }
            }
        }
    }
}
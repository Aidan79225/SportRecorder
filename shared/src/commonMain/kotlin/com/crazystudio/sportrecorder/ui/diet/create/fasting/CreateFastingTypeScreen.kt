package com.crazystudio.sportrecorder.ui.diet.create.fasting

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.diet_create_eating_hours
import com.crazystudio.sportrecorder.shared.resources.diet_create_eating_hours_pick
import com.crazystudio.sportrecorder.shared.resources.diet_create_fasting_cancel
import com.crazystudio.sportrecorder.shared.resources.diet_create_fasting_confirm
import com.crazystudio.sportrecorder.shared.resources.diet_create_fasting_hours
import com.crazystudio.sportrecorder.shared.resources.diet_create_fasting_hours_pick
import com.crazystudio.sportrecorder.shared.resources.diet_create_fasting_name
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_arrow_drop_down
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_fastfood_24
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_no_food_24
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

enum class Type(
    val titleRes: StringResource,
    // What a tap on the row does, read after it by a screen reader; it also names the picker the row opens.
    val pickRes: StringResource,
    val iconRes: DrawableResource,
    val defaultValue: String,
    val values: List<String>,
) {
    Fasting(
        Res.string.diet_create_fasting_hours,
        Res.string.diet_create_fasting_hours_pick,
        Res.drawable.ic_baseline_no_food_24,
        "16",
        (8..49).map { it.toString() },
    ),
    Eating(
        Res.string.diet_create_eating_hours,
        Res.string.diet_create_eating_hours_pick,
        Res.drawable.ic_baseline_fastfood_24,
        "8",
        (1..11).map { it.toString() },
    ),
}

@Composable
fun CreateFastingTypeScreen(
    onDismissRequest: () -> Unit,
    onConfirmRequest: (name: String, fastingHours: String, eatingHours: String) -> Unit,
) {
    SportRecorderTheme {
        val colorScheme = MaterialTheme.colorScheme
        Column(
            Modifier
                .fillMaxWidth()
                .background(colorScheme.surface)
                .padding(20.dp)
                .padding(top = 10.dp),
        ) {
            val nameState = remember { mutableStateOf("") }
            NameField(nameState)
            Spacer(modifier = Modifier.padding(top = 20.dp))
            val fastingTimeState = remember { mutableStateOf(Type.Fasting.defaultValue) }
            TimeSelectRow(Type.Fasting, fastingTimeState)
            Spacer(modifier = Modifier.padding(top = 20.dp))
            val eatingTimeState = remember { mutableStateOf(Type.Eating.defaultValue) }
            TimeSelectRow(Type.Eating, eatingTimeState)
            ActionButtons(
                onCancel = onDismissRequest,
                onConfirm = {
                    onConfirmRequest(nameState.value, fastingTimeState.value, eatingTimeState.value)
                },
            )
        }
    }
}

@Composable
private fun NameField(state: MutableState<String>) {
    val colorScheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = state.value,
        onValueChange = { state.value = it },
        label = { Text(stringResource(Res.string.diet_create_fasting_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colorScheme.onSurface,
            unfocusedTextColor = colorScheme.onSurface,
            focusedBorderColor = colorScheme.primary,
            unfocusedBorderColor = colorScheme.onSurfaceVariant,
            focusedLabelColor = colorScheme.primary,
            unfocusedLabelColor = colorScheme.onSurfaceVariant,
            cursorColor = colorScheme.primary,
        ),
    )
}

@Composable
private fun ActionButtons(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(modifier = Modifier.padding(top = 30.dp)) {
        OutlinedButton(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
            onClick = onCancel,
        ) {
            Text(
                text = stringResource(Res.string.diet_create_fasting_cancel),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Button(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            onClick = onConfirm,
        ) {
            Text(
                text = stringResource(Res.string.diet_create_fasting_confirm),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun TimeSelectRow(type: Type, selectedValueState: MutableState<String>) {
    var showSelectedDialog by remember { mutableStateOf(false) }
    val pick = stringResource(type.pickRes)
    Row(
        // clickable merges the icons and both texts into this one node; with the button role a screen reader
        // reads "title, value" as a single stop, then what a tap does.
        modifier = Modifier.clickable(onClickLabel = pick, role = Role.Button) { showSelectedDialog = true },
    ) {
        Image(
            painter = painterResource(type.iconRes),
            contentDescription = null, // Decoration: the title beside it names the row.
        )
        Text(
            text = stringResource(type.titleRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
        Text(
            text = selectedValueState.value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 10.dp),
        )
        Image(
            painter = painterResource(Res.drawable.ic_baseline_arrow_drop_down),
            contentDescription = null, // Only hints that something opens.
        )
    }

    if (showSelectedDialog) {
        SelectListDialog(
            title = pick,
            current = selectedValueState.value,
            onDismissRequest = {
                showSelectedDialog = false
            },
            onSelectedListener = {
                selectedValueState.value = it
            },
            values = type.values,
        )
    }
}

/**
 * The [values] in a dialog of their own. It holds nothing but numbers, so [title] is what names it for a
 * screen reader, and the [current] one is announced as selected (the others say nothing about it, as in the
 * meal editor's duration menu).
 */
@Composable
private fun SelectListDialog(
    title: String,
    current: String,
    onDismissRequest: () -> Unit,
    onSelectedListener: (String) -> Unit,
    values: List<String>,
) {
    Dialog(onDismissRequest = { onDismissRequest() }) {
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(0.5f)
                .semantics { paneTitle = title },
        ) {
            LazyColumn {
                items(values) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (it == current) Modifier.semantics { selected = true } else Modifier)
                            .padding(10.dp)
                            .clickable {
                                onSelectedListener(it)
                                onDismissRequest()
                            },
                        textAlign = TextAlign.Center,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

package com.crazystudio.sportrecorder.ui.diet.create.fasting

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme

@Preview(showBackground = true)
@Composable
private fun Preview() {
    SportRecorderTheme {
        CreateFastingTypeScreen({}, { _, _, _ -> })
    }
}

enum class Type(
    @StringRes val titleResId: Int,
    @DrawableRes val iconResId: Int,
    val defaultValue: String,
    val values: List<String>
) {
    Fasting(
        R.string.diet_create_fasting_hours,
        R.drawable.ic_baseline_no_food_24,
        "16",
        (8..49).map { it.toString() }.toList()
    ),
    Eating(
        R.string.diet_create_eating_hours,
        R.drawable.ic_baseline_fastfood_24,
        "8",
        (1..11).map { it.toString() }.toList()
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
                .padding(top = 10.dp)
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
        label = { Text(stringResource(id = R.string.diet_create_fasting_name)) },
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
                text = "CANCEL",
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
                text = "OK",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun TimeSelectRow(type: Type, selectedValueState: MutableState<String>) {
    var showSelectedDialog by remember { mutableStateOf(false) }
    Row(modifier = Modifier) {
        Image(
            painter = painterResource(id = type.iconResId),
            contentDescription = ""
        )
        Text(
            text = stringResource(id = type.titleResId),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        )
        Text(
            text = selectedValueState.value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(end = 10.dp)
                .clickable {
                    showSelectedDialog = true
                }
        )
        Image(
            painter = painterResource(id = R.drawable.ic_baseline_arrow_drop_down),
            contentDescription = ""
        )
    }

    if (showSelectedDialog) {
        SelectListDialog(
            onDismissRequest = {
                showSelectedDialog = false
            },
            onSelectedListener = {
                selectedValueState.value = it
            },
            values = type.values
        )
    }
}

@Composable
private fun SelectListDialog(
    onDismissRequest: () -> Unit,
    onSelectedListener: (String) -> Unit,
    values: List<String>
) {
    Dialog(onDismissRequest = { onDismissRequest() }) {
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(0.5f)
        ) {
            LazyColumn {
                items(values) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
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

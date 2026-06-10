package com.crazystudio.sportrecorder.ui.diet.create.food

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.ui.theme.bg_black
import com.crazystudio.sportrecorder.ui.theme.light_green
import com.crazystudio.sportrecorder.ui.theme.white

@Composable
fun CreateFoodRecordSheet(
    onConfirm: (name: String, carbs: String, protein: String, fat: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .background(bg_black)
            .padding(20.dp)
    ) {
        FoodField(
            icon = R.drawable.ic_baseline_fastfood_24,
            title = stringResource(R.string.create_food_name),
            value = name,
            onValueChange = { name = it },
            keyboardType = KeyboardType.Text,
        )
        FoodField(
            icon = R.drawable.ic_baseline_fastfood_24,
            title = stringResource(R.string.create_food_carbohydrate),
            value = carbs,
            onValueChange = { carbs = it },
            keyboardType = KeyboardType.Decimal,
        )
        FoodField(
            icon = R.drawable.ic_baseline_fastfood_24,
            title = stringResource(R.string.create_food_protein),
            value = protein,
            onValueChange = { protein = it },
            keyboardType = KeyboardType.Decimal,
        )
        FoodField(
            icon = R.drawable.ic_baseline_fastfood_24,
            title = stringResource(R.string.create_food_fat),
            value = fat,
            onValueChange = { fat = it },
            keyboardType = KeyboardType.Decimal,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            OutlinedButton(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                onClick = { /* cancel handled by navigation pop externally */ },
                border = androidx.compose.foundation.BorderStroke(1.dp, light_green),
            ) {
                Text(text = "CANCEL", color = light_green)
            }
            Button(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                onClick = { onConfirm(name, carbs, protein, fat) },
                colors = ButtonDefaults.buttonColors(containerColor = light_green),
            ) {
                Text(text = "CREATE", color = Color.Black)
            }
        }
    }
}

@Composable
private fun FoodField(
    @DrawableRes icon: Int,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier
                .padding(10.dp)
                .size(36.dp),
        )
        Text(
            text = title,
            color = white,
            fontSize = 18.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            textStyle = TextStyle(
                color = white,
                fontSize = 18.sp,
                textAlign = TextAlign.End,
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }
}

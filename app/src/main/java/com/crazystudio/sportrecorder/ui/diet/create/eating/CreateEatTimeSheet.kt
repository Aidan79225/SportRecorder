package com.crazystudio.sportrecorder.ui.diet.create.eating

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.entity.FoodRecord
import com.crazystudio.sportrecorder.ui.theme.white
import java.text.SimpleDateFormat

@Composable
fun CreateEatTimeSheet(
    state: CreateEatTimeUiState,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onAddFood: () -> Unit,
    onDeleteFood: (FoodRecord) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(colorResource(id = R.color.bg_black))
            .padding(20.dp)
    ) {
        // DATE row
        HeaderRow(
            icon = R.drawable.ic_baseline_date_range_24,
            title = stringResource(id = R.string.diet_create_eating_date_title),
            content = SimpleDateFormat("yyyy/MM/dd").format(state.date.time),
            actionIcon = R.drawable.ic_baseline_arrow_drop_down,
            onActionClick = onPickDate,
        )
        // TIME row
        HeaderRow(
            icon = R.drawable.ic_baseline_access_time_24,
            title = stringResource(id = R.string.diet_create_eating_time_title),
            content = SimpleDateFormat("HH:mm").format(state.date.time),
            actionIcon = R.drawable.ic_baseline_arrow_drop_down,
            onActionClick = onPickTime,
        )
        // ADD FOOD RECORD (+) row
        HeaderRow(
            icon = R.drawable.ic_baseline_fastfood_24,
            title = stringResource(id = R.string.diet_create_food_title),
            content = "",
            actionIcon = R.drawable.ic_baseline_add_24,
            onActionClick = onAddFood,
        )
        // FOOD rows
        state.foods.forEach { food ->
            HeaderRow(
                icon = R.drawable.ic_baseline_fastfood_24,
                title = stringResource(id = R.string.diet_food_record_title),
                content = food.name,
                actionIcon = R.drawable.ic_baseline_delete_24,
                onActionClick = { onDeleteFood(food) },
            )
        }
        // CREATE button
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 30.dp),
            onClick = onConfirm,
        ) {
            Text(
                text = "CREATE",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun HeaderRow(
    @DrawableRes icon: Int,
    title: String,
    content: String,
    @DrawableRes actionIcon: Int,
    onActionClick: () -> Unit,
) {
    Row(
        modifier = Modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = "",
            modifier = Modifier
                .padding(10.dp)
                .size(36.dp),
        )
        Text(
            text = title,
            color = white,
            fontSize = 18.sp,
        )
        Text(
            text = content,
            color = white,
            fontSize = 18.sp,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Image(
            painter = painterResource(id = actionIcon),
            contentDescription = "",
            modifier = Modifier
                .padding(10.dp)
                .size(36.dp)
                .clickable { onActionClick() },
        )
    }
}

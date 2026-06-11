package com.crazystudio.sportrecorder.ui.diet.select

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme

private val backgroundColors = listOf(
    R.color.google_red,
    R.color.google_blue,
    R.color.google_yellow,
    R.color.google_green,
)

@Preview
@Composable
fun PreviewSelectFastingTypeScreen() {
    SportRecorderTheme {
        SelectFastingTypeScreen(
            FastingItem.defaultFastingItems +
                listOf(
                    FastingItem.CustomFastingItem(
                        16,
                        10
                    )
                ),
            {},
            { fastingHours, eatingHours -> }
        )
    }
}

@Composable
fun SelectFastingTypeScreen(
    itemList: List<FastingItem>,
    onAddClick: () -> Unit,
    onItemClick: (fastingHours: Long, eatingHours: Long) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        item(span = { GridItemSpan(2) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(id = R.string.diet_fasting_type_title),
                    style = MaterialTheme.typography.headlineLarge
                )
            }
        }
        itemsIndexed(itemList) { index, item ->
            when (item) {
                is FastingItem.DefaultFastingItem -> DefaultFastingItem(index, item, onItemClick)
                is FastingItem.CustomFastingItem -> CustomFastingItem(index, item, onItemClick)
            }
        }
        item {
            AddFastingItem(onAddClick)
        }
    }
}

@Composable
fun DefaultFastingItem(
    position: Int,
    item: FastingItem.DefaultFastingItem,
    onItemClick: (fastingHours: Long, eatingHours: Long) -> Unit
) {
    Column(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(10.dp)
            .background(color = colorResource(id = backgroundColors[position % backgroundColors.size]))
            .clickable { onItemClick(item.fastingHours, item.eatingHours) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(id = item.nameResId),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "${item.fastingHours} : ${item.eatingHours}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun CustomFastingItem(
    position: Int,
    item: FastingItem.CustomFastingItem,
    onItemClick: (fastingHours: Long, eatingHours: Long) -> Unit
) {
    Column(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(10.dp)
            .background(color = colorResource(id = backgroundColors[position % backgroundColors.size]))
            .clickable { onItemClick(item.fastingHours, item.eatingHours) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "${item.fastingHours} : ${item.eatingHours}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun AddFastingItem(onClick: () -> Unit) {
    OutlinedButton(
        onClick = { onClick() },
        shape = RectangleShape,
        modifier = Modifier
            .aspectRatio(1f)
            .padding(10.dp),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_baseline_add_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

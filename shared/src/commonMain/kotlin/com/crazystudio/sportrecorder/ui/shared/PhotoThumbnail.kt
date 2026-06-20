package com.crazystudio.sportrecorder.ui.shared

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Square, rounded photo thumbnail backed by Coil 3 (multiplatform). [model] comes from a
 * [com.crazystudio.sportrecorder.data.PhotoImageSource] so the caller stays platform-agnostic.
 */
@Composable
fun PhotoThumbnail(model: Any?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp)),
    )
}

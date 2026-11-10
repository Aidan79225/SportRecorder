package com.crazystudio.sportrecorder.ui.diet.record

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.R

/**
 * The photo viewer's actions, along the top edge. The buttons are icon-only, so their labels are what a
 * screen reader says.
 *
 * A composable of its own because [FullScreenPhotoViewer] shows it inside a `Dialog`, whose content is
 * composed in a root of its own that re-provides `LocalContext` and `LocalConfiguration` from the dialog's
 * context: a test can't hand the viewer a language through composition locals.
 */
@Composable
internal fun PhotoViewerToolbar(
    onShare: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        ToolbarButton(R.drawable.ic_baseline_share_24, stringResource(R.string.photo_viewer_share), onShare)
        ToolbarButton(R.drawable.ic_baseline_download_24, stringResource(R.string.photo_viewer_save), onSave)
        ToolbarButton(R.drawable.ic_baseline_close_24, stringResource(R.string.photo_viewer_close), onClose)
    }
}

@Composable
private fun ToolbarButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painter = painterResource(icon), contentDescription = label, tint = Color.White)
    }
}

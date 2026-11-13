package com.crazystudio.sportrecorder.ui.shared

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Makes a photo that opens the viewer a button a screen reader can name. A photo is only an image, and an
 * image without a description is a stop that says nothing, so [description] says which photo it is (its
 * place among the ones the viewer pages through) and [onClickLabel] what a tap does.
 *
 * The two texts come in as arguments rather than being looked up here, because a modifier factory should
 * not be `@Composable`.
 */
fun Modifier.photoButton(description: String, onClickLabel: String, onClick: () -> Unit): Modifier =
    clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }

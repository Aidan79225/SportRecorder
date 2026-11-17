package com.crazystudio.sportrecorder.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher

// What a screen reader (TalkBack) is told about a control, read from the semantics it reads.

/** A tap is announced as [label], as in "Double tap to [label]". */
fun announcedAs(label: String) = SemanticsMatcher("a tap is announced as \"$label\"") {
    it.config.getOrNull(SemanticsActions.OnClick)?.label == label
}

/** The node is a button to a screen reader. */
fun isButton() = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

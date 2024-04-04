package com.crazystudio.sportrecorder.ui.diet.select

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class SpaceItemDecoration(private val spacePx: Int, private val spanCount: Int): RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.top = spacePx
        outRect.left = spacePx
        outRect.bottom = spacePx
        outRect.right = spacePx
    }
}
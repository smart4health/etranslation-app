package de.hpi.etranslation

import android.graphics.Rect
import android.view.View
import androidx.annotation.Px
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Evenly space items in a grid given a static margin
 *
 * Pairs nicely with items that match parent width
 *
 * Also adds bottom and top spacing as needed
 */
class GridSpacingItemDecoration(
    @Px
    private val spacing: Int,
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildLayoutPosition(view)
        val columnCount = (parent.layoutManager as GridLayoutManager).spanCount

        // everyone gets bottom spacing
        outRect.bottom = spacing

        // everyone gets half on left and right, to finish making
        // everything even, make sure to add horizontal padding!
        outRect.left = spacing / 2
        outRect.right = spacing / 2

        if (position < columnCount) {
            // only first items get top spacing
            outRect.top = spacing
        } else {
            outRect.top = 0
        }
    }
}

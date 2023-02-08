package de.hpi.etranslation

import android.graphics.Rect
import android.text.Spannable
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.widget.TextView

/**
 * Custom movement method that doesn't mess up the selection
 * state as badly as the default LinkMovementMethod
 */
class ClickableMovementMethod(
    private val onActionDown: () -> Unit = {},
) : LinkMovementMethod() {

    private var clickedSpan: ClickableSpan? = null

    private var selectionSpan: BackgroundColorSpan? = null

    /**
     * Stolen mostly from LinkMovementMethod itself, with some more selection manipulation
     */
    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                clickedSpan = getClickedSpan(widget, buffer, event)?.also {
                    onActionDown()

                    selectionSpan = BackgroundColorSpan(widget.highlightColor)

                    buffer.setSpan(
                        selectionSpan,
                        buffer.getSpanStart(it),
                        buffer.getSpanEnd(it),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }

                clickedSpan != null
            }
            MotionEvent.ACTION_MOVE -> {
                if (clickedSpan != null && getClickedSpan(widget, buffer, event) != clickedSpan) {
                    // cancel the clicking
                    buffer.removeSpan(selectionSpan)
                    clickedSpan = null
                }

                clickedSpan != null
            }
            MotionEvent.ACTION_CANCEL -> {
                buffer.removeSpan(selectionSpan)
                clickedSpan = null
                true
            }
            MotionEvent.ACTION_UP -> {
                clickedSpan?.onClick(widget)
                clickedSpan = null
                buffer.removeSpan(selectionSpan)
                true
            }
            else -> false
        }
    }

    private fun getClickedSpan(
        textView: TextView,
        spannable: Spannable,
        event: MotionEvent,
    ): ClickableSpan? {
        val x = event.x - textView.totalPaddingLeft + textView.scrollX
        val y = event.y - textView.totalPaddingTop + textView.scrollY

        val clickedCharacterIndex = with(textView.layout) {
            val line = getLineForVertical(y.toInt())

            // borrowed from https://github.com/saket/Better-Link-Movement-Method/,
            // it prevents the area to the left of centered text being clickable
            // and no, getLineBounds() is wrong
            val rect = Rect()
            rect.left = getLineLeft(line).toInt()
            rect.top = getLineTop(line)
            rect.right = (rect.left + getLineWidth(line)).toInt()
            rect.bottom = getLineBottom(line)

            if (rect.contains(x.toInt(), y.toInt()))
                getOffsetForHorizontal(line, x)
            else
                null
        } ?: return null

        return spannable.getSpans(
            clickedCharacterIndex,
            clickedCharacterIndex,
            ClickableSpan::class.java,
        ).mapNotNull {
            val spanStart = spannable.getSpanStart(it)
            val spanEnd = spannable.getSpanEnd(it)
            if (clickedCharacterIndex in spanStart..spanEnd)
                it
            else
                null
        }.firstOrNull()
    }
}

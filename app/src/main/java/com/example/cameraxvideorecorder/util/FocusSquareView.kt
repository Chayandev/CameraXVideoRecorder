package com.example.cameraxvideorecorder.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View

class FocusSquareView(context: Context, attributeSet: AttributeSet) : View(context, attributeSet) {

    private val paint = Paint()

   var focusSquare: RectF? = null

    private var handler = Handler(Looper.getMainLooper())
    private var removeFocusRunnable = Runnable { }

    init {
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        focusSquare?.let { rect ->
            // Calculate the size of the corner lines
            val cornerSize = rect.width() / 10f

            // Draw top left corner
            canvas.drawLine(rect.left, rect.top, rect.left + cornerSize, rect.top, paint)
            canvas.drawLine(rect.left, rect.top, rect.left, rect.top + cornerSize, paint)

            // Draw top right corner
            canvas.drawLine(rect.right, rect.top, rect.right - cornerSize, rect.top, paint)
            canvas.drawLine(rect.right, rect.top, rect.right, rect.top + cornerSize, paint)

            // Draw bottom left corner
            canvas.drawLine(rect.left, rect.bottom, rect.left + cornerSize, rect.bottom, paint)
            canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - cornerSize, paint)

            // Draw bottom right corner
            canvas.drawLine(rect.right, rect.bottom, rect.right - cornerSize, rect.bottom, paint)
            canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - cornerSize, paint)

            scheduleFocusSquareRemoval()
        }
    }

    private fun scheduleFocusSquareRemoval() {
        handler.removeCallbacks(removeFocusRunnable)
        removeFocusRunnable = Runnable {
            focusSquare = null
            invalidate()
        }
        handler.postDelayed(removeFocusRunnable, 2000)
    }
}
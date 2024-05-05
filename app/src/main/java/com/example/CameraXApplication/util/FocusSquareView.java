package com.example.CameraXApplication.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

public class FocusSquareView extends View {
    private Paint paint = new Paint();
    public RectF focusSquare = null;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable removeFocusRunnable = () -> {};

    public FocusSquareView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (focusSquare != null) {
            // Calculate the size of the corner lines
            float cornerSize = focusSquare.width() / 10f;

            // Draw top left corner
            canvas.drawLine(focusSquare.left, focusSquare.top, focusSquare.left + cornerSize, focusSquare.top, paint);
            canvas.drawLine(focusSquare.left, focusSquare.top, focusSquare.left, focusSquare.top + cornerSize, paint);

            // Draw top right corner
            canvas.drawLine(focusSquare.right, focusSquare.top, focusSquare.right - cornerSize, focusSquare.top, paint);
            canvas.drawLine(focusSquare.right, focusSquare.top, focusSquare.right, focusSquare.top + cornerSize, paint);

            // Draw bottom left corner
            canvas.drawLine(focusSquare.left, focusSquare.bottom, focusSquare.left + cornerSize, focusSquare.bottom, paint);
            canvas.drawLine(focusSquare.left, focusSquare.bottom, focusSquare.left, focusSquare.bottom - cornerSize, paint);

            // Draw bottom right corner
            canvas.drawLine(focusSquare.right, focusSquare.bottom, focusSquare.right - cornerSize, focusSquare.bottom, paint);
            canvas.drawLine(focusSquare.right, focusSquare.bottom, focusSquare.right, focusSquare.bottom - cornerSize, paint);

            scheduleFocusSquareRemoval();
        }
    }

    private void scheduleFocusSquareRemoval() {
        handler.removeCallbacks(removeFocusRunnable);
        removeFocusRunnable = () -> {
            focusSquare = null;
            invalidate();
        };
        handler.postDelayed(removeFocusRunnable, 2000);
    }
}
package io.github.virresh.matvt.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/**
 * Non-interactive highlight used by the Netflix D-pad mode.
 */
public class FocusHighlightView extends View {

    private static final int ACCENT = Color.rgb(0, 181, 247);

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect rect = new Rect();
    private final RectF rectF = new RectF();

    private boolean pointMode;
    private float pointX;
    private float pointY;
    private float pointRadius;

    public FocusHighlightView(Context context) {
        super(context);
        setWillNotDraw(false);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(0x3300B5F7);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(3));
        strokePaint.setColor(ACCENT);

        setVisibility(INVISIBLE);
    }

    public void showRect(Rect newRect) {
        if (newRect == null || newRect.isEmpty()) {
            hide();
            return;
        }
        rect.set(newRect);
        pointMode = false;
        setVisibility(VISIBLE);
        invalidate();
    }

    public void showPoint(float x, float y, float radius) {
        pointX = x;
        pointY = y;
        pointRadius = radius;
        pointMode = true;
        setVisibility(VISIBLE);
        invalidate();
    }

    public void hide() {
        if (getVisibility() != INVISIBLE) {
            setVisibility(INVISIBLE);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getVisibility() != VISIBLE) {
            return;
        }

        if (pointMode) {
            canvas.drawCircle(pointX, pointY, pointRadius, fillPaint);
            canvas.drawCircle(pointX, pointY, pointRadius, strokePaint);
            canvas.drawLine(pointX - pointRadius * 1.4f, pointY,
                    pointX + pointRadius * 1.4f, pointY, strokePaint);
            canvas.drawLine(pointX, pointY - pointRadius * 1.4f,
                    pointX, pointY + pointRadius * 1.4f, strokePaint);
            return;
        }

        rectF.set(rect);
        float inset = strokePaint.getStrokeWidth() / 2f;
        rectF.inset(inset, inset);
        float radius = dp(8);
        canvas.drawRoundRect(rectF, radius, radius, fillPaint);
        canvas.drawRoundRect(rectF, radius, radius, strokePaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

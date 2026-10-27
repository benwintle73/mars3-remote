package io.github.virresh.matvt.navigation;

import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;

/**
 * Builds safe gesture descriptions. All coordinates are clamped to the
 * active window so Android never receives an out-of-bounds path.
 */
public final class GestureHelper {

    public static final long TAP_DURATION_MS = 60;
    public static final long LONG_PRESS_DURATION_MS = 650;

    private GestureHelper() {
    }

    public static GestureDescription tap(Rect bounds, float x, float y) {
        Path path = new Path();
        path.moveTo(clampX(bounds, x), clampY(bounds, y));
        return build(path, TAP_DURATION_MS);
    }

    public static GestureDescription longPress(Rect bounds, float x, float y) {
        Path path = new Path();
        path.moveTo(clampX(bounds, x), clampY(bounds, y));
        return build(path, LONG_PRESS_DURATION_MS);
    }

    public static GestureDescription swipe(Rect bounds, float fromX, float fromY,
                                           float toX, float toY, long durationMs) {
        float x1 = clampX(bounds, fromX);
        float y1 = clampY(bounds, fromY);
        float x2 = clampX(bounds, toX);
        float y2 = clampY(bounds, toY);
        if (x1 == x2 && y1 == y2) {
            x2 = x1 < bounds.right - 1 ? x1 + 1 : x1 - 1;
        }
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        return build(path, Math.max(40, durationMs));
    }

    public static float clampX(Rect bounds, float x) {
        return Math.max(bounds.left + 1, Math.min(bounds.right - 1, x));
    }

    public static float clampY(Rect bounds, float y) {
        return Math.max(bounds.top + 1, Math.min(bounds.bottom - 1, y));
    }

    private static GestureDescription build(Path path, long durationMs) {
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(1, durationMs));
        return new GestureDescription.Builder().addStroke(stroke).build();
    }
}

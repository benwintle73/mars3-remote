package io.github.virresh.matvt.engine.impl;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.Log;
import android.view.KeyEvent;

import java.util.ArrayDeque;

import io.github.virresh.matvt.helper.AppPreferences;
import io.github.virresh.matvt.navigation.GestureHelper;
import io.github.virresh.matvt.view.MouseCursorView;
import io.github.virresh.matvt.view.OverlayView;

public class GestureDispatchMouseEngine extends BaseEngine {

    private static final String LOG_TAG = "GESTURE_MOUSE_EMULATION";

    private final ArrayDeque<GestureDescription> gestureQueue = new ArrayDeque<>();
    private boolean gestureInFlight;

    public GestureDispatchMouseEngine(AccessibilityService service, OverlayView ov,
                                      AppPreferences appPreferences,
                                      MouseCursorView mouseCursorView) {
        super(appPreferences, ov, mouseCursorView);
        this.mService = service;
    }

    @Override
    protected int scroll(KeyEvent ke) {
        if (mService == null || !scrollCodeMap.containsKey(ke.getKeyCode())) {
            return 0;
        }
        PointF pointer = mPointerControl.getPointerLocation();
        int direction = scrollCodeMap.get(ke.getKeyCode());
        int distance = 50 + scrollSpeed * 10;
        float toX = pointer.x + distance * PointerControl.dirX[direction];
        float toY = pointer.y + distance * PointerControl.dirY[direction];
        long duration = 100L + scrollSpeed * 10L;
        enqueue(GestureHelper.swipe(gestureBounds(), pointer.x, pointer.y, toX, toY, duration));
        return 1;
    }

    @Override
    protected int click(KeyEvent ke) {
        if (mService == null) {
            return 0;
        }
        PointF pointer = mPointerControl.getPointerLocation();
        enqueue(GestureHelper.tap(gestureBounds(), pointer.x, pointer.y));
        return 1;
    }

    @Override
    protected int longClick(KeyEvent ke) {
        if (mService == null) {
            return 0;
        }
        PointF pointer = mPointerControl.getPointerLocation();
        enqueue(GestureHelper.longPress(gestureBounds(), pointer.x, pointer.y));
        return 1;
    }

    private void enqueue(GestureDescription gesture) {
        gestureQueue.add(gesture);
        drainQueue();
    }

    private void drainQueue() {
        if (gestureInFlight || mService == null) {
            return;
        }
        GestureDescription next = gestureQueue.poll();
        if (next == null) {
            return;
        }
        gestureInFlight = true;
        try {
            boolean accepted = mService.dispatchGesture(next, gestureResultCallback, null);
            if (!accepted) {
                gestureInFlight = false;
                drainQueue();
            }
        } catch (RuntimeException e) {
            gestureInFlight = false;
            Log.e(LOG_TAG, "Could not dispatch gesture", e);
        }
    }

    private final AccessibilityService.GestureResultCallback gestureResultCallback =
            new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    gestureInFlight = false;
                    drainQueue();
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    gestureInFlight = false;
                    drainQueue();
                }
            };

    private Rect gestureBounds() {
        int width = mOverlayView.getWidth();
        int height = mOverlayView.getHeight();
        if (width <= 1 || height <= 1) {
            width = mOverlayView.getResources().getDisplayMetrics().widthPixels;
            height = mOverlayView.getResources().getDisplayMetrics().heightPixels;
        }
        return new Rect(0, 0, Math.max(2, width), Math.max(2, height));
    }

    @Override
    public void destroy() {
        gestureQueue.clear();
        gestureInFlight = false;
        super.destroy();
    }
}

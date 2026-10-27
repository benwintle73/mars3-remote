package io.github.virresh.matvt.navigation;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import io.github.virresh.matvt.helper.AppPreferences;
import io.github.virresh.matvt.view.FocusHighlightView;

/**
 * Optional app-specific D-pad navigation for the mobile/tablet Netflix app.
 *
 * Netflix does not expose a reliable focusable UI on every build. This class
 * therefore has two layers:
 *  - node mode: move between clickable/actionable accessibility nodes and tap
 *    their centres when OK is pressed;
 *  - coordinate fallback: if the app exposes no usable nodes, move an invisible
 *    pointer on a configurable grid and tap at that point.
 *
 * Nothing here modifies Netflix, bypasses DRM, or changes device certification.
 */
public class NetflixNavigator {

    private static final String TAG = "MATVT_NETFLIX";

    private static final long CACHE_MS = 200;
    private static final long SCROLL_COOLDOWN_MS = 350;
    private static final long SCROLL_SETTLE_MS = 450;
    private static final long LONG_PRESS_MS = 600;
    private static final long FOREGROUND_CHECK_MS = 1500;
    private static final long MOVE_REPEAT_MS = 120;
    private static final int MAX_NODES = 1800;
    private static final int MAX_DEPTH = 50;
    private static final int MIN_TOUCH_DP = 8;

    private static final Comparator<Candidate> READING_ORDER = new Comparator<Candidate>() {
        @Override
        public int compare(Candidate a, Candidate b) {
            int top = Integer.compare(a.bounds.top, b.bounds.top);
            if (top != 0) return top;
            return Integer.compare(a.bounds.left, b.bounds.left);
        }
    };

    private final AccessibilityService service;
    private final AppPreferences preferences;
    private final FocusHighlightView highlight;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<SpatialNavigator.Item> items = new ArrayList<>();
    private final Rect screenBounds = new Rect();

    private String foregroundPackage;
    private long lastForegroundCheck;
    private long lastRefresh;
    private long lastScrollAt;
    private long lastMoveAt;
    private boolean suspended;
    private boolean playbackDetected;
    private boolean directionKeyConsumed;
    private boolean confirmPassThrough;
    private boolean loggedEmptyTree;
    private boolean toastShown;
    private SpatialNavigator.Item selected;
    private Point fallbackPoint;

    private Runnable pendingLongPress;
    private boolean longPressTriggered;

    public NetflixNavigator(AccessibilityService service, AppPreferences preferences,
                            FocusHighlightView highlight) {
        this.service = service;
        this.preferences = preferences;
        this.highlight = highlight;
    }

    public void updateFromPreferences() {
        if (!preferences.isNetflixModeEnabled()) {
            deactivate();
        } else {
            invalidateCache();
            if (preferences.isNetflixFallbackEnabled()
                    && !playbackDetected
                    && fallbackPoint != null
                    && items.isEmpty()) {
                highlight.showPoint(fallbackPoint.x, fallbackPoint.y, dp(22));
            } else if (!preferences.isNetflixFallbackEnabled() && items.isEmpty()) {
                highlight.hide();
            }
        }
    }

    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }

        CharSequence packageName = event.getPackageName();
        if (packageName == null) {
            refreshForegroundFromWindows();
            return;
        }

        String pkg = packageName.toString();
        if (pkg.equals("android") || pkg.equals("com.android.systemui")) {
            refreshForegroundFromWindows();
        } else if (isNetflixPackage(pkg)
                && type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && pkg.equals(foregroundPackage)) {
            // A new Netflix screen/dialog usually means the old item bounds
            // are stale. Start from the centre of the new screen.
            resetNavigationState();
            lastRefresh = 0;
        } else {
            updateForeground(pkg);
        }
    }

    public boolean handleKeyEvent(KeyEvent event) {
        if (event == null) {
            return false;
        }

        ensureForeground();
        if (!isActiveForInput()) {
            return false;
        }

        int keyCode = event.getKeyCode();
        int action = event.getAction();

        // Back is never intercepted here. BaseEngine owns long-press recovery.
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return false;
        }

        if (isConfirmKey(keyCode)) {
            if (confirmPassThrough) {
                if (action == KeyEvent.ACTION_UP) {
                    confirmPassThrough = false;
                }
                return false;
            }
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    refreshCandidates();
                    if (playbackDetected && items.size() < 2) {
                        // Let Netflix handle OK while a full-screen video is
                        // playing, so its own controls/seekbar appear.
                        confirmPassThrough = true;
                        return false;
                    }
                    if (items.isEmpty() && !preferences.isNetflixFallbackEnabled()) {
                        confirmPassThrough = true;
                        return false;
                    }
                    cancelLongPress();
                    longPressTriggered = false;
                    pendingLongPress = new Runnable() {
                        @Override
                        public void run() {
                            longPressTriggered = true;
                            clickSelected(true);
                        }
                    };
                    handler.postDelayed(pendingLongPress, LONG_PRESS_MS);
                }
                return true;
            } else if (action == KeyEvent.ACTION_UP) {
                cancelLongPress();
                if (!longPressTriggered) {
                    clickSelected(false);
                }
                longPressTriggered = false;
                return true;
            }
            return true;
        }

        SpatialNavigator.Direction direction = directionFor(keyCode);
        if (direction == null) {
            if (pendingLongPress != null) {
                cancelLongPress();
            }
            return false;
        }

        if (action == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                refreshCandidates();
                if (!canHandleDirection()) {
                    directionKeyConsumed = false;
                    return false;
                }
                directionKeyConsumed = true;
                move(direction);
                lastMoveAt = SystemClock.uptimeMillis();
            } else if (directionKeyConsumed) {
                long now = SystemClock.uptimeMillis();
                if (now - lastMoveAt >= MOVE_REPEAT_MS) {
                    refreshCandidates();
                    if (canHandleDirection()) {
                        move(direction);
                        lastMoveAt = now;
                    }
                }
            }
            return directionKeyConsumed;
        } else if (action == KeyEvent.ACTION_UP) {
            boolean consumed = directionKeyConsumed;
            directionKeyConsumed = false;
            return consumed;
        }
        return false;
    }

    public boolean isActiveForInput() {
        return preferences.isNetflixModeEnabled()
                && !suspended
                && isNetflixPackage(foregroundPackage);
    }

    public String getForegroundPackage() {
        return foregroundPackage;
    }

    public void suspend() {
        suspended = true;
        resetNavigationState();
        Log.i(TAG, "Netflix smart navigation suspended");
    }

    public void resume() {
        if (suspended) {
            suspended = false;
            invalidateCache();
            Log.i(TAG, "Netflix smart navigation resumed");
        }
    }

    public void destroy() {
        handler.removeCallbacksAndMessages(null);
        cancelLongPress();
        items.clear();
        selected = null;
        fallbackPoint = null;
        highlight.hide();
    }

    private void move(SpatialNavigator.Direction direction) {
        if (items.isEmpty()) {
            if (preferences.isNetflixFallbackEnabled()) {
                moveFallback(direction);
            }
            return;
        }

        SpatialNavigator.Item current = currentSelection();
        if (current == null) {
            return;
        }

        SpatialNavigator.Item next = SpatialNavigator.findNext(items, current, direction);
        if (next != null) {
            select(next);
            return;
        }

        if (!tryScroll(direction)) {
            Log.d(TAG, "No selectable node towards " + direction);
        }
    }

    private boolean canHandleDirection() {
        if (playbackDetected && items.size() < 2) {
            // With only a video surface visible, leave D-pad events to Netflix
            // so native playback/seek behaviour is preserved.
            return false;
        }
        if (items.isEmpty()) {
            return preferences.isNetflixFallbackEnabled();
        }
        return true;
    }

    private void refreshCandidates() {
        long now = SystemClock.uptimeMillis();
        if (now - lastRefresh < CACHE_MS) {
            return;
        }
        lastRefresh = now;

        items.clear();
        playbackDetected = false;
        updateWindowBounds();

        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) {
            selected = null;
            highlight.hide();
            return;
        }

        Rect window = windowBounds();
        List<Candidate> found = new ArrayList<>();
        try {
            collect(root, found, 0, window, null);
            dedupeAndCommit(found);
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not inspect Netflix UI tree", e);
            items.clear();
            selected = null;
        }

        if (items.isEmpty()) {
            selected = null;
            if (playbackDetected) {
                highlight.hide();
                return;
            }
            if (!loggedEmptyTree) {
                loggedEmptyTree = true;
                Log.i(TAG, "Netflix exposes no selectable nodes; fallback="
                        + preferences.isNetflixFallbackEnabled());
            }
            if (preferences.isNetflixFallbackEnabled()) {
                ensureFallbackPoint(window);
                highlight.showPoint(fallbackPoint.x, fallbackPoint.y, dp(22));
                showToastOnce("Netflix UI has no exposed items - using coordinate mode");
            } else {
                highlight.hide();
            }
            return;
        }

        if (playbackDetected && items.size() < 2) {
            selected = null;
            highlight.hide();
            return;
        }

        loggedEmptyTree = false;
        SpatialNavigator.Item current = currentSelection();
        if (current != null) {
            highlight.showRect(rectOf(current));
        }
    }

    private void collect(AccessibilityNodeInfo node, List<Candidate> out, int depth,
                         Rect window, AccessibilityNodeInfo clickableAncestor) {
        if (node == null || depth > MAX_DEPTH || out.size() >= MAX_NODES) {
            return;
        }

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (!bounds.isEmpty() && !Rect.intersects(bounds, window)) {
            return;
        }

        boolean visible = node.isVisibleToUser();
        String className = stringOf(node.getClassName());
        String viewId = stringOf(node.getViewIdResourceName());
        if (visible && isVideoSurface(className, viewId, bounds, window)) {
            playbackDetected = true;
        }

        boolean clickable = node.isClickable() || hasAction(node, AccessibilityNodeInfo.ACTION_CLICK);
        CharSequence labelValue = node.getContentDescription();
        if (TextUtils.isEmpty(labelValue)) {
            labelValue = node.getText();
        }
        String label = labelValue == null ? "" : labelValue.toString().trim();

        int minSize = Math.max(4, (int) (MIN_TOUCH_DP * density()));
        if (visible && bounds.width() >= minSize && bounds.height() >= minSize) {
            AccessibilityNodeInfo target = clickable ? node : clickableAncestor;
            boolean focusableWithLabel = node.isFocusable()
                    && !label.isEmpty()
                    && isInteractiveClass(className);

            if (target != null && (clickable || !label.isEmpty())) {
                Rect targetBounds = new Rect();
                target.getBoundsInScreen(targetBounds);
                if (isValidCandidate(targetBounds, window, minSize)) {
                    out.add(new Candidate(new Rect(targetBounds), label, className));
                }
            } else if (focusableWithLabel && isValidCandidate(bounds, window, minSize)) {
                out.add(new Candidate(new Rect(bounds), label, className));
            }
        }

        AccessibilityNodeInfo nextClickable = clickable ? node : clickableAncestor;
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collect(child, out, depth + 1, window, nextClickable);
            }
        }
    }

    private boolean isValidCandidate(Rect bounds, Rect window, int minSize) {
        if (bounds.width() < minSize || bounds.height() < minSize) {
            return false;
        }
        if (!Rect.intersects(bounds, window)) {
            return false;
        }
        long area = (long) bounds.width() * bounds.height();
        long windowArea = (long) window.width() * window.height();
        return windowArea <= 0 || area <= windowArea * 0.70;
    }

    private void dedupeAndCommit(List<Candidate> found) {
        Collections.sort(found, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                return Long.compare((long) a.bounds.width() * a.bounds.height(),
                        (long) b.bounds.width() * b.bounds.height());
            }
        });

        List<Candidate> kept = new ArrayList<>();
        for (Candidate candidate : found) {
            boolean duplicate = false;
            for (Candidate existing : kept) {
                if (intersectionOverUnion(candidate.bounds, existing.bounds) >= 0.75f) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                kept.add(candidate);
            }
        }

        Collections.sort(kept, READING_ORDER);
        items.clear();
        for (Candidate candidate : kept) {
            items.add(new SpatialNavigator.Item(
                    candidate.bounds.left,
                    candidate.bounds.top,
                    candidate.bounds.right,
                    candidate.bounds.bottom,
                    candidate.label));
        }
    }

    private SpatialNavigator.Item currentSelection() {
        if (items.isEmpty()) {
            return null;
        }
        if (selected != null) {
            for (SpatialNavigator.Item item : items) {
                if (item.sameBounds(selected)) {
                    selected = item;
                    return item;
                }
            }
            SpatialNavigator.Item nearest = SpatialNavigator.findNearestToPoint(
                    items, selected.centerX(), selected.centerY());
            selected = nearest;
            return nearest;
        }

        Rect window = windowBounds();
        selected = SpatialNavigator.findNearestToPoint(items, window.centerX(), window.centerY());
        return selected;
    }

    private void select(SpatialNavigator.Item item) {
        selected = item;
        if (item != null) {
            highlight.showRect(rectOf(item));
        }
    }

    private boolean tryScroll(SpatialNavigator.Direction direction) {
        long now = SystemClock.uptimeMillis();
        if (now - lastScrollAt < SCROLL_COOLDOWN_MS) {
            return true;
        }
        lastScrollAt = now;

        Point point = selected != null
                ? new Point(selected.centerX(), selected.centerY())
                : new Point(windowBounds().centerX(), windowBounds().centerY());

        boolean scrolled = false;
        AccessibilityNodeInfo scrollable = findScrollableNodeAt(point.x, point.y);
        if (scrollable != null) {
            int action = (direction == SpatialNavigator.Direction.DOWN
                    || direction == SpatialNavigator.Direction.RIGHT)
                    ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
            try {
                scrolled = scrollable.performAction(action);
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not scroll Netflix container", e);
            }
        }
        if (!scrolled) {
            scrolled = dispatchSwipe(direction);
        }

        if (scrolled) {
            final SpatialNavigator.Item old = selected;
            final SpatialNavigator.Direction scrollDirection = direction;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    invalidateCache();
                    refreshCandidates();
                    if (items.isEmpty()) {
                        return;
                    }
                    SpatialNavigator.Item next = old == null
                            ? null
                            : SpatialNavigator.findNext(items, old, scrollDirection);
                    if (next == null && old != null) {
                        next = SpatialNavigator.findNearestToPoint(
                                items, old.centerX(), old.centerY());
                    }
                    if (next != null) {
                        select(next);
                    }
                }
            }, SCROLL_SETTLE_MS);
        }
        return scrolled;
    }

    private boolean dispatchSwipe(SpatialNavigator.Direction direction) {
        Rect window = windowBounds();
        float x = window.centerX();
        float y = window.centerY();
        float dx = 0;
        float dy = 0;

        if (direction == SpatialNavigator.Direction.LEFT
                || direction == SpatialNavigator.Direction.RIGHT) {
            float distance = window.width() * 0.55f;
            dx = direction == SpatialNavigator.Direction.RIGHT ? -distance : distance;
        } else {
            float distance = window.height() * 0.45f;
            dy = direction == SpatialNavigator.Direction.DOWN ? -distance : distance;
        }

        try {
            return service.dispatchGesture(
                    GestureHelper.swipe(window, x, y, x + dx, y + dy, 240), null, null);
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not dispatch scroll swipe", e);
            return false;
        }
    }

    private void moveFallback(SpatialNavigator.Direction direction) {
        Rect window = windowBounds();
        ensureFallbackPoint(window);

        int stepX = Math.max((int) (48 * density()),
                window.width() * (2 + preferences.getCursorSpeed()) / 100);
        int stepY = Math.max((int) (32 * density()),
                window.height() * (2 + preferences.getCursorSpeed()) / 100);

        int nextX = fallbackPoint.x;
        int nextY = fallbackPoint.y;
        switch (direction) {
            case LEFT:
                nextX -= stepX;
                break;
            case RIGHT:
                nextX += stepX;
                break;
            case UP:
                nextY -= stepY;
                break;
            case DOWN:
                nextY += stepY;
                break;
        }

        boolean outside = nextX < window.left || nextX > window.right
                || nextY < window.top || nextY > window.bottom;
        if (outside && tryScroll(direction)) {
            highlight.showPoint(fallbackPoint.x, fallbackPoint.y, dp(22));
            return;
        }

        fallbackPoint.x = clamp(nextX, window.left + 1, window.right - 1);
        fallbackPoint.y = clamp(nextY, window.top + 1, window.bottom - 1);
        highlight.showPoint(fallbackPoint.x, fallbackPoint.y, dp(22));
    }

    private void ensureFallbackPoint(Rect window) {
        if (fallbackPoint == null) {
            fallbackPoint = new Point(window.centerX(), window.centerY());
        }
        fallbackPoint.x = clamp(fallbackPoint.x, window.left + 1, window.right - 1);
        fallbackPoint.y = clamp(fallbackPoint.y, window.top + 1, window.bottom - 1);
    }

    private void clickSelected(boolean longClick) {
        if (selected != null) {
            AccessibilityNodeInfo node = findActionableNodeAt(
                    selected.centerX(), selected.centerY(), longClick);
            boolean performed = false;
            if (node != null) {
                try {
                    performed = node.performAction(longClick
                            ? AccessibilityNodeInfo.ACTION_LONG_CLICK
                            : AccessibilityNodeInfo.ACTION_CLICK);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Could not perform node action", e);
                }
            }
            if (!performed) {
                dispatchTapOrLongPress(selected.centerX(), selected.centerY(), longClick);
            }
            invalidateCacheSoon();
            return;
        }

        Rect window = windowBounds();
        Point point = fallbackPoint != null
                ? fallbackPoint
                : new Point(window.centerX(), window.centerY());
        dispatchTapOrLongPress(point.x, point.y, longClick);
        invalidateCacheSoon();
    }

    private AccessibilityNodeInfo findActionableNodeAt(int x, int y, boolean longClick) {
        try {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) {
                return null;
            }
            NodeHolder holder = new NodeHolder();
            findActionableNode(root, x, y, longClick, holder, 0);
            return holder.node;
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not find actionable node", e);
            return null;
        }
    }

    private void findActionableNode(AccessibilityNodeInfo node, int x, int y, boolean longClick,
                                    NodeHolder holder, int depth) {
        if (node == null || depth > MAX_DEPTH) {
            return;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.contains(x, y)) {
            boolean actionable = node.isVisibleToUser() && (longClick
                    ? hasAction(node, AccessibilityNodeInfo.ACTION_LONG_CLICK)
                    || node.isClickable()
                    : node.isClickable()
                    || hasAction(node, AccessibilityNodeInfo.ACTION_CLICK));
            if (actionable) {
                long area = (long) bounds.width() * bounds.height();
                if (holder.node == null || area < holder.area) {
                    holder.node = node;
                    holder.area = area;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findActionableNode(child, x, y, longClick, holder, depth + 1);
            }
        }
    }

    private AccessibilityNodeInfo findScrollableNodeAt(int x, int y) {
        try {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) {
                return null;
            }
            NodeHolder holder = new NodeHolder();
            findScrollableNode(root, x, y, holder, 0);
            return holder.node;
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not find scrollable node", e);
            return null;
        }
    }

    private void findScrollableNode(AccessibilityNodeInfo node, int x, int y,
                                    NodeHolder holder, int depth) {
        if (node == null || depth > MAX_DEPTH) {
            return;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (node.isVisibleToUser() && node.isScrollable() && bounds.contains(x, y)) {
            long area = (long) bounds.width() * bounds.height();
            if (holder.node == null || area < holder.area) {
                holder.node = node;
                holder.area = area;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findScrollableNode(child, x, y, holder, depth + 1);
            }
        }
    }

    private void dispatchTapOrLongPress(int x, int y, boolean longClick) {
        Rect window = windowBounds();
        try {
            service.dispatchGesture(longClick
                            ? GestureHelper.longPress(window, x, y)
                            : GestureHelper.tap(window, x, y),
                    null, null);
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not dispatch " + (longClick ? "long press" : "tap"), e);
        }
    }

    private void scheduleRefresh() {
        invalidateCache();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                invalidateCache();
            }
        }, 350);
    }

    private void invalidateCache() {
        lastRefresh = 0;
    }

    private void invalidateCacheSoon() {
        scheduleRefresh();
    }

    private void ensureForeground() {
        long now = SystemClock.uptimeMillis();
        if (foregroundPackage == null || now - lastForegroundCheck > FOREGROUND_CHECK_MS) {
            refreshForegroundFromWindows();
        }
    }

    private void refreshForegroundFromWindows() {
        long now = SystemClock.uptimeMillis();
        lastForegroundCheck = now;

        List<AccessibilityWindowInfo> windows = service.getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION
                        || !window.isActive()) {
                    continue;
                }
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null && root.getPackageName() != null) {
                    updateForeground(root.getPackageName().toString());
                    return;
                }
            }
        }

        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root != null && root.getPackageName() != null) {
            updateForeground(root.getPackageName().toString());
        }
    }

    private void updateForeground(String packageName) {
        if (TextUtils.isEmpty(packageName) || packageName.equals(foregroundPackage)) {
            return;
        }

        String previous = foregroundPackage;
        foregroundPackage = packageName;
        suspended = false;
        toastShown = false;

        if (isNetflixPackage(packageName)) {
            resetNavigationState();
            Log.i(TAG, "Netflix foreground: " + packageName);
        } else {
            resetNavigationState();
            Log.i(TAG, "Foreground changed: " + previous + " -> " + packageName);
        }
    }

    private void deactivate() {
        suspended = false;
        resetNavigationState();
    }

    private void resetNavigationState() {
        directionKeyConsumed = false;
        confirmPassThrough = false;
        selected = null;
        fallbackPoint = null;
        playbackDetected = false;
        items.clear();
        lastRefresh = 0;
        loggedEmptyTree = false;
        cancelLongPress();
        highlight.hide();
    }

    private void updateWindowBounds() {
        List<AccessibilityWindowInfo> windows = service.getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION
                        && window.isActive()) {
                    window.getBoundsInScreen(screenBounds);
                    if (!screenBounds.isEmpty()) {
                        return;
                    }
                }
            }
        }
        DisplayMetrics metrics = service.getResources().getDisplayMetrics();
        screenBounds.set(0, 0, metrics.widthPixels, metrics.heightPixels);
    }

    private Rect windowBounds() {
        if (screenBounds.isEmpty()) {
            updateWindowBounds();
        }
        if (screenBounds.isEmpty()) {
            DisplayMetrics metrics = service.getResources().getDisplayMetrics();
            return new Rect(0, 0, metrics.widthPixels, metrics.heightPixels);
        }
        return new Rect(screenBounds);
    }

    private void cancelLongPress() {
        if (pendingLongPress != null) {
            handler.removeCallbacks(pendingLongPress);
            pendingLongPress = null;
        }
    }

    private void showToastOnce(String message) {
        if (toastShown || preferences.isHideToastOptionEnabled()) {
            return;
        }
        toastShown = true;
        try {
            Toast.makeText(service, message, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not show toast", e);
        }
    }

    private boolean isConfirmKey(int keyCode) {
        return keyCode == preferences.getConfirmKeyValue()
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private static SpatialNavigator.Direction directionFor(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return SpatialNavigator.Direction.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return SpatialNavigator.Direction.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return SpatialNavigator.Direction.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return SpatialNavigator.Direction.RIGHT;
            default:
                return null;
        }
    }

    private static boolean isNetflixPackage(String packageName) {
        return packageName != null
                && packageName.toLowerCase(Locale.US).contains("netflix");
    }

    private static boolean hasAction(AccessibilityNodeInfo node, int actionId) {
        List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
        if (actions == null) {
            return false;
        }
        for (AccessibilityNodeInfo.AccessibilityAction action : actions) {
            if (action.getId() == actionId) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVideoSurface(String className, String viewId, Rect bounds, Rect window) {
        long area = (long) bounds.width() * bounds.height();
        long windowArea = (long) window.width() * window.height();
        if (windowArea <= 0 || area < windowArea * 0.90) {
            // Netflix home rows autoplay previews; only a nearly full-screen
            // surface should make us defer D-pad input to the player.
            return false;
        }
        String lowerClass = className.toLowerCase(Locale.US);
        if (lowerClass.contains("surfaceview")
                || lowerClass.contains("textureview")
                || lowerClass.contains("videoview")) {
            return true;
        }
        String lowerId = viewId.toLowerCase(Locale.US);
        return lowerId.contains("player") || lowerId.contains("video");
    }

    private static boolean isInteractiveClass(String className) {
        String lower = className.toLowerCase(Locale.US);
        return lower.contains("button")
                || lower.contains("imageview")
                || lower.contains("textview")
                || lower.contains("seekbar")
                || lower.contains("edittext")
                || lower.contains("tab")
                || lower.contains("card")
                || lower.contains("item");
    }

    private static String stringOf(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private static float intersectionOverUnion(Rect a, Rect b) {
        Rect intersection = new Rect(a);
        if (!intersection.intersect(b)) {
            return 0f;
        }
        long intersectionArea = (long) intersection.width() * intersection.height();
        long unionArea = (long) a.width() * a.height()
                + (long) b.width() * b.height()
                - intersectionArea;
        return unionArea <= 0 ? 0f : (float) intersectionArea / unionArea;
    }

    private static Rect rectOf(SpatialNavigator.Item item) {
        return new Rect(item.left, item.top, item.right, item.bottom);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float density() {
        return service.getResources().getDisplayMetrics().density;
    }

    private float dp(float value) {
        return value * density();
    }

    private static final class Candidate {
        final Rect bounds;
        final String label;
        final String className;

        Candidate(Rect bounds, String label, String className) {
            this.bounds = bounds;
            this.label = label;
            this.className = className;
        }
    }

    private static final class NodeHolder {
        AccessibilityNodeInfo node;
        long area = Long.MAX_VALUE;
    }
}

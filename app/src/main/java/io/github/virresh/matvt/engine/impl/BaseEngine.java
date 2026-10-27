package io.github.virresh.matvt.engine.impl;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Point;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.github.virresh.matvt.helper.AppPreferences;
import io.github.virresh.matvt.navigation.NetflixNavigator;
import io.github.virresh.matvt.view.FocusHighlightView;
import io.github.virresh.matvt.view.MouseCursorView;
import io.github.virresh.matvt.view.OverlayView;

public abstract class BaseEngine implements MouseEmulationEngine {

    public enum OperatingMode {
        DPAD,
        MOUSE,
        SCROLL
    }

    private static final String LOG_TAG = "MOUSE_EMULATION";
    private static final long CONFIRM_LONG_PRESS_MS = 550;
    private static final long BACK_RECOVERY_MS = 1200;
    private static final long BOSS_LONG_PRESS_MS = 800;

    protected Handler timerHandler;

    protected Runnable previousRunnable;
    CountDownTimer disappearTimer;

    public OperatingMode operatingMode = OperatingMode.DPAD;

    // service which started this engine
    protected AccessibilityService mService;
    protected final AppPreferences appPreferences;

    protected final PointerControl mPointerControl;
    protected final OverlayView mOverlayView;
    protected final FocusHighlightView mFocusHighlight;
    protected NetflixNavigator mNetflixNavigator;

    protected int momentumStack;

    public int bossKey;

    public int confirmKey;

    protected int scrollSpeed;
    protected int cursorSpeed;
    protected boolean hideCursor;

    public boolean isBossKeyDisabled;

    public boolean isBossKeySetToToggle;

    private Point DPAD_Center_Init_Point = new Point();

    private boolean confirmLongPressTriggered;
    private Runnable confirmLongPressRunnable;

    private boolean backPressed;
    private boolean backRecoveryTriggered;
    private Runnable backRecoveryRunnable;

    private Runnable bossLongPressRunnable;

    // tells which keycodes correspond to which pointer movement in scroll and movement mode
    // scroll directions don't match keycode instruction because that's how swiping works
    protected static final Map<Integer, Integer> scrollCodeMap;
    static {
        Map<Integer, Integer> integerMap = new HashMap<>();
        integerMap.put(KeyEvent.KEYCODE_DPAD_UP, PointerControl.DOWN);
        integerMap.put(KeyEvent.KEYCODE_DPAD_DOWN, PointerControl.UP);
        integerMap.put(KeyEvent.KEYCODE_DPAD_LEFT, PointerControl.RIGHT);
        integerMap.put(KeyEvent.KEYCODE_DPAD_RIGHT, PointerControl.LEFT);
        integerMap.put(KeyEvent.KEYCODE_PROG_GREEN, PointerControl.DOWN);
        integerMap.put(KeyEvent.KEYCODE_PROG_RED, PointerControl.UP);
        integerMap.put(KeyEvent.KEYCODE_PROG_BLUE, PointerControl.RIGHT);
        integerMap.put(KeyEvent.KEYCODE_PROG_YELLOW, PointerControl.LEFT);
        scrollCodeMap = Collections.unmodifiableMap(integerMap);
    }

    protected static final Map<Integer, Integer> movementCodeMap;
    static {
        Map<Integer, Integer> integerMap = new HashMap<>();
        integerMap.put(KeyEvent.KEYCODE_DPAD_UP, PointerControl.UP);
        integerMap.put(KeyEvent.KEYCODE_DPAD_DOWN, PointerControl.DOWN);
        integerMap.put(KeyEvent.KEYCODE_DPAD_LEFT, PointerControl.LEFT);
        integerMap.put(KeyEvent.KEYCODE_DPAD_RIGHT, PointerControl.RIGHT);
        movementCodeMap = Collections.unmodifiableMap(integerMap);
    }

    protected static final Set<Integer> actionableKeyMap;
    static {
        Set<Integer> integerSet = new HashSet<>();
        integerSet.add(KeyEvent.KEYCODE_DPAD_UP);
        integerSet.add(KeyEvent.KEYCODE_DPAD_DOWN);
        integerSet.add(KeyEvent.KEYCODE_DPAD_LEFT);
        integerSet.add(KeyEvent.KEYCODE_DPAD_RIGHT);
        integerSet.add(KeyEvent.KEYCODE_PROG_GREEN);
        integerSet.add(KeyEvent.KEYCODE_PROG_YELLOW);
        integerSet.add(KeyEvent.KEYCODE_PROG_BLUE);
        integerSet.add(KeyEvent.KEYCODE_PROG_RED);
        actionableKeyMap = Collections.unmodifiableSet(integerSet);
    }

    protected static final Set<Integer> colorSet;
    static {
        Set<Integer> integerSet = new HashSet<>();
        integerSet.add(KeyEvent.KEYCODE_PROG_GREEN);
        integerSet.add(KeyEvent.KEYCODE_PROG_YELLOW);
        integerSet.add(KeyEvent.KEYCODE_PROG_BLUE);
        integerSet.add(KeyEvent.KEYCODE_PROG_RED);
        colorSet = Collections.unmodifiableSet(integerSet);
    }

    public BaseEngine(AppPreferences appPreferences, OverlayView ov, MouseCursorView mCursorView) {
        this.appPreferences = appPreferences;
        this.mOverlayView = ov;
        momentumStack = 0;
        ov.addFullScreenLayer(mCursorView);
        mFocusHighlight = new FocusHighlightView(ov.getContext());
        ov.addFullScreenLayer(mFocusHighlight);
        mPointerControl = new PointerControl(ov, mCursorView);
        mPointerControl.disappear();
        Log.i(LOG_TAG, "X, Y: " + mPointerControl.getPointerLocation().x + ", " + mPointerControl.getPointerLocation().y);
    }

    abstract protected int scroll(KeyEvent ke);
    abstract protected int click(KeyEvent ke);
    abstract protected int longClick(KeyEvent ke);

    private void attachTimer(final int direction) {
        if (previousRunnable != null) {
            detachPreviousTimer();
        } else {
            momentumStack = 0;
        }
        previousRunnable = new Runnable() {
            @Override
            public void run() {
                if (timerHandler == null) {
                    return;
                }
                mPointerControl.reappear();
                int distance = Math.max(1, cursorSpeed) + momentumStack * 2;
                mPointerControl.move(direction, distance);
                momentumStack += 1;
                timerHandler.postDelayed(this, 16);
            }
        };
        timerHandler.postDelayed(previousRunnable, 0);
    }

    /**
     * Auto Disappear mouse after some duration and reset momentum
     */
    protected void detachPreviousTimer() {
        if (disappearTimer != null) {
            disappearTimer.cancel();
        }
        momentumStack = 0;
        if (previousRunnable != null && timerHandler != null) {
            timerHandler.removeCallbacks(previousRunnable);
            previousRunnable = null;
            disappearTimer = new CountDownTimer(10000, 10000) {
                @Override
                public void onTick(long l) {
                }

                @Override
                public void onFinish() {
                    mPointerControl.disappear();
                }
            };
            disappearTimer.start();
        }
    }

    @Override
    public void init(@NonNull AccessibilityService s) {
        // Called only once, during service initialisation.
        this.mService = s;
        if (timerHandler == null) {
            timerHandler = new Handler(Looper.getMainLooper());
        }
        if (mNetflixNavigator != null) {
            mNetflixNavigator.destroy();
        }
        mNetflixNavigator = new NetflixNavigator(s, appPreferences, mFocusHighlight);
        mNetflixNavigator.updateFromPreferences();
        operatingMode = OperatingMode.DPAD;
        mOverlayView.post(new Runnable() {
            @Override
            public void run() {
                mPointerControl.reset();
            }
        });
        updateFromPreferences();
    }

    @Override
    public void updateFromPreferences() {
        // Can be called multiple times, please avoid all side-effects / static hacks.
        scrollSpeed = appPreferences.getScrollSpeed();
        cursorSpeed = appPreferences.getCursorSpeed();
        hideCursor = appPreferences.isCursorHidden();
        isBossKeyDisabled = appPreferences.isBossKeyDisabled();
        isBossKeySetToToggle = appPreferences.isBossKeySetToToggle();
        bossKey = appPreferences.getBossKeyValue();
        confirmKey = appPreferences.getConfirmKeyValue();
        boolean isBordered = appPreferences.getMouseBordered();

        mPointerControl.setIsBordered(isBordered);
        mPointerControl.setCursorHidden(hideCursor);
        if (operatingMode == OperatingMode.MOUSE && !hideCursor) {
            mPointerControl.reappear();
        }
        mPointerControl.updateCursorViewPreferences();
        if (mNetflixNavigator != null) {
            mNetflixNavigator.updateFromPreferences();
        }

        Log.i(LOG_TAG, "Configuration -- Scroll Speed " + scrollSpeed);
        Log.i(LOG_TAG, "Configuration -- Cursor Speed " + cursorSpeed);
        Log.i(LOG_TAG, "Configuration -- Hide Cursor " + hideCursor);
        Log.i(LOG_TAG, "Configuration -- Boss Key Disabled " + isBossKeyDisabled);
        Log.i(LOG_TAG, "Configuration -- Boss Key Toggleable " + isBossKeySetToToggle);
        Log.i(LOG_TAG, "Configuration -- Is Bordered " + isBordered);
        Log.i(LOG_TAG, "Configuration -- Boss Key value " + bossKey);
        Log.i(LOG_TAG, "Configuration -- Confirm Key value " + confirmKey);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (mNetflixNavigator != null) {
            mNetflixNavigator.onAccessibilityEvent(event);
            if (mNetflixNavigator.isActiveForInput()) {
                mPointerControl.disappear();
            }
        }
    }

    @Override
    public void destroy() {
        if (timerHandler != null) {
            timerHandler.removeCallbacksAndMessages(null);
        }
        cancelConfirmLongPress();
        cancelBackRecovery();
        cancelBossLongPress();
        if (disappearTimer != null) {
            disappearTimer.cancel();
            disappearTimer = null;
        }
        if (mNetflixNavigator != null) {
            mNetflixNavigator.destroy();
            mNetflixNavigator = null;
        }
        mFocusHighlight.hide();
        mOverlayView.destroy();
        this.mService = null;
    }

    protected boolean handleBossKey(KeyEvent keyEvent) {
        if (isBossKeyDisabled) {
            return false;
        }

        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyEvent.getRepeatCount() > 0) {
                return true;
            }
            if (isBossKeySetToToggle) {
                cycleOperatingMode();
            } else {
                bossLongPressRunnable = new Runnable() {
                    @Override
                    public void run() {
                        setOperatingMode(operatingMode == OperatingMode.DPAD
                                ? OperatingMode.MOUSE
                                : OperatingMode.DPAD);
                    }
                };
                timerHandler.postDelayed(bossLongPressRunnable, BOSS_LONG_PRESS_MS);
            }
            return true;
        } else if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
            if (!isBossKeySetToToggle) {
                cancelBossLongPress();
            }
            return true;
        }

        return true;
    }

    @Override
    public boolean perform(KeyEvent keyEvent) {
        if (mService == null || keyEvent == null) {
            return false;
        }

        int keyCode = keyEvent.getKeyCode();

        // Back is the universal escape hatch. In mouse/scroll/smart mode it is
        // consumed and replayed on release; holding it returns to normal D-pad.
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return handleBackKey(keyEvent);
        }

        // toggle mouse mode if going via bossKey
        if (keyCode == bossKey) {
            return handleBossKey(keyEvent);
        }

        // keep full functionality on full size remotes
        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN
                && keyCode == KeyEvent.KEYCODE_INFO) {
            if (keyEvent.getRepeatCount() == 0) {
                setOperatingMode(operatingMode == OperatingMode.DPAD
                        ? OperatingMode.MOUSE
                        : OperatingMode.DPAD);
            }
            return true;
        }

        if (operatingMode == OperatingMode.DPAD) {
            if (mNetflixNavigator != null) {
                // handleKeyEvent also discovers the foreground app, so this
                // works even before the first window-change event arrives.
                boolean consumed = mNetflixNavigator.handleKeyEvent(keyEvent);
                if (consumed) {
                    mPointerControl.disappear();
                    return true;
                }
            }
            // mouse is disabled, don't do anything and let the system consume this event
            return false;
        }

        boolean consumed = false;
        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
            if (scrollCodeMap.containsKey(keyCode)) {
                if (operatingMode == OperatingMode.SCROLL || colorSet.contains(keyCode)) {
                    if (keyEvent.getRepeatCount() == 0 || operatingMode == OperatingMode.SCROLL) {
                        scroll(keyEvent);
                        momentumStack++;
                    }
                    consumed = true;
                } else if (operatingMode == OperatingMode.MOUSE
                        && mPointerControl.isStuckAtSide() != 0
                        && keyCode == mPointerControl.isStuckAtSide()) {
                    if (keyEvent.getRepeatCount() == 0) {
                        click(keyEvent);
                    }
                    consumed = true;
                } else if (movementCodeMap.containsKey(keyCode)) {
                    if (keyEvent.getRepeatCount() == 0) {
                        attachTimer(movementCodeMap.get(keyCode));
                    }
                    consumed = true;
                }
            } else if (keyCode == confirmKey) {
                if (keyEvent.getRepeatCount() == 0) {
                    DPAD_Center_Init_Point = new Point(
                            (int) mPointerControl.getPointerLocation().x,
                            (int) mPointerControl.getPointerLocation().y);
                    confirmLongPressTriggered = false;
                    confirmLongPressRunnable = new Runnable() {
                        @Override
                        public void run() {
                            confirmLongPressTriggered = true;
                            longClick(keyEvent);
                        }
                    };
                    timerHandler.postDelayed(confirmLongPressRunnable, CONFIRM_LONG_PRESS_MS);
                }
                consumed = true;
            }
        } else if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
            // key released, cancel any ongoing effects and clean-up
            if (actionableKeyMap.contains(keyCode)) {
                detachPreviousTimer();
                consumed = true;
            } else if (keyCode == confirmKey) {
                cancelConfirmLongPress();
                detachPreviousTimer();
                if (!confirmLongPressTriggered) {
                    Point currentPoint = new Point(
                            (int) mPointerControl.getPointerLocation().x,
                            (int) mPointerControl.getPointerLocation().y);
                    if (DPAD_Center_Init_Point.equals(currentPoint)) {
                        click(keyEvent);
                    } else {
                        Log.i(LOG_TAG, "Drag is not supported; use mouse mode for drag gestures.");
                    }
                }
                confirmLongPressTriggered = false;
                consumed = true;
            }
        }
        return consumed;
    }

    private boolean handleBackKey(KeyEvent keyEvent) {
        boolean smartActive = mNetflixNavigator != null && mNetflixNavigator.isActiveForInput();
        if (operatingMode == OperatingMode.DPAD && !smartActive) {
            return false;
        }

        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyEvent.getRepeatCount() == 0) {
                backPressed = true;
                backRecoveryTriggered = false;
                backRecoveryRunnable = new Runnable() {
                    @Override
                    public void run() {
                        backRecoveryTriggered = true;
                        setOperatingMode(OperatingMode.DPAD);
                        if (mNetflixNavigator != null) {
                            mNetflixNavigator.suspend();
                        }
                        showToast("Normal remote mode");
                    }
                };
                timerHandler.postDelayed(backRecoveryRunnable, BACK_RECOVERY_MS);
            }
            return true;
        } else if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
            cancelBackRecovery();
            boolean consumed = backPressed;
            boolean recovered = backRecoveryTriggered;
            backPressed = false;
            backRecoveryTriggered = false;
            if (consumed && !recovered) {
                mService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            }
            return consumed;
        }
        return true;
    }

    private void cycleOperatingMode() {
        switch (operatingMode) {
            case DPAD:
                setOperatingMode(OperatingMode.MOUSE);
                break;
            case MOUSE:
                setOperatingMode(OperatingMode.SCROLL);
                break;
            case SCROLL:
                setOperatingMode(OperatingMode.DPAD);
                break;
        }
    }

    public void setOperatingMode(OperatingMode newMode) {
        if (operatingMode == newMode) {
            return;
        }

        operatingMode = newMode;
        switch (newMode) {
            case DPAD:
                mPointerControl.disappear();
                if (mNetflixNavigator != null) {
                    mNetflixNavigator.resume();
                    if (!mNetflixNavigator.isActiveForInput()) {
                        mFocusHighlight.hide();
                    }
                }
                showToast("D-Pad Mode");
                break;
            case MOUSE:
                if (mNetflixNavigator != null) {
                    mNetflixNavigator.suspend();
                }
                mFocusHighlight.hide();
                mPointerControl.reset();
                mPointerControl.reappear();
                showToast("Mouse Mode");
                break;
            case SCROLL:
                if (mNetflixNavigator != null) {
                    mNetflixNavigator.suspend();
                }
                mFocusHighlight.hide();
                showToast("Scroll Mode");
                break;
        }
    }

    private void cancelConfirmLongPress() {
        if (confirmLongPressRunnable != null && timerHandler != null) {
            timerHandler.removeCallbacks(confirmLongPressRunnable);
            confirmLongPressRunnable = null;
        }
    }

    private void cancelBackRecovery() {
        if (backRecoveryRunnable != null && timerHandler != null) {
            timerHandler.removeCallbacks(backRecoveryRunnable);
            backRecoveryRunnable = null;
        }
    }

    private void cancelBossLongPress() {
        if (bossLongPressRunnable != null && timerHandler != null) {
            timerHandler.removeCallbacks(bossLongPressRunnable);
            bossLongPressRunnable = null;
        }
    }

    private void showToast(String message) {
        if (mService == null) {
            Log.i(LOG_TAG, message);
            return;
        }
        if (!appPreferences.isHideToastOptionEnabled()) {
            try {
                Toast.makeText(mService, message, Toast.LENGTH_SHORT).show();
            } catch (RuntimeException e) {
                Log.w(LOG_TAG, "Could not show toast", e);
            }
        } else {
            Log.i(LOG_TAG, message);
        }
    }
}

package io.github.virresh.matvt.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import io.github.virresh.matvt.BuildConfig;
import io.github.virresh.matvt.engine.impl.GestureDispatchMouseEngine;
import io.github.virresh.matvt.engine.impl.HybridMouseEngine;
import io.github.virresh.matvt.engine.impl.MouseEmulationEngine;
import io.github.virresh.matvt.engine.impl.ShellInputDispatchMouseEngine;
import io.github.virresh.matvt.helper.AccessibilityUtils;
import io.github.virresh.matvt.helper.AppPreferences;
import io.github.virresh.matvt.helper.KeyDetection;
import io.github.virresh.matvt.helper.KeyEventHandler;
import io.github.virresh.matvt.helper.UiTreeDumper;
import io.github.virresh.matvt.view.MouseCursorView;
import io.github.virresh.matvt.view.OverlayView;
import android.widget.Toast;

public class MouseEventService extends AccessibilityService {

    private MouseEmulationEngine mEngine;
    private static final String TAG_NAME = "MATVT_SERVICE";

    private static MouseEventService selfReference;
    private AppPreferences appPreferences;
    private KeyEventHandler keyEventHandler;
    private LocalBroadcastManager broadcastManager;


    public static MouseEventService getInstance() {
        // This is heavily tied to IBinder lifecycle, but cleanup and management is manual because
        // of this being an accessibility service.
        // Remember to update the reference when service is unbound.
        if (selfReference == null) {
            Log.i(TAG_NAME, "Mouse service is not connected. Please check anything interfering with background accessibility services.");
        }
        return selfReference;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        if (mEngine != null) {
            mEngine.onAccessibilityEvent(accessibilityEvent);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        super.onKeyEvent(event);
        if (keyEventHandler != null) {
            keyEventHandler.handleKeyEvent(event);
        }

        if (broadcastManager != null) {
            Intent intent = new Intent(KeyDetection.ACTION_KEY_EVENT);
            intent.putExtra(KeyDetection.EXTRA_KEY_EVENT, event);
            intent.putExtra(KeyDetection.EXTRA_KEY_ACTION, event.getAction());
            broadcastManager.sendBroadcast(intent);
        }

        Log.i(TAG_NAME, "MATVT Received Key => " + event.getKeyCode() + ", Action => " + event.getAction() + ", Repetition value => " + event.getRepeatCount() + ", Scan code => " + event.getScanCode());
        if (AccessibilityUtils.isAnotherServiceInstalled(this) &&
                event.getKeyCode() == KeyEvent.KEYCODE_HOME) return true;
        if (KeyDetection.isActive()) return false;
        if (mEngine == null) return false;
        return mEngine.perform(event);
    }

    @Override
    public void onInterrupt() {}

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG_NAME, "Starting service initialization sequence. App version " + BuildConfig.VERSION_NAME);
        selfReference = this;
        appPreferences = new AppPreferences(this); // Initialize AppPreferences here
        keyEventHandler = new KeyEventHandler(this);
        broadcastManager = LocalBroadcastManager.getInstance(this);

        if (mEngine != null) {
            mEngine.destroy();
            mEngine = null;
        }
        init();
    }

    private void init() {
        OverlayView mOverlayView = new OverlayView(this);
        MouseCursorView mMouseCursorView = new MouseCursorView(this, appPreferences);
        AccessibilityServiceInfo asi = this.getServiceInfo();
        if (asi != null) {
            asi.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            this.setServiceInfo(asi);
        }

        String engineType = appPreferences.getEngineType();
        Log.i(TAG_NAME, "Selected engine type: " + engineType);

        switch (engineType) {
            case "shell":
                mEngine = new ShellInputDispatchMouseEngine(appPreferences, mOverlayView, mMouseCursorView);
                break;
            case "hybrid":
                mEngine = new HybridMouseEngine(appPreferences, mOverlayView, mMouseCursorView);
                break;
            case "gesture":
            default:
                mEngine = new GestureDispatchMouseEngine(this, mOverlayView, appPreferences, mMouseCursorView);
                break;
        }
        mEngine.init(this);
    }

    public void updatePreferences() {
        if(mEngine != null) {
            mEngine.updateFromPreferences();
        }
    }


    @Override
    public boolean onUnbind(Intent intent) {
        if (mEngine != null) {
            mEngine.destroy();
            mEngine = null;
        }
        selfReference = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (mEngine != null) {
            mEngine.destroy();
            mEngine = null;
        }
        selfReference = null;
        super.onDestroy();
    }

    public void dumpUiTree() {
        String path = UiTreeDumper.dump(this);
        String message = path == null
                ? "Could not dump UI tree"
                : "UI tree saved to " + path;
        Log.i(TAG_NAME, message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
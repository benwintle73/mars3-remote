package io.github.virresh.matvt.helper;

import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import io.github.virresh.matvt.services.MouseEventService;

/**
 * Watches key events while the key-detection screen is open and forwards a
 * long hold to that activity. The dialog must live in an activity context;
 * showing an AlertDialog from an accessibility service is unreliable on
 * Android 8+.
 */
public class KeyEventHandler {
    private final Context context;
    private int lastKeyCode = -1;
    private boolean holdBroadcast;

    public KeyEventHandler(MouseEventService service) {
        this.context = service;
    }

    public void handleKeyEvent(KeyEvent event) {
        if (!KeyDetection.isActive()) {
            lastKeyCode = -1;
            holdBroadcast = false;
            return;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            boolean heldLongEnough = event.getEventTime() - event.getDownTime() > 1000;
            if (heldLongEnough
                    && (!holdBroadcast || lastKeyCode != event.getKeyCode())) {
                lastKeyCode = event.getKeyCode();
                holdBroadcast = true;
                Intent intent = new Intent(KeyDetection.ACTION_KEY_HOLD);
                intent.putExtra(KeyDetection.EXTRA_KEY_CODE, event.getKeyCode());
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            lastKeyCode = -1;
            holdBroadcast = false;
        }
    }
}

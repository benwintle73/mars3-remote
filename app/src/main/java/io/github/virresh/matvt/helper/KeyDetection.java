package io.github.virresh.matvt.helper;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import io.github.virresh.matvt.R;
import io.github.virresh.matvt.services.MouseEventService;

public class KeyDetection extends AppCompatActivity {

    public static final String ACTION_KEY_EVENT = "io.github.virresh.matvt.ACTION_KEY_EVENT";
    public static final String ACTION_KEY_HOLD = "io.github.virresh.matvt.ACTION_KEY_HOLD";
    public static final String EXTRA_KEY_EVENT = "extra_key_event";
    public static final String EXTRA_KEY_ACTION = "extra_key_action";
    public static final String EXTRA_KEY_CODE = "extra_key_code";

    private static volatile boolean active;

    public static boolean isActive() {
        return active;
    }

    private TextView textView;
    private AppPreferences appPreferences;
    private AlertDialog holdDialog;

    private final BroadcastReceiver keyEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_KEY_EVENT.equals(intent.getAction())) {
                int action = intent.getIntExtra(EXTRA_KEY_ACTION, -1);
                KeyEvent event = intent.getParcelableExtra(EXTRA_KEY_EVENT);
                if (event == null) {
                    return;
                }
                if (action == KeyEvent.ACTION_DOWN) {
                    textView.setText(String.valueOf(event.getKeyCode()));
                } else if (action == KeyEvent.ACTION_UP) {
                    textView.setText(" ");
                }
            }
        }
    };

    private final BroadcastReceiver keyHoldReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_KEY_HOLD.equals(intent.getAction())) {
                showChangeBossKeyDialog(intent.getIntExtra(EXTRA_KEY_CODE, -1));
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_detection);
        textView = findViewById(R.id.pressed_key);
        appPreferences = new AppPreferences(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        active = true;
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.registerReceiver(keyEventReceiver, new IntentFilter(ACTION_KEY_EVENT));
        manager.registerReceiver(keyHoldReceiver, new IntentFilter(ACTION_KEY_HOLD));
    }

    @Override
    protected void onPause() {
        active = false;
        LocalBroadcastManager.getInstance(this).unregisterReceiver(keyEventReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(keyHoldReceiver);
        dismissHoldDialog();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        dismissHoldDialog();
        super.onDestroy();
    }

    private void showChangeBossKeyDialog(final int keyCode) {
        if (keyCode < 0 || (holdDialog != null && holdDialog.isShowing())) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_changes_title);
        builder.setMessage(getString(R.string.confirm_set_boss_key_message, keyCode));
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                appPreferences.setBossKeyDisabled(false);
                appPreferences.setOverrideStatus(true);
                appPreferences.setBossKeyValue(keyCode);
                MouseEventService service = MouseEventService.getInstance();
                if (service != null) {
                    service.updatePreferences();
                }
                Toast.makeText(KeyDetection.this,
                        getString(R.string.new_boss_key_toast, keyCode),
                        Toast.LENGTH_SHORT).show();
                dialogInterface.dismiss();
            }
        });
        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });
        holdDialog = builder.create();
        holdDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                holdDialog = null;
            }
        });
        holdDialog.show();
    }

    private void dismissHoldDialog() {
        if (holdDialog != null) {
            holdDialog.dismiss();
            holdDialog = null;
        }
    }
}

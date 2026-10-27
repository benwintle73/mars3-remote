package io.github.virresh.matvt.gui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import io.github.virresh.matvt.BuildConfig;
import io.github.virresh.matvt.R;
import io.github.virresh.matvt.helper.AccessibilityUtils;
import io.github.virresh.matvt.helper.AppPreferences;
import io.github.virresh.matvt.helper.KeyDetection;
import io.github.virresh.matvt.services.MouseEventService;

public class GuiActivity extends AppCompatActivity {
    private static final String TAG_NAME = "MATVT_SERVICE";

    CountDownTimer repopulate;
    CheckBox cb_mouse_bordered, cb_disable_bossKey, cb_behaviour_bossKey, cb_hide_toasts;
    CheckBox cb_hide_cursor, cb_netflix_mode, cb_netflix_fallback;
    TextView gui_acc_perm, gui_acc_serv, gui_overlay_perm, gui_overlay_serv, gui_about;

    EditText et_boss_override, et_confirm_override;
    Button bt_saveBossKeyValue, bt_saveConfirmKeyValue, bt_dump_ui;

    Spinner sp_mouse_icon, sp_engine_type;
    SeekBar dsbar_mouse_size;
    SeekBar dsbar_scroll_speed;
    SeekBar dsbar_cursor_speed;

    MouseEventService mService;
    private AppPreferences appPreferences;

    public static final int ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 701;
    public static final int ACTION_ACCESSIBILITY_PERMISSION_REQUEST_CODE = 702;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appPreferences = new AppPreferences(this);
        mService = MouseEventService.getInstance();
        setContentView(R.layout.activity_main_gui);
        gui_acc_perm = findViewById(R.id.gui_acc_perm);
        gui_acc_serv = findViewById(R.id.gui_acc_serv);
        gui_overlay_perm = findViewById(R.id.gui_overlay_perm);
        gui_overlay_serv = findViewById(R.id.gui_overlay_serv);
        gui_about = findViewById(R.id.gui_about);

        bt_saveBossKeyValue = findViewById(R.id.bt_saveBossKey);
        et_boss_override = findViewById(R.id.et_boss_override);

        bt_saveConfirmKeyValue = findViewById(R.id.bt_saveConfirmKey);
        et_confirm_override = findViewById(R.id.et_confirm_override);

        cb_mouse_bordered = findViewById(R.id.cb_border_window);
        cb_hide_toasts = findViewById(R.id.cb_hide_toasts);
        cb_disable_bossKey = findViewById(R.id.cb_disable_bossKey);
        cb_behaviour_bossKey = findViewById(R.id.cb_behaviour_bossKey);
        cb_hide_cursor = findViewById(R.id.cb_hide_cursor);
        cb_netflix_mode = findViewById(R.id.cb_netflix_mode);
        cb_netflix_fallback = findViewById(R.id.cb_netflix_fallback);

        sp_mouse_icon = findViewById(R.id.sp_mouse_icon);
        sp_engine_type = findViewById(R.id.sp_engine_type);
        dsbar_mouse_size = findViewById(R.id.dsbar_mouse_size);
        dsbar_scroll_speed = findViewById(R.id.dsbar_mouse_scspeed);
        dsbar_cursor_speed = findViewById(R.id.dsbar_cursor_speed);
        bt_dump_ui = findViewById(R.id.bt_dump_ui);

        // don't like to advertise in the product, but need to mention here
        // need to increase visibility of the open source version
        gui_about.setText("MATVT v" + BuildConfig.VERSION_NAME + "\nThis is an open source project. It's available for free and will always be. If you find issues / would like to help in improving this project, please contribute at \nhttps://github.com/virresh/matvt");

        // render icon style dropdown
        IconStyleSpinnerAdapter iconStyleSpinnerAdapter = new IconStyleSpinnerAdapter(this, R.layout.spinner_icon_text_gui, R.id.textView, IconStyleSpinnerAdapter.getResourceList());
        sp_mouse_icon.setAdapter(iconStyleSpinnerAdapter);

        // render engine type dropdown
        ArrayAdapter<CharSequence> engineTypeAdapter = ArrayAdapter.createFromResource(this,
                R.array.engine_types, android.R.layout.simple_spinner_item);
        engineTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp_engine_type.setAdapter(engineTypeAdapter);

        checkValues(iconStyleSpinnerAdapter, engineTypeAdapter);

        bt_saveBossKeyValue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String dat = et_boss_override.getText().toString();
                dat = dat.replaceAll("[^0-9]", "");
                int keyValue;
                try {
                    keyValue = dat.isEmpty()
                            ? KeyEvent.KEYCODE_VOLUME_MUTE
                            : Integer.parseInt(dat);
                } catch (NumberFormatException e) {
                    keyValue = KeyEvent.KEYCODE_VOLUME_MUTE;
                }
                appPreferences.setBossKeyDisabled(false);
                appPreferences.setOverrideStatus(true);
                appPreferences.setBossKeyValue(keyValue);
                cb_disable_bossKey.setChecked(false);
                Toast.makeText(GuiActivity.this, "New Boss key is : "+keyValue, Toast.LENGTH_SHORT).show();
                updateFromPreferences();
            }
        });

        bt_saveConfirmKeyValue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String dat = et_confirm_override.getText().toString();
                dat = dat.replaceAll("[^0-9]", "");
                int keyValue;
                try {
                    keyValue = dat.isEmpty()
                            ? KeyEvent.KEYCODE_DPAD_CENTER
                            : Integer.parseInt(dat);
                } catch (NumberFormatException e) {
                    keyValue = KeyEvent.KEYCODE_DPAD_CENTER;
                }
                appPreferences.setConfirmKeyValue(keyValue);
                Toast.makeText(GuiActivity.this, "New confirm key is : "+keyValue, Toast.LENGTH_SHORT).show();
                updateFromPreferences();
            }
        });

        sp_engine_type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                String engineType = (String) adapterView.getItemAtPosition(pos);
                appPreferences.setEngineType(engineType);
                updateFromPreferences();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });


        sp_mouse_icon.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            // the listener is set after setting initial value to avoid echo if any
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                String style = iconStyleSpinnerAdapter.getItem(pos);
                appPreferences.setMouseIconPref(iconStyleSpinnerAdapter.getItem(pos));
                updateFromPreferences();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });

        dsbar_mouse_size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // do not do anything if the progress change was done programmatically
                    appPreferences.setMouseSizePref(progress);
                    updateFromPreferences();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        dsbar_scroll_speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // do not do anything if the progress change was done programmatically
                    appPreferences.setScrollSpeed(progress);
                    updateFromPreferences();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        dsbar_cursor_speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int speed = Math.max(1, progress);
                    if (speed != progress) {
                        seekBar.setProgress(speed);
                    }
                    appPreferences.setCursorSpeed(speed);
                    updateFromPreferences();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        cb_mouse_bordered.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                appPreferences.setMouseBordered(b);
                updateFromPreferences();
            }
        });

        cb_hide_toasts.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean value) {
                appPreferences.setHideToastsOptionEnabled(value);
                updateFromPreferences();
            }
        });

        cb_disable_bossKey.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean value) {
                appPreferences.setBossKeyDisabled(value);
                updateFromPreferences();
            }
        });

        cb_behaviour_bossKey.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean value) {
                appPreferences.setBossKeyBehaviour(value);
                updateFromPreferences();
            }
        });

        cb_hide_cursor.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean value) {
                appPreferences.setCursorHidden(value);
                updateFromPreferences();
            }
        });

        cb_netflix_mode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean value) {
                appPreferences.setNetflixModeEnabled(value);
                updateFromPreferences();
            }
        });

        cb_netflix_fallback.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean value) {
                appPreferences.setNetflixFallbackEnabled(value);
                updateFromPreferences();
            }
        });

        bt_dump_ui.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService = MouseEventService.getInstance();
                if (mService == null) {
                    Toast.makeText(GuiActivity.this,
                            "Accessibility service is not running", Toast.LENGTH_SHORT).show();
                } else {
                    mService.dumpUiTree();
                }
            }
        });

        populateText();
        findViewById(R.id.gui_setup_perm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                askPermissions();
            }
        });
    }

    private void checkValues(IconStyleSpinnerAdapter adapter, ArrayAdapter<CharSequence> engineTypeAdapter) {
        String val = String.valueOf(appPreferences.getBossKeyValue());
        et_boss_override.setText(val);
        String val2 = String.valueOf(appPreferences.getConfirmKeyValue());
        et_confirm_override.setText(val2);

        String iconStyle = appPreferences.getMouseIconPref();
        sp_mouse_icon.setSelection(adapter.getPosition(iconStyle));

        String engineType = appPreferences.getEngineType();
        sp_engine_type.setSelection(engineTypeAdapter.getPosition(engineType));

        int mouseSize = appPreferences.getMouseSizePref();
        dsbar_mouse_size.setProgress(Math.max(Math.min(mouseSize, dsbar_mouse_size.getMax()), 0));

        int scrollSpeed = appPreferences.getScrollSpeed();
        dsbar_scroll_speed.setProgress(Math.max(Math.min(scrollSpeed, dsbar_scroll_speed.getMax()), 0));

        int cursorSpeed = appPreferences.getCursorSpeed();
        dsbar_cursor_speed.setProgress(Math.max(Math.min(cursorSpeed, dsbar_cursor_speed.getMax()), 1));

        boolean bordered = appPreferences.getMouseBordered();
        cb_mouse_bordered.setChecked(bordered);

        boolean toastVisibility = appPreferences.isHideToastOptionEnabled();
        cb_hide_toasts.setChecked(toastVisibility);

        boolean bossKeyStatus = appPreferences.isBossKeyDisabled();
        cb_disable_bossKey.setChecked(bossKeyStatus);

        boolean bossKeyBehaviour = appPreferences.isBossKeySetToToggle();
        cb_behaviour_bossKey.setChecked(bossKeyBehaviour);

        cb_hide_cursor.setChecked(appPreferences.isCursorHidden());
        cb_netflix_mode.setChecked(appPreferences.isNetflixModeEnabled());
        cb_netflix_fallback.setChecked(appPreferences.isNetflixFallbackEnabled());
    }

    private void askPermissions() {
        // TYPE_ACCESSIBILITY_OVERLAY does not need SYSTEM_ALERT_WINDOW on
        // Android 8+, so only the accessibility service is mandatory.
        if (AccessibilityUtils.isAccessibilityDisabled(this)) {
            checkAccPerms();
        }
    }

    private void checkAccPerms() {
        if (AccessibilityUtils.isAccessibilityDisabled(this))
            try {
//                startActivity(new Intent(getPackageManager().getLeanbackLaunchIntentForPackage("com.wolf.apm")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))); HELPER APP
                startActivityForResult(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), 
                        ACTION_ACCESSIBILITY_PERMISSION_REQUEST_CODE);
            } catch (Exception exception) {
                Toast.makeText(this, "Acessibility Handler not Found", Toast.LENGTH_SHORT).show();
            }
    }

    public void populateText() {
        boolean accDisabled = AccessibilityUtils.isAccessibilityDisabled(this);

        if (accDisabled) {
            gui_acc_perm.setText(R.string.perm_acc_denied);
            gui_acc_serv.setText(R.string.serv_acc_denied);
            gui_overlay_serv.setText(R.string.serv_overlay_denied);
            findViewById(R.id.gui_setup_perm).setVisibility(View.VISIBLE);
        } else {
            gui_acc_perm.setText(R.string.perm_acc_allowed);
            gui_acc_serv.setText(R.string.serv_acc_allowed);
            gui_overlay_serv.setText(R.string.serv_overlay_allowed);
            findViewById(R.id.gui_setup_perm).setVisibility(View.GONE);
        }

        if (AccessibilityUtils.isOverlayDisabled(this)) {
            gui_overlay_perm.setText(R.string.overlay_optional);
        } else {
            gui_overlay_perm.setText(R.string.perm_overlay_allowed);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE)
            if (AccessibilityUtils.isOverlayDisabled(this)) {
                Toast.makeText(this, "Overlay Permissions Denied", Toast.LENGTH_SHORT).show();
            } else checkAccPerms();
        if (requestCode == ACTION_ACCESSIBILITY_PERMISSION_REQUEST_CODE)
            if (AccessibilityUtils.isAccessibilityDisabled(this)) {
                Toast.makeText(this, "Accessibility Services not running", Toast.LENGTH_SHORT).show();
            }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mService = MouseEventService.getInstance();
        checkServiceStatus();

        if (et_boss_override != null)
            et_boss_override.setText(appPreferences.getBossKeyValue()+"");
        if (et_confirm_override != null)
            et_confirm_override.setText(appPreferences.getConfirmKeyValue()+"");
        populateText();
    }

    @Override
    protected void onPause() {
        if (repopulate != null) {
            repopulate.cancel();
            repopulate = null;
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (repopulate != null) {
            repopulate.cancel();
            repopulate = null;
        }
        super.onDestroy();
    }

    private void checkServiceStatus() {
        if (repopulate != null) {
            repopulate.cancel();
        }
        //checking for changed every 2 sec
        repopulate = new CountDownTimer(2000, 2000) {
            @Override
            public void onTick(long l) { }
            @Override
            public void onFinish() {
                populateText();
                if (repopulate != null) {
                    repopulate.start(); //restarting the timer
                }
            }
        };
        repopulate.start();
    }

    private void updateFromPreferences() {
        // Initiate update sequence if feasible.
        if (mService == null) {
            mService = MouseEventService.getInstance();
        }
        if (mService == null) {
            Log.i(TAG_NAME, "Accessibility service is not connected. Changes will be applied next time it's started.");
        }
        else{
            mService.updatePreferences();
        }
    }

    public void callDetect(View view) {
        startActivity(new Intent(this, KeyDetection.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}

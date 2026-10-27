package io.github.virresh.matvt.engine.impl;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PointF;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.tananaev.adblib.AdbConnection;
import com.tananaev.adblib.AdbCrypto;
import com.tananaev.adblib.AdbStream;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;

import io.github.virresh.matvt.helper.AdbKeyManager;
import io.github.virresh.matvt.helper.AppPreferences;
import io.github.virresh.matvt.view.MouseCursorView;
import io.github.virresh.matvt.view.OverlayView;

public class ShellInputDispatchMouseEngine extends BaseEngine {

    private Handler adbHandler;
    private Handler mainHandler;
    private HandlerThread adbThread;

    private AdbCrypto adbCrypto;

    private static final String TAG_NAME = "MATVT_ADB_ENGINE";

    public ShellInputDispatchMouseEngine (AppPreferences appPreferences, OverlayView ov, MouseCursorView mCursorView) {
        super(appPreferences, ov, mCursorView);
    }

    private void sendShellInput(String command) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && adbHandler != null) {
            Runnable runInput = new Runnable() {
                @Override
                public void run() {
                    AdbConnection adbConnection = null;
                    AdbStream stream = null;
                    try {
                        Socket socket = new Socket("localhost", 5555);
                        adbConnection = AdbConnection.create(socket, adbCrypto);
                        adbConnection.connect();
                        stream = adbConnection.open("shell:input " + command);
                    } catch (IOException e) {
                        Log.e(TAG_NAME, "Could not create socket for ADB, error: ", e);
                    } catch (InterruptedException e) {
                        Log.e(TAG_NAME, "ADB Connection Interrupted!", e);
                    } catch (Exception e) {
                        Log.e(TAG_NAME, "Unknown error ---> ", e);
                    } finally {
                        try {
                            if (stream != null) {
                                BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(new ByteArrayInputStream(stream.read()))));
                                stream.close();
                                String output = reader.lines().collect(Collectors.joining("\n"));
                                Log.i(TAG_NAME, "ADB Output --> " + output);
                                notifyUser(output);
                            }
                            if (adbConnection != null) {
                                adbConnection.close();
                            }
                            Log.i(TAG_NAME, "Send command " + command + " successfully");
                        } catch (IOException | InterruptedException e) {
                            Log.e(TAG_NAME, "Connection Close failed! ---> ", e);
                        }
                    }
                }
            };
            adbHandler.post(runInput);
        }
        else {
            Log.e(TAG_NAME, "Need Greater than Android O (api version 26) to use ADB for input");
        }
    }

    public void notifyUser(String text) {
        if (mainHandler == null) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mService != null) {
                    Toast.makeText(mService.getApplicationContext(), text, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void shellSwipe(Integer x1, Integer y1, Integer x2, Integer y2, Integer duration) {
        sendShellInput("swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + duration);
    }

    public void shellTap(Integer x, Integer y) {
        sendShellInput("tap " + x.toString() + " " + y.toString());
    }

    @Override
    public void init(@NonNull AccessibilityService s) {
        super.init(s);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                adbCrypto = AdbKeyManager.getAdbCryptoKey(s.getApplicationContext());
                adbThread = new HandlerThread("LocalAdbThread");
                adbThread.start();
                adbHandler = new Handler(adbThread.getLooper());
                mainHandler = new Handler();
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG_NAME, "ADB algorithm not available. Regenerate ADB keys.", e);
            }
        }
        else {
            Log.e(TAG_NAME, "Need Greater than Android O (api version 26) to use ADB for input");
        }
    }

    @Override
    protected int scroll(KeyEvent ke) {
        if (mService != null && scrollCodeMap.containsKey(ke.getKeyCode())) {
            PointF pointer = mPointerControl.getPointerLocation();
            int direction = scrollCodeMap.get(ke.getKeyCode());
            int momentum = momentumStack; // from BaseEngine
            final int DURATION = scrollSpeed + 20; // scrollSpeed from BaseEngine

            PointF lineDirection = new PointF(
                    pointer.x + (momentum + 75) * PointerControl.dirX[direction],
                    pointer.y + (momentum + 75) * PointerControl.dirY[direction]);

            int width = screenWidth();
            int height = screenHeight();
            int endX = Math.max(0, Math.min(width - 1, (int) lineDirection.x));
            int endY = Math.max(0, Math.min(height - 1, (int) lineDirection.y));
            shellSwipe((int) pointer.x, (int) pointer.y, endX, endY, DURATION);
        }
        return 0;
    }

    @Override
    protected int click(KeyEvent ke) {
        if (mService != null) {
            PointF pointer = mPointerControl.getPointerLocation();
            shellTap((int) pointer.x, (int) pointer.y);
        }
        return 0;
    }

    @Override
    protected int longClick(KeyEvent ke) {
        if (mService != null) {
            PointF pointer = mPointerControl.getPointerLocation();
            shellSwipe((int) pointer.x, (int) pointer.y,
                    (int) pointer.x, (int) pointer.y, 650);
        }
        return 0;
    }

    private int screenWidth() {
        int width = mOverlayView.getWidth();
        if (width <= 1) {
            width = mOverlayView.getResources().getDisplayMetrics().widthPixels;
        }
        return Math.max(2, width);
    }

    private int screenHeight() {
        int height = mOverlayView.getHeight();
        if (height <= 1) {
            height = mOverlayView.getResources().getDisplayMetrics().heightPixels;
        }
        return Math.max(2, height);
    }

    @Override
    public void destroy() {
        super.destroy();
        if (adbThread != null) {
            adbThread.quitSafely();
        }
    }
}

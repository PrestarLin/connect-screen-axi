package com.gitee.connect_screen.shizuku;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.IDisplayManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import android.os.RemoteException;
import android.view.Display;

import androidx.annotation.Keep;

import com.gitee.connect_screen.State;

import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

public class UserService extends IUserService.Stub  {
    private Context context;
    private volatile boolean listenVolumeKey = false;
    private Process listenVolumeKeyProcess;
    private Thread volumeKeyThread;
    private volatile boolean keepScreenOff = false;
    private Thread screenOffLoopThread;
    private static final long SCREEN_OFF_CHECK_INTERVAL = 1000; // 保持熄屏的检查间隔（毫秒）
    private int savedStayOnWhilePluggedIn = -1;

    public UserService() {
        Log.i("UserService", "constructor");
    }

    @Keep
    public UserService(Context context) {
        this.context = context;
        Log.i("UserService", "constructor with Context: context=" + context.toString());
    }
    
    /**
     * Reserved destroy method
     */
    @Override
    public void destroy() {
        Log.i("UserService", "destroy");
        System.exit(0);
    }

    @Override
    public void exit() {
        destroy();
    }

    @Override
    public String fetchLogs() throws RemoteException  {
        try {
            Process process = Runtime.getRuntime().exec("logcat -d -f /sdcard/Download/安卓屏连.log");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            reader.close();
            process.waitFor();

            return output.toString();
        } catch (Exception e) {
            Log.e("UserService", "logcat -d failed", e);
            throw new RemoteException("Failed to execute logcat -d: " + e.getMessage());
        }
    }

    @Override
    public String executeCommand(String command) throws RemoteException {
        try {
            Process process = Runtime.getRuntime().exec("dumpsys input");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            reader.close();
            process.waitFor();
            
            return output.toString();
        } catch (Exception e) {
            Log.e("UserService", "execute command failed: " + command, e);
            throw new RemoteException("Failed to execute command: " + command + " " + e.getMessage());
        }
    }

    public void setScreenPower(int powerMode) {
        Log.i("UserService", "try to setScreenPower: " + powerMode);
        boolean succeeded = false;
        // 尝试 API 35+ 的 requestDisplayPower
        if (Build.VERSION.SDK_INT >= 35) {
            IDisplayManager displayManager = IDisplayManager.Stub.asInterface(SystemServiceHelper.getSystemService(Context.DISPLAY_SERVICE));
            if (powerMode == SurfaceControl.POWER_MODE_OFF) {
                try {
                    boolean result = displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, false);
                    Log.i("UserService", "requestDisplayPower by bool, result=" + result);
                    succeeded = result;
                } catch (Throwable e) {
                    Log.e("UserService", "requestDisplayPower(bool) failed", e);
                }
                if (!succeeded) {
                    try {
                        boolean result2 = displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, SurfaceControl.POWER_MODE_OFF);
                        Log.i("UserService", "requestDisplayPower by int, result=" + result2);
                        succeeded = result2;
                    } catch (Throwable e2) {
                        Log.e("UserService", "requestDisplayPower(int) also failed", e2);
                    }
                }
            } else {
                try {
                    boolean result = displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, true);
                    Log.i("UserService", "requestDisplayPower by bool, result=" + result);
                    succeeded = result;
                } catch (Throwable e) {
                    Log.e("UserService", "requestDisplayPower(bool) failed", e);
                }
                if (!succeeded) {
                    try {
                        boolean result2 = displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, SurfaceControl.POWER_MODE_NORMAL);
                        Log.i("UserService", "requestDisplayPower by int, result=" + result2);
                        succeeded = result2;
                    } catch (Throwable e2) {
                        Log.e("UserService", "requestDisplayPower(int) also failed", e2);
                    }
                }
            }
        }
        // 回退到 SurfaceControl.setDisplayPowerMode (所有 API 级别)
        if (!succeeded) {
            IBinder d = SurfaceControl.getBuiltInDisplay();
            if (d == null) {
                Log.i("UserService", "Could not get built-in display");
            } else {
                succeeded = SurfaceControl.setDisplayPowerMode(d, powerMode);
                Log.i("UserService", "setDisplayPowerMode fallback success=" + succeeded);
            }
        }
        if (!succeeded) {
            Log.e("UserService", "All methods to set screen power failed");
        }
    }

    public void startListenVolumeKey() throws RemoteException {
        if (listenVolumeKey && keepScreenOff && screenOffLoopThread != null && screenOffLoopThread.isAlive()) {
            Log.i("UserService", "startListenVolumeKey: already listening and loop is running");
            return;
        }
        Log.i("UserService", "startListenVolumeKey: starting volume key listener");
        listenVolumeKey = true;
        keepScreenOff = true;
        disableStayOnWhilePlugged();

        // 启动保持熄屏循环线程（先启动，确保即使音量键监听失败也能保持熄屏）
        startKeepScreenOffLoop();

        // 启动音量键监听线程
        Thread thread = new Thread(() -> {
            try {
                listenVolumeKeyProcess = Runtime.getRuntime().exec("getevent");
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(listenVolumeKeyProcess.getInputStream()));
                while (true) {
                    String line = reader.readLine();
                    if (line == null || !listenVolumeKey) {
                        break;
                    }
                    if (!line.endsWith("0000 0000 00000000") &&
                        (line.endsWith("0001 0072 00000001") || line.endsWith("0001 0073 00000001"))) {
                        Log.i("UserService", "volume key pressed, exiting pure black activity");
                        keepScreenOff = false;
                        listenVolumeKey = false;
                        restoreStayOnWhilePlugged();
                        setScreenPower(SurfaceControl.POWER_MODE_NORMAL);
                        if (context != null) {
                            Intent intent = new Intent("com.gitee.connect_screen.EXIT_PURE_BLACK");
                            intent.setPackage("com.gitee.connect_screen");
                            context.sendBroadcast(intent);
                        } else {
                            Log.i("UserService", "context is null, can not send EXIT_PURE_BLACK");
                        }
                        break;
                    }
                }
                reader.close();
                listenVolumeKeyProcess.waitFor();
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    listenVolumeKeyProcess.destroyForcibly();
                } else {
                    listenVolumeKeyProcess.destroy();
                }
            } catch (Exception e) {
                Log.e("UserService", "Listen volume key failed", e);
                // 音量键监听失败不影响循环熄屏，只记录日志
            }
        });
        volumeKeyThread = thread;
        thread.start();
    }

    private void startKeepScreenOffLoop() {
        if (screenOffLoopThread != null && screenOffLoopThread.isAlive()) {
            return;
        }
        screenOffLoopThread = new Thread(() -> {
            Log.i("UserService", "keep screen off loop started");
            while (keepScreenOff) {
                try {
                    // 只有屏幕实际处于亮起状态时才重新熄屏，避免与系统电源管理冲突
                    if (isDefaultDisplayOn()) {
                        setScreenPower(SurfaceControl.POWER_MODE_OFF);
                    }
                    Thread.sleep(SCREEN_OFF_CHECK_INTERVAL);
                } catch (InterruptedException e) {
                    Log.i("UserService", "keep screen off loop interrupted");
                    break;
                } catch (Throwable e) {
                    // 捕获所有异常，防止循环线程崩溃
                    Log.e("UserService", "keep screen off loop error, continuing...", e);
                    try {
                        Thread.sleep(SCREEN_OFF_CHECK_INTERVAL);
                    } catch (InterruptedException ie) {
                        Log.i("UserService", "keep screen off loop interrupted during error recovery");
                        break;
                    }
                }
            }
            Log.i("UserService", "keep screen off loop stopped");
        });
        screenOffLoopThread.start();
    }

    private boolean isDefaultDisplayOn() {
        try {
            if (context == null) {
                return true;
            }
            android.hardware.display.DisplayManager dm =
                    (android.hardware.display.DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (dm == null) {
                return true;
            }
            android.view.Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
            if (d == null) {
                return true;
            }
            int state = d.getState();
            Log.d("UserService", "isDefaultDisplayOn: state=" + state);
            return state != android.view.Display.STATE_OFF
                    && state != android.view.Display.STATE_DOZE
                    && state != android.view.Display.STATE_DOZE_SUSPEND;
        } catch (Throwable e) {
            return true;
        }
    }

    private void disableStayOnWhilePlugged() {
        if (savedStayOnWhilePluggedIn != -1) {
            return;
        }
        try {
            savedStayOnWhilePluggedIn = android.provider.Settings.Global.getInt(
                    context.getContentResolver(), "stay_on_while_plugged_in", 0);
            android.provider.Settings.Global.putInt(
                    context.getContentResolver(), "stay_on_while_plugged_in", 0);
            Log.i("UserService", "disabled stay_on_while_plugged_in, previous=" + savedStayOnWhilePluggedIn);
        } catch (Throwable e) {
            Log.e("UserService", "disable stay_on_while_plugged_in failed", e);
            savedStayOnWhilePluggedIn = -1;
        }
    }

    private void restoreStayOnWhilePlugged() {
        if (savedStayOnWhilePluggedIn == -1) {
            return;
        }
        try {
            android.provider.Settings.Global.putInt(
                    context.getContentResolver(), "stay_on_while_plugged_in", savedStayOnWhilePluggedIn);
            Log.i("UserService", "restored stay_on_while_plugged_in=" + savedStayOnWhilePluggedIn);
        } catch (Throwable e) {
            Log.e("UserService", "restore stay_on_while_plugged_in failed", e);
        }
        savedStayOnWhilePluggedIn = -1;
    }

    public void stopListenVolumeKey() {
        Log.i("UserService", "stopListenVolumeKey called");
        listenVolumeKey = false;
        keepScreenOff = false;
        restoreStayOnWhilePlugged();

        // 停止保持熄屏循环线程
        if (screenOffLoopThread != null) {
            screenOffLoopThread.interrupt();
            try {
                screenOffLoopThread.join(1000);
            } catch (InterruptedException e) {
                Log.e("UserService", "join screenOffLoopThread failed", e);
            }
            screenOffLoopThread = null;
        }

        // 停止音量键监听进程和线程
        if (listenVolumeKeyProcess != null) {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                listenVolumeKeyProcess.destroyForcibly();
            } else {
                listenVolumeKeyProcess.destroy();
            }
            listenVolumeKeyProcess = null;
        }
        if (volumeKeyThread != null) {
            volumeKeyThread.interrupt();
            volumeKeyThread = null;
        }
    }

    public boolean isLoopActive() {
        return keepScreenOff;
    }

    public void goToSleep() {
        Log.i("UserService", "goToSleep: injecting KEYCODE_POWER");
        try {
            android.hardware.input.IInputManager inputManager =
                    android.hardware.input.IInputManager.Stub.asInterface(
                            SystemServiceHelper.getSystemService(Context.INPUT_SERVICE));
            long now = android.os.SystemClock.uptimeMillis();
            android.view.KeyEvent down = new android.view.KeyEvent(now, now,
                    android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_POWER, 0);
            android.view.KeyEvent up = new android.view.KeyEvent(now, now + 50,
                    android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_POWER, 0);
            inputManager.injectInputEvent(down, 0);
            inputManager.injectInputEvent(up, 0);
            Log.i("UserService", "goToSleep: power key injected");
        } catch (Throwable e) {
            Log.e("UserService", "goToSleep failed", e);
        }
    }
}

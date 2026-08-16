package com.gitee.connect_screen;

import android.content.Context;
import android.content.SharedPreferences;

import com.gitee.connect_screen.shizuku.ShizukuUtils;
import com.taowen.androidchangeresolution.QtiModeOverride;

/**
 * 高通模式覆盖的授权模式封装：
 * 自动 / Shizuku / Root(su)，统一产出 QtiModeOverride.Runner。
 */
public final class QtiOverride {
    public static final int MODE_AUTO = 0;
    public static final int MODE_SHIZUKU = 1;
    public static final int MODE_ROOT = 2;

    public static final String PREF = "qti_prefs";
    public static final String KEY_AUTH = "auth_mode";

    private QtiOverride() {
    }

    public static int authMode(Context context) {
        return prefs(context).getInt(KEY_AUTH, MODE_AUTO);
    }

    public static void setAuthMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_AUTH, mode).apply();
    }

    public static String authModeName(int mode) {
        switch (mode) {
            case MODE_SHIZUKU:
                return "Shizuku";
            case MODE_ROOT:
                return "Root(su)";
            case MODE_AUTO:
            default:
                return "自动";
        }
    }

    public static QtiModeOverride.Runner runnerFor(Context context) {
        int mode = authMode(context);
        if (mode == MODE_SHIZUKU) {
            return shizukuRunner();
        }
        if (mode == MODE_ROOT) {
            return QtiModeOverride.ROOT;
        }
        if (ShizukuUtils.hasPermission() && State.userService != null) {
            return shizukuRunner();
        }
        return QtiModeOverride.ROOT;
    }

    public static QtiModeOverride.Runner shizukuRunner() {
        return command -> {
            if (State.userService == null) {
                throw new IllegalStateException("Shizuku 用户服务未连接，请先授权 Shizuku");
            }
            String output = State.userService.executeCommand(command);
            return new QtiModeOverride.Result(0, output == null ? "" : output);
        };
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
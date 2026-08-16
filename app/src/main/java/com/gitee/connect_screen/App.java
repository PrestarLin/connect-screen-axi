package com.gitee.connect_screen;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

public class App extends Application {
    public static final String PREF = "settings";
    public static final String KEY_THEME = "theme_mode";
    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    @Override
    public void onCreate() {
        super.onCreate();
        int mode = getSharedPreferences(PREF, MODE_PRIVATE)
                .getInt(KEY_THEME, THEME_SYSTEM);
        applyTheme(mode);
    }

    public static void applyTheme(int mode) {
        switch (mode) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
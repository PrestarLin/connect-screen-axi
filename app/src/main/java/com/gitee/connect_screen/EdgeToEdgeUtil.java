package com.gitee.connect_screen;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Android 15/16 (targetSdk 36) 强制 edge-to-edge，系统栏会覆盖内容。
 * 本工具给 Activity 内容根视图加上系统栏（状态栏/导航栏/刘海）内边距，
 * 避免内容跑到状态栏里。
 */
public final class EdgeToEdgeUtil {

    private EdgeToEdgeUtil() {
    }

    public static void padSystemBars(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (root == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            if (insets == null) {
                return WindowInsetsCompat.CONSUMED;
            }
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
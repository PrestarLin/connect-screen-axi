package com.gitee.connect_screen;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gitee.connect_screen.job.AcquireShizuku;
import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class HomeFragment extends Fragment {

    private View connectedDisplayCard;
    private TextView displayStatusText;
    private Button btnExitProjection;
    private TextView recentAppsTitle;
    private LinearLayout recentAppsRow;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        connectedDisplayCard = view.findViewById(R.id.connectedDisplayCard);
        displayStatusText = view.findViewById(R.id.displayStatusText);
        btnExitProjection = view.findViewById(R.id.btnExitProjection);
        recentAppsTitle = view.findViewById(R.id.recentAppsTitle);
        recentAppsRow = view.findViewById(R.id.recentAppsRow);

        Button shizukuPermissionBtn = view.findViewById(R.id.shizukuPermissionBtn);
        shizukuPermissionBtn.setOnClickListener(v -> {
            State.startNewJob(new AcquireShizuku());
        });

        TextView shizukuStatus = view.findViewById(R.id.shizukuStatus);
        View shizukuDot = view.findViewById(R.id.shizukuDot);
        updateShizukuStatus(shizukuStatus, shizukuDot, shizukuPermissionBtn);

        // 点击 Shizuku 药丸弹出授权方式选择
        View shizukuPill = view.findViewById(R.id.shizukuPill);
        shizukuPill.setOnClickListener(v -> showAuthModeDialog());

        view.findViewById(R.id.cardSingleApp).setOnClickListener(v ->
                State.breadcrumbManager.pushBreadcrumb("屏幕", () -> new DisplayListFragment()));

        view.findViewById(R.id.cardFullscreen).setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setAction(android.provider.Settings.ACTION_CAST_SETTINGS);
            try {
                startActivity(intent);
            } catch (Exception e) {
                showHelp();
            }
        });

        view.findViewById(R.id.cardDisplaySettings).setOnClickListener(v ->
                State.breadcrumbManager.pushBreadcrumb("屏幕", () -> new DisplayListFragment()));

        view.findViewById(R.id.tileDisplays).setOnClickListener(v ->
                State.breadcrumbManager.pushBreadcrumb("屏幕", () -> new DisplayListFragment()));

        view.findViewById(R.id.tileDisplaylink).setOnClickListener(v ->
                State.breadcrumbManager.pushBreadcrumb("Displaylink", () -> new DisplaylinkFragment()));

        view.findViewById(R.id.tileWireless).setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setAction(android.provider.Settings.ACTION_CAST_SETTINGS);
            try {
                startActivity(intent);
            } catch (Exception e) {
                showHelp();
            }
        });

        view.findViewById(R.id.tileTouchpad).setOnClickListener(v -> {
            if (State.lastSingleAppDisplay <= 0) {
                showHelp();
            } else {
                TouchpadActivity.startTouchpad(getContext(), State.lastSingleAppDisplay, false);
            }
        });

        view.findViewById(R.id.tileSettings).setOnClickListener(v ->
                State.breadcrumbManager.pushBreadcrumb("设置", () -> new SettingsFragment()));

        view.findViewById(R.id.tileAbout).setOnClickListener(v ->
                State.breadcrumbManager.pushBreadcrumb("关于", () -> new AboutFragment()));

        btnExitProjection.setOnClickListener(v -> exitProjection());

        refreshConnectedDisplay();
        refreshRecentApps();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshConnectedDisplay();
        refreshRecentApps();
    }

    private void refreshConnectedDisplay() {
        try {
            Context ctx = getContext();
            if (ctx == null) {
                return;
            }
            DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
            if (State.externalDisplayIds.isEmpty()) {
                connectedDisplayCard.setVisibility(View.GONE);
                btnExitProjection.setVisibility(View.GONE);
                return;
            }
            connectedDisplayCard.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (int id : State.externalDisplayIds) {
                Display d = dm.getDisplay(id);
                if (d != null) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(d.getName()).append(" · ").append(d.getWidth())
                            .append("x").append(d.getHeight()).append(" @ ")
                            .append(String.format("%.1f", d.getRefreshRate())).append(" Hz");
                }
            }
            boolean hasProjection = State.lastSingleAppDisplay > 0
                    || State.bridgeVirtualDisplay != null
                    || State.mirrorVirtualDisplay != null
                    || State.displaylinkState.getVirtualDisplay() != null;
            if (hasProjection) {
                sb.append("\n（投屏中）");
                btnExitProjection.setVisibility(View.VISIBLE);
            } else {
                btnExitProjection.setVisibility(View.GONE);
            }
            displayStatusText.setText(sb.toString());
        } catch (Exception ignored) {
        }
    }

    private void exitProjection() {
        try {
            if (State.bridgeVirtualDisplay != null) {
                State.bridgeVirtualDisplay.release();
                State.bridgeVirtualDisplay = null;
            }
            if (State.mirrorVirtualDisplay != null) {
                State.mirrorVirtualDisplay.release();
                State.mirrorVirtualDisplay = null;
            }
            if (State.displaylinkState.getVirtualDisplay() != null) {
                State.displaylinkState.stopVirtualDisplay();
                State.displaylinkState.destroy();
            }
            State.lastSingleAppDisplay = -1;
            State.log("已退出投屏");
            refreshConnectedDisplay();
        } catch (Exception e) {
            State.log("退出投屏失败: " + e.getMessage());
        }
    }

    private void refreshRecentApps() {
        try {
            Context ctx = getContext();
            if (ctx == null) {
                return;
            }
            SharedPreferences prefs = ctx.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
            Map<String, ?> all = prefs.getAll();
            TreeMap<Long, String> sorted = new TreeMap<>((a, b) -> Long.compare(b, a));
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                if (entry.getKey().startsWith("launch_time_") && entry.getValue() instanceof Long) {
                    String pkg = entry.getKey().substring("launch_time_".length());
                    Long time = (Long) entry.getValue();
                    if (time > 0) {
                        sorted.put(time, pkg);
                    }
                }
            }
            if (sorted.isEmpty()) {
                recentAppsTitle.setVisibility(View.GONE);
                recentAppsRow.setVisibility(View.GONE);
                return;
            }
            int count = 0;
            recentAppsRow.removeAllViews();
            PackageManager pm = ctx.getPackageManager();
            for (Map.Entry<Long, String> entry : sorted.entrySet()) {
                if (count >= 6) {
                    break;
                }
                String pkg = entry.getValue();
                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                    final int targetDisplay = State.lastSingleAppDisplay > 0
                            ? State.lastSingleAppDisplay
                            : (!State.externalDisplayIds.isEmpty() ? State.externalDisplayIds.get(0) : Display.DEFAULT_DISPLAY);
                    ImageView icon = new ImageView(ctx);
                    int size = getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                    lp.setMargins(0, 0, (int) (12 * ctx.getResources().getDisplayMetrics().density), 0);
                    icon.setLayoutParams(lp);
                    icon.setImageDrawable(pm.getApplicationIcon(pkg));
                    icon.setContentDescription(appInfo.loadLabel(pm).toString());
                    icon.setOnClickListener(v -> {
                        if (targetDisplay > 0) {
                            ServiceUtils.launchPackage(ctx, pkg, targetDisplay);
                        } else {
                            Toast.makeText(ctx, "未连接外接显示器", Toast.LENGTH_SHORT).show();
                        }
                    });
                    recentAppsRow.addView(icon);
                    count++;
                } catch (Exception ignored) {
                }
            }
            recentAppsTitle.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            recentAppsRow.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        } catch (Exception ignored) {
        }
    }

    private void showHelp() {
        new AlertDialog.Builder(requireContext())
            .setTitle("还没有投屏应用")
            .setMessage(
                    "• USB3.0手机：连接屏幕后进入屏幕列表选择屏幕开始投屏单个应用\n\n" +
                    "• USB2.0手机：点击Displaylink按钮，选择单应用投屏模式\n\n" +
                    "• 无线方式：使用安卓自带的无线投屏，然后进入屏幕列表找到无线屏幕，进行单应用投屏")
            .setPositiveButton("知道了", null)
            .show();
    }

    private void updateShizukuStatus(TextView statusView, View dot, Button permissionBtn) {
        int authMode = QtiOverride.authMode(requireContext());
        String authName = QtiOverride.authModeName(authMode);

        boolean started = ShizukuUtils.hasShizukuStarted();
        boolean hasPermission = ShizukuUtils.hasPermission();

        // 更新权限前缀文字
        TextView prefixView = getView() != null ? getView().findViewById(R.id.shizukuStatusPrefix) : null;
        if (prefixView != null) {
            prefixView.setText("授权方式: ");
        }

        int dotColor;
        String status;
        // 根据授权模式显示状态
        switch (authMode) {
            case QtiOverride.MODE_ROOT:
                dotColor = requireContext().getColor(R.color.md_secondary);
                status = "Root";
                permissionBtn.setVisibility(View.GONE);
                break;
            case QtiOverride.MODE_SHIZUKU:
                if (!started) {
                    dotColor = requireContext().getColor(R.color.md_error);
                    status = "Shizuku 未启动";
                    permissionBtn.setVisibility(View.GONE);
                } else if (!hasPermission) {
                    dotColor = requireContext().getColor(R.color.md_tertiary);
                    status = "Shizuku 未授权";
                    permissionBtn.setVisibility(View.VISIBLE);
                } else {
                    dotColor = requireContext().getColor(R.color.md_secondary);
                    status = "Shizuku 已授权";
                    permissionBtn.setVisibility(View.GONE);
                }
                break;
            default: // AUTO
                if (hasPermission) {
                    dotColor = requireContext().getColor(R.color.md_secondary);
                    status = "自动 (Shizuku)";
                    permissionBtn.setVisibility(View.GONE);
                } else {
                    dotColor = requireContext().getColor(R.color.md_secondary);
                    status = "自动 (Root)";
                    permissionBtn.setVisibility(View.GONE);
                }
                break;
        }

        try {
            GradientDrawable d = (GradientDrawable) dot.getBackground();
            d.setColor(dotColor);
        } catch (Throwable ignored) {
        }

        statusView.setText(status);
    }

    private void showAuthModeDialog() {
        int currentMode = QtiOverride.authMode(requireContext());
        String[] modes = {"自动", "Root", "Shizuku"};
        final int[] values = {QtiOverride.MODE_AUTO, QtiOverride.MODE_ROOT, QtiOverride.MODE_SHIZUKU};
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == currentMode) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("授权方式")
                .setSingleChoiceItems(modes, checked, (dialog, which) -> {
                    int selected = values[which];
                    requireContext().getSharedPreferences(QtiOverride.PREF, Context.MODE_PRIVATE)
                            .edit().putInt(QtiOverride.KEY_AUTH, selected).apply();
                    State.log("授权方式切换为: " + QtiOverride.authModeName(selected));
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
package com.gitee.connect_screen;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gitee.connect_screen.job.AcquireShizuku;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        Button shizukuPermissionBtn = view.findViewById(R.id.shizukuPermissionBtn);
        shizukuPermissionBtn.setOnClickListener(v -> {
            State.startNewJob(new AcquireShizuku());
        });

        TextView shizukuStatus = view.findViewById(R.id.shizukuStatus);
        View shizukuDot = view.findViewById(R.id.shizukuDot);
        updateShizukuStatus(shizukuStatus, shizukuDot, shizukuPermissionBtn);

        // 三大主功能：进入屏幕列表选择外接显示器
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

        // 更多功能
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

        view.findViewById(R.id.tileSettings).setOnClickListener(v -> {
            if (ShizukuUtils.hasPermission()) {
                State.breadcrumbManager.pushBreadcrumb("设置", () -> new SettingsFragment());
            } else {
                Toast.makeText(requireContext(), "需要先授权 Shizuku", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.tileAbout).setOnClickListener(v ->
                State.breadcrumbManager.pushBreadcrumb("关于", () -> new AboutFragment()));

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (State.breadcrumbManager != null) {
            State.breadcrumbManager.forceHomeToolbar();
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
        boolean started = ShizukuUtils.hasShizukuStarted();
        boolean hasPermission = ShizukuUtils.hasPermission();

        int dotColor;
        String status;
        if (!started) {
            status = "未启动";
            dotColor = requireContext().getColor(R.color.md_error);
            permissionBtn.setVisibility(View.GONE);
        } else if (!hasPermission) {
            status = "已启动，未授权";
            dotColor = requireContext().getColor(R.color.md_tertiary);
            permissionBtn.setVisibility(View.VISIBLE);
        } else {
            status = "已授权";
            dotColor = requireContext().getColor(R.color.md_secondary);
            permissionBtn.setVisibility(View.GONE);
        }

        try {
            GradientDrawable d = (GradientDrawable) dot.getBackground();
            d.setColor(dotColor);
        } catch (Throwable ignored) {
        }

        statusView.setText(status);
    }
}
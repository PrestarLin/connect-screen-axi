package com.gitee.connect_screen;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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

        TextView shizukuStatusPrefix = view.findViewById(R.id.shizukuStatusPrefix);
        shizukuStatusPrefix.setPaintFlags(shizukuStatusPrefix.getPaintFlags());

        // 添加授权按钮
        Button shizukuPermissionBtn = view.findViewById(R.id.shizukuPermissionBtn);
        shizukuPermissionBtn.setOnClickListener(v -> {
            State.startNewJob(new AcquireShizuku());
        });

        // 更新Shizuku状态
        TextView shizukuStatus = view.findViewById(R.id.shizukuStatus);
        updateShizukuStatus(shizukuStatus, shizukuPermissionBtn);

        // 三大主功能：都先进入屏幕列表，选择外接显示器
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

    private void updateShizukuStatus(TextView statusView, Button permissionBtn) {
        boolean started = ShizukuUtils.hasShizukuStarted();
        boolean hasPermission = ShizukuUtils.hasPermission();

        String status;
        if (!started) {
            status = "未启动";
            permissionBtn.setVisibility(View.GONE);
        } else if (!hasPermission) {
            status = "已启动，未授权";
            permissionBtn.setVisibility(View.VISIBLE);
        } else {
            status = "已授权";
            permissionBtn.setVisibility(View.GONE);
        }

        statusView.setText(status);
    }
}
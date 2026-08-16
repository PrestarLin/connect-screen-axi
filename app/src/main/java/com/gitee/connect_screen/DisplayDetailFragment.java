package com.gitee.connect_screen;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.IDisplayManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.DisplayInfo;
import android.view.IWindowManager;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.gitee.connect_screen.job.BindAllExternalInputToDisplay;
import com.gitee.connect_screen.job.TermuxDisablePhantomProcess;
import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;
import com.gitee.connect_screen.dialog.RotationDialog;
import com.gitee.connect_screen.dialog.ResolutionDialog;
import com.gitee.connect_screen.dialog.BridgeDialog;
import com.gitee.connect_screen.dialog.DpiDialog;
import com.gitee.connect_screen.shizuku.WindowingMode;

import com.taowen.androidchangeresolution.QtiModeOverride;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DisplayDetailFragment extends Fragment {
    private static final String ARG_DISPLAY_ID = "display_id";

    private TextView shizukuStatusText;
    private Button launchButton;
    private int displayId;
    private Display display;
    private Button supportedModesToggle;
    private TextView supportedModesText;
    private Button gotoDisplaylinkButton;
    private Button setImePolicyButton;
    private CheckBox autoOpenLastAppCheckbox;
    private Button floatingButtonToggle;
    private CheckBox forceLandscapeCheckbox;

    public static DisplayDetailFragment newInstance(int displayId) {
        DisplayDetailFragment fragment = new DisplayDetailFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_DISPLAY_ID, displayId);
        fragment.setArguments(args);
        return fragment;
    }

    private String getDisplayFlags(Display display) {
        int flags = display.getFlags();
        StringBuilder flagsStr = new StringBuilder();

        if ((flags & Display.FLAG_SECURE) != 0) flagsStr.append("FLAG_SECURE, ");
        if ((flags & Display.FLAG_SUPPORTS_PROTECTED_BUFFERS) != 0) flagsStr.append("FLAG_SUPPORTS_PROTECTED_BUFFERS, ");
        if ((flags & Display.FLAG_PRIVATE) != 0) flagsStr.append("FLAG_PRIVATE, ");
        if ((flags & Display.FLAG_PRESENTATION) != 0) flagsStr.append("FLAG_PRESENTATION, ");
        if ((flags & Display.FLAG_ROUND) != 0) flagsStr.append("FLAG_ROUND, ");

        if (flagsStr.length() > 0) {
            flagsStr.setLength(flagsStr.length() - 2);
        }

        return flagsStr.length() > 0 ? flagsStr.toString() : "无";
    }

    private void showToast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_display_detail, container, false);
        setImePolicyButton = view.findViewById(R.id.set_ime_policy_button);
        supportedModesToggle = view.findViewById(R.id.supported_modes_toggle);
        supportedModesText = view.findViewById(R.id.supported_modes_text);
        autoOpenLastAppCheckbox = view.findViewById(R.id.autoOpenLastAppCheckbox);
        displayId = getArguments().getInt(ARG_DISPLAY_ID);
        DisplayManager displayManager = (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);
        display = displayManager.getDisplay(displayId);

        if(display == null) {
            if (State.breadcrumbManager != null) {
                State.breadcrumbManager.popBreadcrumb();
            }
            return view;
        }

        DisplayCutout cutout = display.getCutout();
        String cutoutInfo = "无刘海";
        if (cutout != null) {
            StringBuilder cutoutDetails = new StringBuilder("刘海边界:\n");
            for (android.graphics.Rect rect : cutout.getBoundingRects()) {
                cutoutDetails.append(String.format("左:%d 上:%d 右:%d 下:%d\n",
                    rect.left, rect.top, rect.right, rect.bottom));
            }
            cutoutInfo = cutoutDetails.toString();
        }

        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);

        TextView detailText = view.findViewById(R.id.detail_text);
        TextView resolutionText = view.findViewById(R.id.resolution_text);

        // 设置分辨率文本
        String resolution = String.format("分辨率: %dx%d", display.getWidth(), display.getHeight());
        resolutionText.setText(resolution);

        String details = String.format(
            "显示器 ID: %d\n" +
            "名称: %s\n" +
            "刷新率: %.1f Hz\n" +
            "状态: %s\n" +
            "HDR支持: %s\n" +
            "显示器标志: %s\n" +
            "刘海信息: %s",
            display.getDisplayId(),
            display.getName(),
            display.getRefreshRate(),
            display.getState() == Display.STATE_ON ? "开启" : "关闭",
            display.isHdr() ? "是" : "否",
            getDisplayFlags(display),
            cutoutInfo
        );

        // 添加显示模式信息到状态文本
        setupDisplayModes(display.getSupportedModes());
        detailText.setText(details);

        shizukuStatusText = view.findViewById(R.id.shizuku_status);

        launchButton = view.findViewById(R.id.start_launcher_button);
        if (displayId == 0) {
            launchButton.setVisibility(View.GONE);
        }
        launchButton.setOnClickListener(v -> {
            LauncherActivity.start(getContext(), displayId);
        });

        Button qtiForceModeButton = view.findViewById(R.id.btn_qti_force_mode);
        Button qtiDiagButton = view.findViewById(R.id.btn_qti_diag);
        if (displayId != Display.DEFAULT_DISPLAY) {
            qtiForceModeButton.setVisibility(View.VISIBLE);
            qtiDiagButton.setVisibility(View.VISIBLE);
        } else {
            qtiForceModeButton.setVisibility(View.GONE);
            qtiDiagButton.setVisibility(View.GONE);
        }
        qtiForceModeButton.setOnClickListener(v -> showQtiForceModeDialog());
        qtiDiagButton.setOnClickListener(v ->
                State.breadcrumbManager.pushBreadcrumb("高通诊断", DiagnosticsFragment::new));

        Button touchpadButton = view.findViewById(R.id.touchpad_button);
        if (displayId != Display.DEFAULT_DISPLAY) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R || ShizukuUtils.hasPermission()) {
                touchpadButton.setVisibility(View.VISIBLE);
            }
        }
        touchpadButton.setOnClickListener(v -> {
            TouchpadActivity.startTouchpad(getContext(), displayId, false);
        });

        // 添加修改按钮点击事件
        Button editResolutionButton = view.findViewById(R.id.edit_resolution_button);
        if(ShizukuUtils.hasShizukuStarted()) {
            editResolutionButton.setVisibility(View.VISIBLE);
            editResolutionButton.setOnClickListener(v -> {
                ResolutionDialog.show(getContext(), displayId, display.getWidth(), display.getHeight());
            });
        }

        TextView dpiText = view.findViewById(R.id.dpi_text);
        dpiText.setText(String.format("DPI: %d", metrics.densityDpi));

        Button editDpiButton = view.findViewById(R.id.edit_dpi_button);
        if(ShizukuUtils.hasShizukuStarted()) {
            editDpiButton.setVisibility(View.VISIBLE);
            editDpiButton.setOnClickListener(v -> {
                DpiDialog.show(getContext(), displayId, metrics.densityDpi);
            });
        }

        TextView userRotationText = view.findViewById(R.id.user_rotation_text);
        Button editRotationButton = view.findViewById(R.id.edit_rotation_button);

        // 更新当前旋转状态
        updateUserRotationText(userRotationText);

        if(ShizukuUtils.hasShizukuStarted()) {
            if (displayId != Display.DEFAULT_DISPLAY) {
                editRotationButton.setVisibility(View.VISIBLE);
            }
            editRotationButton.setOnClickListener(v -> {
                showRotationDialog();
            });
        }

        updateShizukuStatus();

        // 添加 Displaylink 按钮相关逻辑
        gotoDisplaylinkButton = view.findViewById(R.id.goto_displaylink_button);
        if(displayId == State.getDisplaylinkVirtualDisplayId()) {
            if (!ShizukuUtils.hasPermission()) {
                launchButton.setVisibility(View.GONE);
            }
            gotoDisplaylinkButton.setVisibility(View.VISIBLE);
            gotoDisplaylinkButton.setOnClickListener(v -> {
                State.breadcrumbManager.pushBreadcrumb("Displaylink", () ->
                        DisplaylinkFragment.newInstance()
                );
            });
        } else {
            if (displayId != Display.DEFAULT_DISPLAY) {
                autoOpenLastAppCheckbox.setVisibility(View.VISIBLE);
                SharedPreferences appPreferences = getActivity().getSharedPreferences("app_preferences", MODE_PRIVATE);
                autoOpenLastAppCheckbox.setChecked(appPreferences.getBoolean("AUTO_OPEN_LAST_APP_" + display.getName(), false));
                autoOpenLastAppCheckbox.setOnClickListener(v -> {
                    boolean isChecked = autoOpenLastAppCheckbox.isChecked();
                    appPreferences.edit().putBoolean("AUTO_OPEN_LAST_APP_" + display.getName(), isChecked).apply();
                });
            }
        }

        // 添加桥接按钮
        Button bridgeButton = view.findViewById(R.id.bridge_button);

        if (displayId == State.getBridgeVirtualDisplayId() || displayId == State.bridgeDisplayId) {
            bridgeButton.setVisibility(View.VISIBLE);
            bridgeButton.setText("退出桥接");
            bridgeButton.setOnClickListener(v -> {
                BridgeActivity.stopVirtualDisplay();
                if (BridgeActivity.getInstance() != null) {
                    BridgeActivity.getInstance().finish();
                }
            });
        } else if (displayId == State.getMirrorVirtualDisplayId() || displayId == State.mirrorDisplayId) {
            bridgeButton.setVisibility(View.VISIBLE);
            bridgeButton.setText("退出镜像");
            bridgeButton.setOnClickListener(v -> {
                MirrorActivity.stopVirtualDisplay();
                if (MirrorActivity.getInstance() != null) {
                    MirrorActivity.getInstance().finish();
                }
            });
        } else if (displayId == State.getDisplaylinkVirtualDisplayId()) {
            bridgeButton.setVisibility(View.VISIBLE);
            bridgeButton.setText("退出 Displaylink 投屏");
            bridgeButton.setOnClickListener(v -> {
                State.displaylinkState.stopVirtualDisplay();
                State.displaylinkState.destroy();
                State.breadcrumbManager.popBreadcrumb();
            });
        } else if(displayId != Display.DEFAULT_DISPLAY && ShizukuUtils.hasShizukuStarted()) {
            bridgeButton.setVisibility(View.VISIBLE);
            bridgeButton.setOnClickListener(v -> showBridgeDialog());
        }

        floatingButtonToggle = view.findViewById(R.id.floating_button_toggle);
        forceLandscapeCheckbox = view.findViewById(R.id.force_landscape_checkbox);
        
        if (displayId != Display.DEFAULT_DISPLAY) {
            floatingButtonToggle.setVisibility(View.VISIBLE);
            forceLandscapeCheckbox.setVisibility(View.VISIBLE);
            SharedPreferences appPreferences = getActivity().getSharedPreferences("app_preferences", MODE_PRIVATE);
            boolean isEnabled = appPreferences.getBoolean("FLOATING_BUTTON_" + display.getName(), false);
            boolean forceLandscape = appPreferences.getBoolean("FLOATING_BUTTON_FORCE_LANDSCAPE", false);
            
            updateFloatingBackButtonText(isEnabled);
            forceLandscapeCheckbox.setChecked(forceLandscape);
            
            floatingButtonToggle.setOnClickListener(v -> {
                boolean newIsEnabled = !appPreferences.getBoolean("FLOATING_BUTTON_" + display.getName(), false);
                if (newIsEnabled) {
                    if (FloatingButtonService.startFloating(getContext(), displayId, false)) {
                        appPreferences.edit().putBoolean("FLOATING_BUTTON_" + display.getName(), true).apply();
                    }
                } else {
                    Intent serviceIntent = new Intent(getContext(), FloatingButtonService.class);
                    getContext().stopService(serviceIntent);
                    appPreferences.edit().putBoolean("FLOATING_BUTTON_" + display.getName(), false).apply();
                }
                updateFloatingBackButtonText(newIsEnabled);
            });

            forceLandscapeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                appPreferences.edit().putBoolean("FLOATING_BUTTON_FORCE_LANDSCAPE", isChecked).apply();
            });
        }

        // 真实熄屏 + 唤醒键绑定（仅外接显示器）
        Button realScreenOffBtn = view.findViewById(R.id.btn_real_screen_off);
        Button wakeKeyBtn = view.findViewById(R.id.btn_wake_key);
        if (displayId != Display.DEFAULT_DISPLAY) {
            realScreenOffBtn.setVisibility(View.VISIBLE);
            wakeKeyBtn.setVisibility(View.VISIBLE);
            realScreenOffBtn.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), PureBlackActivity.class);
                ActivityOptions options = ActivityOptions.makeBasic();
                startActivity(intent, options.toBundle());
            });
            String[] keys = {"音量上", "音量下", "电源键"};
            final int[] keyValues = {android.view.KeyEvent.KEYCODE_VOLUME_UP,
                    android.view.KeyEvent.KEYCODE_VOLUME_DOWN,
                    android.view.KeyEvent.KEYCODE_POWER};
            SharedPreferences prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
            int savedKey = prefs.getInt("wake_key", android.view.KeyEvent.KEYCODE_VOLUME_UP);
            String currentKeyName = "音量上";
            for (int i = 0; i < keyValues.length; i++) {
                if (keyValues[i] == savedKey) {
                    currentKeyName = keys[i];
                    break;
                }
            }
            wakeKeyBtn.setText("唤醒键：" + currentKeyName);
            wakeKeyBtn.setOnClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(getContext())
                        .setTitle("选择唤醒键")
                        .setItems(keys, (dialog, which) -> {
                            int selectedKey = keyValues[which];
                            prefs.edit().putInt("wake_key", selectedKey).apply();
                            wakeKeyBtn.setText("唤醒键：" + keys[which]);
                            showToast("唤醒键已设置为 " + keys[which]);
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
        }

        return view;
    }

    private void updateShizukuStatus() {
        if (shizukuStatusText == null) {
            return;
        }
        if (!ShizukuUtils.hasShizukuStarted()) {
            shizukuStatusText.setText("Shizuku权限状态: 未启动");
            return;
        }
        try {
            boolean hasPermission = ShizukuUtils.hasPermission();
            String statusText = "Shizuku权限状态: " + (hasPermission ? "已授权" : "未授权");
            if (hasPermission) {
                Point baseSize = new Point();
                IWindowManager windowManager = ServiceUtils.getWindowManager();
                windowManager.getBaseDisplaySize(displayId, baseSize);
                statusText += String.format("\nOverride size: %dx%d", baseSize.x, baseSize.y);
                Point initialSize = new Point();
                windowManager.getInitialDisplaySize(displayId, initialSize);
                statusText += String.format("\nPhysical size: %dx%d", initialSize.x, initialSize.y);
               try {
                int imePolicy = windowManager.getDisplayImePolicy(displayId);
                switch (imePolicy) {
                    case 0:
                        statusText += "\n输入法策略: LOCAL";
                        break;
                    case 1:
                        statusText += "\n输入法策略: FALLBACK_DISPLAY";
                        break;
                    case 2:
                        statusText += "\n输入法策略: HIDE";
                        break;
                    default:
                        statusText += ("\n输入法策略: " + imePolicy);
                        break;
                }

                   if (displayId != Display.DEFAULT_DISPLAY) {
                       setImePolicyButton.setVisibility(View.VISIBLE);
                       if(imePolicy == 0) {
                           setImePolicyButton.setText("回主屏显示输入法");
                           setImePolicyButton.setOnClickListener(v -> {
                               windowManager.setDisplayImePolicy(Display.DEFAULT_DISPLAY, 0);
                               windowManager.setDisplayImePolicy(displayId, 1);
                               try {
                                   State.breadcrumbManager.refreshCurrentFragment();
                               } catch (Throwable e) {
                                   State.log("回主屏显示输入法，设置失败" + e);
                               }
                           });
                       } else {
                           setImePolicyButton.setText("在此屏幕显示输入法");
                           setImePolicyButton.setOnClickListener(v -> {
                               windowManager.setDisplayImePolicy(Display.DEFAULT_DISPLAY, 1);
                               try {
                                   windowManager.setDisplayImePolicy(displayId, 0);
                                   State.breadcrumbManager.refreshCurrentFragment();
                               } catch (Throwable e) {
                                   windowManager.setDisplayImePolicy(Display.DEFAULT_DISPLAY, 0);
                                   State.log("在此屏幕显示输入法，设置失败" + e);
                               }
                           });
                       }
                   }
               } catch(Throwable e) {
                // ignore
               }

                DisplayInfo displayInfo = ServiceUtils.getDisplayManager().getDisplayInfo(displayId);
                statusText += String.format("\n默认模式ID: %d", displayInfo.defaultModeId);
                try {
                    statusText += String.format("\n刷新率覆盖: %.1f Hz", displayInfo.refreshRateOverride);
                } catch(Throwable e) {
                    // ignore
                }
                try {
                    statusText += String.format("\n安装方向: %d", displayInfo.installOrientation);
                } catch(Throwable e) {
                    // ignore
                }
                try {
                    String windowingMode = WindowingMode.getWindowingMode(displayId);
                    statusText += String.format("\n窗口模式: %s", windowingMode);
                } catch(Throwable e) {
                }
            }
            shizukuStatusText.setText(statusText);
        } catch(Exception e) {
            shizukuStatusText.setText("Shizuku权限状态: 未授权");
            State.log("获取 Shizuku 权限失败：" + e.getMessage());
        }
    }

    private void setupDisplayModes(Display.Mode[] supportedModes) {
        StringBuilder supportedModesStr = new StringBuilder();
        for (Display.Mode mode : supportedModes) {
            supportedModesStr.append(String.format("模式ID: %d, 分辨率: %dx%d, 刷新率: %.1f Hz\n",
                    mode.getModeId(),
                    mode.getPhysicalWidth(),
                    mode.getPhysicalHeight(),
                    mode.getRefreshRate()));
        }
        supportedModesText.setText(supportedModesStr.toString());

        // 添加双击事件
        supportedModesText.setOnClickListener(new View.OnClickListener() {
            private long lastClickTime = 0;
            @Override
            public void onClick(View v) {
                long clickTime = System.currentTimeMillis();
                if (clickTime - lastClickTime < 300) { // 双击判断
                    showDisplayModeDialog(supportedModes);
                }
                lastClickTime = clickTime;
            }
        });

        // 设置点击展开/收起事件
        supportedModesToggle.setOnClickListener(v -> {
            boolean isVisible = supportedModesText.getVisibility() == View.VISIBLE;
            supportedModesText.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            supportedModesToggle.setText("支持的显示模式 " + (isVisible ? "▼" : "▲"));
            supportedModesText.requestLayout();
        });
    }

    private void showDisplayModeDialog(Display.Mode[] supportedModes) {
        if (!ShizukuUtils.hasShizukuStarted()) {
            showToast("需要 Shizuku 权限");
            return;
        }

        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_select_mode, null);
        RadioGroup radioGroup = dialogView.findViewById(R.id.modeRadioGroup);

        for (int i = 0; i < supportedModes.length; i++) {
            Display.Mode mode = supportedModes[i];
            RadioButton rb = new RadioButton(getContext());
            rb.setText(String.format("ID:%d  %dx%d  %.1fHz",
                    mode.getModeId(),
                    mode.getPhysicalWidth(),
                    mode.getPhysicalHeight(),
                    mode.getRefreshRate()));
            rb.setTextColor(getContext().getColor(R.color.md_on_surface));
            rb.setId(i);
            rb.setPadding(0, (int) (8 * getResources().getDisplayMetrics().density), 0,
                    (int) (8 * getResources().getDisplayMetrics().density));
            radioGroup.addView(rb);
        }
        if (supportedModes.length > 0) {
            radioGroup.check(0);
        }

        final androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btnConfirmMode).setOnClickListener(v -> {
            int checkedId = radioGroup.getCheckedRadioButtonId();
            if (checkedId >= 0 && checkedId < supportedModes.length) {
                Display.Mode selectedMode = supportedModes[checkedId];
                try {
                    IDisplayManager displayManager = ServiceUtils.getDisplayManager();
                    displayManager.setUserPreferredDisplayMode(displayId, selectedMode);
                    showToast("显示模式已设置（可能需接显示器后生效）");
                } catch (Exception e) {
                    State.log("设置显示模式失败: " + e);
                }
            }
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.btnCancelMode).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    
// 添加新方法:
private void updateUserRotationText(TextView rotationText) {
    int rotation = display.getRotation();
    String rotationStr;
    switch(rotation) {
        case Surface.ROTATION_0:
            rotationStr = "0°";
            break;
        case Surface.ROTATION_90:
            rotationStr = "90°";
            break;
        case Surface.ROTATION_180:
            rotationStr = "180°";
            break;
        case Surface.ROTATION_270:
            rotationStr = "270°";
            break;
        default:
            rotationStr = "未知";
    }
    rotationText.setText("旋转角度: " + rotationStr);
}

private void showRotationDialog() {
    RotationDialog.show(getContext(), displayId);
}

private void showBridgeDialog() {
    if (android.os.Build.VERSION.SDK_INT >= 34) {
        Toast.makeText(getContext(), "安卓15可以直接修改旋转角度，无需桥接", Toast.LENGTH_SHORT).show();
    }
    BridgeDialog.show(getContext(), display, displayId);
}

private void showQtiForceModeDialog() {
    if (display == null || getContext() == null) {
        return;
    }
    Display.Mode[] modes = display.getSupportedModes();
    if (modes == null || modes.length == 0) {
        showToast("此显示器没有可用的显示模式");
        return;
    }
    String[] items = new String[modes.length];
    for (int i = 0; i < modes.length; i++) {
        Display.Mode mode = modes[i];
        items[i] = "#" + mode.getModeId() + "  " + mode.getPhysicalWidth()
                + "x" + mode.getPhysicalHeight() + " @ "
                + Math.round(mode.getRefreshRate()) + " Hz";
    }
    new androidx.appcompat.app.AlertDialog.Builder(getContext())
            .setTitle("选择强制模式（高通）")
            .setItems(items, (dialog, which) -> applyQtiForceMode(modes[which]))
            .setNegativeButton("取消", null)
            .show();
}

private void applyQtiForceMode(Display.Mode mode) {
    if (getContext() == null) {
        return;
    }
    int modeId = QtiOverride.authMode(requireContext());
    String authName = QtiOverride.authModeName(modeId);
    showToast("正在通过 " + authName + " 设置 " + QtiModeOverride.modeSpec(mode) + " …");
    State.log("高通强制模式: " + QtiModeOverride.formatMode(mode) + " 授权=" + authName);

    final Context ctx = requireContext();
    final Activity activity = requireActivity();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(() -> {
        try {
            final QtiModeOverride.Runner runner = QtiOverride.runnerFor(ctx);
            final QtiModeOverride.ApplyResult result =
                    QtiModeOverride.apply(ctx, display, mode, runner);
            String current = QtiModeOverride.currentOverride(ctx, runner);
            boolean ok = result.propertyValue.equals(current);
            activity.runOnUiThread(() -> {
                if (ok) {
                    new androidx.appcompat.app.AlertDialog.Builder(ctx)
                            .setTitle(result.setActiveOk ? "模式已切换" : "模式已配置")
                            .setMessage(result.setActiveOk
                                    ? "已切换至 " + QtiModeOverride.modeSpec(mode) + "\n\n" + result.diagnosticsSummary
                                    : "已设置 " + QtiModeOverride.QUALCOMM_MODE_OVERRIDE_PROP
                                            + "=" + result.propertyValue + "\n\n"
                                            + result.diagnosticsSummary + "\n\n请重插 Type-C，让高通 composer 以此模式初始化外接显示器。")
                            .setPositiveButton("知道了", null)
                            .show();
                } else {
                    showToast("设置失败，当前属性值: " + current);
                }
            });
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            activity.runOnUiThread(() -> {
                showToast("高通强制模式失败");
                State.log("高通强制模式失败: " + msg);
                new androidx.appcompat.app.AlertDialog.Builder(ctx)
                        .setTitle("高通强制模式失败")
                        .setMessage(msg)
                        .setPositiveButton("知道了", null)
                        .show();
            });
        } finally {
            executor.shutdown();
        }
    });
}

private void updateFloatingBackButtonText(boolean isEnabled) {
    floatingButtonToggle.setText(isEnabled ? "隐藏悬浮返回键" : "展示悬浮返回键");
}
}
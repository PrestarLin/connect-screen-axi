package com.gitee.connect_screen;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserHandleHidden;
import android.permission.IPermissionManager;
import android.view.Display;
import android.view.View;
import android.widget.FrameLayout;

import org.lsposed.hiddenapibypass.HiddenApiBypass;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gitee.connect_screen.job.AcquireShizuku;
import com.gitee.connect_screen.job.DisplayMonitor;
import com.gitee.connect_screen.job.InputDeviceMonitor;
import com.gitee.connect_screen.job.ProjectViaDisplaylink;
import com.gitee.connect_screen.job.DisplaylinkMonitor;
import com.gitee.connect_screen.job.VirtualDisplayArgs;
import com.gitee.connect_screen.shizuku.ServiceUtils;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import java.lang.ref.WeakReference;

import dev.rikka.tools.refine.Refine;
import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity implements IMainActivity {
    public static final String ACTION_USB_PERMISSION = "com.gitee.connect_screen.USB_PERMISSION";
    public static final int REQUEST_CODE_MEDIA_PROJECTION = 1001; // 定义一个请求码

    private BreadcrumbManager breadcrumbManager;

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            android.util.Log.d("MainActivity", "received action: " + intent.getAction());
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                State.resumeJob();
            }
        }
    };

    
    private void onRequestPermissionsResult(int requestCode, int grantResult) {
        if (requestCode == AcquireShizuku.SHIZUKU_PERMISSION_REQUEST_CODE) {
            State.log("Shizuku 权限请求结果: " + (grantResult == PackageManager.PERMISSION_GRANTED ? "已授权" : "被拒绝"));
            State.resumeJob();
        } else {
            State.log("未知 Shizuku 请求代码: " + requestCode);
        }
    }

    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER =
        this::onRequestPermissionsResult;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("");
                android.util.Log.i("MainActivity", "成功添加隐藏API豁免");
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "添加隐藏API豁免失败: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
        if (ShizukuUtils.hasPermission() && State.userService == null) {
            Shizuku.peekUserService(State.userServiceArgs, State.userServiceConnection);
            Shizuku.bindUserService(State.userServiceArgs, State.userServiceConnection);
        }

        // 移除默认的 ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_main);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.app_name);
        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_open_log) {
                State.breadcrumbManager.pushBreadcrumb("运行日志", LogFragment::new);
                return true;
            }
            return false;
        });

        breadcrumbManager = new BreadcrumbManager(this, getSupportFragmentManager(), findViewById(R.id.breadcrumb));
        State.breadcrumbManager = breadcrumbManager;
        breadcrumbManager.setToolbar(toolbar);
        breadcrumbManager.pushBreadcrumb("首页", () -> new HomeFragment());

        // 预测性返回：有下级页面时拦截并弹栈，根层交给系统（有返回动画）
        androidx.activity.OnBackPressedCallback backCallback =
                new androidx.activity.OnBackPressedCallback(false) {
                    @Override
                    public void handleOnBackPressed() {
                        breadcrumbManager.popBreadcrumb();
                    }
                };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
        Runnable syncBack = () -> backCallback.setEnabled(breadcrumbManager.hasBackNavigation());
        breadcrumbManager.setOnNavigationChangedListener(syncBack);
        syncBack.run();

        // 设置 State.currentActivity 为当前的 MainActivity 实例
        State.currentActivity = new WeakReference<>(this);

        // 获取启动 Intent 并打印其 Action 到日志
        Intent intent = getIntent();
        String action = intent.getAction();
        State.log("MainActivity created with action: " + action);

        // 查是否是 USB 设备连接的 Intent
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            DisplaylinkMonitor.onUsbDeviceAttached(device);
            // 处理 USB 设备连接的逻辑
            if (State.displaylinkDeviceName.equals(device.getDeviceName())) {
                State.log("USB 设备已连接: " + device.getDeviceName());
                DisplaylinkPref.load(this);
                State.displaylinkState.virtualDisplayArgs = new VirtualDisplayArgs("DisplayLink", DisplaylinkPref.monitorWidth, DisplaylinkPref.monitorHeight, DisplaylinkPref.refreshRate, DisplaylinkPref.dpi, DisplaylinkPref.rotatesWithContent);
                State.startNewJob(new ProjectViaDisplaylink(device, State.displaylinkState.virtualDisplayArgs, ProjectionMode.SINGLE_APP));
            }
        }

        // 注册 USB 权限广播接收器
        IntentFilter permissionFilter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbPermissionReceiver, permissionFilter, null, null, Context.RECEIVER_EXPORTED);

        // 监听显示器变化
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        DisplayMonitor.init(displayManager);
        InputManager inputManager = (InputManager)getSystemService(Context.INPUT_SERVICE);
        InputDeviceMonitor.init(inputManager);
        DisplaylinkMonitor.init(this);
    }


    @Override
    protected void onResume() {
        super.onResume();
        // 设置 State.currentActivity 为当前的 MainActivity 实例
        State.currentActivity = new WeakReference<>(this);
        State.resumeJob();
        
        // 检查是否需要在应用打开时自动熄屏
        checkAutoScreenOffOnAppOpen();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        State.unbindUserService();
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);

        State.currentActivity = null;
        unregisterReceiver(usbPermissionReceiver);
    }
    
    /**
     * 检查是否需要在应用打开时自动熄屏
     */
    private void checkAutoScreenOffOnAppOpen() {
        // 检查是否启用了"打开应用时自动熄屏"功能
        boolean autoScreenOffOnAppOpen = getSharedPreferences("settings", MODE_PRIVATE)
                .getBoolean("auto_screen_off_on_app_open", false);
        if (!autoScreenOffOnAppOpen) {
            return;
        }
        
        // 检查是否启用了真实熄屏
        boolean useRealScreenOff = getSharedPreferences("settings", MODE_PRIVATE)
                .getBoolean("use_real_screen_off", false);
        if (!useRealScreenOff) {
            return;
        }
        
        // 检查是否有绑定的熄屏屏幕
        String boundDisplayName = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("auto_screen_off_display_name", "");
        if (boundDisplayName.isEmpty()) {
            return;
        }
        
        // 检查是否有权限
        if (!ShizukuUtils.hasPermission() || State.userService == null) {
            return;
        }
        
        // 检查绑定的屏幕是否已连接
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = displayManager.getDisplays();
        boolean boundDisplayConnected = false;
        for (Display display : displays) {
            if (display.getName().equals(boundDisplayName)) {
                boundDisplayConnected = true;
                break;
            }
        }
        
        if (boundDisplayConnected) {
            State.log("应用打开时检测到绑定屏幕 " + boundDisplayName + " 已连接，自动熄屏");
            try {
                State.userService.startListenVolumeKey();
                State.userService.setScreenPower(com.gitee.connect_screen.shizuku.SurfaceControl.POWER_MODE_OFF);
            } catch (Exception e) {
                State.log("应用打开时自动熄屏失败: " + e.getMessage());
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                State.log("用户授予了投屏权限");
                if (MediaProjectionService.instance != null) {
                    MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    State.setMediaProjection(mediaProjectionManager.getMediaProjection(RESULT_OK, data));
                    State.getMediaProjection().registerCallback(new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            super.onStop();
                            State.log("MediaProjection onStop 回调");
                        }
                    }, null);
                    State.resumeJob();
                } else {
                    Intent serviceIntent = new Intent(this, MediaProjectionService.class);
                    serviceIntent.putExtra("data", data);
                    startService(serviceIntent);
                }
            } else {
                MediaProjectionService.isStarting = false;
                State.log("用户拒绝了投屏权限");
                State.resumeJob();
            }
        }
    }

    // 添加一个方法来检查服务是否在运行
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    // 更新日志列表的方法（日志页自行刷新，这里留空实现）
    public void updateLogs() {
    }
} 
package com.gitee.connect_screen;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gitee.connect_screen.shizuku.ShizukuUtils;
import com.taowen.androidchangeresolution.QtiModeOverride;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiagnosticsFragment extends Fragment {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private RadioGroup authModeGroup;
    private TextView diagOutput;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_diagnostics, container, false);

        authModeGroup = view.findViewById(R.id.authModeGroup);
        diagOutput = view.findViewById(R.id.diagOutput);

        int mode = QtiOverride.authMode(requireContext());
        checkMode(mode);

        authModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int selected = checkedId == R.id.modeShizuku ? QtiOverride.MODE_SHIZUKU
                    : checkedId == R.id.modeRoot ? QtiOverride.MODE_ROOT : QtiOverride.MODE_AUTO;
            QtiOverride.setAuthMode(requireContext(), selected);
            State.log("授权模式切换为: " + QtiOverride.authModeName(selected));
        });

        view.findViewById(R.id.btnRunDiagnostics).setOnClickListener(v -> runDiagnostics());

        return view;
    }

    private void checkMode(int mode) {
        int id = mode == QtiOverride.MODE_SHIZUKU ? R.id.modeShizuku
                : mode == QtiOverride.MODE_ROOT ? R.id.modeRoot : R.id.modeAuto;
        RadioButton button = authModeGroup.findViewById(id);
        if (button != null) {
            button.setChecked(true);
        }
    }

    private void runDiagnostics() {
        final Context ctx = requireContext();
        final Activity activity = requireActivity();
        int mode = QtiOverride.authMode(ctx);
        if (mode != QtiOverride.MODE_ROOT) {
            if (!ShizukuUtils.hasPermission() || State.userService == null) {
                Toast.makeText(ctx, "当前授权模式不可用（Shizuku 未授权或未连接）", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        QtiModeOverride.Runner runner = QtiOverride.runnerFor(ctx);
        diagOutput.setText("运行中…");
        executor.execute(() -> {
            try {
                QtiModeOverride.Diagnostics d = QtiModeOverride.diag(ctx, runner);
                String current = QtiModeOverride.currentOverride(ctx, runner);
                String text = buildText(d, current);
                activity.runOnUiThread(() -> diagOutput.setText(text));
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                activity.runOnUiThread(() -> {
                    diagOutput.setText("诊断失败:\n" + msg);
                    State.log("高通诊断失败: " + msg);
                });
            }
        });
    }

    private String buildText(QtiModeOverride.Diagnostics d, String currentOverride) {
        StringBuilder sb = new StringBuilder();
        sb.append("属性 ").append(QtiModeOverride.QUALCOMM_MODE_OVERRIDE_PROP)
                .append(" = ").append(currentOverride.isEmpty() ? "(空)" : currentOverride).append("\n\n");

        sb.append("探针: ").append(d.statusLine()).append("\n");
        if (d.library != null && !d.library.isEmpty()) {
            sb.append("库: ").append(d.library).append("\n");
        }
        if (d.connected != null) {
            sb.append("连接: ").append(d.connectedLine()).append("\n");
        }
        sb.append("配置数: ").append(d.configCountLine()).append("\n");
        sb.append("活动配置: ").append(d.activeConfigLine()).append("\n");
        sb.append("符号: ").append(d.symbolLine()).append("\n");
        sb.append("DRM: ").append(d.drmLine()).append("\n");

        if (!d.configs.isEmpty()) {
            sb.append("\n— 配置 —\n");
            for (QtiModeOverride.QtiConfig config : d.configs) {
                sb.append(config.format(null)).append("\n");
            }
        }
        if (!d.drmConnectors.isEmpty()) {
            sb.append("\n— DRM 连接器 —\n");
            for (QtiModeOverride.DrmConnector connector : d.drmConnectors) {
                sb.append(connector.format()).append("\n");
                for (QtiModeOverride.DrmMode mode : connector.modes) {
                    sb.append("  ").append(mode.format(null)).append("\n");
                }
            }
        }
        if (!d.missingSymbols.isEmpty()) {
            sb.append("\n缺失符号:\n");
            for (String s : d.missingSymbols) {
                sb.append("  ").append(s).append("\n");
            }
        }
        if (!d.loadErrors.isEmpty()) {
            sb.append("\n加载错误:\n");
            for (String s : d.loadErrors) {
                sb.append("  ").append(s).append("\n");
            }
        }
        if (d.extraDiagnostics != null && !d.extraDiagnostics.isEmpty()) {
            sb.append("\n— 系统诊断 —\n").append(d.extraDiagnostics).append("\n");
        }
        return sb.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdownNow();
    }
}
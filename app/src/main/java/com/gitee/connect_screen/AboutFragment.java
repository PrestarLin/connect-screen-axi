package com.gitee.connect_screen;

import static com.gitee.connect_screen.job.AcquireShizuku.SHIZUKU_PERMISSION_REQUEST_CODE;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.gitee.connect_screen.job.FetchLogAndShare;
import com.gitee.connect_screen.shizuku.ShizukuUtils;

import rikka.shizuku.Shizuku;

public class AboutFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_about, container, false);

        TextView aboutContent = view.findViewById(R.id.aboutContent);
        aboutContent.setText("本应用使用了 DisplayLink® 的驱动程序(.so 文件)用于支持 DisplayLink® 设备的连接功能。"
                + "DisplayLink® 是 Synaptics Incorporated 的注册商标。我们仅将其驱动程序用于其预期用途，即支持 DisplayLink® 设备的连接，未对驱动程序进行任何修改。\n\n"
                + "- DisplayLink® 驱动程序的所有权利均属于 Synaptics Incorporated\n"
                + "- 用户在使用 DisplayLink® 相关功能时应遵守 Synaptics Incorporated 的相关许可条款\n"
                + "- 本应用与 Synaptics Incorporated 没有任何官方关联，不代表或暗示存在合作关系\n\n"
                + "如有任何与 DisplayLink® 相关的法律问题，请直接联系 Synaptics Incorporated：www.synaptics.com");

        TextView emailText = view.findViewById(R.id.emailText);
        emailText.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO,
                    Uri.parse("mailto:prestarlin@gmail.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "屏连·副屏 反馈");
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(getContext(), "未找到邮件应用", Toast.LENGTH_SHORT).show();
            }
        });

        TextView versionText = view.findViewById(R.id.versionText);
        try {
            String versionName = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            String androidVersion = android.os.Build.VERSION.RELEASE;
            String commit = BuildConfig.GIT_COMMIT;
            String buildTime = BuildConfig.BUILD_TIME;
            versionText.setText("版本：" + versionName + " · Android " + androidVersion
                    + "\n编译：" + commit + " · " + buildTime);
        } catch (Exception e) {
            versionText.setText("版本：未知");
        }

        // 更新通道选择
        RadioGroup channelGroup = view.findViewById(R.id.updateChannelGroup);
        TextView updateStatus = view.findViewById(R.id.updateStatus);
        Button btnCheck = view.findViewById(R.id.btnCheckUpdate);
        channelGroup.setOnCheckedChangeListener((g, id) -> updateStatus.setText(""));
        btnCheck.setOnClickListener(v -> checkUpdate(channelGroup, updateStatus));

        GestureDetector gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!ShizukuUtils.hasShizukuStarted()) {
                    State.log("shizuku not started");
                    return false;
                }
                if (!ShizukuUtils.hasPermission()) {
                    State.log("ask shizuku permission");
                    Toast.makeText(getContext(), "导出故障日志需要 shizuku 权限", Toast.LENGTH_SHORT).show();
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
                    return false;
                }
                State.startNewJob(new FetchLogAndShare());
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

        View header = view.findViewById(R.id.header);
        header.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });

        return view;
    }

    private void checkUpdate(RadioGroup channelGroup, TextView statusView) {
        boolean isBeta = channelGroup.getCheckedRadioButtonId() == R.id.channelBeta;
        statusView.setText("正在检查…");
        new Thread(() -> {
            try {
                String url = "https://api.github.com/repos/PrestarLin/connect-screen-axi/releases"
                        + (isBeta ? "?per_page=5" : "/latest");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                int code = conn.getResponseCode();
                if (code != 200) {
                    requireActivity().runOnUiThread(() -> statusView.setText("检测失败（HTTP " + code + "）"));
                    return;
                }
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                String json = sb.toString();
                org.json.JSONObject release;
                if (isBeta) {
                    org.json.JSONArray arr = new org.json.JSONArray(json);
                    release = null;
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject r = arr.getJSONObject(i);
                        if (r.optBoolean("prerelease", false)) {
                            release = r;
                            break;
                        }
                    }
                    if (release == null) {
                        requireActivity().runOnUiThread(() -> statusView.setText("未找到测试版"));
                        return;
                    }
                } else {
                    release = new org.json.JSONObject(json);
                }
                String latestCommit = release.optString("target_commitish", "");
                String tagName = release.optString("tag_name", "");
                String htmlUrl = release.optString("html_url", "");
                String currentCommit = BuildConfig.GIT_COMMIT;
                if (latestCommit.equals(currentCommit) || latestCommit.startsWith(currentCommit)
                        || currentCommit.startsWith(latestCommit)) {
                    requireActivity().runOnUiThread(() -> statusView.setText("已是最新版本（" + tagName + "）"));
                } else {
                    final String urlFinal = htmlUrl;
                    requireActivity().runOnUiThread(() -> {
                        statusView.setText("有新版本 " + tagName + "\n点击下载");
                        statusView.setOnClickListener(v -> {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlFinal));
                            startActivity(intent);
                        });
                        statusView.setTextColor(getResources().getColor(R.color.md_primary));
                    });
                }
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                requireActivity().runOnUiThread(() -> statusView.setText("检测失败：" + msg));
            }
        }).start();
    }
}
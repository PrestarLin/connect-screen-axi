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
}
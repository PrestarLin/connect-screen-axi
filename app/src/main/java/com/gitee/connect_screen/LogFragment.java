package com.gitee.connect_screen;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class LogFragment extends Fragment {

    private TextView logText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log, container, false);
        logText = view.findViewById(R.id.logText);

        view.findViewById(R.id.btnRefresh).setOnClickListener(v -> refresh());
        view.findViewById(R.id.btnClear).setOnClickListener(v -> {
            State.logs.clear();
            refresh();
            Toast.makeText(requireContext(), "日志已清空", Toast.LENGTH_SHORT).show();
        });

        refresh();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (logText == null) {
            return;
        }
        if (State.logs.isEmpty()) {
            logText.setText("（暂无日志）");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = Math.max(0, State.logs.size() - 500); i < State.logs.size(); i++) {
                sb.append(State.logs.get(i)).append('\n');
            }
            logText.setText(sb.toString());
        }
        scrollToBottom();
    }

    private void scrollToBottom() {
        try {
            View parent = logText;
            while (parent != null && !(parent instanceof android.widget.ScrollView)) {
                parent = (View) parent.getParent();
            }
            if (parent instanceof android.widget.ScrollView) {
                ((android.widget.ScrollView) parent).post(() ->
                        ((android.widget.ScrollView) parent).fullScroll(View.FOCUS_DOWN));
            }
        } catch (Exception ignored) {
        }
    }
}
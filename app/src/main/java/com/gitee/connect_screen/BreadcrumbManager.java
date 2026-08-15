package com.gitee.connect_screen;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;
import java.util.List;

public class BreadcrumbManager {
    private final LinearLayout breadcrumb;
    private final List<String> navigationPath = new ArrayList<>();
    private final List<FragmentFactory> factoryStack = new ArrayList<>();
    private final FragmentManager fragmentManager;
    private androidx.appcompat.widget.Toolbar toolbar;
    private Runnable onNavigationChanged;

    public BreadcrumbManager(Context context, FragmentManager fragmentManager, LinearLayout breadcrumb) {
        this.breadcrumb = breadcrumb;
        this.fragmentManager = fragmentManager;
    }

    public void setToolbar(androidx.appcompat.widget.Toolbar toolbar) {
        this.toolbar = toolbar;
        toolbar.setNavigationOnClickListener(v -> popBreadcrumb());
    }

    /** 是否有下级页面（用于预测性返回回调开关）。 */
    public boolean hasBackNavigation() {
        return factoryStack.size() > 1;
    }

    public void setOnNavigationChangedListener(Runnable listener) {
        this.onNavigationChanged = listener;
    }

    private void notifyNavigationChanged() {
        if (onNavigationChanged != null) {
            onNavigationChanged.run();
        }
    }

    /**
     * 压入一个新页面。depth 用自定义地管理，不使用 FragmentManager 的退栈，
     * 避免 popBackStack() 清空整条栈或刷新时重建到陈旧的页面。
     */
    public void pushBreadcrumb(String newPath, FragmentFactory fragmentFactory) {
        try {
            if (!newPath.isEmpty() && !navigationPath.contains(newPath)) {
                navigationPath.add(newPath);
            }
            factoryStack.add(fragmentFactory);
            showTop();
        } catch (Throwable e) {
            // ignore
        }
    }

    /**
     * 回退一层。始终以 factoryStack 栈顶为准重建页面，保证回到正确的上一页。
     */
    public void popBreadcrumb() {
        try {
            if (factoryStack.size() > 1) {
                navigationPath.remove(navigationPath.size() - 1);
                factoryStack.remove(factoryStack.size() - 1);
                showTop();
            } else {
                Fragment current = fragmentManager.findFragmentById(R.id.fragmentContainer);
                if (current != null && current.getActivity() != null) {
                    current.getActivity().finish();
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * 刷新当前页面（例如 Shizuku 授权、任务完成、从其它界面返回后）。
     * 无论之前访问过哪个页面，都只重建当前栈顶，避免跳到陈旧的页面。
     */
    public void refreshCurrentFragment() {
        try {
            if (factoryStack.isEmpty()) {
                return;
            }
            if (State.currentActivity == null || State.currentActivity.get() == null) {
                return;
            }
            showTop();
        } catch (Exception e) {
            // ignore
        }
    }

    private void showTop() {
        if (factoryStack.isEmpty()) {
            return;
        }
        Fragment fragment = factoryStack.get(factoryStack.size() - 1).createFragment();
        fragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
        updateBreadcrumbView();
        notifyNavigationChanged();
    }

    private void updateBreadcrumbView() {
        if (toolbar != null) {
            boolean hasBack = navigationPath.size() > 1;
            toolbar.setNavigationIcon(hasBack ? R.drawable.ic_back : null);
            String last = navigationPath.isEmpty() ? "" : navigationPath.get(navigationPath.size() - 1);
            toolbar.setTitle("首页".equals(last) ? "屏连·副屏" : last);
        }

        breadcrumb.removeAllViews();

        for (int i = 0; i < navigationPath.size(); i++) {
            TextView separator = new TextView(breadcrumb.getContext());
            separator.setText(" > ");
            breadcrumb.addView(separator);

            TextView pathView = new TextView(breadcrumb.getContext());
            pathView.setText(navigationPath.get(i));
            pathView.setTextColor(breadcrumb.getContext().getResources().getColor(R.color.blue));
            final int index = i;
            pathView.setClickable(true);
            pathView.setOnClickListener(v -> {
                while (navigationPath.size() > index + 1) {
                    popBreadcrumb();
                }
            });
            breadcrumb.addView(pathView);
        }
    }

    public LinearLayout getBreadcrumbView() {
        return breadcrumb;
    }

    public interface FragmentFactory {
        Fragment createFragment();
    }
}
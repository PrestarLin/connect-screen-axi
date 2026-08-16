package com.gitee.connect_screen;

import android.content.Context;
import android.widget.LinearLayout;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 页面导航管理：以工厂栈方式维护页面层级，push 进入、pop 返回。
 * 不负责顶部工具栏标题/返回键（已统一为固定应用名 + 日志按钮）。
 */
public class BreadcrumbManager {
    private final LinearLayout breadcrumb;
    private final List<String> navigationPath = new ArrayList<>();
    private final List<FragmentFactory> factoryStack = new ArrayList<>();
    private final FragmentManager fragmentManager;
    private Runnable onNavigationChanged;
    private boolean isPop = false;
    private boolean noAnimation = false;

    public BreadcrumbManager(Context context, FragmentManager fragmentManager, LinearLayout breadcrumb) {
        this.breadcrumb = breadcrumb;
        this.fragmentManager = fragmentManager;
    }

    /** 当前导航页标题（用于判重，如日志页去重）。 */
    public String getCurrentTitle() {
        return navigationPath.isEmpty() ? "" : navigationPath.get(navigationPath.size() - 1);
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

    /** 压入一个新页面。 */
    public void pushBreadcrumb(String newPath, FragmentFactory fragmentFactory) {
        try {
            if (!newPath.isEmpty() && !newPath.equals(getCurrentTitle())) {
                navigationPath.add(newPath);
            }
            factoryStack.add(fragmentFactory);
            isPop = false;
            showTop();
        } catch (Throwable e) {
            // ignore
        }
    }

    /** 回退一层。 */
    public void popBreadcrumb() {
        try {
            if (factoryStack.size() > 1) {
                if (!navigationPath.isEmpty()) {
                    navigationPath.remove(navigationPath.size() - 1);
                }
                factoryStack.remove(factoryStack.size() - 1);
                if (navigationPath.isEmpty()) {
                    navigationPath.add("首页");
                }
                isPop = true;
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

    /** 刷新当前页面。 */
    public void refreshCurrentFragment() {
        try {
            if (factoryStack.isEmpty()) {
                return;
            }
            if (State.currentActivity == null || State.currentActivity.get() == null) {
                return;
            }
            noAnimation = true;
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
        androidx.fragment.app.FragmentTransaction ft = fragmentManager.beginTransaction();
        if (noAnimation) {
            ft.setCustomAnimations(0, 0);
        } else if (isPop) {
            ft.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
        } else {
            ft.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);
        }
        ft.replace(R.id.fragmentContainer, fragment);
        ft.commit();
        notifyNavigationChanged();
        isPop = false;
        noAnimation = false;
    }

    public LinearLayout getBreadcrumbView() {
        return breadcrumb;
    }

    public interface FragmentFactory {
        Fragment createFragment();
    }
}
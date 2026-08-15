# 屏连·副屏 (Connect Screen · Axi)

基于 [connect-screen](https://github.com/PrestarLin/connect-screen)（1.3.3 分支）扩展的副屏投屏应用。
UI 参考「副屏·阿西西」的卡片风格，融合副屏启动应用、全屏投屏与显示设置。

## 功能

- 首页卡片式入口：单应用副屏 / 全屏投屏 / 显示设置 + 功能宫格（屏幕列表、DisplayLink、无线投屏、触控板、设置、关于）
- 副屏应用抽屉：4 列图标网格，点击投屏到指定显示器，长按回手机主屏
- 显示设置：分辨率 / DPI / 刷新率 / 旋转 / 显示模式（Shizuku）
- 投屏：单应用投屏、桥接、镜像、DisplayLink、无线投屏
- 触控板、模拟/真实熄屏、悬浮返回键、Shizuku 管理

## 构建

依赖 Android SDK（compileSdk 34、build-tools 36.1.0）、Gradle Wrapper 8.7、JDK 17。

```sh
./gradlew :app:assembleDebug
```

CI 在推送到 `main` 时自动构建并发布到 GitHub Releases。

## 说明

- 已移除 termux-x11 依赖，构建自包含。
- 本应用使用 DisplayLink® 驱动（.so）仅用于兼容 DisplayLink 设备，与 Synaptics 无官方关联。
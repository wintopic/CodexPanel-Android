# CodexPanel Android

CodexPanel Android 是 CodexPanel 的安卓远控客户端。App 名称为 **CodexPanel**，用于在手机上打开并操控 CodexPanel 的 Cloudflare 远端控制台。

## 工作原理

本项目采用原生 Android WebView 容器方案，直接复用 CodexPanel WAN 远端页面能力，而不是重新实现一套远控协议或界面。

这样做的目的很明确：

- 远端网页新增功能后，Android App 可以最大程度保持兼容。
- 文本发送、文件选择、图片上传、会话操作等能力由远端页面统一承载。
- App 只负责移动端容器、配置保存、权限桥接和系统集成。

## 支持能力

- 保存 Cloudflare 服务地址、设备路径、远控密钥。
- 支持粘贴完整远控入口，例如 `https://codexpanel-wan.pages.dev/remote/win/?token=******`。
- 自动解析完整远控入口中的服务地址、设备路径和 token。
- 使用 Android WebView 打开远控页面。
- 支持 JavaScript、Cookie、localStorage、文件选择、图片上传。
- 支持摄像头、麦克风等 WebView 权限请求。
- 支持 Android 返回键返回网页历史。
- 支持从系统浏览器分享或打开远控链接到 App。

## 安全说明

远控密钥不会写入源码、不会写入安装包、不会提交到 GitHub。

用户在 App 内填写的 Cloudflare 服务地址、设备路径、远控密钥只保存在 Android App 私有数据中。卸载 App 后，这些配置会随 App 数据一起删除。

## 使用方法

1. 安装 Release 页面中的 APK。
2. 首次打开 CodexPanel。
3. 填入完整远控入口，或分别填写：
   - Cloudflare 服务地址，例如 `https://codexpanel-wan.pages.dev`
   - 设备路径，例如 `/remote/win/`
   - 远控密钥
4. 点击“保存并打开”。

## 与 CodexPanel WAN 的关系

广域网远控服务的使用和搭建方法请参考：

[wintopic/CodexPanel-WAN](https://github.com/wintopic/CodexPanel-WAN)

Android App 本身不是被控端，不运行本地 sidecar，也不负责启动桌面端服务。被控端仍然是 Windows、Linux 或 macOS 上运行的 CodexPanel 桌面客户端。

## 构建

本仓库使用 GitHub Actions 自动构建发布 APK。本地开发需要：

- JDK 17
- Android SDK
- Gradle 8.10.2 或兼容版本

本地构建命令：

```bash
gradle assembleRelease
```

生成文件位于：

```text
app/build/outputs/apk/release/app-release.apk
```

当前 Release APK 使用调试签名，适合直接侧载安装和功能验证。后续如需发布到应用商店，应替换为正式签名。

## 自动发布

推送到 `main` 后，GitHub Actions 会自动：

1. 安装 Java、Android SDK 和 Gradle。
2. 构建 Release APK。
3. 读取 `app/build.gradle` 中的 `versionName`。
4. 发布或更新对应版本的 GitHub Release。
5. 上传 `CodexPanel-Android-v版本号.apk`。


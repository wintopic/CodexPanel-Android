# CodexPanel Android

CodexPanel Android 是 CodexPanel 的安卓远控客户端。App 名称为 **CodexPanel**，用于在手机上打开并操控 CodexPanel 的 Cloudflare 远端控制台。

## 工作原理

本项目采用原生 Android 控制台方案，不使用 WebView。App 直接调用 CodexPanel 桌面端通过 CodexPanel WAN 暴露的远控 HTTP API。

这样做的目的很明确：

- 文本发送、图片上传、会话操作、停止生成、线程管理等能力由原生组件触发。
- Cloudflare WAN 仍然只负责透明中转，真正控制 Codex Desktop 的仍然是电脑端 CodexPanel。
- Android App 负责移动端原生界面、配置保存、线路请求、附件读取和状态轮询。

## 支持能力

- 保存 Cloudflare 服务地址、设备路径、远控密钥。
- 支持粘贴完整远控入口，例如 `https://codexpanel-wan.pages.dev/remote/win/?token=******`。
- 自动解析完整远控入口中的服务地址、设备路径和 token。
- 原生线程列表、聊天记录、发送框、快捷操作。
- 支持发送文本和图片附件。
- 支持停止生成、新建线程、选择线程。
- 支持置顶、取消置顶、重命名、归档线程。
- 支持模型切换、推理模式切换。
- 支持 Slash command 和 Codex App 快捷命令。
- 支持服务诊断。
- 支持 Android 原生文件选择。
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

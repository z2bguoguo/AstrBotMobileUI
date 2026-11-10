<div align="center">

<img src="assets/banner.svg" width="740" alt="小猫举牌：AstrBot Mobile UI" />

**随身掌控你的 AstrBot**

[English](README.en.md) · **简体中文**

[![Quality](https://github.com/z2bguoguo/AstrBotMobileUI/actions/workflows/quality.yml/badge.svg)](https://github.com/z2bguoguo/AstrBotMobileUI/actions/workflows/quality.yml)
[![Android](https://img.shields.io/badge/Android-8.0%2B-5769dd)](https://github.com/z2bguoguo/AstrBotMobileUI/releases)
[![AGPL-3.0](https://img.shields.io/badge/license-AGPL--3.0-747b95)](LICENSE)

[下载安装](https://github.com/z2bguoguo/AstrBotMobileUI/releases) · [使用文档](https://z2bguoguo.github.io/AstrBotMobileUI/) · [反馈问题](https://github.com/z2bguoguo/AstrBotMobileUI/issues)

</div>

AstrBot Mobile UI 是面向 Android 的社区原生管理客户端，让连接、配置与维护多个 AstrBot 实例成为便捷的移动工作流。界面采用 Android 原生控件，通过服务器 API 完成操作。

### 为移动管理而设计

- **多个工作空间** — 保存多个连接与密码，按分组管理，快速复制和切换。
- **跟随服务器能力** — 优先读取 OpenAPI；缺少契约的旧版使用 WebUI 接口目录。功能分类、参数说明与枚举建议跟随已知契约。
- **直观的设置** — 使用开关、枚举选择、分组表单与逐项列表调整配置，修改暂存草稿，确认后保存。
- **从容编辑长文本** — 全屏编辑提示词与 MCP JSON，提供查找替换、格式化、字段表单和可选补全。
- **复用配置** — 将请求保存为本机加密预设，在其他实例加载并确认执行。
- **可扩展** — 导入声明式客户端插件，增加原生操作入口；支持独立嵌入服务器提供的令牌式 HTML 插件页面。
- **双语与个性化** — 中英文切换、浅深色外观、自定义主题色与字段说明。
- **脚本核心** — 完整 APK 内置业务与原生界面脚本，布局、点击规则和状态展示可随签名核心更新；底层架构变化再安装 APK。
- **持续更新** — 启动自动检查、可选自动下载，支持正式版与预发布渠道，校验安装包后由系统确认安装。
- **轻量与本地优先** — 无广告、无统计 SDK，使用 Android Keystore 加密连接及预设。

### 项目状态

当前为 **0.3 开发预览版**。已经提供原生设置、资源管理列表与连接管理；尚不能宣称达到 AstrBot WebUI 全功能等价。操作目录中的接口不代表每项都完成端到端验证。专用聊天流式协议、复杂文件/备份流程、插件文件与流式桥接以及旧版本实机矩阵仍在开发，详见 [功能边界](https://z2bguoguo.github.io/AstrBotMobileUI/guide/features)。

需要 Android 8.0 或更高版本，以及可访问的 AstrBot 服务。推荐 HTTPS。发布包使用维护者配置的持久签名；预览版本默认标记为 Pre-release。

### 社区

欢迎通过 Issues 报告问题，通过 Pull Requests 改进兼容性、原生交互和文档。贡献说明见 [CONTRIBUTING.md](CONTRIBUTING.md)。本项目与 AstrBot 官方无隶属关系。

以 [AGPL-3.0](LICENSE) 发布。第三方来源见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

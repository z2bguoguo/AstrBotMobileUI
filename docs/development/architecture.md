# 架构与构建

项目使用 Java 17、Android 平台原生 View 与 Gradle。运行时没有 UI 框架、统计 SDK 或第三方 HTTP 库。网络操作在单线程执行器运行；界面页面代次阻止过期请求更新新页面。离开/销毁页面可取消网络连接。

- `core/Connection`：连接模型及 URL 边界。
- `core/ApiClient`：能力探测、登录、权限错误及单次请求执行。
- `core/Endpoint` / `Schema`：契约到原生参数页的映射。
- `core/Helpers`：脱敏、输入建议和请求预设。
- `ui/Vault`：Android Keystore AES-GCM 存储。
- `ui/MainActivity`：原生工作空间、编辑器、预设和插件管理。
- `ui/PluginPageActivity`：隔离的服务器 HTML 页面。
- `app/src/main/assets`：已知契约与旧接口基线。
- `docs`：由 VitePress / Vite 构建的中英文文档站。

## 本地开发

安装 JDK 17、Android SDK 35 与 Node.js 22。在项目根目录运行：

```sh
./gradlew :app:spotlessApply
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
npm ci
npm run docs:build
python3 scripts/check_repository.py
```

不要用构建成功来代替功能测试。接口增加后补充真实脱敏 fixture、参数转换与失败用例。动态插件不下载 DEX 或运行任意 Java 代码。

# 签名与发布

## 一次性配置

持久 Android 签名密钥不能写入公开仓库，也不能每次构建随机生成。维护者在可信机器安装 JDK 和已登录的 GitHub CLI，然后运行：

```sh
./scripts/setup-signing.sh z2bguoguo/AstrBotMobileUI
```

Windows 可直接运行：

```bat
scripts\setup-signing.bat z2bguoguo/AstrBotMobileUI
```

BAT 是单文件，可下载后双击，默认目标就是本仓库。需要 JDK 17+（也支持 Android Studio 自带的 jbr）和 GitHub CLI；先执行 `gh auth login` 登录有仓库管理权限的账户。无需 OpenSSL 或 Git Bash。

默认密钥目录为 `%USERPROFILE%\.astrbot-mobile-signing`，可用 `ASTRBOT_SIGNING_DIR` 覆盖。首次成功后不要重新生成；网络或权限导致上传中断时，使用 `scripts\setup-signing.bat z2bguoguo/AstrBotMobileUI --reuse` 重新上传同一把本地密钥，脚本会先验证密码和别名。

脚本会在仓库外创建密钥，并将以下配置通过标准输入写入 GitHub Actions secrets；不把秘密打印到日志：

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 持久 JKS 的 Base64 |
| `ANDROID_STORE_PASSWORD` | Store 密码 |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key 密码 |

妥善离线备份密钥和密码，丢失后不能为已安装用户签出可覆盖更新的包。只运行一次；脚本检测本地同名文件并拒绝覆盖，维护者不要通过其他方式重置已有发布密钥。

## 发布策略

所有 push / PR 执行质量检查。`main` 的成功流水线自动构建发布，默认 Pre-release。手动运行 `Quality`，填写 `stable=true` 和 `version`（如 `0.1.0`）可发布正式版本；仍需要通过测试、规范、文档与敏感信息检查。

发布 job 缺少任何签名 Secret 会立即失败，**不生成临时签名、不降级 debug 发布**。包名固定，`versionCode` 使用同一工作流递增的 run number，以支持后续版本覆盖。

安装更新通过下载新版 APK 交给系统完成；本版本未实现应用内自动更新检查。

## GitHub Pages

维护者需在仓库 Settings → Pages 选择 **GitHub Actions** 作为 Source。工作流构建 VitePress，上传并部署 `docs/.vitepress/dist`。预期地址为 `https://z2bguoguo.github.io/AstrBotMobileUI/`。

仓库当前是否公开以及账户是否允许私有仓库 Pages，取决于仓库设置与 GitHub 套餐。工作流不能绕过这些限制。

## 应用内更新清单

发布流程从最终签名 APK 生成 `update-<versionCode>.json`，与 APK 一起上传 Release，包含版本号、版本名称、下载 URL、字节大小与 SHA-256。客户端按 Android versionCode 选取比本机高的版本，排除草稿，并按用户渠道过滤预发布。清单版本号必须与安装包一致。不要替换已发布的 APK 或重置版本号。应用不保存 GitHub Token；内置更新需要仓库和 Release 公开。手动安装旧版仍可行，但更新器不会推荐降级。

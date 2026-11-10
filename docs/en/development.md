# Build and release

Use JDK 17, Android SDK 35 and Node 22. Run `./gradlew :app:spotlessCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`, then `npm ci && npm run docs:build`. Run repository checks with `python3 scripts/check_repository.py`.

The `core` package contains connection validation, transport, authentication, contract interpretation and presets. `ui` uses Android platform views. Credentials are stored through Android Keystore AES-GCM. Only server HTML views use WebView.

## Persistent signing

Run `./scripts/setup-signing.sh z2bguoguo/AstrBotMobileUI` once on a trusted machine with JDK and authenticated GitHub CLI. On Windows, use `scripts\setup-signing.bat z2bguoguo/AstrBotMobileUI` (standalone BAT; no OpenSSL or Git Bash required). It uses `%USERPROFILE%\.astrbot-mobile-signing` by default, also finds Android Studio jbr, and supports `--reuse` to retry uploading the same verified key after a partial failure. Back up the generated JKS and passwords offline. The script uploads `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` as repository secrets without printing values. Never regenerate the key after publishing.

Successful main-branch runs publish pre-releases by default. To publish a stable release, dispatch `Quality` with `stable=true` and a semantic version. Signing is mandatory, the package ID stays fixed and the workflow run number is the version code. Missing secrets stop publishing instead of using a temporary key.

Enable GitHub Actions as the repository Pages source. The VitePress/Vite site deploys to `/AstrBotMobileUI/`. Private-repository Pages availability depends on GitHub settings and plan.

## Validation scope

CI runs JVM tests, Spotless, Android Lint, debug builds, docs, repository consistency checks and a full-history Gitleaks scan. These do not establish full WebUI parity or replace testing on representative AstrBot versions and Android devices. Test login, scopes, two-factor flow, server failures, cancellation, subpath proxies, plugin views, presets and signed upgrade installation before promoting a release.

# Build and release

Use JDK 17, Android SDK 35 and Node 22. Run `./gradlew :app:spotlessCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`, then `npm ci && npm run docs:build`. Run repository checks with `python3 scripts/check_repository.py`.

The `core` package contains connection validation, transport, authentication, contract interpretation and presets. `ui` uses Android platform views. Credentials are stored through Android Keystore AES-GCM. Only server HTML views use WebView.

## Persistent signing

Run `./scripts/setup-signing.sh z2bguoguo/AstrBotMobileUI` once on a trusted machine with JDK and authenticated GitHub CLI. On Windows, use `scripts\setup-signing.bat z2bguoguo/AstrBotMobileUI` (standalone BAT; no OpenSSL or Git Bash required). It uses `%USERPROFILE%\.astrbot-mobile-signing` by default, also finds Android Studio jbr, and supports `--reuse` to retry uploading the same verified key after a partial failure. Back up the generated JKS and passwords offline. The script uploads `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` as repository secrets without printing values. Never regenerate the key after publishing.

Successful main-branch runs publish pre-releases by default. To publish a stable release, dispatch `Quality` with `stable=true` and a semantic version. Signing is mandatory, the package ID stays fixed and the workflow run number is the version code. Missing secrets stop publishing instead of using a temporary key.

Enable GitHub Actions as the repository Pages source. The VitePress/Vite site deploys to `/AstrBotMobileUI/`. Private-repository Pages availability depends on GitHub settings and plan.

## Validation scope

CI runs JVM tests, Spotless, Android Lint, debug builds, docs, repository consistency checks and a full-history Gitleaks scan. These do not establish full WebUI parity or replace testing on representative AstrBot versions and Android devices. Test login, scopes, two-factor flow, server failures, cancellation, subpath proxies, plugin views, presets and signed upgrade installation before promoting a release.

## Update release metadata

The release job generates `update-<versionCode>.json` from the final signed APK and uploads it alongside the APK. It contains the Android version code, display version, download URL, byte size and SHA-256. The app selects higher version codes, filters drafts/previews, verifies metadata against the archive and compares current signing certificates. Keep version codes increasing and never replace a published APK. Built-in updates require public repository/releases access; no GitHub token is embedded in the client.

## Script core and architecture versions

Every release still builds a complete APK containing the latest JavaScript core and bilingual data. A fingerprint over native Java, Manifest, resources and build configuration identifies the architecture. Compatible higher core versions download a signed JSON bundle from Releases and reload the UI without reinstalling; a different fingerprint requires the full APK. Quality run numbers identify both core versions and APK version codes.

`core/main.js` owns management request plans, response adapters, OpenAPI forms, metadata/default generation, draft differences and input assistance. `locales.json` and `fields.json` carry bilingual UI and field data. Native Android retains widgets, lifecycle, transport/authentication, encrypted storage, exact numeric validation, files, trust checks and installation. Rhino receives only JSON, with Java access disabled and an instruction deadline. No downloaded DEX is executed.

Core payloads are signed with the APK's persistent RSA key. The installed APK certificate verifies the signature and binds architecture and version before atomic staging. Invalid signatures, downgrades, incompatible architecture and malformed cores are rejected. One previous generation is retained, with a manual rollback action and automatic recovery when a newly activated core fails before the home screen. The embedded core is the final fallback. Connection data is retained. See the [script core reference](/development/script-core) for the JSON boundary and development details.

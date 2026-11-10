# AstrBot Mobile UI — agent instructions

## Product and scope

Independent native Android AstrBot administration client, Java 17, minSdk 26, target/compile SDK 35. Keep public documentation formal, bilingual and explicit about unfinished parity. API catalogue coverage is not feature verification. Do not claim all WebUI functionality or historical compatibility without real server evidence.

## Source map

- app/src/main/java/io/github/astrbot/mobile/core: pure Java connection model, HTTP transport, contract parsing, schema, auth, presets.
- app/src/main/java/io/github/astrbot/mobile/ui: platform-native views, encrypted Vault, plugin WebView.
- app/src/main/assets: bundled OpenAPI contract and legacy operation catalogue; source revision in docs/reference/upstream.md.
- app/src/test: JVM behavioral tests.
- docs: bilingual VitePress documentation, built by Vite.
- examples: importable declarative client plugin manifests.
- scripts: repository checks, signing setup.
- .github/workflows/quality.yml: push/PR checks and gated main release/Pages.

## Non-negotiable invariants

1. Never log, commit or export real credentials or signing material. Saved connections/presets use Android Keystore AES-GCM. Never silently reset an unreadable vault.
2. Keep package ID and release signing key stable. Release must fail without persistent signing secrets; no debug or random key fallback. Version code must increase.
3. Do not auto-retry writes on a different API. Capability downgrade only follows read-only OpenAPI 404/405, never 401/403/500.
4. Keep tokens and cookies scoped to one connection. Switching clears suggestion candidates. Network calls stay off the main thread, responses cannot update stale pages.
5. URL parameters must be encoded; no remote-origin credentials. WebView gets no global JWT or unrestricted native bridge, no TLS bypass, file access or arbitrary navigation.
6. Client plugins are versioned declarative JSON, no downloaded DEX execution. Group ID checks remain advisory.
7. Keep Chinese and English README links and documentation in sync. README presents the product; put build tutorials and release detail in the wiki.

## Validation

Run `./gradlew :app:spotlessApply` before committing Java, then `./gradlew :app:spotlessCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
Run `npm ci && npm run docs:build`, `python3 scripts/check_repository.py` and `git diff --check`. CI also scans secrets. Meaningful new behavior needs failure and isolation tests; don't merely assert implementation constants.

No production AstrBot instance is bundled. Record actual tested backend/device versions. Update the coverage matrix rather than hiding gaps. Avoid destructive live-server writes in tests.

## Known work still needed

Dedicated native chat and streaming protocol, complex uploads/backup workflows, HTML file/stream bridge actions, old-version fixture matrix, device instrumentation and pixel review. Generic route forms must not be labelled complete native WebUI parity.

## Repository workflow

Work only in z2bguoguo/AstrBotMobileUI. Preserve user changes. Use standard AGENTS.md spelling; agent.md points here. No upstream AstrBot writes are authorized. Contributions may use repository-local PRs when explicitly requested; normal authorized delivery can commit directly.

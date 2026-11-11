# AstrBot Mobile UI — agent instructions

## Product and scope

Independent native Android AstrBot administration client, Java 17, minSdk 26, target/compile SDK 35. Keep public documentation formal, bilingual and explicit about unfinished parity. API catalogue coverage is not feature verification. Do not claim all WebUI functionality or historical compatibility without real server evidence.

## Source map

- app/src/main/java/io/github/astrbot/mobile/core: pure Java connection model, HTTP transport, contract parsing, schema, auth, presets.
- app/src/main/java/io/github/astrbot/mobile/ui: platform-native views, SettingsEditor, encrypted Vault, plugin WebView.
- core/FormModel and core/ManagementApi: detached typed drafts and native management workflows with explicit legacy payloads.
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
6. Client plugins are versioned declarative JSON. Only maintainer-signed core scripts may replace business logic; never allow downloaded DEX or untrusted Java access. Group ID checks remain advisory.
7. Keep Chinese and English README links and documentation in sync. README presents the product; put build tutorials and release detail in the wiki.

8. Screenshots and screen recording must remain allowed for troubleshooting. Do not set FLAG_SECURE. Ordinary management pages use settings controls; HTTP methods and paths belong only in advanced diagnostic tools.

## Validation

Run `./gradlew :app:spotlessApply` before committing Java, then `./gradlew :app:spotlessCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
Run `npm ci && npm run docs:build`, `python3 scripts/check_repository.py` and `git diff --check`. CI also scans secrets. Meaningful new behavior needs failure and isolation tests; don't merely assert implementation constants.

No production AstrBot instance is bundled. Record actual tested backend/device versions. Update the coverage matrix rather than hiding gaps. Avoid destructive live-server writes in tests.

## Known work still needed

Dedicated native chat and streaming protocol, complex uploads/backup workflows, HTML file/stream bridge actions, old-version fixture matrix, device instrumentation and pixel review. Generic route forms must not be labelled complete native WebUI parity.

## Repository workflow

Work only in z2bguoguo/AstrBotMobileUI. Preserve user changes. Use standard AGENTS.md spelling; agent.md points here. No upstream AstrBot writes are authorized. Contributions may use repository-local PRs when explicitly requested; normal authorized delivery can commit directly.

## App updater

`core/AppUpdate` selects release manifests by strictly increasing Android version code and channel. `ui/AppUpdater` performs foreground checks/downloads, SHA-256 and package/signature verification, then hands installation to Android with an explicit user confirmation. `ui/UpdateProvider` exposes only the fixed verified APK through temporary read grants. Never attach AstrBot credentials to updater requests. Startup checks are throttled to once per day; auto-download is opt-in. `scripts/create_update_manifest.py` publishes metadata from the final signed release APK. Public GitHub access is required; do not embed a repository token in the app. Keep download bounds and HTTPS enforcement. Screenshots stay enabled.

## Script core and presentation

`assets/core/main.js` is the primary business logic for management route plans, response adaptation, schema generation, draft comparisons and suggestions. Preserve its JSON ABI. `CoreRuntime` is an interpreted JSON-only boundary; `ScriptJson` protects exact numbers. Android widgets/transport/storage/trust remain native. `SignedCore` verifies exact signed payload bytes using the installed APK public key. `CoreStore` handles atomic staging, bootstrap confirmation and rollback. Native source/resource/build changes automatically change the architecture hash; compatible scripts alone may update without an APK. Every commit still builds a complete APK with current scripts. Never run unsigned scripts from a branch URL.

`Appearance` applies local language/theme/accent choices; `core/locales.json` and `core/fields.json` ship bilingual copy and metadata. `scripts/build_locales.py` regenerates UI and bundled-operation labels from committed TSV sources. Never translate input values, config JSON or user prompts. Unknown external metadata must retain original identifiers and server text. Run Node script tests, Python manifest/core tests, JVM tests, formatter/lint/build and device tests for changes. Keep docs bilingual and describe server permission limits explicitly.

Navigation items must activate across their entire row, including title, subtitle, icon, padding and arrow. Decorative descendants must not be clickable, focusable or long-clickable; child tooltips can consume touch events. Keep help and switches as separate intentional controls. Regression tests must dispatch real touch events over descendants rather than only calling performClick on the row.

`status.sections` in the script core owns dashboard grouping and metric classification. `StatusDashboard` renders native progress bars and `StatusFormat` formats duration, date/time, counts and explicit units. Preserve parent context. Legacy memory process/system values are MiB and system means capacity, not used RAM. Do not invent units or zero values for missing metrics. Format timestamps in the device time zone. `scripts/dev/field-names.json` supplies bilingual fallback labels (including legacy root configuration groups) and is merged into `fields.json` by `build_locales.py`; never translate saved data keys or values.


## Native UI ABI 1

`assets/core/ui.js` returns JSON widget trees and handles presentation/events. `NativeViews` is the bounded native renderer for the existing widget/event ABI. Screen shells, common controls, row hit areas, settings rows, status pages, connection editing/lists, menus, appearance and long-text actions belong in script modules. Add script handlers via `CoreModules`; signed JSON resources are available through `CoreData`. CoreSource loads main.js first, then sorted additional JS files. The publisher and embedded loader include all JS/JSON modules automatically.

Do not add native layout branches for changes expressible through the existing widget ABI. Keep only generic rendering, platform operations and compatibility/transport adapters in Java. Preserve typed host slot IDs when modifying shared templates. Single-action rows use atomic=true; independent help, switches or actions must not be disabled by an atomic parent. User input never passes through label translation. Native exact JSON parsing, credential scope, write confirmations, signature verification and installation remain security boundaries.


## Reviewed field help

`core/help.js` enriches `form.metadata` through the raw metadata alias without changing types, defaults, options, constraints or saved values. Full-path matches precede leaf aliases. Preserve newer or custom server hints; unknown plugin semantics must be identified as undocumented. `scripts/dev/help-source.json` pins reviewed upstream evidence, `help-authored.json` holds editorial corrections/examples, and `build_help.py` emits bilingual help keys into `fields.json` plus the `help.json` index. Never replace semantic explanations with type-only padding to satisfy coverage. `build_locales.py` regenerates help too; repository checks reject missing inventory keys, missing English explanations and stale generated output. Coverage counts keys including aliases, not verified WebUI features or unknown plugins. Keep the description button left of the title with its independent hit target. Palette swatches must use literal backgrounds instead of theme-mapped default-accent glyphs.

## Foreground read subscriptions

`core/ReadWatch` is a bounded GET/SSE primitive: each watch forks the current connection transport with its cookie jar and captured credentials, cancels its own network work, backs off reconnects, and only follows read-only 404/405 fallbacks. `SseReader` bounds each line/event; never use the bounded JSON downloader for endless streams. MainActivity binds watch callbacks to page generation, client identity and watch identity; stop watches on page changes/onStop and restore only retained foreground subscriptions onResume. One-shot subscriptions must be removed after completion. Never retry writes through this engine.

`core/monitor.js` owns refresh preferences, log normalization/deduplication/filtering/bounds, market normalization/search/paging and install plans. Raw logs and plugin-provided content must use literal native text. Do not persist logs or share module caches across connection openings. Preserve exact event-cursor strings across ScriptJson number wrappers. Installing a market plugin must retain server version checks and show a meaningful confirmation with target instance and repository. Do not label cached listings or missing compatibility metadata as verified compatibility.

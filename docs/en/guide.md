# User guide

## Connect

Install a signed release APK on Android 8.0+. Add a connection using the WebUI base URL, such as `https://bot.example.com` or `https://example.com/astrbot`. Do not include a hash route or query string. Save a username/password, or an API key with appropriate scopes. The API key takes priority.

Current servers use plain-password login over HTTPS. Select MD5 only for older servers that explicitly require it. If two-factor authentication is enabled, enter the code after the initial attempt and connect again.

OpenAPI is fetched from `/api/v1/openapi.json`. Only a 404/405 selects the legacy catalogue. An internal error, invalid TLS or proxy HTML does not trigger downgrade. Failed mutations are never automatically replayed against another route.

## Workspaces and presets

Connections are grouped and searchable. Duplicating a connection creates a distinct identity. Credentials and presets are encrypted on-device with Android Keystore; uninstalling loses them.

Open Manage and select general configuration, profiles, personas, model providers, MCP, bot platforms or plugins. Settings load existing values into native switches, choices, numeric inputs and nested lists. Changes remain in a draft until Save is confirmed. The full API browser is available under Tools → Advanced tools. Full-screen editing supports find, replace-all, formatting and restoring the original text. Input validation warns about potentially incomplete group IDs or URLs without blocking submission.

Save a request as a preset, connect to the destination instance, then load it from the preset library. Review resource identifiers and version-dependent fields. Secret-like fields are blanked unless explicitly retained. Cross-version request conversion and unattended group writes are not implemented.

Screenshots and screen recording are allowed for troubleshooting. Error dialogs can copy diagnostic details.

## Limits

The developer preview provides a generic native API workspace, not complete dedicated WebUI-equivalent screens. Streaming chat/SSE/WebSocket workflows, complex backup/file protocols, HTML file/stream bridge actions and comprehensive old-server validation remain unfinished. Uploads are limited to 8 MiB and responses to 16 MiB. Use WebUI for larger files or unsupported workflows.

HTML plugin views require a token-scoped server entry. Only same-instance `/api/` resources are loaded; external scripts and file access are blocked. The scoped bridge supports apiGet/apiPost; file and SSE bridge actions are not implemented yet.

401 means reconnect or check credentials; 403 means check scopes; 404 means the target server may not expose that route. After a timed-out mutation, read the server state before repeating it. Never include secrets in issue reports.

## App updates

Open Settings → App updates from the connection home. Check manually, choose stable or preview releases, enable daily startup checks, or opt into automatic downloads. Startup checks are enabled by default. Preview/development builds initially include previews; stable builds initially use stable releases only. Automatic downloading is off by default and runs only while the app is open, with no persistent service.

Updates come from this project's public GitHub Releases. The app verifies SHA-256, package identity, increasing Android version code and matching signing certificates before opening Android's installation confirmation. Allow this app to install unknown apps in system settings when prompted, then return to continue. Connections and settings are retained by an in-place update. Different signing keys block installation; early local test APKs may use a different key from signed Actions releases. Releases without updater metadata are ignored. Manual checks explain network or private-repository access failures.

## Language, appearance and help

Settings → Appearance offers system/Chinese/English language, system/light/dark themes, preset accent colors and custom `#RRGGBB` colors. Applying a preference reloads the home screen while retaining connections and presets. Editor values are never translated. The `ⓘ` next to settings shows identifiers, types, server hints, defaults, ranges and choices. API parameter help includes required scopes and write behavior. Unknown server fields and third-party HTML retain their server/plugin-provided text.

Install the 0.2 architecture APK once to enable script updates. Later compatible core releases download signed scripts and reload the UI; native architecture changes still require APK installation. App updates displays the core version and architecture fingerprint and offers core rollback.

## Readable runtime status

The overview separates runtime counters from resource usage. Explicit percentages include native progress bars; uptime is combined into days/hours/minutes/seconds, and second/millisecond startup timestamps use the phone's time zone. Byte quantities use B/KiB/MiB/GiB. AstrBot's legacy `memory.process` and `memory.system` values are MiB: process resident memory and system memory capacity. Their progress bar shows the process share of total memory, not overall system usage. Missing data never becomes fake zero usage. Raw status retains the full response with common credential fields redacted. Local bilingual names supplement configuration fields whose server metadata has no translation, while identifiers and actual values remain unchanged.

## Auto refresh, live logs and plugin marketplace

Overview saves the refresh interval per connection (default 10 seconds). Use `0` to disable or an integer from 5–3600 seconds. Only the status region updates, and failures retain the previous data. Leaving the page or backgrounding the app stops requests; returning resumes them. Manual refresh remains available.

Live logs loads recent history, then subscribes to SSE with an event cursor. Interrupted streams reconnect with backoff and request cached replay. Pause, level filters, text search and clearing the local view are supported. Retention is bounded to 300 records/256 Ki characters; the latest 100 matches are shown, with individual message length limits. Logs are not persisted on the phone. Clearing the view does not remove server logs. Replay is limited by the server cache, so offline completeness is not guaranteed. Reverse proxies must allow long connections and disable SSE buffering.

The marketplace uses the current server registry. Search names, authors, descriptions or tags; sort by stars/name; filter installed items; browse pages and plugin details. Repository documentation opens in your browser. Installation shows the plugin, source and target instance for confirmation, then sends one mutation while retaining server version checks. An optional download proxy is supported. Failed installs are never automatically replayed; use live logs for diagnosis.

API keys need `system` scope for status/logs and `plugins` for marketplace/install. Legacy endpoints may require dashboard-account authentication. Read-only fallbacks occur only after 404/405, never 401/403.

This release adds native streaming and lifecycle scheduling primitives, so older architectures need the complete APK once. Compatible future page/behavior changes can continue through signed core updates. Validation uses pinned upstream contracts and simulated services; it is not a claim of live production or universal historical-version testing.

# User guide

## Connect

Install a signed release APK on Android 8.0+. Add a connection using the WebUI base URL, such as `https://bot.example.com` or `https://example.com/astrbot`. Do not include a hash route or query string. Save a username/password, or an API key with appropriate scopes. The API key takes priority.

Current servers use plain-password login over HTTPS. Select MD5 only for older servers that explicitly require it. If two-factor authentication is enabled, enter the code after the initial attempt and connect again.

OpenAPI is fetched from `/api/v1/openapi.json`. Only a 404/405 selects the legacy catalogue. An internal error, invalid TLS or proxy HTML does not trigger downgrade. Failed mutations are never automatically replayed against another route.

## Workspaces and presets

Connections are grouped and searchable. Duplicating a connection creates a distinct identity. Credentials and presets are encrypted on-device with Android Keystore; uninstalling loses them.

Select a native API category and operation. Edit path, query and body JSON, or open the field editor. Enum values appear as suggestions. Full-screen editing supports find, replace-all, formatting and restoring the original text. Input validation warns about potentially incomplete group IDs or URLs without blocking submission.

Save a request as a preset, connect to the destination instance, then load it from the preset library. Review resource identifiers and version-dependent fields. Secret-like fields are blanked unless explicitly retained. Cross-version request conversion and unattended group writes are not implemented.

## Limits

The developer preview provides a generic native API workspace, not complete dedicated WebUI-equivalent screens. Streaming chat/SSE/WebSocket workflows, complex backup/file protocols, HTML file/stream bridge actions and comprehensive old-server validation remain unfinished. Uploads are limited to 8 MiB and responses to 16 MiB. Use WebUI for larger files or unsupported workflows.

HTML plugin views require a token-scoped server entry. Only same-instance `/api/` resources are loaded; external scripts and file access are blocked. The scoped bridge supports apiGet/apiPost; file and SSE bridge actions are not implemented yet.

401 means reconnect or check credentials; 403 means check scopes; 404 means the target server may not expose that route. After a timed-out mutation, read the server state before repeating it. Never include secrets in issue reports.

# Field help and coverage

The information button sits to the left of each setting title, including switches and read-only fields. It opens purpose, behavior, examples and pinned source references. Cards show short summaries while their full rows remain editable.

The current inventory covers **671 configuration/status keys and compatibility aliases**, with Chinese and English explanations and examples. These are not 671 distinct features or a count of every third-party plugin field. Evidence is pinned to [AstrBot revision 6b10af7](https://github.com/AstrBotDevs/AstrBot/commit/6b10af75e118714335aff3aab047bf7689fa1388).

Newer server documentation takes precedence over bundled snapshots. Unknown third-party fields retain their server metadata; missing semantic documentation is explicitly identified. The client does not infer plugin behavior from a field name. Newly introduced server fields need review before joining the verified inventory.

Examples illustrate structure and never modify a draft automatically. Replace dummy credentials and resource IDs with your own. Session IDs vary by platform: use `/sid`; a valid example is `aiocqhttp:GroupMessage:123456789`. Do not add `group:` universally. In the pinned source, an empty allowlist does not filter messages, and administrator/WebChat exemptions also apply.

`provider_settings.provider_pool` is documented as unused. `default_kb_collection` is obsolete in the current source. Legacy `memory.process` and `memory.system` are MiB values, with system meaning total memory, not used memory. Timestamps display in the phone time zone.

Reviewed evidence lives in `scripts/dev/help-source.json`, editorial explanations in `scripts/dev/help-authored.json`, and bilingual titles in `scripts/dev/field-names.json`. Run `python3 scripts/build_locales.py` to regenerate data. Repository checks reject missing explanations, missing English copies and stale generated files. Node tests cover inventory, examples, contextual matching and metadata preservation; JVM tests load modules through Rhino; device tests exercise help hit targets and fixed palette colors.

Help ships in the signed script core. Compatible installations update the core without reinstalling the APK; every complete APK also contains the current data.

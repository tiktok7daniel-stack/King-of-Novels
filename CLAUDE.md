# RIWAYA: rules for Claude Code

Novels-only Android app (Kotlin, Jetpack Compose, Material 3). Plan: `docs/RIWAYA_PLAN.md`. Decisions:
`docs/RIWAYA_DECISIONS.md`. Sources: `docs/RIWAYA_SOURCES.md`. Design: `docs/riwaya-mockup.html`.
The engine comes from `github.com/tiktok7daniel-stack/MANHWA`.

- Reply to the owner in Egyptian Arabic, short and plain.
- Commit with `[skip ci]` and list changes in `docs/PENDING_CHANGES.md`. Build only when the owner says "اعمل بيلد نهائي".
- Material Symbols only, no emoji in the app. Every string in resources (Arabic + English). Full RTL.
- Never delete a feature or user data; new stored fields get backward-compatible defaults.
- Never use published or stolen API keys, fake accounts, or anything that gets past CAPTCHA or site protections.
- Never claim a build or an APK exists before checking it on GitHub.

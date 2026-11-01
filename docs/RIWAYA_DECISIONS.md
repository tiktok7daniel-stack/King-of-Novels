# RIWAYA: decisions

1. **Standalone repository.** The master prompt planned RIWAYA as a module inside MANHWA. The owner instead gave an
   empty repository (`King-of-Novels`), so RIWAYA is its own Gradle project here and MANHWA is not touched at all.
   This removes the biggest risk in the prompt (breaking MANHWA while extracting shared code).
2. **What is copied from MANHWA** (`github.com/tiktok7daniel-stack/MANHWA`): only the IReader part of `:source-api`
   (`ireader.core.*`, so that the same IReader extension APKs load) and the novel engine (`tachiyomi/novel/*`,
   `translation/translator/*` text engines). The manga, anime, OCR and the novel/manhwa bridge stay out.
3. **Mihon dependencies are replaced** with small local ones: `logcat` becomes a thin `Log` wrapper, `appGraph` and
   the Mihon `Preference` become DataStore-backed settings, Voyager screens become Compose Navigation, and Mihon icons
   become Material Symbols through the font (the same glyphs, without the 4,000-file icon module).
4. **Storage:** small JSON files written whole (library, progress, history, chapter lists, chapter text, translations,
   glossary) and SharedPreferences for settings. Room was dropped for v1: it needs KSP, and no KSP release is known to
   work with AGP 9.4 + Kotlin 2.4 here (no local Android SDK to try), so a failed build would block the owner's APK.
5. **applicationId:** `app.riwaya.novels` (MANHWA can stay installed next to it).
6. **Design direction:** "Night Library", approved through `docs/riwaya-mockup.html` before any Compose code is written.
7. **Navigation:** a small back stack of our own (`ui/Nav.kt`) with screen models that survive going deeper, instead of
   Voyager or Navigation Compose: one dependency less, and the reader/listen screens hide the bottom bar.
8. **Listening v1:** the phone's own voices (Android TTS), a narrator voice plus male and female character voices,
   chosen per paragraph from the dialogue. Piper offline voices (sherpa-onnx) come in a later build: they need native
   libraries and voice downloads.
9. **New Arabic sources:** Free Kol Novel is built in (`engine/builtin/FreeKolNovel.kt`). Azora (azoramoon.com) and
   Olaoe (olaoe.cyou) are skipped: LNReader marks both sites as down (`down: true`; Olaoe since January 2026).
10. **Arabic names:** every Arabic source shows its Arabic name (`engine/SourceCatalog.kt`), with the Latin name small next to it.
11. **Translation speed:** batches of 1,800 characters for Google and 3,200 for the AI, 3 at once by default (1 to 6), each
    paragraph shown when its batch returns; the screen in Settings shows the last chapter's timing (first paragraphs and whole chapter).

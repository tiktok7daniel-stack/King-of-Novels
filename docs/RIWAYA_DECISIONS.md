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
4. **Storage:** Room for the library, chapters, progress, translated-chapter cache and glossary; DataStore for settings.
5. **applicationId:** `app.riwaya.novels` (MANHWA can stay installed next to it).
6. **Design direction:** "Night Library", approved through `docs/riwaya-mockup.html` before any Compose code is written.

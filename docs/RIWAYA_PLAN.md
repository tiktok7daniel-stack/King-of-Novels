# RIWAYA: plan

Novels-only Android app (web novels, light novels, translated novels). Kotlin, Jetpack Compose, Material 3.

## Module map

```
:source-api   ireader.core.* copied from MANHWA (CatalogSource, HttpSource, ChapterInfo, ParsingUtils, ...)
:novel-core   the engine: extension manager + loader, runtime (HTTP clients, prefs), sources health,
              translator (batches, glossary, engine chain), speaker (system TTS, Piper/sherpa-onnx), cast, caches
:app          RIWAYA UI: theme "Night Library", navigation, screens, Room DB, WorkManager jobs, media service
riwaya-sources/  new IReader-format sources for Free Kol Novel, Azora, Olaoe
```

## What comes from MANHWA and what happens to it

| MANHWA file | In RIWAYA |
|---|---|
| `source-api/.../ireader/core/**` | copied as is |
| `NovelExtensionManager.kt`, `NovelRuntime.kt` | `:novel-core`, Mihon prefs replaced by DataStore |
| `NovelSourceHealth.kt` | `:novel-core`, adds last-checked time and reason |
| `NovelStore.kt` | replaced by Room (`novels`, `chapters`, `progress`, `translations`, `glossary`) |
| `NovelTranslator.kt` | rewritten for speed: parallel batches (default 3), paragraphs streamed as they arrive, cache, prefetch |
| `translation/translator/*` (Google, FastTranslator, AiTranslator, MLKit, fixers) | `:novel-core`, without the OCR page types |
| `NovelSpeaker.kt`, `PiperVoices.kt`, sherpa `Tts.kt`, `NovelCast.kt` | `:novel-core`, driven by a foreground media service |
| `VoxClones.kt` | later, optional |
| `NovelReader.kt`, `NovelScreens.kt` | not copied; new screens from the mockup |
| `novel/bridge/**` | not copied |

## Database (Room)

- `novel(id, sourceId, url, title, titleTranslated, author, cover, description, genres, status, inLibrary, category, addedAt)`
- `chapter(id, novelId, url, name, number, read, downloaded, uploadDate)`
- `progress(novelId, chapterId, paragraph, percent, updatedAt)`
- `translation(key = source+novel+chapter+lang+engine, paragraphs JSON, createdAt)`
- `glossary(novelId, original, translated)`
- `source_state(sourceId, enabled, pinned, health, reason, checkedAt)`

## Phases

1. Plan, sources list, mockup (this commit)
2. Gradle project: `:source-api`, `:novel-core`, `:app` skeleton, theme, navigation, CI workflow `build_riwaya.yml`
3. Sources: extension install/load, onboarding, Sources screen, health, global search (8 s per source)
4. Library, novel page, reader, downloads
5. Translation (parallel, streamed, cached, prefetch, glossary editor, engine chain)
6. Listening (media service, multi-voice, sleep timer, auto next chapter)
7. Home and recommendations
8. Polish, tests, final build only when the owner says "اعمل بيلد نهائي"

## Risks

- Some Arabic sites sit behind Cloudflare: the user passes the check once in an in-app WebView and the cookies are reused.
- Piper voices and the sherpa-onnx native libraries make the APK big: voices are downloaded on demand, arm64 only.
- No Android SDK in the cloud session: every compile check runs on GitHub Actions.

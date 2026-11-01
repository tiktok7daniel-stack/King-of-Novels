package app.riwaya.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One stored setting, readable as a flow so screens update when it changes. */
class Pref<T>(
    private val prefs: SharedPreferences,
    private val key: String,
    private val default: T,
    private val read: SharedPreferences.(String, T) -> T,
    private val write: SharedPreferences.Editor.(String, T) -> Unit,
) {
    private val state = MutableStateFlow(prefs.read(key, default))
    val flow: StateFlow<T> = state.asStateFlow()
    fun get(): T = state.value
    fun set(value: T) {
        state.value = value
        prefs.edit().apply { write(key, value) }.apply()
    }
}

/** Every setting of the app, with defaults that keep older installs working. */
class Settings(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("riwaya", Context.MODE_PRIVATE)

    private fun bool(k: String, d: Boolean) = Pref(p, k, d, { key, def -> getBoolean(key, def) }, { key, v -> putBoolean(key, v) })
    private fun int(k: String, d: Int) = Pref(p, k, d, { key, def -> getInt(key, def) }, { key, v -> putInt(key, v) })
    private fun float(k: String, d: Float) = Pref(p, k, d, { key, def -> getFloat(key, def) }, { key, v -> putFloat(key, v) })
    private fun str(k: String, d: String) = Pref(p, k, d, { key, def -> getString(key, def) ?: def }, { key, v -> putString(key, v) })
    private fun set(k: String, d: Set<String>) =
        Pref(p, k, d, { key, def -> getStringSet(key, def)?.toSet() ?: def }, { key, v -> putStringSet(key, v) })

    // First run
    val onboarded = bool("onboarded", false)
    val languages = set("languages", setOf("ar", "en"))
    val genres = set("genres", emptySet())

    // Sources
    val repos = set("repos", emptySet())
    val reposSeeded = bool("repos_seeded", false)
    val disabledSources = set("disabled_sources", emptySet())
    val pinnedSources = set("pinned_sources", emptySet())
    val showNsfw = bool("show_nsfw", false)
    /** Sources switched off by the app because their check failed; switched back on when they work again. */
    val autoDisabled = set("auto_disabled", emptySet())
    /** Sources the reader keeps on even when their check fails. */
    val keepOn = set("keep_on", emptySet())
    val autoHideBroken = bool("auto_hide_broken", true)
    val lastHealthRun = str("last_health_run", "0")
    val englishAdded = bool("english_added", false)

    // Translation
    val targetLanguage = str("target_language", "ar")
    val engineOrder = str("engine_order", "AI,GOOGLE,MLKIT")
    val disabledEngines = set("disabled_engines", emptySet())
    val geminiKey = str("gemini_key", "")
    val geminiModel = str("gemini_model", "gemini-2.5-flash")
    val parallelBatches = int("parallel_batches", 3)
    val prefetchNext = bool("prefetch_next", true)
    val prefetchWifiOnly = bool("prefetch_wifi_only", true)
    val autoTranslate = bool("auto_translate", true)
    val translateTitles = bool("translate_titles", true)

    // Reader
    val readerTheme = str("reader_theme", "PAPER")
    val readerFont = str("reader_font", "NASKH")
    val fontSize = float("font_size", 18f)
    val lineHeight = float("line_height", 1.9f)
    val margin = int("margin", 22)
    val justify = bool("justify", true)
    val translationMode = str("translation_mode", "TRANSLATED")
    val keepScreenOn = bool("keep_screen_on", true)
    val volumeKeys = bool("volume_keys", false)

    // Listening
    val ttsRate = float("tts_rate", 1.0f)
    val ttsPitch = float("tts_pitch", 1.0f)
    val multiVoice = bool("multi_voice", true)
    val pauseMs = int("pause_ms", 250)
    val voiceNarrator = str("voice_narrator", "")
    val voiceMale = str("voice_male", "")
    val voiceFemale = str("voice_female", "")
    val autoNextChapter = bool("auto_next_chapter", true)

    // App
    val appTheme = str("app_theme", "DARK")
}

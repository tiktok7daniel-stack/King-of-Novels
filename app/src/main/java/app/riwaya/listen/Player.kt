package app.riwaya.listen

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import app.riwaya.Riwaya
import app.riwaya.data.SavedNovel
import app.riwaya.engine.Content
import ireader.core.source.model.ChapterInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What is being listened to. */
data class PlayerState(
    val novel: SavedNovel? = null,
    val chapters: List<ChapterInfo> = emptyList(),
    val chapterIndex: Int = -1,
    val paragraphs: List<String> = emptyList(),
    val paragraph: Int = 0,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val translated: Boolean = false,
    val language: String = "ar",
    val sleepEndsAt: Long = 0,
    val sleepEndOfChapter: Boolean = false,
    val error: String? = null,
) {
    val active: Boolean get() = novel != null
    val chapter: ChapterInfo? get() = chapters.getOrNull(chapterIndex)
    val progress: Float get() = if (paragraphs.isEmpty()) 0f else paragraph.toFloat() / paragraphs.size
}

/**
 * Reads a novel aloud: the translation when there is one (translating the chapter first when the reader wants it),
 * else the original, in the right language's voice; goes on to the next chapter by itself, stops at the sleep timer,
 * pauses for calls, and keeps going with the screen off through [ListenService].
 */
object Player {

    private lateinit var app: Context
    private var speaker: Speaker? = null
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var sleepJob: Job? = null
    private var focus: AudioFocusRequest? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    private fun speaker(): Speaker = speaker ?: Speaker(app).also { s ->
        speaker = s
        s.onReady = { ok -> if (ok && _state.value.playing) Riwaya.scope.launch(Dispatchers.Main) { speakCurrent() } }
    }

    fun voices(language: String): List<VoiceOption> = speaker().voices(language)

    /** Starts [novel] at chapter [index], paragraph [paragraph]. */
    fun start(novel: SavedNovel, chapters: List<ChapterInfo>, index: Int, paragraph: Int = 0) {
        speaker?.stop()
        _state.value = PlayerState(novel = novel, chapters = chapters, chapterIndex = index, paragraph = paragraph, playing = true, loading = true)
        ContextCompat.startForegroundService(app, Intent(app, ListenService::class.java))
        load(index, paragraph)
    }

    private fun load(index: Int, paragraph: Int) {
        loadJob?.cancel()
        speaker?.stop()
        val s = _state.value
        val novel = s.novel ?: return
        val chapter = s.chapters.getOrNull(index) ?: return stop()
        _state.update { it.copy(chapterIndex = index, paragraph = paragraph, loading = true, error = null, paragraphs = emptyList()) }
        loadJob = Riwaya.scope.launch(Dispatchers.Main) {
            try {
                val (text, translated, lang) = withContext(Dispatchers.IO) { textOf(novel, chapter) }
                _state.update { it.copy(paragraphs = text, translated = translated, language = lang, loading = false, paragraph = paragraph.coerceIn(0, (text.size - 1).coerceAtLeast(0))) }
                if (_state.value.playing) speakCurrent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w("RiwayaPlayer", "Chapter not loaded", e)
                _state.update { it.copy(loading = false, playing = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    /** The chapter's text to read: its translation (made now if needed), else the original. */
    private suspend fun textOf(novel: SavedNovel, chapter: ChapterInfo): Triple<List<String>, Boolean, String> {
        val lib = Riwaya.library
        val original = lib.cachedText(novel.id, chapter.key) ?: run {
            val source = Riwaya.extensions.source(novel.sourceId) ?: error("Source not installed")
            Content.fetch(source, chapter).also { if (it.isNotEmpty()) lib.cacheText(novel.id, chapter.key, it) }
        }
        if (original.isEmpty()) error("Empty chapter")
        val tr = Riwaya.translator
        val target = tr.target()
        val isTarget = if (target == "ar") Content.isArabic(original.joinToString(" ")) else novel.lang == target
        if (isTarget) return Triple(original, false, target)
        tr.cached(novel.id, chapter.key)?.takeIf { it.size == original.size }?.let { return Triple(it, true, target) }
        if (Riwaya.settings.autoTranslate.get()) {
            val done = tr.translateAll(novel.id, novel.title, chapter.key, original, novel.lang)
            if (done != original) return Triple(done, true, target)
        }
        return Triple(original, false, novel.lang.ifBlank { "en" })
    }

    private fun speakCurrent() {
        val s = _state.value
        if (!s.playing || s.loading || s.paragraphs.isEmpty()) return
        val sp = speaker()
        if (!sp.ready) return
        if (!requestFocus()) return
        val i = s.paragraph
        if (i >= s.paragraphs.size) {
            chapterEnded()
            return
        }
        val st = Riwaya.settings
        val novel = s.novel ?: return
        // Progress is kept as the chapter is heard, so reading picks up where listening stopped
        s.chapter?.let { ch -> Riwaya.library.saveProgress(novel.sourceId, novel.key, ch, s.chapterIndex, i.toFloat() / s.paragraphs.size, i, listening = true) }
        sp.speak(
            id = i,
            lines = Speaker.lines(s.paragraphs[i], st.multiVoice.get()),
            language = s.language,
            rate = st.ttsRate.get(),
            pitch = st.ttsPitch.get(),
            pauseMs = st.pauseMs.get(),
            voiceFor = { role -> voiceFor(role, s.language) },
        ) {
            Riwaya.scope.launch(Dispatchers.Main) {
                if (_state.value.playing && _state.value.paragraph == i) {
                    _state.update { it.copy(paragraph = i + 1) }
                    speakCurrent()
                }
            }
        }
    }

    private fun voiceFor(role: Role, language: String): String? {
        val st = Riwaya.settings
        val picked = when (role) {
            Role.NARRATOR -> st.voiceNarrator.get()
            Role.MALE -> st.voiceMale.get()
            Role.FEMALE -> st.voiceFemale.get()
        }
        return picked.ifBlank { null } ?: speaker?.autoVoice(language, role)
    }

    private fun chapterEnded() {
        val s = _state.value
        val novel = s.novel ?: return
        s.chapter?.let { Riwaya.library.saveProgress(novel.sourceId, novel.key, it, s.chapterIndex, 1f, s.paragraphs.size, listening = true) }
        if (s.sleepEndOfChapter) {
            _state.update { it.copy(sleepEndOfChapter = false) }
            pause()
            return
        }
        if (Riwaya.settings.autoNextChapter.get() && s.chapterIndex + 1 < s.chapters.size) {
            load(s.chapterIndex + 1, 0)
        } else {
            pause()
        }
    }

    fun toggle() = if (_state.value.playing) pause() else play()

    fun play() {
        if (!_state.value.active) return
        _state.update { it.copy(playing = true) }
        ContextCompat.startForegroundService(app, Intent(app, ListenService::class.java))
        if (_state.value.paragraphs.isEmpty() && !_state.value.loading) load(_state.value.chapterIndex, _state.value.paragraph) else speakCurrent()
    }

    fun pause() {
        _state.update { it.copy(playing = false) }
        speaker?.stop()
        abandonFocus()
    }

    fun seek(paragraph: Int) {
        val s = _state.value
        if (s.paragraphs.isEmpty()) return
        speaker?.stop()
        _state.update { it.copy(paragraph = paragraph.coerceIn(0, s.paragraphs.size - 1), playing = true) }
        speakCurrent()
    }

    fun nextParagraph() = seek(_state.value.paragraph + 1)
    fun previousParagraph() = seek(_state.value.paragraph - 1)

    fun nextChapter() {
        val s = _state.value
        if (s.chapterIndex + 1 < s.chapters.size) load(s.chapterIndex + 1, 0)
    }

    fun previousChapter() {
        val s = _state.value
        if (s.chapterIndex > 0) load(s.chapterIndex - 1, 0)
    }

    /** Stops after [minutes]; 0 cancels; -1 stops at the end of this chapter. */
    fun sleep(minutes: Int) {
        sleepJob?.cancel()
        when {
            minutes == 0 -> _state.update { it.copy(sleepEndsAt = 0, sleepEndOfChapter = false) }
            minutes < 0 -> _state.update { it.copy(sleepEndsAt = 0, sleepEndOfChapter = true) }
            else -> {
                val end = System.currentTimeMillis() + minutes * 60_000L
                _state.update { it.copy(sleepEndsAt = end, sleepEndOfChapter = false) }
                sleepJob = Riwaya.scope.launch(Dispatchers.Main) {
                    delay(minutes * 60_000L)
                    _state.update { it.copy(sleepEndsAt = 0) }
                    pause()
                }
            }
        }
    }

    /** Re-reads the current paragraph with new settings (speed, voices). */
    fun refresh() {
        if (_state.value.playing) seek(_state.value.paragraph)
    }

    fun stop() {
        loadJob?.cancel()
        sleepJob?.cancel()
        speaker?.stop()
        abandonFocus()
        _state.value = PlayerState()
        app.stopService(Intent(app, ListenService::class.java))
    }

    // ---- Audio focus: a call or another app's sound pauses the reading ----

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> Riwaya.scope.launch(Dispatchers.Main) { pause() }
            else -> Unit
        }
    }

    private fun requestFocus(): Boolean {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (focus != null) return true
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        val ok = am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (ok) focus = req
        return ok || Build.VERSION.SDK_INT < Build.VERSION_CODES.O
    }

    private fun abandonFocus() {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        focus?.let { am.abandonAudioFocusRequest(it) }
        focus = null
    }
}

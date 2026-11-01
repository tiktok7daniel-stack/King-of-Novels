package app.riwaya.listen

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

enum class Role { NARRATOR, MALE, FEMALE }

data class Line(val text: String, val role: Role)

/** A voice the reader can pick. */
data class VoiceOption(val id: String, val label: String, val offline: Boolean)

/**
 * The phone's voices reading a paragraph: the narration in one voice and what the characters say in theirs, each
 * paragraph queued as it comes so the speed, the voices and the text can change between paragraphs.
 */
class Speaker(context: Context) {

    @Volatile var ready = false
        private set

    private var onDone: (() -> Unit)? = null
    private var lastId = ""

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        onReady?.invoke(ready)
    }
    var onReady: ((Boolean) -> Unit)? = null

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) {
                if (utteranceId == lastId) onDone?.invoke()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                if (utteranceId == lastId) onDone?.invoke()
            }
            override fun onError(utteranceId: String, errorCode: Int) {
                if (utteranceId == lastId) onDone?.invoke()
            }
        })
    }

    fun voices(language: String): List<VoiceOption> = systemVoices(language).map {
        VoiceOption(it.name, prettyName(it), !it.isNetworkConnectionRequired)
    }

    private fun systemVoices(language: String): List<Voice> = runCatching {
        tts.voices.orEmpty()
            .filter { it.locale.language == Locale.forLanguageTag(language).language }
            .filter { !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name }))
    }.getOrDefault(emptyList())

    private fun prettyName(v: Voice): String {
        val country = v.locale.displayCountry.ifBlank { v.locale.displayLanguage }
        val tail = v.name.substringAfterLast('-').take(8)
        return "$country · $tail"
    }

    /** Says one paragraph, then calls [done]. */
    fun speak(
        id: Int,
        lines: List<Line>,
        language: String,
        rate: Float,
        pitch: Float,
        pauseMs: Int,
        voiceFor: (Role) -> String?,
        done: () -> Unit,
    ) {
        if (!ready) return
        onDone = done
        val voices = systemVoices(language)
        tts.setSpeechRate(rate)
        lines.forEachIndexed { i, line ->
            val voice = voiceFor(line.role)?.let { v -> voices.firstOrNull { it.name == v } }
            if (voice != null) tts.setVoice(voice) else tts.setLanguage(Locale.forLanguageTag(language))
            // Without a voice of its own a character is told apart by the pitch alone
            tts.setPitch(
                pitch * when {
                    voice != null -> 1f
                    line.role == Role.FEMALE -> 1.25f
                    line.role == Role.MALE -> 0.85f
                    else -> 1f
                },
            )
            tts.speak(line.text, TextToSpeech.QUEUE_ADD, Bundle(), "p$id-$i")
        }
        lastId = "p$id-gap"
        tts.playSilentUtterance(pauseMs.toLong().coerceAtLeast(1), TextToSpeech.QUEUE_ADD, lastId)
    }

    fun stop() {
        onDone = null
        lastId = ""
        runCatching { tts.stop() }
    }

    fun release() {
        stop()
        runCatching { tts.shutdown() }
    }

    /** Picks distinct voices for the narrator and the characters when the phone has them. */
    fun autoVoice(language: String, role: Role): String? {
        val list = systemVoices(language)
        if (list.isEmpty()) return null
        return when (role) {
            Role.NARRATOR -> list.first().name
            Role.MALE -> list.getOrNull(1)?.name
            Role.FEMALE -> list.getOrNull(2)?.name ?: list.getOrNull(1)?.name
        }
    }

    companion object {
        private val QUOTES = Regex("[\"“”«»„]([^\"“”«»„]{1,600})[\"“”«»„]")
        private val FEMALE = Regex("(?<!\\p{L})[وف]?(قالت|صرخت|همست|أجابت|اجابت|ردت|سألت|تمتمت|ضحكت|صاحت|she said|she asked|she whispered|she shouted|she replied)(?!\\p{L})", RegexOption.IGNORE_CASE)

        /**
         * A paragraph in its [Line]s: what is between quotation marks is said by a character, the rest is told; a
         * paragraph that opens with a dash is a character speaking as a whole (as Arabic novels write dialogue).
         */
        fun lines(paragraph: String, multiVoice: Boolean): List<Line> {
            val trimmed = paragraph.trim()
            if (!multiVoice) return listOf(Line(trimmed, Role.NARRATOR))
            val voice = if (FEMALE.containsMatchIn(trimmed)) Role.FEMALE else Role.MALE
            if (trimmed.startsWith("-") || trimmed.startsWith("–") || trimmed.startsWith("—")) {
                return listOf(Line(trimmed.trimStart('-', '–', '—', ' '), voice))
            }
            val out = mutableListOf<Line>()
            var last = 0
            for (match in QUOTES.findAll(trimmed)) {
                val before = trimmed.substring(last, match.range.first).trim()
                if (before.isNotEmpty()) out += Line(before, Role.NARRATOR)
                val said = match.groupValues[1].trim()
                if (said.isNotEmpty()) out += Line(said, voice)
                last = match.range.last + 1
            }
            val rest = trimmed.substring(last).trim()
            if (rest.isNotEmpty()) out += Line(rest, Role.NARRATOR)
            return out.ifEmpty { listOf(Line(trimmed, Role.NARRATOR)) }
        }
    }
}

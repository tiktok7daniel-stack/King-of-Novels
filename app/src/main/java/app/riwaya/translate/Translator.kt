package app.riwaya.translate

import android.content.Context
import android.util.Log
import app.riwaya.core.Settings
import app.riwaya.core.readOrNull
import app.riwaya.core.sha1
import app.riwaya.core.writeAtomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** The engines, in the order the reader sets. */
enum class Engine { AI, GOOGLE, MLKIT }

/** How far a chapter's translation is: each paragraph is filled in as its batch comes back. */
data class TranslationProgress(val paragraphs: List<String?>, val done: Int, val total: Int, val engine: Engine? = null) {
    val finished: Boolean get() = done >= total
}

@Serializable
private data class CachedChapter(val target: String, val engine: String, val paragraphs: List<String>, val at: Long)

/**
 * A chapter's paragraphs in the reader's language, fast: batches go out together (3 at once by default), each
 * paragraph shows as soon as its batch returns, and a finished chapter is kept so it is never translated twice.
 * The AI (the reader's own key) uses the novel's names glossary; a batch it cannot do goes to Google, then to
 * ML Kit on the phone.
 */
class Translator(private val context: Context, private val settings: Settings) {

    private val json = Json { ignoreUnknownKeys = true }
    private val dir get() = File(context.filesDir, "translations").apply { mkdirs() }
    private val resting = ConcurrentHashMap.newKeySet<Engine>()
    private val glossaryLock = Mutex()

    private val _lastTiming = MutableStateFlow<Pair<Long, Long>?>(null)
    /** First paragraphs shown after, and whole chapter done after (milliseconds), for the last chapter translated. */
    val lastTiming: StateFlow<Pair<Long, Long>?> = _lastTiming.asStateFlow()

    fun target(): String = settings.targetLanguage.get()

    /** The engines to try, in the reader's order, without the switched-off ones and those resting this session. */
    fun chain(sourceLang: String): List<Engine> {
        val order = settings.engineOrder.get().split(',').mapNotNull { runCatching { Engine.valueOf(it.trim()) }.getOrNull() }
        val all = (order + Engine.entries).distinct()
        val off = settings.disabledEngines.get()
        return all.filter { it.name !in off && it !in resting }.filter { e ->
            when (e) {
                Engine.AI -> settings.geminiKey.get().isNotBlank()
                Engine.MLKIT -> MlKit.supports(sourceLang.ifBlank { "en" }) && MlKit.supports(target())
                Engine.GOOGLE -> true
            }
        }
    }

    // ---- Cache ----

    private fun cacheFile(novelId: String, chapterKey: String, target: String) =
        File(dir, "${sha1(novelId)}/${sha1("$chapterKey|$target")}.json")

    suspend fun cached(novelId: String, chapterKey: String): List<String>? = withContext(Dispatchers.IO) {
        cacheFile(novelId, chapterKey, target()).readOrNull()
            ?.let { runCatching { json.decodeFromString(CachedChapter.serializer(), it) }.getOrNull() }
            ?.paragraphs
    }

    fun isCached(novelId: String, chapterKey: String): Boolean = cacheFile(novelId, chapterKey, target()).exists()

    fun cachedKeys(novelId: String): Set<String> =
        File(dir, sha1(novelId)).list()?.map { it.removeSuffix(".json") }?.toSet().orEmpty()

    fun cacheHash(chapterKey: String): String = sha1("$chapterKey|${target()}")

    fun forget(novelId: String, chapterKey: String) {
        cacheFile(novelId, chapterKey, target()).delete()
    }

    fun forgetNovel(novelId: String) {
        File(dir, sha1(novelId)).deleteRecursively()
    }

    // ---- Chapters ----

    /**
     * Translates [paragraphs] and sends the progress each time a batch comes back. The last value has every paragraph
     * (one that no engine could do keeps its original text).
     */
    fun translate(
        novelId: String,
        title: String,
        chapterKey: String,
        paragraphs: List<String>,
        sourceLang: String,
    ): Flow<TranslationProgress> = channelFlow {
        cached(novelId, chapterKey)?.takeIf { it.size == paragraphs.size }?.let {
            send(TranslationProgress(it, it.size, it.size))
            return@channelFlow
        }
        val start = System.currentTimeMillis()
        var firstAt = 0L
        val result = arrayOfNulls<String>(paragraphs.size)
        val state = MutableStateFlow(TranslationProgress(result.toList(), 0, paragraphs.size))
        send(state.value)
        val glossary = readGlossary(novelId).toMutableMap()
        val chain = chain(sourceLang)
        val ai = Engine.AI in chain
        val batches = batches(paragraphs.indices.toList(), paragraphs, if (ai) AI_BATCH_CHARS else MT_BATCH_CHARS)
        val gate = Semaphore(settings.parallelBatches.get().coerceIn(1, 6))
        var engineUsed: Engine? = null
        val lock = Mutex()
        coroutineScope {
            batches.map { idx ->
                launch(Dispatchers.IO) {
                    gate.withPermit {
                        val texts = idx.map { paragraphs[it] }
                        val (out, engine) = translateBatch(texts, title, sourceLang, glossary, chain)
                        lock.withLock {
                            idx.forEachIndexed { i, p -> result[p] = out[i] }
                            if (engineUsed == null || engine == Engine.AI) engineUsed = engine
                            if (firstAt == 0L) firstAt = System.currentTimeMillis() - start
                            val done = result.count { it != null }
                            state.value = TranslationProgress(result.toList(), done, paragraphs.size, engineUsed)
                            send(state.value)
                        }
                    }
                }
            }
        }
        val final = result.mapIndexed { i, s -> s ?: paragraphs[i] }
        _lastTiming.value = firstAt to (System.currentTimeMillis() - start)
        if (final.indices.any { final[it] != paragraphs[it] }) {
            withContext(Dispatchers.IO) {
                runCatching {
                    cacheFile(novelId, chapterKey, target()).writeAtomic(
                        json.encodeToString(CachedChapter.serializer(), CachedChapter(target(), engineUsed?.name.orEmpty(), final, System.currentTimeMillis())),
                    )
                }
            }
        }
        writeGlossary(novelId, glossary)
        send(TranslationProgress(final, final.size, final.size, engineUsed))
    }

    /** The whole chapter, for prefetching and listening. */
    suspend fun translateAll(novelId: String, title: String, chapterKey: String, paragraphs: List<String>, sourceLang: String): List<String> {
        var last: List<String> = paragraphs
        translate(novelId, title, chapterKey, paragraphs, sourceLang).collect { p -> if (p.finished) last = p.paragraphs.map { it.orEmpty() } }
        return last
    }

    private suspend fun translateBatch(
        texts: List<String>,
        title: String,
        sourceLang: String,
        glossary: MutableMap<String, String>,
        chain: List<Engine>,
    ): Pair<List<String>, Engine?> {
        val target = target()
        for (engine in chain) {
            if (engine in resting) continue
            try {
                val out: List<String>? = when (engine) {
                    Engine.AI -> {
                        val names = glossaryLock.withLock { glossary.toMap() }
                        Gemini.translate(settings.geminiKey.get(), settings.geminiModel.get(), languageName(target), title, texts, names)?.let { a ->
                            glossaryLock.withLock { a.names.forEach { (k, v) -> if (k !in glossary) glossary[k] = v } }
                            a.paragraphs
                        }
                    }
                    Engine.GOOGLE -> {
                        val names = glossaryLock.withLock { glossary.toMap() }
                        val prepared = texts.map { protect(it, names) }
                        GoogleFree.translateLines(prepared, target)
                            ?: coroutineScope { prepared.map { p -> async { GoogleFree.translate(p, target).ifBlank { p } } }.awaitAll() }
                    }
                    Engine.MLKIT -> MlKit.translate(texts, sourceLang.ifBlank { "en" }, target)
                }
                if (out != null && out.size == texts.size) return out.map { it.trim() } to engine
            } catch (e: CancellationException) {
                throw e
            } catch (e: EngineOut) {
                Log.w(TAG, "$engine resting for this session: ${e.message}")
                resting += engine
            } catch (e: Exception) {
                Log.w(TAG, "$engine batch failed", e)
            }
        }
        return texts to null
    }

    /** Names the glossary knows are put in already translated, so a plain engine keeps them the same everywhere. */
    private fun protect(text: String, glossary: Map<String, String>): String {
        if (glossary.isEmpty()) return text
        var out = text
        glossary.entries.sortedByDescending { it.key.length }.forEach { (k, v) ->
            if (k.length >= 2 && out.contains(k)) out = out.replace(Regex("\\b${Regex.escape(k)}\\b"), v)
        }
        return out
    }

    // ---- Short texts: titles, synopses, genres ----

    private val shortSer = MapSerializer(String.serializer(), String.serializer())
    private val shortFile get() = File(dir, "short.json")
    private val shortCache: MutableMap<String, String> by lazy {
        ConcurrentHashMap(runCatching { json.decodeFromString(shortSer, shortFile.readOrNull() ?: "{}") }.getOrDefault(emptyMap()))
    }
    private val shortLock = Mutex()

    fun cachedShort(text: String): String? = shortCache[sha1("${target()}|$text")]

    suspend fun translateShort(text: String): String {
        if (text.isBlank() || app.riwaya.engine.Content.isArabic(text) && target() == "ar") return text
        val key = sha1("${target()}|$text")
        shortCache[key]?.let { return it }
        val out = runCatching { GoogleFree.translate(text, target()) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return text
        shortCache[key] = out
        shortLock.withLock { withContext(Dispatchers.IO) { runCatching { shortFile.writeAtomic(json.encodeToString(shortSer, shortCache.toMap())) } } }
        return out
    }

    // ---- The novel's names ----

    private fun glossaryFile(novelId: String) = File(context.filesDir, "glossary/${sha1(novelId)}.json")

    fun readGlossary(novelId: String): Map<String, String> =
        runCatching { json.decodeFromString(shortSer, glossaryFile(novelId).readOrNull() ?: "{}") }.getOrDefault(emptyMap())

    fun writeGlossary(novelId: String, glossary: Map<String, String>) {
        runCatching { glossaryFile(novelId).writeAtomic(json.encodeToString(shortSer, glossary)) }
    }

    fun resetSession() = resting.clear()

    private fun languageName(code: String): String = when (code) {
        "ar" -> "Arabic"
        "en" -> "English"
        "fr" -> "French"
        "es" -> "Spanish"
        "tr" -> "Turkish"
        else -> code
    }

    companion object {
        private const val TAG = "RiwayaTr"
        private const val AI_BATCH_CHARS = 3_200
        private const val MT_BATCH_CHARS = 1_800

        /** Paragraph indexes grouped so each batch stays small enough to come back whole, order kept. */
        fun batches(indexes: List<Int>, paragraphs: List<String>, maxChars: Int): List<List<Int>> {
            val result = mutableListOf<List<Int>>()
            var current = mutableListOf<Int>()
            var size = 0
            for (i in indexes) {
                val len = paragraphs[i].length
                if (current.isNotEmpty() && size + len > maxChars) {
                    result += current
                    current = mutableListOf()
                    size = 0
                }
                current += i
                size += len
            }
            if (current.isNotEmpty()) result += current
            return result
        }
    }
}

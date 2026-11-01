package app.riwaya.data

import android.content.Context
import app.riwaya.core.readOrNull
import app.riwaya.core.sha1
import app.riwaya.core.writeAtomic
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.MangaInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/** A novel the app knows: in the library, or opened once (for history and continue reading). */
@Serializable
data class SavedNovel(
    val sourceId: Long,
    val key: String,
    val title: String,
    val cover: String = "",
    val author: String = "",
    val description: String = "",
    val genres: List<String> = emptyList(),
    val status: Long = 0,
    val lang: String = "",
    val inLibrary: Boolean = false,
    val category: String = "",
    val addedAt: Long = 0,
    val lastReadAt: Long = 0,
    val lastChapterKey: String = "",
    val lastChapterName: String = "",
    val lastChapterIndex: Int = -1,
    val lastFraction: Float = 0f,
    val totalChapters: Int = 0,
    val newChapters: Int = 0,
    val lastListened: Long = 0,
) {
    val id: String get() = idOf(sourceId, key)
    fun toInfo() = MangaInfo(key = key, title = title, cover = cover, author = author, description = description, genres = genres, status = status)

    companion object {
        fun idOf(sourceId: Long, key: String) = "$sourceId|$key"
    }
}

/** Where a chapter was left. */
@Serializable
data class ChapterState(val read: Boolean = false, val fraction: Float = 0f, val paragraph: Int = 0, val at: Long = 0)

@Serializable
data class HistoryEntry(val novelId: String, val chapterKey: String, val chapterName: String, val at: Long)

/**
 * The reader's library, progress and history, kept as small JSON files (written whole, so a crash never leaves half
 * of one), plus each novel's chapter list and the downloaded chapters' text.
 */
class Library(private val context: Context, private val scope: CoroutineScope) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dir get() = File(context.filesDir, "library").apply { mkdirs() }
    private val lock = Mutex()

    private val novelsSer = MapSerializer(String.serializer(), SavedNovel.serializer())
    private val progressSer = MapSerializer(String.serializer(), MapSerializer(String.serializer(), ChapterState.serializer()))
    private val historySer = ListSerializer(HistoryEntry.serializer())

    private val _novels = MutableStateFlow<Map<String, SavedNovel>>(emptyMap())
    val novels: StateFlow<Map<String, SavedNovel>> = _novels.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, Map<String, ChapterState>>>(emptyMap())
    val progress: StateFlow<Map<String, Map<String, ChapterState>>> = _progress.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    init {
        _novels.value = load(File(dir, "novels.json"), novelsSer) ?: emptyMap()
        _progress.value = load(File(dir, "progress.json"), progressSer) ?: emptyMap()
        _history.value = load(File(dir, "history.json"), historySer) ?: emptyList()
    }

    private fun <T> load(file: File, ser: kotlinx.serialization.KSerializer<T>): T? =
        file.readOrNull()?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() }

    private fun persist() {
        val n = _novels.value
        val p = _progress.value
        val h = _history.value
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    File(dir, "novels.json").writeAtomic(json.encodeToString(novelsSer, n))
                    File(dir, "progress.json").writeAtomic(json.encodeToString(progressSer, p))
                    File(dir, "history.json").writeAtomic(json.encodeToString(historySer, h))
                }
            }
        }
    }

    fun get(sourceId: Long, key: String): SavedNovel? = _novels.value[SavedNovel.idOf(sourceId, key)]

    fun libraryList(): List<SavedNovel> = _novels.value.values.filter { it.inLibrary }

    /** Keeps what a source says about a novel, without losing what the reader did with it. */
    fun remember(sourceId: Long, info: MangaInfo, lang: String) {
        _novels.update { all ->
            val id = SavedNovel.idOf(sourceId, info.key)
            val old = all[id]
            val merged = (old ?: SavedNovel(sourceId = sourceId, key = info.key, title = info.title)).copy(
                title = info.title.ifBlank { old?.title.orEmpty() },
                cover = info.cover.ifBlank { old?.cover.orEmpty() },
                author = info.author.ifBlank { old?.author.orEmpty() },
                description = info.description.ifBlank { old?.description.orEmpty() },
                genres = info.genres.ifEmpty { old?.genres.orEmpty() },
                status = if (info.status != 0L) info.status else old?.status ?: 0L,
                lang = lang,
            )
            all + (id to merged)
        }
        persist()
    }

    fun setInLibrary(sourceId: Long, key: String, inLibrary: Boolean) {
        val id = SavedNovel.idOf(sourceId, key)
        _novels.update { all ->
            val n = all[id] ?: return@update all
            all + (id to n.copy(inLibrary = inLibrary, addedAt = if (inLibrary && n.addedAt == 0L) System.currentTimeMillis() else n.addedAt))
        }
        persist()
    }

    fun setChapterCount(sourceId: Long, key: String, count: Int, markNew: Boolean) {
        val id = SavedNovel.idOf(sourceId, key)
        _novels.update { all ->
            val n = all[id] ?: return@update all
            val added = if (markNew && n.totalChapters in 1 until count) count - n.totalChapters else 0
            all + (id to n.copy(totalChapters = count, newChapters = if (markNew) n.newChapters + added else 0))
        }
        persist()
    }

    fun clearNew(sourceId: Long, key: String) {
        val id = SavedNovel.idOf(sourceId, key)
        _novels.update { all -> all[id]?.let { all + (id to it.copy(newChapters = 0)) } ?: all }
        persist()
    }

    fun chapterState(novelId: String, chapterKey: String): ChapterState? = _progress.value[novelId]?.get(chapterKey)

    /** Saves where the reader is in a chapter, and the novel's continue point. */
    fun saveProgress(sourceId: Long, key: String, chapter: ChapterInfo, index: Int, fraction: Float, paragraph: Int, listening: Boolean = false) {
        val id = SavedNovel.idOf(sourceId, key)
        val now = System.currentTimeMillis()
        val read = fraction >= 0.97f
        _progress.update { all ->
            val forNovel = all[id].orEmpty()
            val prev = forNovel[chapter.key]
            all + (id to (forNovel + (chapter.key to ChapterState(read = read || prev?.read == true, fraction = fraction, paragraph = paragraph, at = now))))
        }
        _novels.update { all ->
            val n = all[id] ?: return@update all
            all + (id to n.copy(
                lastReadAt = now,
                lastChapterKey = chapter.key,
                lastChapterName = chapter.name,
                lastChapterIndex = index,
                lastFraction = fraction,
                lastListened = if (listening) now else n.lastListened,
            ))
        }
        _history.update { h ->
            val entry = HistoryEntry(id, chapter.key, chapter.name, now)
            (listOf(entry) + h.filterNot { it.novelId == id }).take(200)
        }
        persist()
    }

    fun markRead(novelId: String, chapterKeys: Collection<String>, read: Boolean) {
        _progress.update { all ->
            val forNovel = all[novelId].orEmpty().toMutableMap()
            chapterKeys.forEach { k -> forNovel[k] = (forNovel[k] ?: ChapterState()).copy(read = read, fraction = if (read) 1f else 0f) }
            all + (novelId to forNovel)
        }
        persist()
    }

    fun removeHistory(novelId: String) {
        _history.update { h -> h.filterNot { it.novelId == novelId } }
        persist()
    }

    // ---- Chapter lists ----

    private val chaptersSer = ListSerializer(ChapterInfo.serializer())

    private fun chaptersFile(novelId: String) = File(dir, "chapters/${sha1(novelId)}.json")

    suspend fun cachedChapters(novelId: String): List<ChapterInfo>? = withContext(Dispatchers.IO) {
        chaptersFile(novelId).readOrNull()?.let { runCatching { json.decodeFromString(chaptersSer, it) }.getOrNull() }
    }

    suspend fun cacheChapters(novelId: String, chapters: List<ChapterInfo>) = withContext(Dispatchers.IO) {
        runCatching { chaptersFile(novelId).writeAtomic(json.encodeToString(chaptersSer, chapters)) }
    }

    // ---- Chapter text (read once = kept, so it opens offline) ----

    private val textSer = ListSerializer(String.serializer())

    private fun textFile(novelId: String, chapterKey: String) = File(dir, "text/${sha1(novelId)}/${sha1(chapterKey)}.json")

    suspend fun cachedText(novelId: String, chapterKey: String): List<String>? = withContext(Dispatchers.IO) {
        textFile(novelId, chapterKey).readOrNull()?.let { runCatching { json.decodeFromString(textSer, it) }.getOrNull() }
    }

    suspend fun cacheText(novelId: String, chapterKey: String, paragraphs: List<String>) = withContext(Dispatchers.IO) {
        runCatching { textFile(novelId, chapterKey).writeAtomic(json.encodeToString(textSer, paragraphs)) }
    }

    fun isDownloaded(novelId: String, chapterKey: String): Boolean = textFile(novelId, chapterKey).exists()

    fun downloadedKeys(novelId: String): Set<String> {
        val d = File(dir, "text/${sha1(novelId)}")
        return d.list()?.map { it.removeSuffix(".json") }?.toSet().orEmpty()
    }

    fun isDownloadedHash(hashes: Set<String>, chapterKey: String): Boolean = sha1(chapterKey) in hashes
}

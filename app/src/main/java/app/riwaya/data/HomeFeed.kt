package app.riwaya.data

import android.content.Context
import app.riwaya.Riwaya
import app.riwaya.core.readOrNull
import app.riwaya.core.writeAtomic
import app.riwaya.engine.Health
import app.riwaya.engine.NovelExt
import app.riwaya.engine.Search
import ireader.core.source.model.MangaInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class FeedItem(val sourceId: Long, val key: String, val title: String, val cover: String, val sourceName: String = "") {
    fun info() = MangaInfo(key = key, title = title, cover = cover)
}

@Serializable
data class FeedRow(val sourceId: Long, val sourceName: String, val lang: String, val items: List<FeedItem>)

@Serializable
data class Feed(val recommended: List<FeedItem> = emptyList(), val rows: List<FeedRow> = emptyList(), val at: Long = 0)

/**
 * The home screen's rows: what is suggested for the reader (their languages, their genres, not already in their
 * library) and what is popular on their sources. Shown from the last copy at once, refreshed in the background.
 */
class HomeFeed(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val file get() = File(context.filesDir, "home_feed.json")

    private val _feed = MutableStateFlow(runCatching { json.decodeFromString(Feed.serializer(), file.readOrNull() ?: "{}") }.getOrDefault(Feed()))
    val feed: StateFlow<Feed> = _feed.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun isStale(): Boolean = System.currentTimeMillis() - _feed.value.at > STALE_MS || _feed.value.rows.isEmpty()

    suspend fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        try {
            val st = Riwaya.settings
            val langs = st.languages.get()
            val pinned = st.pinnedSources.get()
            val checks = Riwaya.health.checks.value
            val sources = Riwaya.extensions.enabled()
                .filter { it.lang in langs || it.lang == "multi" }
                .filter { checks[it.source.id]?.status != Health.Status.BROKEN }
                .sortedWith(compareBy({ it.source.id.toString() !in pinned }, { langs.indexOf(it.lang).let { i -> if (i < 0) 99 else i } }, { checks[it.source.id]?.status != Health.Status.WORKING }))
            // Up to 3 sources per language for the rows
            val chosen = sources.groupBy { it.lang }.flatMap { it.value.take(3) }.take(8)
            val rows = coroutineScope {
                chosen.map { ext -> async(Dispatchers.IO) { popular(ext) } }.awaitAll().filterNotNull().filter { it.items.isNotEmpty() }
            }
            val genres = st.genres.get().toList().shuffled().take(2)
            val genreHits = coroutineScope {
                genres.flatMap { g -> chosen.filter { it.lang == "ar" }.take(2).map { ext -> async(Dispatchers.IO) { search(ext, g) } } }.awaitAll().flatten()
            }
            val inLibrary = Riwaya.library.libraryList().map { it.id }.toSet()
            val mixed = (genreHits + interleave(rows.map { it.items }))
                .filter { SavedNovel.idOf(it.sourceId, it.key) !in inLibrary }
                .distinctBy { Search.normalize(it.title) }
                .take(24)
            val feed = Feed(mixed, rows, System.currentTimeMillis())
            if (rows.isNotEmpty()) {
                _feed.value = feed
                withContext(Dispatchers.IO) { runCatching { file.writeAtomic(json.encodeToString(Feed.serializer(), feed)) } }
            }
        } finally {
            _refreshing.value = false
        }
    }

    private suspend fun popular(ext: NovelExt): FeedRow? = try {
        withTimeoutOrNull(12_000) {
            val list = ext.source.getMangaList(ext.source.getListings().firstOrNull(), 1).mangas
            FeedRow(ext.source.id, ext.displayName, ext.lang, list.filter { it.title.isNotBlank() }.distinctBy { it.key }.take(15).map { FeedItem(ext.source.id, it.key, it.title, it.cover, ext.displayName) })
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Riwaya.health.record(ext.source.id, false, 0, e)
        null
    }

    private suspend fun search(ext: NovelExt, query: String): List<FeedItem> = try {
        withTimeoutOrNull(Search.PER_SOURCE_TIMEOUT_MS) {
            ext.source.getMangaList(Search.filtersFor(ext.source, query), 1).mangas.take(6)
                .map { FeedItem(ext.source.id, it.key, it.title, it.cover, ext.displayName) }
        }.orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        emptyList()
    }

    private fun <T> interleave(lists: List<List<T>>): List<T> {
        val out = mutableListOf<T>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let { out += it } }
        return out
    }

    /** Looks for new chapters in the library's novels. */
    suspend fun checkLibraryUpdates() = coroutineScope {
        val gate = kotlinx.coroutines.sync.Semaphore(4)
        Riwaya.library.libraryList().map { n ->
            async(Dispatchers.IO) {
                gate.acquire()
                try {
                    val source = Riwaya.extensions.source(n.sourceId) ?: return@async
                    val chapters = withTimeoutOrNull(20_000) { source.getChapterList(n.toInfo(), emptyList()) } ?: return@async
                    if (chapters.isNotEmpty()) {
                        Riwaya.library.cacheChapters(n.id, chapters)
                        Riwaya.library.setChapterCount(n.sourceId, n.key, chapters.size, markNew = true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                } finally {
                    gate.release()
                }
            }
        }.awaitAll()
    }

    companion object {
        private const val STALE_MS = 6 * 60 * 60 * 1000L
    }
}

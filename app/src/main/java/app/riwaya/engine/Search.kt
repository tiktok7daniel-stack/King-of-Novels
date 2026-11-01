package app.riwaya.engine

import ireader.core.source.CatalogSource
import ireader.core.source.model.Filter
import ireader.core.source.model.MangaInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/** One source's answer to a global search. */
data class SourceResult(
    val ext: NovelExt,
    val novels: List<MangaInfo> = emptyList(),
    val state: State = State.LOADING,
    val ms: Long = 0,
    val error: Health.Reason = Health.Reason.NONE,
) {
    enum class State { LOADING, DONE, EMPTY, FAILED, TIMEOUT }
}

/** The same novel on several sources, shown as one card. */
data class MergedNovel(val title: String, val cover: String, val hits: List<Pair<NovelExt, MangaInfo>>)

object Search {

    const val PER_SOURCE_TIMEOUT_MS = 8_000L

    /** The source's filters with [query] in its title box. */
    fun filtersFor(source: CatalogSource, query: String): List<Filter<*>> {
        val filters = source.getFilters()
        val title = filters.firstOrNull { it is Filter.Title } ?: filters.firstOrNull { it is Filter.Text }
        if (title is Filter.Text) title.value = query
        return if (title == null) filters + Filter.Title().apply { value = query } else filters
    }

    /**
     * Searches every one of [exts] at once (a few at a time) and sends each source's answer as soon as it comes; a
     * source that takes longer than 8 seconds is marked and never holds up the others.
     */
    fun global(exts: List<NovelExt>, query: String, health: Health?): Flow<SourceResult> = channelFlow {
        val gate = Semaphore(PARALLEL)
        exts.forEach { ext ->
            launch(Dispatchers.IO) {
                gate.withPermit {
                    val start = System.currentTimeMillis()
                    val result = try {
                        val list = withTimeout(PER_SOURCE_TIMEOUT_MS) {
                            ext.source.getMangaList(filtersFor(ext.source, query), 1).mangas
                        }.filter { it.title.isNotBlank() && it.key.isNotBlank() }
                        val ms = System.currentTimeMillis() - start
                        health?.record(ext.source.id, true, ms, null)
                        SourceResult(ext, list, if (list.isEmpty()) SourceResult.State.EMPTY else SourceResult.State.DONE, ms)
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        SourceResult(ext, state = SourceResult.State.TIMEOUT, ms = PER_SOURCE_TIMEOUT_MS, error = Health.Reason.TIMEOUT)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        val ms = System.currentTimeMillis() - start
                        health?.record(ext.source.id, false, ms, e)
                        SourceResult(ext, state = SourceResult.State.FAILED, ms = ms, error = Health.reasonOf(e))
                    }
                    send(result)
                }
            }
        }
    }

    /** A title reduced to its letters and digits, so the same novel matches across sources. */
    fun normalize(title: String): String =
        title.lowercase()
            .replace(Regex("[\\u064B-\\u0652\\u0640]"), "") // Arabic diacritics and tatweel
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي')
            .filter { it.isLetterOrDigit() }

    /** Novels found on two sources or more. */
    fun merge(results: List<SourceResult>): List<MergedNovel> =
        results.flatMap { r -> r.novels.map { r.ext to it } }
            .groupBy { normalize(it.second.title) }
            .filter { (k, v) -> k.length >= 3 && v.map { it.first.pkg }.distinct().size >= 2 }
            .map { (_, v) -> MergedNovel(v.first().second.title, v.firstOrNull { it.second.cover.isNotBlank() }?.second?.cover.orEmpty(), v.distinctBy { it.first.pkg }) }
            .sortedByDescending { it.hits.size }

    private const val PARALLEL = 6
}

package app.riwaya.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import app.riwaya.Riwaya
import app.riwaya.engine.Content
import ireader.core.source.model.ChapterInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Work that runs while the reader does something else: downloads and translating chapters ahead. */
data class JobState(
    val id: Long,
    val novelTitle: String,
    val kind: Kind,
    val total: Int,
    val done: Int = 0,
    val failed: Int = 0,
    val finished: Boolean = false,
) {
    enum class Kind { DOWNLOAD, TRANSLATE, PREFETCH }
}

object Jobs {

    private val _jobs = MutableStateFlow<List<JobState>>(emptyList())
    val jobs: StateFlow<List<JobState>> = _jobs.asStateFlow()
    private val running = HashMap<Long, Job>()
    private val gate = Semaphore(2)
    private val prefetched = HashSet<String>()

    /** Downloads [chapters] (and translates them when [translate]). */
    fun run(novel: SavedNovel, chapters: List<ChapterInfo>, translate: Boolean, kind: JobState.Kind = if (translate) JobState.Kind.TRANSLATE else JobState.Kind.DOWNLOAD) {
        if (chapters.isEmpty()) return
        val id = System.nanoTime()
        _jobs.update { listOf(JobState(id, novel.title, kind, chapters.size)) + it.filter { j -> !j.finished }.take(30) }
        running[id] = Riwaya.scope.launch(Dispatchers.IO) {
            gate.withPermit {
                for (ch in chapters) {
                    val ok = try {
                        process(novel, ch, translate)
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Log.w("RiwayaJobs", "Chapter ${ch.name} not done", e)
                        false
                    }
                    _jobs.update { list -> list.map { if (it.id == id) it.copy(done = it.done + 1, failed = it.failed + if (ok) 0 else 1) else it } }
                }
            }
            _jobs.update { list -> list.map { if (it.id == id) it.copy(finished = true) else it } }
            running.remove(id)
        }
    }

    private suspend fun process(novel: SavedNovel, chapter: ChapterInfo, translate: Boolean) {
        val lib = Riwaya.library
        val text = lib.cachedText(novel.id, chapter.key) ?: run {
            val source = Riwaya.extensions.source(novel.sourceId) ?: error("Source not installed")
            Content.fetch(source, chapter).also { if (it.isNotEmpty()) lib.cacheText(novel.id, chapter.key, it) }
        }
        if (text.isEmpty()) error("Empty")
        if (translate && needsTranslation(novel, text)) {
            Riwaya.translator.translateAll(novel.id, novel.title, chapter.key, text, novel.lang)
        }
    }

    fun needsTranslation(novel: SavedNovel, text: List<String>): Boolean {
        val target = Riwaya.translator.target()
        return if (target == "ar") !Content.isArabic(text.take(6).joinToString(" ")) else novel.lang != target
    }

    /** Translates the chapter after [index] in the background, once, when the reader is far enough into this one. */
    fun prefetch(context: Context, novel: SavedNovel, chapters: List<ChapterInfo>, index: Int) {
        val st = Riwaya.settings
        if (!st.prefetchNext.get()) return
        val next = chapters.getOrNull(index + 1) ?: return
        val key = novel.id + "|" + next.key
        if (!prefetched.add(key)) return
        if (st.prefetchWifiOnly.get() && !unmetered(context)) return
        if (Riwaya.translator.isCached(novel.id, next.key)) return
        run(novel, listOf(next), translate = true, kind = JobState.Kind.PREFETCH)
    }

    fun cancel(id: Long) {
        running.remove(id)?.cancel()
        _jobs.update { list -> list.map { if (it.id == id) it.copy(finished = true) else it } }
    }

    fun clearFinished() = _jobs.update { list -> list.filter { !it.finished } }

    private fun unmetered(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}

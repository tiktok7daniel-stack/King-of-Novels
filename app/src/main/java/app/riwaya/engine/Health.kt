package app.riwaya.engine

import android.content.Context
import app.riwaya.core.readOrNull
import app.riwaya.core.writeAtomic
import ireader.core.source.CatalogSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Whether each source really works, checked the way the app uses it: its list opens and a search finds something.
 * Green works, amber is slow or cannot search, red is broken with the reason. The last answers are kept.
 */
class Health(private val context: Context) {

    enum class Status { WORKING, SLOW, NO_SEARCH, BROKEN }

    /** Why a source is broken, shown under its name. */
    enum class Reason { NONE, CHALLENGE, TIMEOUT, NETWORK, EMPTY, ERROR }

    @Serializable
    data class Check(val status: Status, val at: Long, val ms: Long = 0, val reason: Reason = Reason.NONE)

    private val json = Json { ignoreUnknownKeys = true }
    private val saver = MapSerializer(String.serializer(), Check.serializer())
    private val file get() = File(context.filesDir, "source_health.json")

    private val _checks = MutableStateFlow<Map<Long, Check>>(emptyMap())
    val checks: StateFlow<Map<Long, Check>> = _checks.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    init {
        val saved = runCatching { json.decodeFromString(saver, file.readOrNull() ?: "{}") }.getOrNull().orEmpty()
        _checks.value = saved.mapNotNull { (k, v) -> k.toLongOrNull()?.let { it to v } }.toMap()
    }

    private fun save() = runCatching { file.writeAtomic(json.encodeToString(saver, _checks.value.mapKeys { it.key.toString() })) }

    suspend fun checkAll(sources: List<CatalogSource>) {
        if (_running.value) return
        _running.value = true
        try {
            coroutineScope {
                val gate = Semaphore(PARALLEL)
                sources.map { source ->
                    async(Dispatchers.IO) {
                        gate.withPermit {
                            val check = checkOne(source)
                            _checks.update { it + (source.id to check) }
                        }
                    }
                }.awaitAll()
            }
            withContext(Dispatchers.IO) { save() }
            applyAutoDisable(sources.map { it.id })
        } finally {
            _running.value = false
        }
    }

    /**
     * Switches off the checked sources that do not work, and back on the ones that work again (unless the reader keeps
     * one on by hand).
     */
    private fun applyAutoDisable(checked: List<Long>) {
        val st = app.riwaya.Riwaya.settings
        if (!st.autoHideBroken.get()) return
        val keep = st.keepOn.get()
        var auto = st.autoDisabled.get()
        checked.forEach { id ->
            val key = id.toString()
            val broken = _checks.value[id]?.status == Status.BROKEN
            auto = if (broken && key !in keep) auto + key else auto - key
        }
        st.autoDisabled.set(auto)
        st.lastHealthRun.set(System.currentTimeMillis().toString())
    }

    suspend fun checkOne(source: CatalogSource): Check {
        val start = System.currentTimeMillis()
        val list = attempt { source.getMangaList(source.getListings().firstOrNull(), 1).mangas.size }
        val ms = System.currentTimeMillis() - start
        val search = if ((list.first ?: 0) > 0) attempt { source.getMangaList(Search.filtersFor(source, PROBE), 1).mangas.size } else null to Reason.NONE
        val status = when {
            (list.first ?: 0) == 0 -> Status.BROKEN
            (search.first ?: 0) == 0 -> Status.NO_SEARCH
            ms > SLOW_MS -> Status.SLOW
            else -> Status.WORKING
        }
        val reason = if (status == Status.BROKEN) list.second.takeIf { it != Reason.NONE } ?: Reason.EMPTY else Reason.NONE
        return Check(status, System.currentTimeMillis(), ms, reason)
    }

    fun record(sourceId: Long, ok: Boolean, ms: Long, error: Throwable?) {
        val prev = _checks.value[sourceId]
        val check = if (ok) {
            Check(if (ms > SLOW_MS) Status.SLOW else prev?.status?.takeIf { it == Status.NO_SEARCH } ?: Status.WORKING, System.currentTimeMillis(), ms)
        } else {
            Check(Status.BROKEN, System.currentTimeMillis(), ms, reasonOf(error))
        }
        _checks.update { it + (sourceId to check) }
    }

    private suspend fun attempt(block: suspend () -> Int): Pair<Int?, Reason> = try {
        withTimeout(TIMEOUT_MS) { block() } to Reason.NONE
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        null to Reason.TIMEOUT
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null to reasonOf(e)
    }

    companion object {
        private const val PROBE = "a"
        private const val TIMEOUT_MS = 15_000L
        const val SLOW_MS = 5_000L
        private const val PARALLEL = 4

        fun reasonOf(e: Throwable?): Reason {
            val m = (e?.message.orEmpty() + " " + e?.cause?.message.orEmpty()).lowercase()
            return when {
                e == null -> Reason.NONE
                "challenge" in m || "cloudflare" in m || "403" in m || "503" in m || "captcha" in m -> Reason.CHALLENGE
                e is kotlinx.coroutines.TimeoutCancellationException || "timeout" in m || "timed out" in m -> Reason.TIMEOUT
                e is java.net.UnknownHostException || e is java.net.ConnectException || "unable to resolve" in m -> Reason.NETWORK
                else -> Reason.ERROR
            }
        }
    }
}

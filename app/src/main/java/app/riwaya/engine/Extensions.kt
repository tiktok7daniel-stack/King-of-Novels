package app.riwaya.engine

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import app.riwaya.core.Net
import app.riwaya.core.Settings
import app.riwaya.engine.builtin.FreeKolNovel
import dalvik.system.DelegateLastClassLoader
import ireader.core.source.CatalogSource
import ireader.core.source.Dependencies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File

/** A novel source on the phone: an IReader extension, or one built into the app. */
data class NovelExt(
    val pkg: String,
    val name: String,
    val displayName: String,
    val versionCode: Long,
    val versionName: String,
    val lang: String,
    val nsfw: Boolean,
    val source: CatalogSource,
    val iconUrl: String,
    val builtIn: Boolean,
    val kind: SourceKind,
)

/** A novel extension a repository offers. */
data class AvailableExt(
    val pkg: String,
    val name: String,
    val displayName: String,
    val versionCode: Long,
    val versionName: String,
    val lang: String,
    val nsfw: Boolean,
    val description: String,
    val apkUrl: String,
    val iconUrl: String,
)

/**
 * IReader's novel extensions: installed from the repositories the reader adds, kept in the app's own folder (no system
 * install prompt) and loaded as IReader loads them: the class named in `source.class`, built with the [Dependencies]
 * (network and settings) the app gives it. Extensions installed on the phone as apps load too.
 */
class Extensions(
    private val context: Context,
    private val settings: Settings,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _installed = MutableStateFlow<List<NovelExt>>(emptyList())
    val installed: StateFlow<List<NovelExt>> = _installed.asStateFlow()

    private val _available = MutableStateFlow<List<AvailableExt>>(emptyList())
    val available: StateFlow<List<AvailableExt>> = _available.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Package being installed right now, for the progress shown on its row. */
    private val _installing = MutableStateFlow<Set<String>>(emptySet())
    val installing: StateFlow<Set<String>> = _installing.asStateFlow()

    private val dir get() = File(context.filesDir, "novel_exts").apply { mkdirs() }
    private val lock = Mutex()
    private val clients by lazy { NovelHttpClients(Net.client) }
    private val iconCache = mutableMapOf<String, String>()

    init {
        scope.launch(Dispatchers.IO) {
            if (!settings.reposSeeded.get()) {
                settings.repos.set(settings.repos.get() + IREADER_REPO)
                settings.reposSeeded.set(true)
            }
            reload()
            refreshAvailable()
        }
    }

    fun sources(): List<CatalogSource> = _installed.value.map { it.source }

    /** Installed sources the reader has not switched off. */
    fun enabled(): List<NovelExt> {
        val off = settings.disabledSources.get()
        return _installed.value.filter { it.source.id.toString() !in off }
    }

    fun source(id: Long): CatalogSource? = _installed.value.firstOrNull { it.source.id == id }?.source

    fun ext(sourceId: Long): NovelExt? = _installed.value.firstOrNull { it.source.id == sourceId }

    suspend fun reload() = lock.withLock {
        withContext(Dispatchers.IO) {
            val loaded = runCatching { loadAll() }
                .onFailure { Log.e(TAG, "Novel extensions not loaded", it) }
                .getOrDefault(emptyList())
            _installed.value = (builtIns() + loaded).sortedWith(compareBy({ it.lang != "ar" }, { it.displayName }))
        }
    }

    // ---- Repositories ----

    fun repos(): Set<String> = settings.repos.get()

    fun addRepo(url: String): Boolean {
        val clean = normalizeRepo(url) ?: return false
        settings.repos.set(settings.repos.get() + clean)
        scope.launch(Dispatchers.IO) { refreshAvailable() }
        return true
    }

    fun removeRepo(url: String) {
        settings.repos.set(settings.repos.get() - url)
        scope.launch(Dispatchers.IO) { refreshAvailable() }
    }

    suspend fun refreshAvailable() = withContext(Dispatchers.IO) {
        _loading.value = true
        try {
            _available.value = repos().flatMap { repo ->
                runCatching { fetchIndex(repo) }
                    .onFailure { Log.w(TAG, "Repo $repo not read", it) }
                    .getOrDefault(emptyList())
            }.distinctBy { it.pkg }.sortedWith(compareBy({ it.lang != "ar" }, { it.displayName.lowercase() }))
            _available.value.forEach { iconCache[it.pkg] = it.iconUrl }
            // Icons for the extensions already loaded
            _installed.value = _installed.value.map { e -> if (e.iconUrl.isEmpty()) e.copy(iconUrl = iconCache[e.pkg].orEmpty()) else e }
        } finally {
            _loading.value = false
        }
    }

    private fun fetchIndex(repo: String): List<AvailableExt> {
        val body = Net.client.newCall(Request.Builder().url("$repo/index.min.json").build()).execute().use {
            if (!it.isSuccessful) error("HTTP ${it.code}")
            it.body.string()
        }
        return json.decodeFromString<List<IndexEntry>>(body).map {
            AvailableExt(
                pkg = it.pkg,
                name = it.name,
                displayName = SourceCatalog.displayName(it.pkg, it.lang, it.name),
                versionCode = it.code,
                versionName = it.version,
                lang = it.lang,
                nsfw = it.nsfw,
                description = it.description,
                apkUrl = "$repo/apk/${it.apk}",
                iconUrl = "$repo/icon/${it.pkg}.png",
            )
        }
    }

    @Serializable
    private data class IndexEntry(
        val name: String,
        val pkg: String,
        val apk: String,
        val lang: String = "",
        val code: Long = 0,
        val version: String = "",
        val nsfw: Boolean = false,
        val description: String = "",
    )

    // ---- Install ----

    suspend fun install(ext: AvailableExt, reloadAfter: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "${ext.pkg}.apk")
        _installing.value = _installing.value + ext.pkg
        try {
            Net.client.newCall(Request.Builder().url(ext.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                tmp.outputStream().use { out -> response.body.byteStream().copyTo(out) }
            }
            val info = context.packageManager.getPackageArchiveInfo(tmp.absolutePath, FLAGS)
                ?.takeIf { isExtension(it) } ?: error("Not a novel extension")
            check(info.packageName == ext.pkg) { "Package ${info.packageName} is not ${ext.pkg}" }
            val target = File(dir, "${ext.pkg}.$EXT")
            if (target.exists()) {
                target.setWritable(true)
                target.delete()
            }
            tmp.copyTo(target, overwrite = true)
            target.setReadOnly()
            if (!reloadAfter) return@withContext true
            reload()
            _installed.value.any { it.pkg == ext.pkg }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Extension ${ext.pkg} not installed", e)
            false
        } finally {
            tmp.delete()
            _installing.value = _installing.value - ext.pkg
        }
    }

    /** Installs every extension offered in [langs] that is not on the phone yet; returns how many installed. */
    suspend fun installAll(langs: Set<String>, nsfw: Boolean, onProgress: (Int, Int) -> Unit = { _, _ -> }): Int {
        if (_available.value.isEmpty()) refreshAvailable()
        val have = _installed.value.map { it.pkg }.toSet()
        val todo = _available.value.filter { it.pkg !in have && it.lang in langs && (nsfw || !it.nsfw) }
        todo.forEachIndexed { i, ext ->
            onProgress(i, todo.size)
            install(ext, reloadAfter = false)
        }
        reload()
        onProgress(todo.size, todo.size)
        val wanted = todo.map { it.pkg }.toSet()
        return _installed.value.count { it.pkg in wanted }
    }

    suspend fun uninstall(pkg: String) {
        withContext(Dispatchers.IO) {
            File(dir, "$pkg.$EXT").apply { setWritable(true) }.delete()
        }
        reload()
    }

    fun updates(): Map<String, AvailableExt> {
        val byPkg = _available.value.associateBy { it.pkg }
        return _installed.value.filter { !it.builtIn }.mapNotNull { ext ->
            byPkg[ext.pkg]?.takeIf { it.versionCode > ext.versionCode }?.let { ext.pkg to it }
        }.toMap()
    }

    // ---- Loading (as IReader's catalog loader) ----

    private fun builtIns(): List<NovelExt> = listOf(FreeKolNovel()).map { s ->
        NovelExt(
            pkg = "riwaya.freekolnovel.ar",
            name = s.name,
            displayName = SourceCatalog.displayName("ireader.freekolnovel.ar", s.lang, s.name),
            versionCode = 1,
            versionName = "1.0",
            lang = s.lang,
            nsfw = false,
            source = s,
            iconUrl = "",
            builtIn = true,
            kind = SourceKind.TRANSLATED,
        )
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun loadAll(): List<NovelExt> {
        val pm = context.packageManager
        val shared = runCatching {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(FLAGS).filter { isExtension(it) }
        }.getOrDefault(emptyList())
        val private = dir.listFiles().orEmpty()
            .filter { it.isFile && it.extension == EXT }
            .mapNotNull { file ->
                if (file.canWrite()) file.setReadOnly()
                pm.getPackageArchiveInfo(file.absolutePath, FLAGS)
                    ?.also { it.applicationInfo?.fixBasePaths(file.absolutePath) }
                    ?.takeIf { isExtension(it) }
            }
        val all = (shared + private)
            .groupBy { it.packageName }
            .map { (_, both) -> both.maxBy { PackageInfoCompat.getLongVersionCode(it) } }
        return all.mapNotNull { load(it) }
    }

    private fun load(info: PackageInfo): NovelExt? {
        val app = info.applicationInfo ?: return null
        val meta = app.metaData ?: return null
        val versionName = info.versionName ?: return null
        val lib = versionName.substringBefore('.').toIntOrNull()
        if (lib == null || lib !in LIB_VERSIONS) {
            Log.w(TAG, "Extension ${info.packageName}: lib $lib not supported")
            return null
        }
        val cls = meta.getString(META_CLASS)?.trim()?.let { if (it.startsWith(".")) info.packageName + it else it } ?: return null
        val source = try {
            val loader = DelegateLastClassLoader(app.sourceDir, null, context.classLoader)
            val deps = Dependencies(clients, NovelPreferenceStore(context, info.packageName))
            Class.forName(cls, false, loader).getConstructor(Dependencies::class.java).newInstance(deps) as? CatalogSource
        } catch (e: Throwable) {
            Log.e(TAG, "Extension ${info.packageName}: $cls not loaded", e)
            null
        } ?: return null
        return NovelExt(
            pkg = info.packageName,
            name = source.name,
            displayName = SourceCatalog.displayName(info.packageName, source.lang, source.name),
            versionCode = PackageInfoCompat.getLongVersionCode(info),
            versionName = versionName,
            lang = source.lang,
            nsfw = meta.getInt(META_NSFW, 0) == 1,
            source = source,
            iconUrl = iconCache[info.packageName].orEmpty(),
            builtIn = false,
            kind = SourceCatalog.kind(info.packageName, source.name),
        )
    }

    private fun isExtension(info: PackageInfo): Boolean = info.reqFeatures.orEmpty().any { it.name == FEATURE }

    private fun ApplicationInfo.fixBasePaths(apkPath: String) {
        if (sourceDir == null) sourceDir = apkPath
        if (publicSourceDir == null) publicSourceDir = apkPath
    }

    companion object {
        const val IREADER_REPO = "https://raw.githubusercontent.com/IReaderorg/IReader-extensions/repov2"
        private const val TAG = "RiwayaExt"
        private const val FEATURE = "ireader"
        private const val META_CLASS = "source.class"
        private const val META_NSFW = "source.nsfw"
        private const val EXT = "ext"
        private val LIB_VERSIONS = 1..2
        private const val FLAGS = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA

        fun normalizeRepo(url: String): String? {
            val u = url.trim().removeSuffix("/index.min.json").removeSuffix("/index.json").trimEnd('/')
            return u.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        }
    }
}

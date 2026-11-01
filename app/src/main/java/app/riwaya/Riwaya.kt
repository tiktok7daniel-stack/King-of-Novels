package app.riwaya

import android.app.Application
import android.content.Context
import app.riwaya.core.Net
import app.riwaya.core.Settings
import app.riwaya.data.HomeFeed
import app.riwaya.data.Library
import app.riwaya.engine.Extensions
import app.riwaya.engine.Health
import app.riwaya.listen.Player
import app.riwaya.translate.Translator
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The app's parts, made once when the app starts. */
object Riwaya {
    lateinit var context: Context
        private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var settings: Settings
        private set
    lateinit var extensions: Extensions
        private set
    lateinit var library: Library
        private set
    lateinit var health: Health
        private set
    lateinit var translator: Translator
        private set
    lateinit var home: HomeFeed
        private set

    fun init(app: Application) {
        context = app
        Net.init(app)
        settings = Settings(app)
        library = Library(app, scope)
        health = Health(app)
        translator = Translator(app, settings)
        extensions = Extensions(app, settings, scope)
        home = HomeFeed(app)
        Player.init(app)
        // Arabic and English are the languages kept by default (asked for by the owner)
        if (!settings.englishAdded.get()) {
            settings.languages.set(settings.languages.get() + "en")
            settings.englishAdded.set(true)
        }
        // Sources are checked again every 12 hours; the broken ones are switched off, the fixed ones back on
        scope.launch {
            extensions.installed.first { it.isNotEmpty() }
            delay(4_000)
            val last = settings.lastHealthRun.get().toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - last > 12 * 60 * 60 * 1000L) checkSources()
        }
    }

    /** Checks every installed source in the reader's languages (also the ones switched off for being broken). */
    suspend fun checkSources() {
        val langs = settings.languages.get() + "multi"
        val off = settings.disabledSources.get()
        health.checkAll(extensions.installed.value.filter { it.lang in langs && it.source.id.toString() !in off }.map { it.source })
    }
}

class RiwayaApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Riwaya.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { Net.client })) }
            .crossfade(true)
            .build()
}

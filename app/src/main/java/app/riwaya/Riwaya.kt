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

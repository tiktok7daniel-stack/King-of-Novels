package app.riwaya

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import app.riwaya.ui.App
import app.riwaya.ui.Nav
import app.riwaya.ui.Route
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        handle(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_LISTEN, false) == true && Nav.stack.lastOrNull() != Route.ListenFull) Nav.open(Route.ListenFull)
        // A new-chapters notification opens its novel
        val key = intent?.getStringExtra(EXTRA_KEY)
        if (key != null) {
            Nav.open(Route.Novel(intent.getLongExtra(EXTRA_SOURCE, 0), key, intent.getStringExtra(EXTRA_TITLE).orEmpty(), intent.getStringExtra(EXTRA_COVER).orEmpty()))
            intent.removeExtra(EXTRA_KEY)
        }
    }

    fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (volumePaging && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            _volumeKeys.tryEmit(if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_OPEN_LISTEN = "open_listen"
        const val EXTRA_SOURCE = "novel_source"
        const val EXTRA_KEY = "novel_key"
        const val EXTRA_TITLE = "novel_title"
        const val EXTRA_COVER = "novel_cover"

        /** Set by the reader while volume-key paging is on. */
        @Volatile var volumePaging = false
        private val _volumeKeys = MutableSharedFlow<Int>(extraBufferCapacity = 4)
        val volumeKeys: SharedFlow<Int> = _volumeKeys.asSharedFlow()
    }
}

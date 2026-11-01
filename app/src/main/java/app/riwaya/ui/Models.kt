package app.riwaya.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** A screen's state that outlives its composition (going deeper and coming back keeps it). */
abstract class ScreenModel {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    open fun onClear() = scope.cancel()
}

object Models {
    private val map = HashMap<Any, ScreenModel>()

    @Suppress("UNCHECKED_CAST")
    fun <T : ScreenModel> get(key: Any, create: () -> T): T = map.getOrPut(key) { create() } as T

    fun clear(key: Any) {
        map.remove(key)?.onClear()
    }
}

@Composable
fun <T : ScreenModel> model(key: Any, create: () -> T): T = remember(key) { Models.get(key, create) }

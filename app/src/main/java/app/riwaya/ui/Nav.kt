package app.riwaya.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

enum class Tab { HOME, DISCOVER, LIBRARY, LISTEN, MORE }

/** The screens that open over the tabs. */
sealed interface Route {
    data class Novel(val sourceId: Long, val key: String, val title: String, val cover: String) : Route
    data class Reader(val sourceId: Long, val key: String, val chapterKey: String) : Route
    data class Search(val query: String = "") : Route
    data class SourceBrowse(val sourceId: Long, val query: String = "") : Route
    data class Glossary(val sourceId: Long, val key: String, val title: String) : Route
    data class Web(val url: String) : Route
    data object Sources : Route
    data object Settings : Route
    data object History : Route
    data object Jobs : Route
    data object ListenFull : Route
}

/** A small back stack: the current tab, and the screens opened over it. */
object Nav {
    var tab by mutableStateOf(Tab.HOME)
    val stack = mutableStateListOf<Route>()

    fun open(route: Route) {
        stack.add(route)
    }

    fun replace(route: Route) {
        if (stack.isNotEmpty()) pop()
        stack.add(route)
    }

    private fun pop() {
        val top = stack.removeAt(stack.lastIndex)
        if (top !in stack) Models.clear(top)
    }

    fun back(): Boolean {
        if (stack.isNotEmpty()) {
            pop()
            return true
        }
        if (tab != Tab.HOME) {
            tab = Tab.HOME
            return true
        }
        return false
    }

    fun goTab(t: Tab) {
        while (stack.isNotEmpty()) pop()
        tab = t
    }
}

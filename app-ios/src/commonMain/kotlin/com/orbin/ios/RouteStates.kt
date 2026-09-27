package com.orbin.ios

/**
 * Which screens keep their saved state (scroll positions above all) once they are off screen, the
 * way Android's navigation keeps each back-stack entry's.
 *
 * Every route on the back stack keeps it, so coming back from a thread finds the feed where it was
 * and coming back from the viewer finds the thread where it was. The tabs always keep it. Catalogs
 * and threads that have left the stack keep it too, for the [limit] most recently visited, so
 * reopening one returns to where it was read to; older ones are forgotten.
 */
internal class RouteStates(
    private val limit: Int = KEPT_ROUTES,
) {
    // Least recently visited first.
    private val visited = LinkedHashSet<String>()

    /** Records [backStack] as visited and returns the keys whose state can now be discarded. */
    fun visit(backStack: List<Route>): List<String> {
        backStack.filterNot { it.isTab }.forEach { route ->
            val key = route.stateKey
            visited.remove(key)
            visited.add(key)
        }
        val onStack = backStack.mapTo(HashSet()) { it.stateKey }
        val evicted = mutableListOf<String>()
        val iterator = visited.iterator()
        var excess = visited.size - limit
        while (excess > 0 && iterator.hasNext()) {
            val key = iterator.next()
            if (key !in onStack) {
                iterator.remove()
                evicted += key
                excess--
            }
        }
        return evicted
    }

    private companion object {
        const val KEPT_ROUTES = 30
    }
}

/** The key a route's saved state is kept under. */
internal val Route.stateKey: String
    get() = toString()

private val Route.isTab: Boolean
    get() = this == Route.Feed || this == Route.Boards || this == Route.Downloads

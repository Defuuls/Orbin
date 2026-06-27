package com.orbin.core.common.dispatchers

import javax.inject.Qualifier

/**
 * Qualifiers for injecting specific [kotlinx.coroutines.CoroutineDispatcher]s. Injecting
 * dispatchers (rather than referencing `Dispatchers.IO` directly) keeps suspend code testable —
 * tests swap in a single test dispatcher.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val type: OrbinDispatcher)

enum class OrbinDispatcher { Default, IO }

/** Marks the application-scoped [kotlinx.coroutines.CoroutineScope]. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

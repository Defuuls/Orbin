package com.orbin.ios

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class PageLoad {
    var job: Job? = null
    var generation = 0
}

internal class BrowserPageLoader(
    private val scope: CoroutineScope,
) {
    fun <T> load(
        page: PageLoad,
        target: MutableStateFlow<Load<T>>,
        refreshing: MutableStateFlow<Boolean>,
        refreshFailure: MutableStateFlow<Load.Failed?>,
        retainContent: Boolean,
        preparePublication: suspend (T) -> (() -> Unit) = { {} },
        fetch: suspend () -> T,
    ) {
        page.job?.cancel()
        val generation = ++page.generation
        val retained = target.value.takeIf { retainContent && it is Load.Ready }
        refreshFailure.value = null
        refreshing.value = retained != null
        if (retained == null) target.value = Load.Loading
        page.job =
            scope.launch {
                suspend fun publishFailure(
                    error: Exception,
                    contentBlocked: Boolean = false,
                ) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (generation != page.generation) return
                    val failure = Load.Failed(error.readable(), error.browserFailureCause(), contentBlocked)
                    if (retained == null) target.value = failure else refreshFailure.value = failure
                }
                try {
                    val value = fetch()
                    val publishPrepared = preparePublication(value)
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (generation != page.generation) return@launch
                    publishPrepared()
                    target.value = Load.Ready(value)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (blocked: ContentBlocked) {
                    publishFailure(blocked, contentBlocked = true)
                } catch (
                    @Suppress("TooGenericExceptionCaught") error: Exception,
                ) {
                    publishFailure(error)
                } finally {
                    if (generation == page.generation) refreshing.value = false
                }
            }
    }
}

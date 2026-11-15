package com.orbin.ios

import com.orbin.provider.api.ImageBoardProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Each source publishes independently; refresh generations cannot replace a newer directory. */
internal class BrowserBoardDirectory(
    private val providers: List<ImageBoardProvider>,
    private val scope: CoroutineScope,
) {
    val boards = MutableStateFlow<Load<List<SiteBoard>>>(Load.Loading)
    val unreachable = MutableStateFlow<List<String>>(emptyList())
    val directory =
        MutableStateFlow(
            HomeFeedDirectory(loading = providers.mapTo(mutableSetOf()) { it.metadata.id }),
        )
    private var boardLoadJob: Job? = null
    private var boardLoadGeneration = 0
    private val boardPublication = Mutex()

    fun load() {
        boardLoadJob?.cancel()
        val generation = ++boardLoadGeneration
        boards.value = Load.Loading
        directory.update {
            it.copy(
                loading = providers.mapTo(mutableSetOf()) { provider -> provider.metadata.id },
                failures = emptyMap(),
            )
        }
        boardLoadJob =
            scope.launch {
                providers
                    .map { provider ->
                        async {
                            val result =
                                try {
                                    Result.success(provider.siteBoards())
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (
                                    @Suppress("TooGenericExceptionCaught") error: Exception,
                                ) {
                                    Result.failure(error)
                                }
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            boardPublication.withLock {
                                if (generation != boardLoadGeneration) return@withLock
                                publishBoardIndex(provider, result)
                            }
                        }
                    }.awaitAll()
            }
    }

    private fun publishBoardIndex(
        provider: ImageBoardProvider,
        result: Result<List<SiteBoard>>,
    ) {
        val id = provider.metadata.id
        directory.update { current ->
            if (result.isSuccess) {
                current.copy(
                    boards = current.boards.filterNot { it.provider == id } + result.getOrThrow(),
                    completed = current.completed + id,
                    loading = current.loading - id,
                    failures = current.failures - id,
                )
            } else {
                current.copy(
                    loading = current.loading - id,
                    failures =
                        current.failures + (id to result.exceptionOrNull()!!.readable()),
                )
            }
        }
        val index = directory.value
        unreachable.value =
            providers.mapNotNull { source ->
                index.failures[source.metadata.id]?.let { "${source.metadata.displayName} ($it)" }
            }
        boards.value =
            when {
                index.loading.isNotEmpty() -> Load.Loading
                index.boards.isNotEmpty() ->
                    Load.Ready(
                        providers.flatMap { provider ->
                            index.boards.filter { it.provider == provider.metadata.id }
                        },
                    )
                index.failures.isNotEmpty() -> Load.Failed(index.failures.values.first())
                else -> Load.Ready(emptyList())
            }
    }
}

package com.orbin.ios

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StagedStreamingTest {
    private class Staging {
        val staged = mutableListOf<Pair<Int, String>>()
        val released = mutableListOf<String>()

        suspend fun stage(
            bytes: ByteArray,
            name: String,
        ): String {
            staged += bytes.size to name
            return "/stage/${staged.size}/$name"
        }

        suspend fun release(path: String) {
            released += path
        }
    }

    @Test
    fun aVideoIsFetchedInProcessStagedAndReleasedWhenThePlayerLeaves() =
        runTest {
            val staging = Staging()
            val states = mutableListOf<StagedPlayback>()
            val fetched = mutableListOf<String>()
            val job =
                launch {
                    holdStagedMedia(
                        url = "https://i.example/g/clip.webm?x=1#t",
                        fetch = { url, onProgress ->
                            fetched += url
                            onProgress(1, 4)
                            onProgress(4, 4)
                            byteArrayOf(1, 2, 3, 4)
                        },
                        stage = staging::stage,
                        release = staging::release,
                        fileUrl = { "file://$it" },
                        report = states::add,
                    )
                }
            advanceUntilIdle()

            // The system player only ever sees the local copy, never the remote address.
            assertEquals(listOf("https://i.example/g/clip.webm?x=1#t"), fetched)
            assertEquals(listOf(4 to "clip.webm"), staging.staged)
            assertEquals(
                listOf(
                    StagedPlayback.Loading(null),
                    StagedPlayback.Loading(0.25f),
                    StagedPlayback.Loading(1f),
                    StagedPlayback.Ready("file:///stage/1/clip.webm"),
                ),
                states,
            )
            // Held, not deleted, for as long as the player is on screen.
            assertTrue(job.isActive)
            assertTrue(staging.released.isEmpty())

            job.cancelAndJoin()

            assertEquals(listOf("/stage/1/clip.webm"), staging.released)
        }

    @Test
    fun aFailedFetchIsReportedAndLeavesNothingStaged() =
        runTest {
            val staging = Staging()
            val states = mutableListOf<StagedPlayback>()

            holdStagedMedia(
                "https://i.example/big.mp4",
                fetch = { _, _ -> error("Response exceeds the permitted size") },
                stage = staging::stage,
                release = staging::release,
                fileUrl = { it },
                report = states::add,
            )

            assertEquals(StagedPlayback.Failed, states.last())
            assertTrue(staging.staged.isEmpty() && staging.released.isEmpty())
        }

    @Test
    fun anEmptyResponseIsAFailureNotAnEmptyFile() =
        runTest {
            val staging = Staging()
            val states = mutableListOf<StagedPlayback>()

            holdStagedMedia("https://i.example/a.mp4", { _, _ -> byteArrayOf() }, staging::stage, staging::release, {
                it
            }, states::add)

            assertEquals(StagedPlayback.Failed, states.last())
            assertTrue(staging.staged.isEmpty())
        }

    @Test
    fun aFileThatCannotBeStagedIsAFailure() =
        runTest {
            val states = mutableListOf<StagedPlayback>()

            holdStagedMedia(
                "https://i.example/a.mp4",
                fetch = { _, _ -> byteArrayOf(1) },
                stage = { _, _ -> error("disk full") },
                release = { error("nothing was staged") },
                fileUrl = { it },
                report = states::add,
            )

            assertEquals(StagedPlayback.Failed, states.last())
        }

    @Test
    fun leavingMidDownloadStagesNothing() =
        runTest {
            val staging = Staging()
            val job =
                launch {
                    holdStagedMedia(
                        "https://i.example/a.mp4",
                        fetch = { _, _ -> kotlinx.coroutines.awaitCancellation() },
                        stage = staging::stage,
                        release = staging::release,
                        fileUrl = { it },
                        report = {},
                    )
                }
            advanceUntilIdle()

            job.cancelAndJoin()

            assertTrue(staging.staged.isEmpty() && staging.released.isEmpty())
        }

    @Test
    fun anUnknownLengthReportsIndeterminateProgress() =
        runTest {
            val states = mutableListOf<StagedPlayback>()

            holdStagedMedia(
                "https://i.example/a.mp4",
                fetch = { _, onProgress ->
                    onProgress(10, null)
                    onProgress(10, 0)
                    error("stop")
                },
                stage = { _, _ -> "" },
                release = {},
                fileUrl = { it },
                report = states::add,
            )

            assertEquals(
                listOf(StagedPlayback.Loading(null), StagedPlayback.Loading(null), StagedPlayback.Loading(null)),
                states.dropLast(1),
            )
        }

    @Test
    fun aStagedNameCannotLeaveItsFolderAndKeepsItsExtension() {
        assertEquals("clip.webm", stagedMediaName("clip.webm"))
        assertEquals("passwd", stagedMediaName("../../etc/passwd"))
        assertEquals("b.mp4", stagedMediaName("a\\b.mp4"))
        assertEquals("a_b.mp4", stagedMediaName("a..b.mp4"))
        assertEquals("ab.mp4", stagedMediaName("a\u0000b.mp4"))
        assertEquals("saved-media", stagedMediaName(""))
    }

    @Test
    fun progressIsShownAsAWholePercentage() {
        assertEquals("Loading over encrypted DNS…", stagedLoadingMessage(null))
        assertEquals("Loading over encrypted DNS… 43%", stagedLoadingMessage(0.426f))
    }
}

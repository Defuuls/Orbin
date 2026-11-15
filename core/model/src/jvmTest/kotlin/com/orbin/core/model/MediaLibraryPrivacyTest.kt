package com.orbin.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLibraryPrivacyTest {
    private val record = MediaLibraryRecord("one", "fourchan", "g", fileName = "cat.jpg", storageKey = "one")
    private val safeBoard = Board(BoardId("g"), "Technology", isNsfw = false)
    private val adultBoard = safeBoard.copy(isNsfw = true)

    @Test
    fun sessionAndPermanentHidingApplyToSavedFilesAndUnknownBoards() {
        val hidden = AppSettings.Default.copy(hideNsfwBoards = true)
        assertTrue(record.isVisibleInLibrary(hidden, safeBoard))
        assertFalse(record.isVisibleInLibrary(hidden, adultBoard))
        assertFalse(record.isVisibleInLibrary(hidden, null))
        assertFalse(record.isVisibleInLibrary(hidden, safeBoard.copy(id = BoardId("x"))))
        assertFalse(
            record.copy(fileName = "gore.jpg").isVisibleInLibrary(hidden.copy(hideNsfwBoards = false), safeBoard),
        )
        assertFalse(record.isVisibleInLibrary(AppSettings.Default, safeBoard.copy(title = "gore")))
    }

    @Test
    fun coversTrackCurrentPolicyWithoutConvertingThemIntoPermanentHiding() {
        val covering =
            AppSettings.Default.copy(
                hideNsfwBoards = false,
                coverNsfwMedia = true,
                coverViolentMedia = false,
            )
        assertTrue(record.isVisibleInLibrary(covering, adultBoard))
        assertTrue(record.isCoveredInLibrary(covering, adultBoard))
        assertTrue(record.isCoveredInLibrary(covering, null))
        assertFalse(record.isCoveredInLibrary(covering, safeBoard))
        assertFalse(record.isCoveredInLibrary(covering.copy(coverNsfwMedia = false), adultBoard))
        assertTrue(
            record
                .copy(
                    fileName = "fight.mp4",
                ).isCoveredInLibrary(covering.copy(coverNsfwMedia = false, coverViolentMedia = true), safeBoard),
        )
    }
}

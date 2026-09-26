package com.orbin.uinext

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Man
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PregnantWoman
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A symbol for a board's tile in place of its path, where one fits the board's topic. Keyed by
 * site as well as board, since two sites can use the same path for different things; boards
 * without one keep the path.
 */
fun boardSymbol(
    siteId: String,
    boardId: String,
): ImageVector? = SYMBOLS[siteId]?.get(boardId.lowercase())

private val SYMBOLS: Map<String, Map<String, ImageVector>> =
    mapOf(
        "bbwchan" to
            mapOf(
                "bbw" to Icons.Outlined.PhotoCamera,
                "bbwdraw" to Icons.Outlined.Brush,
                "ssbbw" to Icons.Outlined.OpenInFull,
                "bbwalt" to Icons.AutoMirrored.Outlined.AltRoute,
                "inf" to Icons.Outlined.BubbleChart,
                "gen" to Icons.Outlined.Forum,
                "bbfurries" to Icons.Outlined.Pets,
                "booty" to Icons.Outlined.Favorite,
                "bhm" to Icons.Outlined.Man,
                "tits" to Icons.Outlined.Stars,
                "bbwai" to Icons.Outlined.SmartToy,
                "ee" to Icons.Outlined.Category,
                "elite" to Icons.AutoMirrored.Outlined.MenuBook,
                "preg" to Icons.Outlined.PregnantWoman,
            ),
    )

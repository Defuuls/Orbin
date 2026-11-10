package com.orbin.uinext

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every board has a local symbol. Known boards use a topic pictogram; any future board receives
 * a deterministic provider-qualified identity seal, so discovery never depends on an icon update.
 */
fun boardSymbol(
    siteId: String,
    boardId: String,
): ImageVector = boardIconDefinition(siteId, boardId).symbol

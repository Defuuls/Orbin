package com.orbin.core.model

/** Board metadata must belong to the record's qualified provider/board at the call site. */
fun MediaLibraryRecord.isVisibleInLibrary(
    settings: AppSettings,
    board: Board?,
): Boolean {
    val known = board?.takeIf { it.id.value == boardId }
    if (PermanentContentFilter.matchesAny(listOf(providerId, boardId, fileName))) return false
    if (known?.isPermanentlyFiltered() == true) return false
    // Missing cached metadata cannot silently classify an adult board as safe after restart/import.
    return !settings.nsfwContentHidden || known != null && !known.isNsfw
}

fun MediaLibraryRecord.isCoveredInLibrary(
    settings: AppSettings,
    board: Board?,
): Boolean =
    settings.coverNsfwMedia &&
        (board?.takeIf { it.id.value == boardId }?.isNsfw != false) ||
        settings.coverViolentMedia &&
        ViolentMediaCover.matches(fileName)

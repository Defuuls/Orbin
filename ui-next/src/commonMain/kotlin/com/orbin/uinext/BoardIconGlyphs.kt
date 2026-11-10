package com.orbin.uinext

/** Neutral, authored topic pictograms. Identity seals distinguish boards sharing a topic. */
internal enum class BoardTopic {
    THREED,
    ANIMATION,
    ART,
    DISCUSSION,
    ANIMALS,
    SHAPES,
    WORLD,
    FINANCE,
    HEART,
    FASHION,
    COOKING,
    PEOPLE,
    PORTRAIT,
    CRAFT,
    VIDEO,
    FITNESS,
    TECHNOLOGY,
    DESIGN,
    PHOTOGRAPHY,
    HISTORY,
    BOOKS,
    ROBOT,
    MUSIC,
    TRANSPORT,
    NEWS,
    AUTOMOTIVE,
    NATURE,
    SHIELD,
    COMMUNITY,
    CIVICS,
    SPORTS,
    SCIENCE,
    FANTASY,
    REQUEST,
    DOWNLOAD,
    TABLETOP,
    TOYS,
    TRAVEL,
    GAMES,
    PHONE,
    RETRO,
    STRATEGY,
    WALLPAPER,
    PARANORMAL,
    STAR,
    EXPAND,
    ALTERNATIVE,
    BALLOON,
    SPARKLE,
    HEALTH,
}

internal fun BoardTopic.pathData(): String = BOARD_TOPIC_PATHS.getValue(this)

private val BOARD_TOPIC_PATHS =
    mapOf(
        BoardTopic.THREED to "M12 2 21 7v10l-9 5-9-5V7l9-5ZM3 7l9 5 9-5M12 12v10",
        BoardTopic.ANIMATION to "M6 3h12v18H6ZM9 7h6M9 11h6M9 15l6 3M3 6h3M18 6h3M3 18h3M18 18h3",
        BoardTopic.ART to "M4 17 17 4l3 3L7 20H3l1-3ZM14 7l3 3M12 21h9",
        BoardTopic.DISCUSSION to
            "M21 11c0 4.4-4 8-9 8-1.3 0-2.5-.2-3.6-.7L3 21l1.6-5.4A7.3 7.3 0 0 1 3 11c0-4.4 4-8 9-8s9 3.6 9 8ZM8 11h8",
        BoardTopic.ANIMALS to
            "M8 13c-2 2-4 4-3 6 1 2 4 1 7 1s6 1 7-1c1-2-1-4-3-6-2-2-6-2-8 0ZM5 9v1M10 5v2M15 5v2M20 9v1",
        BoardTopic.SHAPES to "M3 4h7v7H3ZM17 3l4 8h-8l4-8ZM10 18a4 4 0 1 1-8 0a4 4 0 0 1 8 0ZM15 16l6 6M21 16l-6 6",
        BoardTopic.WORLD to "$BOARD_CIRCLE M3 12h18M12 3c5 5 5 13 0 18-5-5-5-13 0-18Z",
        BoardTopic.FINANCE to "M3 20h18M5 17v-5h3v5M11 17V8h3v9M17 17V4h3v13M4 7l5-4 4 2 7-3",
        BoardTopic.HEART to "M12 21 4 13C-2 6 7-1 12 6c5-7 14 0 8 7l-8 8Z",
        BoardTopic.FASHION to "M9 6a3 3 0 0 1 6 0c0 2-3 2-3 4M12 10 2 17c-1 1 0 3 1 3h18c1 0 2-2 1-3l-10-7Z",
        BoardTopic.COOKING to "M4 3v6c0 3 5 3 5 0V3M6.5 3v18M19 3c-4 3-4 8 0 9V3ZM19 12v9",
        BoardTopic.PEOPLE to
            "M10 7a3 3 0 1 1-6 0a3 3 0 0 1 6 0ZM15 4a3 3 0 0 1 0 6M2 21v-3c0-5 10-5 10 0v3M15 13c5 0 7 2 7 5v3",
        BoardTopic.PORTRAIT to "M4 3h16v18H4ZM15 9a3 3 0 1 1-6 0a3 3 0 0 1 6 0ZM6 19c0-6 12-6 12 0",
        BoardTopic.CRAFT to "M4 20 15 3l6 6-17 11ZM4 20l4-7 6 3M9 10l5 6M3 4l5 1M3 4l1 5",
        BoardTopic.VIDEO to "M3 7h18v14H3ZM3 7l17-5 1 5M7 6 5 3M13 5l-2-3M10 11l6 3-6 3Z",
        BoardTopic.FITNESS to "M2 9v6M5 6v12M8 9v6M8 12h8M16 9v6M19 6v12M22 9v6",
        BoardTopic.TECHNOLOGY to "M6 6h12v12H6ZM9 9h6v6H9ZM9 2v4M15 2v4M9 18v4M15 18v4M2 9h4M2 15h4M18 9h4M18 15h4",
        BoardTopic.DESIGN to
            "M3 3h5v5H3ZM16 16h5v5h-5ZM8 5c9 0 0 14 8 14" +
            "M5 8c0 9 14 0 14 8M3 19h4M5 17v4M17 3h4M19 1v4",
        BoardTopic.PHOTOGRAPHY to "M3 7h5l2-3h4l2 3h5v14H3ZM16 13a4 4 0 1 1-8 0a4 4 0 0 1 8 0ZM18 10h.01",
        BoardTopic.HISTORY to "M2 7 12 2l10 5H2ZM3 21h18M5 10v8M10 10v8M14 10v8M19 10v8",
        BoardTopic.BOOKS to "M12 5C9 2 4 2 2 3v17c3-1 7-1 10 2 3-3 7-3 10-2V3c-2-1-7-1-10 2ZM12 5v17M5 7h4M15 7h4",
        BoardTopic.ROBOT to "M5 6h14v15H5ZM12 2v4M2 10h3M19 10h3M9 10h.01M15 10h.01M8 16h8M9 21v2M15 21v2",
        BoardTopic.MUSIC to "M9 17V5l11-3v13M9 7l11-3M9 17c0 4-7 5-7 1 0-3 7-4 7-1ZM20 15c0 4-7 5-7 1 0-3 7-4 7-1Z",
        BoardTopic.TRANSPORT to "M5 3h14v15H5ZM5 12h14M8 6h8M8 15h.01M16 15h.01M7 18l-3 4M17 18l3 4M7 21h10",
        BoardTopic.NEWS to "M3 3h15v18H3ZM18 8h3v13h-3M6 6h9M6 10h4v5H6ZM13 10h2M13 13h2M6 18h9",
        BoardTopic.AUTOMOTIVE to "M3 12l3-7h12l3 7v8h-3v-3H6v3H3ZM3 12h18M6 14h2M16 14h2",
        BoardTopic.NATURE to "M3 21 10 5l4 8 3-5 5 13H3ZM7 12l3 2 2-3M15 15l2 2 2-2M3 4h.01",
        BoardTopic.SHIELD to "M12 2c-3 2-6 3-9 3v7c0 5 5 8 9 10 4-2 9-5 9-10V5c-3 0-6-1-9-3ZM8 12l3 3 5-6",
        BoardTopic.COMMUNITY to
            "M8 6a3 3 0 1 1-6 0a3 3 0 0 1 6 0ZM22 6a3 3 0 1 1-6 0a3 3 0 0 1 6 0Z" +
            "M1 15v-2c0-3 8-3 8 0M15 13c0-3 8-3 8 0v2M8 18l4 4 4-4M12 13v9",
        BoardTopic.CIVICS to "M3 8 12 3l9 5M4 8h16M5 11v8M12 11v8M19 11v8M3 22h18M12 3V1",
        BoardTopic.SCIENCE to
            "M21 12c0 3-18 3-18 0s18-3 18 0ZM17 4c3 1.5-6 17-9 15S14 2 17 4Z" +
            "M7 4c3-2 13 14 10 16S4 6 7 4ZM12 12h.01",
        BoardTopic.SPORTS to "$BOARD_CIRCLE M12 3l5 5-2 6H9L7 8l5-5ZM3 12l6 2-1 6M21 12l-6 2 1 6",
        BoardTopic.FANTASY to "M12 2 21 12l-9 10-9-10 9-10ZM12 2v20M3 12h18M6 8l12 8M18 8 6 16",
        BoardTopic.REQUEST to "M18 10a7 7 0 1 1-14 0a7 7 0 0 1 14 0ZM16 16l6 6M8 10h6M11 7v6",
        BoardTopic.DOWNLOAD to "M12 2v13M7 10l5 5 5-5M3 16v6h18v-6",
        BoardTopic.TABLETOP to "M3 3h18v18H3ZM7 7h.01M17 7h.01M12 12h.01M7 17h.01M17 17h.01",
        BoardTopic.TOYS to "M3 9h12v12H3ZM8 9V4h12v12h-5M3 9l5-5M15 9l5-5M7 14h4M9 12v4",
        BoardTopic.TRAVEL to "M3 7h18v15H3ZM8 7V3h8v4M7 7v15M17 7v15M6 22v1M18 22v1",
        BoardTopic.GAMES to
            "M7 6h10c3 0 5 11 4 13-2 2-5-4-6-4H9c-1 0-4 6-6 4C2 17 4 6 7 6ZM6 10v4M4 12h4M16 10h.01M19 13h.01",
        BoardTopic.PHONE to "M7 2h10v20H7ZM10 5h4M11 19h2",
        BoardTopic.RETRO to "M3 16h18v6H3ZM12 16V7M15 4a3 3 0 1 1-6 0a3 3 0 0 1 6 0ZM6 19h.01M18 19h.01",
        BoardTopic.STRATEGY to "M5 22h14M7 18h10l-2-8 3-6H6l3 6-2 8ZM9 10h6M12 1v4M10 2h4",
        BoardTopic.WALLPAPER to "M2 3h20v18H2ZM2 18l6-7 5 5 3-3 6 6M18 8a2 2 0 1 1-4 0a2 2 0 0 1 4 0Z",
        BoardTopic.PARANORMAL to "M5 11a7 7 0 0 1 14 0v11l-3-2-4 2-4-2-3 2V11ZM9 10h.01M15 10h.01M10 15h4",
        BoardTopic.STAR to "M12 2l3 6 7 1-5 5 1 8-6-4-6 4 1-8-5-5 7-1 3-6Z",
        BoardTopic.EXPAND to "M3 9V3h6M15 3h6v6M21 15v6h-6M9 21H3v-6M3 3l6 6M21 21l-6-6",
        BoardTopic.ALTERNATIVE to "M5 3v18M5 12h6c4 0 2-7 7-7h3M17 2l4 3-4 3M11 12c4 0 2 7 7 7h3M17 16l4 3-4 3",
        BoardTopic.BALLOON to
            "M19 9c0 5-7 10-7 10S5 14 5 9a7 7 0 0 1 14 0ZM10 19h4l-2 2M12 21c-1 1 1 2 0 3M8 8c0-2 2-3 3-3",
        BoardTopic.SPARKLE to "M12 2l3 7 7 3-7 3-3 7-3-7-7-3 7-3 3-7ZM3 3v4M1 5h4M20 19v4M18 21h4",
        BoardTopic.HEALTH to "M8 3h8v5h5v8h-5v5H8v-5H3V8h5V3Z",
    )

private const val BOARD_CIRCLE = "M21 12a9 9 0 1 1-18 0a9 9 0 0 1 18 0Z"

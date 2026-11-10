package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * A decorative topic avatar for any board, including boards added after the app was released.
 *
 * Topic art is authored locally. The provider-qualified identity determines its color and small
 * geometric seal; matching paths on 4chan and BBWChan therefore keep different visual identities.
 * The adjacent board/source label owns accessibility narration, so this art adds no duplicate text.
 */
@Composable
fun BoardIcon(
    siteId: String,
    boardId: String,
    modifier: Modifier = Modifier,
    title: String = "",
) {
    val definition = remember(siteId, boardId, title) { boardIconDefinition(siteId, boardId, title) }
    val background = (if (next.dark) DARK_BOARD_COLORS else LIGHT_BOARD_COLORS)[definition.colorIndex]
    Box(
        modifier =
            modifier
                .size(DEFAULT_BOARD_ICON_SIZE.dp)
                .clip(CircleShape)
                .background(background)
                .clearAndSetSemantics {},
    ) {
        Icon(
            imageVector = definition.symbol,
            contentDescription = null,
            tint = if (next.dark) DARK_BOARD_INK else LIGHT_BOARD_INK,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Presentation identity survives site labels, slash notation, and case differences. */
internal data class BoardIconDefinition(
    val identity: String,
    val topic: BoardTopic,
    val fingerprint: ULong,
    val colorIndex: Int,
    val providerMark: Int,
    val registered: Boolean,
    val symbol: ImageVector,
) {
    /** The inputs actually rendered by the glyph, color, provider marker, and geometric seal. */
    val visualIdentity: String
        get() = "$topic:$colorIndex:$providerMark:${fingerprint and SEAL_MASK}"
}

/** Metadata does not discover or expose boards; provider/content policy still owns all lists. */
internal fun boardIconDefinition(
    siteId: String,
    boardId: String,
    title: String = "",
): BoardIconDefinition {
    val identity = boardIconIdentity(siteId, boardId)
    return KNOWN_BOARD_DEFINITIONS[identity] ?: createBoardIconDefinition(identity, topicForTitle(title), false)
}

internal fun boardIconIdentity(
    siteId: String,
    boardId: String,
): String {
    val provider =
        when (val source = siteId.trim().lowercase()) {
            "4chan", "fourchan", "4chan (read-only example)" -> "fourchan"
            "bbwchan", "bbw chan" -> "bbwchan"
            "" -> "unlisted"
            else -> source
        }
    val board =
        boardId
            .trim()
            .trim('/')
            .lowercase()
            .ifEmpty { "board" }
    return "$provider/$board"
}

/** The explicit snapshot is semantic art coverage, never a replacement board directory. */
internal val registeredBoardIconIds: Set<String> get() = KNOWN_BOARD_TOPICS.keys

private val KNOWN_BOARD_DEFINITIONS by lazy {
    KNOWN_BOARD_TOPICS.mapValues { (identity, topic) -> createBoardIconDefinition(identity, topic, true) }
}

private fun createBoardIconDefinition(
    identity: String,
    topic: BoardTopic,
    registered: Boolean,
): BoardIconDefinition {
    val fingerprint = identityFingerprint(identity)
    val colorIndex = ((fingerprint shr SEAL_BITS) % LIGHT_BOARD_COLORS.size.toULong()).toInt()
    val providerMark =
        when (identity.substringBefore('/')) {
            "fourchan" -> FOURCHAN_MARK
            "bbwchan" -> BBWCHAN_MARK
            else -> OTHER_SITE_MARK
        }
    return BoardIconDefinition(
        identity = identity,
        topic = topic,
        fingerprint = fingerprint,
        colorIndex = colorIndex,
        providerMark = providerMark,
        registered = registered,
        symbol = boardVector(identity, topic, fingerprint, providerMark),
    )
}

/** FNV-1a over code units is explicit and stable across Kotlin/JVM and Kotlin/Native. */
private fun identityFingerprint(identity: String): ULong {
    var hash = FNV_OFFSET
    identity.forEach { character -> hash = (hash xor character.code.toULong()) * FNV_PRIME }
    return hash
}

private fun topicForTitle(title: String): BoardTopic {
    val words = title.lowercase()
    return TOPIC_HINTS.firstOrNull { (hints, _) -> hints.any(words::contains) }?.second ?: BoardTopic.SHAPES
}

private val TOPIC_HINTS =
    listOf(
        listOf("technology", "computer", "programming") to BoardTopic.TECHNOLOGY,
        listOf("food", "cooking", "recipe") to BoardTopic.COOKING,
        listOf("game", "gaming") to BoardTopic.GAMES,
        listOf("music", "audio") to BoardTopic.MUSIC,
        listOf("photo", "camera") to BoardTopic.PHOTOGRAPHY,
        listOf("wallpaper", "landscape") to BoardTopic.WALLPAPER,
        listOf("video", "film", "gif") to BoardTopic.VIDEO,
        listOf("art", "draw", "illustration") to BoardTopic.ART,
        listOf("book", "literature", "writing") to BoardTopic.BOOKS,
        listOf("sport", "fitness") to BoardTopic.SPORTS,
        listOf("animal", "nature", "pet") to BoardTopic.ANIMALS,
        listOf("travel", "outdoor") to BoardTopic.TRAVEL,
        listOf("general", "discussion", "advice") to BoardTopic.DISCUSSION,
    )

private fun boardVector(
    identity: String,
    topic: BoardTopic,
    fingerprint: ULong,
    providerMark: Int,
): ImageVector {
    val builder =
        ImageVector.Builder(
            name = "Board:$identity",
            defaultWidth = BOARD_VIEWPORT.dp,
            defaultHeight = BOARD_VIEWPORT.dp,
            viewportWidth = BOARD_VIEWPORT,
            viewportHeight = BOARD_VIEWPORT,
        )
    builder.addGroup(
        name = "topic",
        scaleX = TOPIC_SCALE,
        scaleY = TOPIC_SCALE,
        translationX = TOPIC_OFFSET_X,
        translationY = TOPIC_OFFSET_Y,
    )
    builder.addPath(
        pathData = PathParser().parsePathString(topic.pathData()).toNodes(),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = TOPIC_STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    builder.clearGroup()
    builder.addPath(
        pathData = PathParser().parsePathString(PROVIDER_MARK_PATHS.getValue(providerMark)).toNodes(),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = MARK_STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    builder.addPath(
        pathData = PathParser().parsePathString(sealPath(fingerprint)).toNodes(),
        fill = SolidColor(Color.Black),
    )
    return builder.build()
}

private fun sealPath(fingerprint: ULong): String =
    buildString {
        repeat(SEAL_BITS) { bit ->
            if (((fingerprint shr bit) and 1uL) != 0uL) {
                val x = SEAL_X + bit % SEAL_COLUMNS * SEAL_COLUMN_STEP
                val y = SEAL_Y + bit / SEAL_COLUMNS * SEAL_ROW_STEP
                append("M$x ${y}h$SEAL_CELL v$SEAL_CELL h-${SEAL_CELL}Z")
            }
        }
        // An empty path is still a valid visible fallback, even for a theoretical all-zero seal.
        if (isEmpty()) append("M12 19.2a.4.4 0 1 1-.8 0a.4.4 0 0 1 .8 0Z")
    }

private val PROVIDER_MARK_PATHS =
    mapOf(
        FOURCHAN_MARK to "M3.3 3.5h3",
        BBWCHAN_MARK to "M5.8 3.5a1.1 1.1 0 1 1-2.2 0a1.1 1.1 0 0 1 2.2 0Z",
        OTHER_SITE_MARK to "M3.3 4.4l1.4-2 1.4 2Z",
    )

private val LIGHT_BOARD_COLORS =
    listOf(
        Color(0xFFE7EFF9),
        Color(0xFFE3F2EB),
        Color(0xFFF5EADC),
        Color(0xFFF0E7F4),
        Color(0xFFF6E4E8),
        Color(0xFFE1F1F3),
        Color(0xFFEEEBDC),
        Color(0xFFE8E7F7),
        Color(0xFFF3E8DC),
        Color(0xFFE5EFE2),
        Color(0xFFF4E7F1),
        Color(0xFFE5EDF0),
    )
private val DARK_BOARD_COLORS =
    listOf(
        Color(0xFF203249),
        Color(0xFF203B30),
        Color(0xFF413425),
        Color(0xFF382B43),
        Color(0xFF442B33),
        Color(0xFF1C3940),
        Color(0xFF3A3825),
        Color(0xFF2E2D45),
        Color(0xFF413025),
        Color(0xFF2A3928),
        Color(0xFF402B3B),
        Color(0xFF27383F),
    )
private val LIGHT_BOARD_INK = Color(0xFF17212B)
private val DARK_BOARD_INK = Color(0xFFECEFF4)
private const val FNV_OFFSET = 0xCBF29CE484222325uL
private const val FNV_PRIME = 0x100000001B3uL
private const val BOARD_VIEWPORT = 24f
private const val DEFAULT_BOARD_ICON_SIZE = 40
private const val TOPIC_SCALE = 0.67f
private const val TOPIC_OFFSET_X = 3.96f
private const val TOPIC_OFFSET_Y = 2f
private const val TOPIC_STROKE = 1.9f
private const val MARK_STROKE = 1.1f
private const val FOURCHAN_MARK = 0
private const val BBWCHAN_MARK = 1
private const val OTHER_SITE_MARK = 2
private const val SEAL_BITS = 18
private const val SEAL_MASK = 0x3FFFFuL
private const val SEAL_COLUMNS = 6
private const val SEAL_X = 7f
private const val SEAL_Y = 18f
private const val SEAL_COLUMN_STEP = 1.8f
private const val SEAL_ROW_STEP = 1.2f
private const val SEAL_CELL = 1f

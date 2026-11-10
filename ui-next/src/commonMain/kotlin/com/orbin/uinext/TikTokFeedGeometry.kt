package com.orbin.uinext

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.tiktok_feed_following
import com.orbin.uinext.resources.tiktok_feed_for_you
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

internal val TikTokFeedTabStyle: TextStyle
    @Composable get() = NextType.titleMedium.copy(fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)

internal data class TikTokFeedGeometry(
    val stackedTabs: Boolean,
    val headerHeight: Dp,
)

/** The feed, overlay controls, and player use the same measured accessibility arrangement. */
@Composable
internal fun rememberTikTokFeedGeometry(
    viewportWidth: Dp,
    statusMessage: String? = null,
): TikTokFeedGeometry {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val padding = WindowInsets.safeDrawing.asPaddingValues()
    val width = viewportWidth - padding.calculateLeftPadding(direction) - padding.calculateRightPadding(direction)
    val following = stringResource(Res.string.tiktok_feed_following)
    val forYou = stringResource(Res.string.tiktok_feed_for_you)
    val measurer = rememberTextMeasurer()
    val tabStyle = TikTokFeedTabStyle
    val footnoteStyle = NextType.footnote
    return remember(measurer, density, width, following, forYou, statusMessage, tabStyle, footnoteStyle) {
        with(density) {
            val labels = listOf(following, forYou)
            val measured = labels.map { measurer.measure(it, tabStyle, softWrap = false) }
            val inlineWidth = measured.sumOf { it.size.width }.toDp() + 16.dp + 18.dp + MIN_TOUCH_TARGET * 2
            val stacked = inlineWidth > width
            val tabsHeight =
                if (stacked) {
                    val textWidth = (width - 32.dp).roundToPx().coerceAtLeast(1)
                    labels.fold(TikTokDesign.FeedHeaderHeight) { height, label ->
                        val text =
                            measurer.measure(
                                label,
                                tabStyle,
                                constraints = Constraints(maxWidth = textWidth),
                            )
                        height + maxOf(MIN_TOUCH_TARGET, text.size.height.toDp() + 20.dp)
                    }
                } else {
                    maxOf(TikTokDesign.FeedHeaderHeight, measured.maxOf { it.size.height }.toDp() + 20.dp)
                }
            val statusHeight =
                if (statusMessage.isNullOrBlank()) {
                    0.dp
                } else {
                    measurer
                        .measure(
                            statusMessage,
                            footnoteStyle,
                            maxLines = 2,
                            constraints = Constraints(maxWidth = (width - 152.dp).roundToPx().coerceAtLeast(1)),
                        ).size.height
                        .toDp() + 12.dp
                }
            TikTokFeedGeometry(stacked, tabsHeight + statusHeight)
        }
    }
}

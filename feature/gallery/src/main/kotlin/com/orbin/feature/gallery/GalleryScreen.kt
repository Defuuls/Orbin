package com.orbin.feature.gallery

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.MediaType
import com.orbin.media.image.ImageCopyResult
import com.orbin.media.image.ZoomableImage
import com.orbin.media.video.VideoPlayer
import com.orbin.uinext.InlineAction
import com.orbin.uinext.NextEmpty
import com.orbin.uinext.NextLinearProgress
import com.orbin.uinext.NextTheme
import com.orbin.uinext.next
import com.orbin.uinext.nextFrosted
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Full-screen, vertically swipeable media gallery for a thread. Images support pinch-zoom; videos
 * play with Media3. Chrome is Orbin Next frosted overlay actions — Close / Copy / Download — rather
 * than a Material TopAppBar. The download action is provided by the host (wired to the download
 * manager).
 */
@Composable
fun GalleryScreen(
    onClose: () -> Unit,
    onMediaPageChanged: (Int) -> Unit = {},
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val media by viewModel.media.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    NextTheme(darkTheme = true) {
        if (media.isEmpty()) {
            NextEmpty("No media", Modifier.fillMaxSize())
            return@NextTheme
        }

        val pagerState =
            rememberPagerState(
                initialPage = viewModel.initialPageIn(media).coerceIn(0, media.lastIndex),
                pageCount = { media.size },
            )

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .distinctUntilChanged()
                .collect(onMediaPageChanged)
        }

        // Hide the gallery chrome while a video plays in fullscreen; reset on every page change so a
        // swipe away always brings the top bar back.
        var videoFullscreen by remember { mutableStateOf(false) }
        LaunchedEffect(pagerState.settledPage) { videoFullscreen = false }

        LaunchedEffect(pagerState.settledPage, media) {
            viewModel.prefetchAround(pagerState.settledPage, media)
        }

        val closeLabel = stringResource(R.string.gallery_close)
        val copyLabel = stringResource(R.string.gallery_copy_image)
        val downloadLabel = stringResource(R.string.gallery_download)

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
            ) { page ->
                val item = media[page]
                val isNear = kotlin.math.abs(page - pagerState.settledPage) <= 1
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.type == MediaType.VIDEO || item.type == MediaType.AUDIO) {
                        val isActive = page == pagerState.settledPage
                        VideoPlayer(
                            url = item.sourceUrl,
                            modifier = Modifier.fillMaxSize(),
                            autoPlay = true,
                            muted = settings.muteByDefault,
                            // Only the settled page plays, so swiping away stops its audio.
                            active = isActive,
                            // Only the active page controls the gallery chrome.
                            onFullscreenChange = { if (isActive) videoFullscreen = it },
                        )
                    } else {
                        ZoomableImage(
                            url = item.sourceUrl,
                            contentDescription = item.originalFileName,
                            modifier = Modifier.fillMaxSize(),
                            placeholderUrl = item.thumbnailUrl,
                            // Drop full decodes more than one page away from the settled item.
                            active = isNear,
                        )
                    }
                }
            }

            if (!videoFullscreen) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                                ),
                            ).padding(horizontal = NextSpace.gutter, vertical = 8.dp)
                            .nextFrosted(RoundedCornerShape(14.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InlineAction(
                        label = closeLabel,
                        onClick = {
                            onMediaPageChanged(pagerState.settledPage)
                            onClose()
                        },
                        modifier = Modifier.semantics { contentDescription = closeLabel },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${pagerState.currentPage + 1} / ${media.size}",
                        style = NextType.footnote,
                        color = next.muted,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    val currentItem = media[pagerState.currentPage]
                    val isImage = currentItem.type == MediaType.IMAGE
                    if (isImage) {
                        InlineAction(
                            label = copyLabel,
                            onClick = {
                                scope.launch {
                                    val result = viewModel.copyImage(currentItem.sourceUrl)
                                    val message =
                                        when (result) {
                                            ImageCopyResult.IMAGE -> "Image copied"
                                            ImageCopyResult.URL -> "Image unavailable; URL copied"
                                        }
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.semantics { contentDescription = copyLabel },
                        )
                    }
                    InlineAction(
                        label = downloadLabel,
                        accent = true,
                        onClick = { viewModel.download(currentItem) },
                        modifier = Modifier.semantics { contentDescription = downloadLabel },
                    )
                }
            }

            if (downloadState.isBusy) {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 72.dp)
                            .fillMaxWidth(0.92f)
                            .nextFrosted(RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = downloadState.label ?: "Preparing media…",
                        color = next.ink,
                        style = NextType.footnote,
                    )
                    NextLinearProgress(progress = downloadState.progressValue)
                }
            }
        }
    }
}

package com.orbin.feature.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.MediaType
import com.orbin.core.ui.state.EmptyView
import com.orbin.media.image.ZoomableImage
import com.orbin.media.video.VideoPlayer
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Full-screen, vertically swipeable media gallery for a thread. Images support pinch-zoom; videos
 * play with Media3. The download action is provided by the host (wired to the download manager).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onClose: () -> Unit,
    onMediaPageChanged: (Int) -> Unit = {},
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val media by viewModel.media.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()

    if (media.isEmpty()) {
        Scaffold { padding -> EmptyView("No media", Modifier.padding(padding)) }
        return
    }

    val pagerState =
        rememberPagerState(
            initialPage = viewModel.startIndex.coerceIn(0, media.lastIndex),
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

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            if (!videoFullscreen) {
                TopAppBar(
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Black.copy(alpha = 0.4f),
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                            actionIconContentColor = Color.White,
                        ),
                    title = { Text("${pagerState.currentPage + 1} / ${media.size}") },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                onMediaPageChanged(pagerState.settledPage)
                                onClose()
                            },
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.download(media[pagerState.currentPage]) }) {
                            Icon(Icons.Filled.Download, contentDescription = "Download")
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val item = media[page]
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.type == MediaType.VIDEO || item.type == MediaType.AUDIO) {
                        val isActive = page == pagerState.settledPage
                        VideoPlayer(
                            url = item.sourceUrl,
                            modifier = Modifier.fillMaxSize(),
                            autoPlay = settings.autoplayVideos,
                            muted = settings.muteByDefault,
                            // Only the settled page plays, so swiping away stops its audio.
                            active = isActive,
                            fullscreenByDefault = settings.fullscreenVideoPlayback,
                            autoRotate = settings.autoRotateVideoFullscreen,
                            // Only the active page controls the gallery chrome.
                            onFullscreenChange = { if (isActive) videoFullscreen = it },
                        )
                    } else {
                        ZoomableImage(
                            url = item.sourceUrl,
                            contentDescription = item.originalFileName,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            if (downloadState.isBusy) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).fillMaxWidth(0.92f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    tonalElevation = 2.dp,
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = downloadState.label ?: "Preparing media…",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        LinearProgressIndicator(
                            progress = { downloadState.progressValue },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

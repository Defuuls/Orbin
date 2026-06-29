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
import androidx.compose.runtime.getValue
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

/**
 * Full-screen, vertically swipeable media gallery for a thread. Images support pinch-zoom; videos
 * play with Media3. The download action is provided by the host (wired to the download manager).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onClose: () -> Unit,
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

    Scaffold(
        containerColor = Color.Black,
        topBar = {
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
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.download(media[pagerState.currentPage]) }) {
                        Icon(Icons.Filled.Download, contentDescription = "Download")
                    }
                },
            )
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
                        VideoPlayer(
                            url = item.sourceUrl,
                            modifier = Modifier.fillMaxSize(),
                            autoPlay = settings.autoplayVideos,
                            muted = settings.muteByDefault,
                            // Only the settled page plays, so swiping away stops its audio.
                            active = page == pagerState.settledPage,
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

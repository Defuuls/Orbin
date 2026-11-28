package com.orbin.app.pulse

import com.orbin.core.model.stream.StreamLens
import com.orbin.uinext.components.NextDestination

/** Where choosing a lens goes, or `null` for a lens the app has no screen for yet. */
internal fun StreamLens.destination(): NextDestination? =
    when (this) {
        StreamLens.Home -> NextDestination.FEED
        StreamLens.Watched -> NextDestination.WATCHED
        StreamLens.Saved -> NextDestination.SAVED
        StreamLens.Settings -> NextDestination.SETTINGS
        StreamLens.Feeds, StreamLens.History, StreamLens.Clips -> null
    }

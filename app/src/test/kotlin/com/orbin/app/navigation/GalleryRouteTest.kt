package com.orbin.app.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GalleryRouteTest {
    @Test
    fun restoredGalleryRoutesWithoutVideoModeKeepTheirOriginalMedia() {
        val oldRoute = """{"provider":"fourchan","board":"g","thread":87,"startIndex":2,"attachmentId":"image"}"""

        val restored = Json.decodeFromString<Route.Gallery>(oldRoute)

        assertThat(restored.startIndex).isEqualTo(2)
        assertThat(restored.attachmentId).isEqualTo("image")
    }

    @Test
    fun aVideoRoutePreservesItsSourceAndLargeThreadIdentity() {
        val route = Route.ThreadVideos("bbwchan", "g", Int.MAX_VALUE.toLong() + 918L, videosOnly = true)

        val restored = Json.decodeFromString<Route.ThreadVideos>(Json.encodeToString(route))

        assertThat(restored).isEqualTo(route)
        assertThat(restored.videosOnly).isTrue()
        assertThat(restored.provider).isEqualTo("bbwchan")
    }

    @Test
    fun ordinaryGalleryRetainsTheDestinationIdStoredByEarlierAppVersions() {
        val navigation = controller()
        navigation.navigate(Route.Gallery("fourchan", "p", 92L, startIndex = 2))

        // Read from the v196 compiled Gallery serializer through Navigation's generateHashCode.
        // Adding even an optional field to Gallery would change this ID and break saved stacks.
        assertThat(navigation.currentDestination!!.id).isEqualTo(1699187340)
        assertThat(navigation.currentBackStackEntry!!.savedStateHandle.get<Boolean>("videosOnly")).isNull()
    }

    @Test
    fun threadVideoNavigationSuppliesTheGalleryFilterAndSourceArguments() {
        val navigation = controller()
        val thread = Int.MAX_VALUE.toLong() + 202L
        navigation.navigate(Route.ThreadVideos("bbwchan", "g", thread, videosOnly = true))

        val arguments = navigation.currentBackStackEntry!!.savedStateHandle
        assertThat(arguments.get<Boolean>("videosOnly")).isTrue()
        assertThat(arguments.get<String>("provider")).isEqualTo("bbwchan")
        assertThat(arguments.get<Long>("thread")).isEqualTo(thread)
    }

    @Test
    fun selectedMediaCarriesThePreviewWithoutChangingOrdinaryGalleryState() {
        val navigation = controller()
        val preview = "https://media.example/g/preview.jpg?size=small"
        val route = Route.SelectedMedia("bbwchan", "g", 92L, "catalog-id", preview)
        assertThat(Json.decodeFromString<Route.SelectedMedia>(Json.encodeToString(route))).isEqualTo(route)
        navigation.navigate(route)

        val entry = navigation.currentBackStackEntry!!
        assertThat(entry.destination.id).isNotEqualTo(1699187340)
        assertThat(entry.savedStateHandle.get<String>("attachmentId")).isEqualTo("catalog-id")
        assertThat(entry.savedStateHandle.get<String>("thumbnailUrl")).isEqualTo(preview)
        assertThat(entry.savedStateHandle.get<Long>("thread")).isEqualTo(92L)
    }

    @Test
    fun selectedOriginalSurvivesNavigationAndSerializationWithoutLosingUrlCharacters() {
        val original =
            MediaAttachment(
                id = "clip/with?punctuation",
                originalFileName = "A \"quiet\" clip.webm",
                extension = "webm",
                type = MediaType.VIDEO,
                sourceUrl = "https://media.example/original.webm?name=a%20b&token=one%2Ftwo#clip",
                thumbnailUrl = "https://media.example/preview.jpg?size=250&format=jpg",
                width = 1920,
                height = 1080,
            )
        val route = selectedMediaRoute("bbwchan", "g", 92L, original) as Route.SeededMedia
        val encoded = route.encodedMediaSeed
        assertThat(Json.decodeFromString<Route.SeededMedia>(Json.encodeToString(route))).isEqualTo(route)
        val navigation = controller()
        navigation.navigate(route)
        assertThat(
            navigation.currentBackStackEntry!!.savedStateHandle.get<String>("encodedMediaSeed"),
        ).isEqualTo(encoded)
        assertThat(
            Json
                .decodeFromString<Route.SelectedMedia>(
                    """{"provider":"bbwchan","board":"g","thread":92,"attachmentId":"old"}""",
                ).attachmentId,
        ).isEqualTo("old")
    }

    private fun controller(): NavHostController {
        val owner =
            object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
            }
        return NavHostController(RuntimeEnvironment.getApplication()).apply {
            setLifecycleOwner(owner)
            setViewModelStore(ViewModelStore())
            navigatorProvider.addNavigator(ComposeNavigator())
            graph =
                createGraph(startDestination = Route.NextFeed) {
                    composable<Route.NextFeed> {}
                    composable<Route.Gallery> {}
                    composable<Route.ThreadVideos> {}
                    composable<Route.SelectedMedia> {}
                    composable<Route.SeededMedia> {}
                }
        }
    }
}

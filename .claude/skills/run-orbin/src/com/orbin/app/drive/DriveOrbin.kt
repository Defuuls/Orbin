package com.orbin.app.drive

import android.Manifest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.orbin.app.MainActivity
import androidx.room.Room
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.imageDecoderEnabled
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import com.orbin.data.database.OrbinDatabase
import com.orbin.data.database.dao.BoardDao
import com.orbin.data.database.dao.BookmarkDao
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.database.dao.HistoryDao
import com.orbin.data.database.dao.RecentSearchDao
import com.orbin.data.database.dao.SavedSearchDao
import com.orbin.data.database.dao.SavedThreadDao
import com.orbin.data.di.DatabaseModule
import com.orbin.data.repository.BookmarkRepositoryImpl
import com.orbin.data.repository.HistoryRepositoryImpl
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.HistoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import javax.inject.Inject
import javax.inject.Singleton
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Agent driver: launches the real MainActivity (real OrbinApplication, Hilt graph, DataStore,
 * navigation) under Robolectric and runs the step script in -Porbin.steps, e.g.
 *   wait:Welcome to Orbin;shot:welcome;click:Get started;tree
 * Steps (separated by ';'):
 *   wait:<text>    wait (up to 20s) for a node containing <text>
 *   gone:<text>    wait (up to 60s) until no node contains <text> (e.g. a loading message)
 *   click:<text>   click the first node containing <text>
 *   type:<value>   type into the first editable text field
 *   scroll:<text>  scroll the first scrollable container to the node containing <text>
 *   idle:<ms>      advance the Compose clock by <ms> (animations, debounces)
 *   sleep:<ms>     wait <ms> of wall-clock time (network/image loads run on real threads)
 *   shot:<name>    screenshot the whole window to <orbin.out>/<name>.png
 *   tree           print the semantics tree (what text/buttons are on screen)
 *   back           press system back
 */
@HiltAndroidTest
@UninstallModules(DatabaseModule::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi", application = HiltTestApplication::class)
class DriveOrbin {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    /** The app's real Coil loader; OrbinApplication normally hands it to Coil as the singleton. */
    @Inject
    lateinit var imageLoader: ImageLoader

    /** The manifest removes WorkManager's auto-initializer (OrbinApplication provides it), and
     *  HiltTestApplication stands in for OrbinApplication here, so initialize it before launch. */
    @get:Rule(order = 1)
    val workManager = TestRule { base, _ ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                FakeAndroidKeyStore.install()
                val context = ApplicationProvider.getApplicationContext<android.content.Context>()
                WorkManager.initialize(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
                hilt.inject()
                // Robolectric's native graphics implements BitmapFactory but not ImageDecoder,
                // which Coil prefers on API 28+ ("DecodeException: Only supported on Android").
                SingletonImageLoader.setUnsafe(imageLoader.newBuilder().imageDecoderEnabled(false).build())
                if (System.getProperty("orbin.dark") == "true") {
                    org.robolectric.RuntimeEnvironment.setQualifiers("+night")
                }
                Shadows.shadowOf(context as android.app.Application)
                    .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 2)
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun drive() {
        val out = File(System.getProperty("orbin.out") ?: "build/orbin-drive").apply { mkdirs() }
        val steps = (System.getProperty("orbin.steps") ?: "idle:2000;shot:launch;tree")
            .split(';').map { it.trim() }.filter { it.isNotEmpty() }
        for (step in steps) {
            val verb = step.substringBefore(':')
            val arg = step.substringAfter(':', "")
            println("[drive] $step")
            when (verb) {
                "wait" -> rule.waitUntil(20_000) { node(arg).let { runCatching { it.fetchSemanticsNode() }.isSuccess } }
                "gone" -> rule.waitUntil(60_000) {
                    rule.onAllNodesWithText(arg, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
                }
                "click" -> node(arg).performClick()
                "type" -> rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(arg)
                "scroll" -> rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(arg, substring = true))
                "idle" -> rule.mainClock.advanceTimeBy(arg.toLong())
                "sleep" -> Thread.sleep(arg.toLong())
                "shot" -> {
                    val f = File(out, "$arg.png")
                    rule.onRoot().captureRoboImage(f.path)
                    println("[drive] screenshot ${f.absolutePath}")
                }
                "tree" -> println(rule.onRoot(useUnmergedTree = false).printToString())
                "back" -> rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
                else -> error("unknown step '$step'")
            }
            rule.waitForIdle()
        }
    }

    private fun node(text: String): SemanticsNodeInteraction =
        rule.onAllNodesWithText(text, substring = true, useUnmergedTree = true).onFirst()
}

/**
 * Stands in for [DatabaseModule]: same schema, DAOs and repositories, but an in-memory,
 * unencrypted Room database. SQLCipher's native library has no JVM build, so the real module
 * throws UnsatisfiedLinkError under Robolectric. Every other binding is the app's real graph.
 */
@Module
@InstallIn(SingletonComponent::class)
object DriveDatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: android.content.Context): OrbinDatabase =
        Room.inMemoryDatabaseBuilder(context, OrbinDatabase::class.java).allowMainThreadQueries().build()

    @Provides fun boardDao(db: OrbinDatabase): BoardDao = db.boardDao()
    @Provides fun savedThreadDao(db: OrbinDatabase): SavedThreadDao = db.savedThreadDao()
    @Provides fun bookmarkDao(db: OrbinDatabase): BookmarkDao = db.bookmarkDao()
    @Provides fun historyDao(db: OrbinDatabase): HistoryDao = db.historyDao()
    @Provides fun recentSearchDao(db: OrbinDatabase): RecentSearchDao = db.recentSearchDao()
    @Provides fun downloadDao(db: OrbinDatabase): DownloadDao = db.downloadDao()
    @Provides fun savedSearchDao(db: OrbinDatabase): SavedSearchDao = db.savedSearchDao()

    @Provides @Singleton
    fun bookmarkRepository(dao: BookmarkDao): BookmarkRepository = BookmarkRepositoryImpl(dao)

    @Provides @Singleton
    fun historyRepository(dao: HistoryDao): HistoryRepository = HistoryRepositoryImpl(dao)
}

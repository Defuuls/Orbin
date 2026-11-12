package com.orbin.benchmark

import android.content.ComponentName
import android.content.Intent
import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Condition
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

internal const val READER_PACKAGE = "com.orbin.app"
private const val FIXTURE_ACTIVITY = "com.orbin.app.benchmark.ReaderBenchmarkActivity"
private const val FIXTURE_STATUS = "reader-benchmark-status"
private const val WAIT_TIMEOUT_MS = 10_000L
private const val SCROLL_PASSES = 3
private const val CATALOG_GRID = "catalog-grid"
private const val CATALOG_ROW = "catalog-row"
private const val THREAD_LIST = "thread-list"
private const val THREAD_POST = "thread-post"
private const val THREAD_FILE = "thread-file"

/** Stable production component tags, exported as resource IDs by the benchmark-only host. */
internal enum class ReaderJourney(
    val argument: String,
    val collectionTag: String,
    val itemTag: String,
    val widthPx: Int = 1080,
    val heightPx: Int = 1920,
    val densityDpi: Int = 420,
) {
    LIST("catalog-list", CATALOG_GRID, CATALOG_ROW),
    GRID("catalog-grid", CATALOG_GRID, "catalog-card"),
    THREAD("thread", THREAD_LIST, THREAD_POST),
    FILES("files", THREAD_LIST, THREAD_FILE),
    TWO_PANE("two-pane", CATALOG_GRID, CATALOG_ROW, widthPx = 1280, heightPx = 800, densityDpi = 160),
}

internal fun resetReaderWindow() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)
    device.executeShellCommand("am force-stop $READER_PACKAGE")
    val size = device.executeShellCommand("wm size")
    val density = device.executeShellCommand("wm density")
    val physicalSize = checkNotNull(PHYSICAL_WINDOW_SIZE.find(size)) { "Missing physical display size: $size" }
    val widthPx = physicalSize.groupValues[1].toInt()
    val heightPx = physicalSize.groupValues[2].toInt()
    val densityDpi =
        checkNotNull(PHYSICAL_WINDOW_DENSITY.find(density)) { "Missing physical display density: $density" }
            .groupValues[1]
            .toInt()
    // Startup does not override the display. Avoid sending redundant configuration broadcasts.
    if (size.contains("Override size:")) device.executeShellCommand("wm size reset")
    if (density.contains("Override density:")) device.executeShellCommand("wm density reset")
    val settled =
        device.wait(
            Condition<UiDevice, Boolean> { current ->
                current.displayWidth == widthPx &&
                    current.displayHeight == heightPx &&
                    instrumentation.targetContext.resources.displayMetrics.densityDpi == densityDpi
            },
            WAIT_TIMEOUT_MS,
        )
    assertTrue("Physical display did not settle at ${widthPx}x$heightPx / ${densityDpi}dpi", settled == true)
    // A shell reset returns before Launcher, System UI and the test process finish reconfiguration.
    // Wait outside collection before the next test starts compiling or pulling its output.
    device.waitForIdle(WAIT_TIMEOUT_MS)
    val restoredSize = device.executeShellCommand("wm size")
    val restoredDensity = device.executeShellCommand("wm density")
    assertFalse("Window-size override remains after teardown: $restoredSize", restoredSize.contains("Override size:"))
    assertFalse(
        "Window-density override remains after teardown: $restoredDensity",
        restoredDensity.contains("Override density:"),
    )
}

/** Window overrides are outside the measured region and restored by each test's teardown. */
internal fun MacrobenchmarkScope.startReaderJourney(journey: ReaderJourney) {
    pressHome()
    killProcess()
    device.executeShellCommand("wm size ${journey.widthPx}x${journey.heightPx}")
    device.executeShellCommand("wm density ${journey.densityDpi}")
    val appliedSize = device.executeShellCommand("wm size")
    val appliedDensity = device.executeShellCommand("wm density")
    assertEquals(
        "Window manager did not apply the requested size: $appliedSize",
        "${journey.widthPx}x${journey.heightPx}",
        WINDOW_SIZE
            .findAll(appliedSize)
            .lastOrNull()
            ?.groupValues
            ?.get(1),
    )
    assertEquals(
        "Window manager did not apply the requested density: $appliedDensity",
        journey.densityDpi.toString(),
        WINDOW_DENSITY
            .findAll(appliedDensity)
            .lastOrNull()
            ?.groupValues
            ?.get(1),
    )
    startActivityAndWait(
        Intent()
            .setComponent(ComponentName(READER_PACKAGE, FIXTURE_ACTIVITY))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("readerJourney", journey.argument),
    )
    assertTrue(
        "Fixture status did not appear for $journey",
        device.wait(Until.hasObject(By.res(FIXTURE_STATUS).descContains("ready=true")), WAIT_TIMEOUT_MS),
    )
    assertFixtureGeometry(journey)
    assertPopulated(journey)
    assertEquals("Fixture data must be prepared once per Activity", 1, fixtureStatus().getValue("models").toInt())
}

private fun MacrobenchmarkScope.assertFixtureGeometry(journey: ReaderJourney) {
    val status = fixtureStatus()
    val density = journey.densityDpi / DENSITY_BASE
    assertEquals("Drawn width differs from requested display", journey.widthPx, status.getValue("widthPx").toInt())
    assertEquals("Drawn height differs from requested display", journey.heightPx, status.getValue("heightPx").toInt())
    assertEquals(
        "Compose density differs from requested display",
        density,
        status.getValue("density").toDouble(),
        DP_PRECISION,
    )
    assertEquals(
        "Drawn dp width differs from requested display",
        journey.widthPx / density,
        status.getValue("widthDp").toDouble(),
        DP_PRECISION,
    )
    assertEquals(
        "Drawn dp height differs from requested display",
        journey.heightPx / density,
        status.getValue("heightDp").toDouble(),
        DP_PRECISION,
    )
    assertEquals("Fixture text scale must be deterministic", 1.0, status.getValue("fontScale").toDouble(), DP_PRECISION)
    when (journey) {
        ReaderJourney.TWO_PANE -> {
            assertEquals(
                "Wide catalog pane must use the production 420dp cap",
                WIDE_CATALOG_PX,
                status.getValue("catalogWidthPx").toInt(),
            )
            assertEquals(
                "Wide detail pane must take the remaining width after its 1dp divider",
                WIDE_DETAIL_PX,
                status.getValue("detailWidthPx").toInt(),
            )
            assertEquals(WIDE_CATALOG_PX, requiredObject("reader-catalog-pane").visibleBounds.width())
            assertEquals(WIDE_DETAIL_PX, requiredObject("reader-thread-pane").visibleBounds.width())
        }
        ReaderJourney.LIST, ReaderJourney.GRID ->
            assertEquals(
                "Single-pane catalog must fill the drawn width",
                journey.widthPx,
                status.getValue("catalogWidthPx").toInt(),
            )
        ReaderJourney.THREAD, ReaderJourney.FILES ->
            assertEquals(
                "Single-pane thread must fill the drawn width",
                journey.widthPx,
                status.getValue("detailWidthPx").toInt(),
            )
    }
}

internal fun MacrobenchmarkScope.exerciseReaderJourney(journey: ReaderJourney) {
    if (journey == ReaderJourney.TWO_PANE) {
        exerciseTwoPaneRetention()
        return
    }
    assertPopulated(journey)
    val before = scrollPosition(journey)
    scroll(journey.collectionTag, Direction.DOWN)
    assertPopulated(journey)
    val afterDown = scrollPosition(journey)
    assertNotEquals("A populated $journey must actually scroll", before, afterDown)
    scroll(journey.collectionTag, Direction.UP)
    assertPopulated(journey)
    assertNotEquals("The return scroll in $journey must produce frames", afterDown, scrollPosition(journey))
    assertEquals("Scrolling must not rebuild the prepared fixture data", 1, fixtureStatus().getValue("models").toInt())
}

private fun MacrobenchmarkScope.exerciseTwoPaneRetention() {
    scroll(CATALOG_GRID, Direction.DOWN)
    val before = fixtureStatus()
    val beforeCompositions = before.getValue("compositions").toInt()
    assertTrue("Catalog must be away from its first row", before.getValue("index").toInt() > 0)
    assertEquals("Catalog is composed once in the pane", 1, before.getValue("mounts").toInt())
    assertTrue("Catalog must have a recorded composition", beforeCompositions > 0)

    requiredObject(CATALOG_ROW).click()
    assertTrue("Selected thread did not populate", device.wait(Until.hasObject(By.res(THREAD_POST)), WAIT_TIMEOUT_MS))
    assertTrue(fixtureStatus().getValue("open").toBoolean())
    scroll(THREAD_LIST, Direction.DOWN)
    assertTrue("Thread pane must scroll independently", fixtureStatus().getValue("threadIndex").toInt() > 1)
    device.pressBack()
    assertTrue(
        "Closing a thread must reveal the empty detail pane",
        device.wait(Until.hasObject(By.res("reader-empty-thread-pane")), WAIT_TIMEOUT_MS),
    )

    val after = fixtureStatus()
    assertFalse(after.getValue("open").toBoolean())
    assertEquals(
        "Catalog's first visible item must survive opening and closing a thread",
        before.getValue("index"),
        after.getValue("index"),
    )
    assertEquals(
        "Catalog's pixel offset must survive opening and closing a thread",
        before.getValue("offset"),
        after.getValue("offset"),
    )
    assertEquals("Opening a thread must not rebuild fixture data", "1", after.getValue("models"))
    assertEquals("Opening a thread must keep the catalog mounted", "1", after.getValue("mounts"))
    val afterCompositions = after.getValue("compositions").toInt()
    assertTrue(afterCompositions >= beforeCompositions)
    Log.i(
        "ReaderBenchmark",
        "Catalog compositions before=$beforeCompositions after=$afterCompositions; models=1; mounts=1; scroll retained",
    )
}

private fun MacrobenchmarkScope.assertPopulated(journey: ReaderJourney) {
    assertTrue(
        "Production collection is missing for $journey",
        device.wait(Until.hasObject(By.res(journey.collectionTag)), WAIT_TIMEOUT_MS),
    )
    assertTrue(
        "Production $journey contains no visible fixture items",
        device.wait(Until.hasObject(By.res(journey.itemTag)), WAIT_TIMEOUT_MS),
    )
    val collection = requiredObject(journey.collectionTag)
    assertTrue("Collection has no visible bounds for $journey", collection.visibleBounds.height() > 0)
}

private fun MacrobenchmarkScope.scroll(
    collectionTag: String,
    direction: Direction,
) {
    repeat(SCROLL_PASSES) {
        requiredObject(collectionTag).fling(direction)
        device.waitForIdle()
    }
}

private fun MacrobenchmarkScope.scrollPosition(journey: ReaderJourney): String =
    when (journey) {
        ReaderJourney.LIST, ReaderJourney.GRID -> {
            val status = fixtureStatus()
            "${status.getValue("index")}:${status.getValue("offset")}"
        }
        ReaderJourney.THREAD -> fixtureStatus().getValue("threadIndex")
        ReaderJourney.FILES ->
            checkNotNull(device.findObject(By.descStartsWith("benchmark/bench/"))) {
                "Files journey has no local thumbnail fixture"
            }.contentDescription
        ReaderJourney.TWO_PANE -> error("Two-pane retention uses its full catalog snapshot")
    }

private fun MacrobenchmarkScope.requiredObject(tag: String): UiObject2 =
    checkNotNull(device.findObject(By.res(tag))) { "Required production UI component is absent: $tag" }

private fun MacrobenchmarkScope.fixtureStatus(): Map<String, String> =
    requiredObject(FIXTURE_STATUS).contentDescription.split(';').associate { entry ->
        val (key, value) = entry.split('=', limit = 2)
        key to value
    }

private val WINDOW_SIZE = Regex("(?:Physical|Override) size:\\s*(\\d+x\\d+)")
private val WINDOW_DENSITY = Regex("(?:Physical|Override) density:\\s*(\\d+)")
private val PHYSICAL_WINDOW_SIZE = Regex("Physical size:\\s*(\\d+)x(\\d+)")
private val PHYSICAL_WINDOW_DENSITY = Regex("Physical density:\\s*(\\d+)")
private const val DENSITY_BASE = 160.0
private const val DP_PRECISION = 0.001
private const val WIDE_CATALOG_PX = 420
private const val WIDE_DETAIL_PX = 859

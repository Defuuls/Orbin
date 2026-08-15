package com.orbin.data.version

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPackageManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VersionGuardRepositoryImplTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context
            .getSharedPreferences("orbin_version_guard", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    /** Robolectric reports the manifest's version code; this overrides it per test. */
    private fun guardRunning(versionCode: Int): VersionGuardRepositoryImpl {
        val shadow: ShadowPackageManager = org.robolectric.Shadows.shadowOf(context.packageManager)
        val info = shadow.getInternalMutablePackageInfo(context.packageName)
        info.longVersionCode = versionCode.toLong()
        return VersionGuardRepositoryImpl(context)
    }

    @Test
    fun `a fresh install has no high-water mark and is not a downgrade`() {
        val guard = guardRunning(107)
        assertThat(guard.highestVersionCodeSeen()).isEqualTo(0)
        assertThat(guard.isDowngrade()).isFalse()
    }

    @Test
    fun `a successful launch records the running version`() {
        guardRunning(107).recordSuccessfulLaunch()
        assertThat(guardRunning(107).highestVersionCodeSeen()).isEqualTo(107)
    }

    @Test
    fun `an older build is blocked once a newer one has run`() {
        guardRunning(108).recordSuccessfulLaunch()

        val older = guardRunning(107)
        assertThat(older.isDowngrade()).isTrue()
    }

    @Test
    fun `the same build is not a downgrade`() {
        guardRunning(108).recordSuccessfulLaunch()
        assertThat(guardRunning(108).isDowngrade()).isFalse()
    }

    @Test
    fun `a newer build runs and raises the mark`() {
        guardRunning(107).recordSuccessfulLaunch()

        val newer = guardRunning(120)
        assertThat(newer.isDowngrade()).isFalse()
        newer.recordSuccessfulLaunch()
        assertThat(newer.highestVersionCodeSeen()).isEqualTo(120)
    }

    /**
     * The mark is a high-water mark, not "last seen". If an older build ever does run — sideloaded
     * before this guard existed, say — it must not lower the bar for the next launch.
     */
    @Test
    fun `recording an older build never lowers the mark`() {
        guardRunning(120).recordSuccessfulLaunch()

        guardRunning(107).recordSuccessfulLaunch()

        assertThat(guardRunning(107).highestVersionCodeSeen()).isEqualTo(120)
    }

    /**
     * An unreadable version code reads as 0. Blocking on that would brick every launch, so the
     * guard has to fail open — and must not write the 0 into the mark either.
     */
    @Test
    fun `an unknown version code neither blocks nor is recorded`() {
        guardRunning(120).recordSuccessfulLaunch()

        val unknown = guardRunning(0)
        assertThat(unknown.isDowngrade()).isFalse()
        unknown.recordSuccessfulLaunch()
        assertThat(unknown.highestVersionCodeSeen()).isEqualTo(120)
    }
}

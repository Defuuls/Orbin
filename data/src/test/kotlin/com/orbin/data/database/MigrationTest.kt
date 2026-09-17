package com.orbin.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Newest SDK Robolectric 4.16 ships an image for; the app's target SDK is ahead of it. */
private const val ROBOLECTRIC_SDK = 35
private const val TEST_DB = "migration-test.db"

/**
 * Runs every registered [Migration][androidx.room.migration.Migration] against the exported
 * schema JSON under `data/schemas`. [MigrationTestHelper.runMigrationsAndValidate] fails the test
 * if the migrated schema doesn't byte-for-byte match what Room expects at that version — the same
 * check Room performs against a real device database at app startup, just moved earlier.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class MigrationTest {
    private val databaseFile: File
        get() = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(TEST_DB)

    @get:Rule
    val migrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            databaseFile,
            AndroidSQLiteDriver(),
            OrbinDatabase::class,
        )

    @Test
    fun migrate2To3() {
        migrationTestHelper.createDatabase(2).close()

        migrationTestHelper.runMigrationsAndValidate(3, listOf(MIGRATION_2_3)).close()
    }

    @Test
    fun migrate3To4() {
        migrationTestHelper.createDatabase(3).close()

        migrationTestHelper.runMigrationsAndValidate(4, listOf(MIGRATION_3_4)).close()
    }

    @Test
    fun migrate4To5() {
        migrationTestHelper.createDatabase(4).close()

        migrationTestHelper.runMigrationsAndValidate(5, listOf(MIGRATION_4_5)).close()
    }

    @Test
    fun migrate5To6() {
        migrationTestHelper.createDatabase(5).close()

        migrationTestHelper.runMigrationsAndValidate(6, listOf(MIGRATION_5_6)).close()
    }

    @Test
    fun migrate6To7() {
        migrationTestHelper.createDatabase(6).close()

        migrationTestHelper.runMigrationsAndValidate(7, listOf(MIGRATION_6_7)).close()
    }

    /** The full chain a v2 install actually walks through when it upgrades straight to current. */
    @Test
    fun migrateAllTheWayFrom2To7() {
        migrationTestHelper.createDatabase(2).close()

        migrationTestHelper
            .runMigrationsAndValidate(
                7,
                listOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7),
            ).close()
    }
}

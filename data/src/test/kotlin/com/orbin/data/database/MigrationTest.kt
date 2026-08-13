package com.orbin.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Newest SDK Robolectric 4.16 ships an image for; the app's target SDK is ahead of it. */
private const val ROBOLECTRIC_SDK = 35
private const val TEST_DB = "migration-test"

/**
 * Runs every registered [Migration][androidx.room.migration.Migration] against the exported
 * schema JSON under `data/schemas`. [MigrationTestHelper.runMigrationsAndValidate] fails the test
 * if the migrated schema doesn't byte-for-byte match what Room expects at that version — the same
 * check Room performs against a real device database at app startup, just moved earlier.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class MigrationTest {
    @get:Rule
    val migrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            OrbinDatabase::class.java,
        )

    @Test
    fun migrate2To3() {
        migrationTestHelper.createDatabase(TEST_DB, 2).close()

        migrationTestHelper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)
    }

    @Test
    fun migrate3To4() {
        migrationTestHelper.createDatabase(TEST_DB, 3).close()

        migrationTestHelper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)
    }

    @Test
    fun migrate4To5() {
        migrationTestHelper.createDatabase(TEST_DB, 4).close()

        migrationTestHelper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)
    }

    @Test
    fun migrate5To6() {
        migrationTestHelper.createDatabase(TEST_DB, 5).close()

        migrationTestHelper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)
    }

    @Test
    fun migrate6To7() {
        migrationTestHelper.createDatabase(TEST_DB, 6).close()

        migrationTestHelper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)
    }

    /** The full chain a v2 install actually walks through when it upgrades straight to current. */
    @Test
    fun migrateAllTheWayFrom2To7() {
        migrationTestHelper.createDatabase(TEST_DB, 2).close()

        migrationTestHelper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
        )
    }
}

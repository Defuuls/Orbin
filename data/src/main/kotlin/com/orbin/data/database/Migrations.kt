package com.orbin.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Android adapts the shared migration SQL to Room's SupportSQLiteDatabase callback. iOS adapts the
 * same statements to SQLiteConnection in its database builder.
 */
private fun androidMigration(
    startVersion: Int,
    endVersion: Int,
): Migration {
    val schemaMigration =
        OrbinSchemaMigrations.all.single {
            it.startVersion == startVersion && it.endVersion == endVersion
        }
    return object : Migration(startVersion, endVersion) {
        override fun migrate(db: SupportSQLiteDatabase) {
            schemaMigration.statements.forEach(db::execSQL)
        }
    }
}

internal val MIGRATION_2_3 = androidMigration(2, 3)
internal val MIGRATION_3_4 = androidMigration(3, 4)
internal val MIGRATION_4_5 = androidMigration(4, 5)
internal val MIGRATION_5_6 = androidMigration(5, 6)
internal val MIGRATION_6_7 = androidMigration(6, 7)
internal val MIGRATION_7_8 = androidMigration(7, 8)

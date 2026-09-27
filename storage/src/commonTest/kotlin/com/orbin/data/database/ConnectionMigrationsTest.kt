package com.orbin.data.database

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The iOS app opens the database with [CONNECTION_MIGRATIONS] alone, and Room refuses to open a
 * database it cannot migrate, so a schema bump without a step here crashes the app on launch (as
 * 157-Xigua's did). This fails first.
 */
class ConnectionMigrationsTest {
    @Test
    fun theChainTakesEveryIosDatabaseToTheCurrentSchema() {
        val steps = CONNECTION_MIGRATIONS.map { it.startVersion to it.endVersion }

        assertEquals(IOS_FIRST_SCHEMA, steps.first().first)
        assertEquals(OrbinDatabase.VERSION, steps.last().second)
        steps.zipWithNext().forEach { (step, next) -> assertEquals(step.second, next.first) }
    }
}

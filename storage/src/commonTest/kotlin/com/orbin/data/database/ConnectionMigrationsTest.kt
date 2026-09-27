package com.orbin.data.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Both platforms open the database with [OrbinSchemaMigrations], and Room refuses to open a
 * database it cannot migrate, so a schema bump without a step here crashes the iOS app on launch
 * (as 157-Xigua's did). This fails first.
 */
class ConnectionMigrationsTest {
    @Test
    fun theChainReachesTheCurrentSchemaWithoutGaps() {
        val steps = OrbinSchemaMigrations.all.map { it.startVersion to it.endVersion }

        assertTrue(steps.first().first <= IOS_FIRST_SCHEMA, "iOS copies start at schema $IOS_FIRST_SCHEMA")
        assertEquals(OrbinDatabase.VERSION, steps.last().second)
        steps.zipWithNext().forEach { (step, next) -> assertEquals(step.second, next.first) }
    }

    private companion object {
        /** The schema the iOS app first shipped the database at. */
        const val IOS_FIRST_SCHEMA = 7
    }
}

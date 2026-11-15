package com.orbin.app

import androidx.test.platform.app.InstrumentationRegistry
import com.orbin.data.database.OrbinDatabase
import com.orbin.graph.SharedGraph
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Runs outside ActivityScenario, but inside Hilt: stop observers before closing/deleting their file. */
class GraphResourcesRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                try {
                    base.evaluate()
                } finally {
                    val context = InstrumentationRegistry.getInstrumentation().targetContext
                    val resources = EntryPointAccessors.fromApplication(context, Resources::class.java)
                    runBlocking(Dispatchers.Default) { resources.graph().shutdown() }
                    resources.database().close()
                    context.deleteDatabase(resources.file().name)
                }
            }
        }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Resources {
        fun graph(): SharedGraph

        fun database(): OrbinDatabase

        fun file(): TestDatabaseFile
    }
}

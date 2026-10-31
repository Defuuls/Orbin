package com.orbin.data.crypto

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.orbin.data.database.DatabaseKeyRepair
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Losing the database key used to lose the reader's data silently. Robolectric has no Android
 * Keystore, which is exactly the "Keystore will not save a key" case this pins.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DatabaseKeyLossTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() = LocalDataNotices.acknowledge()

    @Test
    fun aKeyTheKeystoreCannotSaveIsKeptOffDiskAndReported() {
        val resolved = DatabasePassphrase(context).resolve()

        assertThat(resolved.persisted).isFalse()
        assertThat(resolved.wipeDatabase).isFalse()
        assertThat(context.getSharedPreferences("orbin_db_key", Context.MODE_PRIVATE).all).isEmpty()
        assertThat(LocalDataNotices.current.value).isEqualTo(LocalDataNotice.NOT_SAVED)
    }

    @Test
    fun deletingADatabaseThatCannotBeOpenedIsReported() {
        val file = context.getDatabasePath("unopenable.db")
        file.parentFile?.mkdirs()
        file.writeText("not a database")

        DatabaseKeyRepair(context, ByteArray(32) { 1 }, "unopenable.db").repair()

        assertThat(file.exists()).isFalse()
        assertThat(LocalDataNotices.current.value).isEqualTo(LocalDataNotice.RESET)
    }

    @Test
    fun repairingWithNoFileReportsNothing() {
        DatabaseKeyRepair(context, ByteArray(32) { 1 }, "never-created.db").repair()

        assertThat(LocalDataNotices.current.value).isNull()
    }
}

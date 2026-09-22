package com.cyopstd.game

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.cyopstd.game.save.GameRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Builds a [GameRepository] backed by its own private DataStore file.
 *
 * Robolectric shares one process across test classes and a view model's
 * coroutines outlive the test that created them, so tests that share the app's
 * real store can see writes from a previous test land after their own setup.
 * Giving every test its own file removes that whole class of flakiness instead
 * of trying to time around it.
 */
object TestStores {

    private val counter = AtomicInteger(0)

    fun isolatedRepository(): GameRepository = GameRepository(isolatedStore())

    fun isolatedStore(): DataStore<Preferences> {
        val directory = File(
            System.getProperty("java.io.tmpdir"),
            "packet-bastion-test-stores"
        ).apply { mkdirs() }

        val file = File(directory, "store-${counter.incrementAndGet()}-${System.nanoTime()}.preferences_pb")
        file.delete()
        file.deleteOnExit()

        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ) { file }
    }
}

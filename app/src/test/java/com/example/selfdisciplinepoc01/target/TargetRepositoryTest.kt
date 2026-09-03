package com.example.selfdisciplinepoc01.target

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.example.selfdisciplinepoc01.target.model.LockedApp
import com.example.selfdisciplinepoc01.target.repository.TargetRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@OptIn(ExperimentalCoroutinesApi::class)
class TargetRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun TestScope.createDataStore(fileName: String = "test_target_prefs.preferences_pb") =
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { tempFolder.newFile(fileName) }
        )

    // 1. Fresh repository: Chrome exists and enabled.
    @Test
    fun test1_freshRepository_chromeExistsAndEnabled() = runTest {
        val dataStore = createDataStore("t1.preferences_pb")
        val repository = TargetRepositoryImpl(dataStore, backgroundScope)

        assertTrue(
            "Chrome must be locked in fresh repository cache",
            repository.isLocked("com.android.chrome")
        )

        val initialList = repository.getLockedPackages().first()
        assertTrue(
            "Initial list must contain Chrome enabled",
            initialList.any { it.packageName == "com.android.chrome" && it.enabled }
        )
    }

    // 2. Add target: isLocked(target) == true.
    @Test
    fun test2_addTarget_isLockedIsTrue() = runTest {
        val dataStore = createDataStore("t2.preferences_pb")
        val repository = TargetRepositoryImpl(dataStore, backgroundScope)
        val target = "com.android.bbkcalculator"

        assertFalse("Before adding, target should not be locked", repository.isLocked(target))

        repository.add(target)

        assertTrue("After adding, target should be locked", repository.isLocked(target))
        val list = repository.getLockedPackages().first()
        assertTrue("List should contain the added target", list.any { it.packageName == target && it.enabled })
    }

    // 3. Disable target: isLocked(target) == false.
    @Test
    fun test3_disableTarget_isLockedIsFalse() = runTest {
        val dataStore = createDataStore("t3.preferences_pb")
        val repository = TargetRepositoryImpl(dataStore, backgroundScope)
        val target = "com.android.bbkcalculator"

        repository.add(target)
        assertTrue(repository.isLocked(target))

        repository.setEnabled(target, false)

        assertFalse("Disabled target should not be locked", repository.isLocked(target))
        val list = repository.getLockedPackages().first()
        assertTrue("Target exists in list with enabled=false", list.any { it.packageName == target && !it.enabled })
    }

    // 4. Enable target: isLocked(target) == true.
    @Test
    fun test4_enableTarget_isLockedIsTrue() = runTest {
        val dataStore = createDataStore("t4.preferences_pb")
        val repository = TargetRepositoryImpl(dataStore, backgroundScope)
        val target = "com.android.chrome"

        repository.setEnabled(target, false)
        assertFalse(repository.isLocked(target))

        repository.setEnabled(target, true)
        assertTrue("Re-enabled target should be locked", repository.isLocked(target))
    }

    // 5. Remove target: isLocked(target) == false.
    @Test
    fun test5_removeTarget_isLockedIsFalse() = runTest {
        val dataStore = createDataStore("t5.preferences_pb")
        val repository = TargetRepositoryImpl(dataStore, backgroundScope)
        val target = "com.android.bbkcalculator"

        repository.add(target)
        assertTrue(repository.isLocked(target))

        repository.remove(target)
        assertFalse("Removed target should not be locked", repository.isLocked(target))
        val list = repository.getLockedPackages().first()
        assertFalse("Target should not be in list", list.any { it.packageName == target })
    }

    // 6. Persistence: recreate repository and verify state restored.
    @Test
    fun test6_persistence_recreateRepositoryRestoresState() = runTest {
        val file = tempFolder.newFile("shared_prefs.preferences_pb")
        val job1 = kotlinx.coroutines.Job()
        val scope1 = kotlinx.coroutines.CoroutineScope(job1 + kotlinx.coroutines.Dispatchers.IO)
        val dataStore1 = PreferenceDataStoreFactory.create(scope = scope1, produceFile = { file })
        val repository1 = TargetRepositoryImpl(dataStore1, scope1)

        val customApp = "com.custom.app"
        repository1.add(customApp)
        repository1.setEnabled("com.android.chrome", false)

        // Cancel and wait for job1 to close first DataStore instance cleanly
        job1.cancel()
        job1.join()

        // Recreate second DataStore and repository instance on the same file
        val job2 = kotlinx.coroutines.Job()
        val scope2 = kotlinx.coroutines.CoroutineScope(job2 + kotlinx.coroutines.Dispatchers.IO)
        val dataStore2 = PreferenceDataStoreFactory.create(scope = scope2, produceFile = { file })
        val repository2 = TargetRepositoryImpl(dataStore2, scope2)

        val restoredList = repository2.getLockedPackages().first()

        assertTrue(
            "Persisted list contains custom target",
            restoredList.any { it.packageName == customApp && it.enabled }
        )
        assertTrue(
            "Persisted list preserves Chrome disabled",
            restoredList.any { it.packageName == "com.android.chrome" && !it.enabled }
        )
        assertTrue(repository2.isLocked(customApp))
        assertFalse(repository2.isLocked("com.android.chrome"))

        job2.cancel()
        job2.join()
    }

    // 7. Corrupt/invalid persisted data: repository does not crash and Chrome safe default remains.
    @Test
    fun test7_corruptInvalidData_doesNotCrash_chromeSafeDefaultRemains() = runTest {
        // Test direct deserialization of corrupt JSON
        val corruptResult = TargetRepositoryImpl.deserialize("INVALID_JSON_CORRUPT{[[")
        assertEquals(TargetRepositoryImpl.DEFAULT_APPS, corruptResult)

        // Test DataStore containing invalid string
        val file = tempFolder.newFile("corrupt_prefs.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file })
        dataStore.edit { prefs ->
            prefs[TargetRepositoryImpl.KEY_LOCKED_APPS] = "MALFORMED_DATA_###"
        }

        val repository = TargetRepositoryImpl(dataStore, backgroundScope)
        assertTrue(
            "Should safely fall back to Chrome locked",
            repository.isLocked("com.android.chrome")
        )

        val packages = repository.getLockedPackages().first()
        assertTrue(
            "Packages list contains safe default Chrome",
            packages.any { it.packageName == "com.android.chrome" && it.enabled }
        )
    }

    // 8. Concurrent cache access: no race/crash.
    @Test
    fun test8_concurrentCacheAccess_noRaceOrCrash() = runTest {
        val dataStore = createDataStore("t8.preferences_pb")
        val repository = TargetRepositoryImpl(dataStore, backgroundScope)

        val threadCount = 16
        val operationsPerThread = 500
        val latch = CountDownLatch(threadCount)
        val errors = mutableListOf<Throwable>()

        for (i in 0 until threadCount) {
            thread {
                try {
                    val pkg = "com.test.app$i"
                    for (j in 0 until operationsPerThread) {
                        // Concurrent synchronous reads
                        repository.isLocked("com.android.chrome")
                        repository.isLocked(pkg)
                        repository.isLocked("com.random.package.$j")
                    }
                } catch (t: Throwable) {
                    synchronized(errors) { errors.add(t) }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("Threads should complete within timeout", latch.await(10, TimeUnit.SECONDS))
        assertTrue("No concurrent access errors: $errors", errors.isEmpty())
    }

    // 9. Negative time limit validation
    @Test(expected = IllegalArgumentException::class)
    fun test9_negativeTimeLimit_throwsException() {
        com.example.selfdisciplinepoc01.target.model.TimeLimit(
            enabled = true,
            dailyLimitMinutes = -1
        )
    }

    // 10. Invalid schedule hours validation
    @Test(expected = IllegalArgumentException::class)
    fun test10_invalidScheduleHour_throwsException() {
        com.example.selfdisciplinepoc01.target.model.TimeSchedule(
            enabled = true,
            startHour = 25
        )
    }

    // 11. Update policy: schedule and timeLimit are serialized and restored accurately.
    @Test
    fun test11_updatePolicy_persistsScheduleAndTimeLimit() = runTest {
        val file = tempFolder.newFile("policy_prefs.preferences_pb")
        val job1 = kotlinx.coroutines.Job()
        val scope1 = kotlinx.coroutines.CoroutineScope(job1 + kotlinx.coroutines.Dispatchers.IO)
        val dataStore1 = PreferenceDataStoreFactory.create(scope = scope1, produceFile = { file })
        val repository1 = TargetRepositoryImpl(dataStore1, scope1)

        val pkg = "com.custom.target"
        repository1.add(pkg)

        val schedule = com.example.selfdisciplinepoc01.target.model.TimeSchedule(
            enabled = true,
            startHour = 22,
            startMinute = 30,
            endHour = 7,
            endMinute = 15
        )
        val timeLimit = com.example.selfdisciplinepoc01.target.model.TimeLimit(
            enabled = true,
            dailyLimitMinutes = 45
        )

        repository1.updatePolicy(pkg, schedule, timeLimit)

        val target1 = repository1.getTarget(pkg)
        assertNotNull("Target should be cached in targetMap", target1)
        assertEquals(schedule, target1?.schedule)
        assertEquals(timeLimit, target1?.timeLimit)

        job1.cancel()
        job1.join()

        // Reopen with new instance to verify persistence
        val job2 = kotlinx.coroutines.Job()
        val scope2 = kotlinx.coroutines.CoroutineScope(job2 + kotlinx.coroutines.Dispatchers.IO)
        val dataStore2 = PreferenceDataStoreFactory.create(scope = scope2, produceFile = { file })
        val repository2 = TargetRepositoryImpl(dataStore2, scope2)

        val list = repository2.getLockedPackages().first()
        val restoredTarget = list.find { it.packageName == pkg }
        assertNotNull("Restored target must exist", restoredTarget)
        assertEquals(schedule, restoredTarget?.schedule)
        assertEquals(timeLimit, restoredTarget?.timeLimit)

        job2.cancel()
        job2.join()
    }
}


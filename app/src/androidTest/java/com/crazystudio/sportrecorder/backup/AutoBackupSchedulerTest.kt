package com.crazystudio.sportrecorder.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented because the thing under test *is* WorkManager: that the daily job is enqueued once,
 * with the constraints that keep a backup off someone's data plan and battery, and that turning the
 * switch off really withdraws it. Local-only, like the rest of `androidTest` here.
 */
@RunWith(AndroidJUnit4::class)
class AutoBackupSchedulerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scheduler = WorkManagerAutoBackupScheduler(context)
    private val workManager = WorkManager.getInstance(context)

    @After fun tearDown() = scheduler.cancel()

    private fun infos(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork("auto-backup").get()

    @Test
    fun scheduling_enqueuesOneJobThatWaitsForChargingAndWifi() {
        scheduler.schedule()

        val info = infos().single()
        assertTrue(info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING)
        assertTrue("must wait for a charger", info.constraints.requiresCharging())
        assertEquals(NetworkType.UNMETERED, info.constraints.requiredNetworkType)
    }

    @Test
    fun schedulingTwice_doesNotStackUpJobs() {
        scheduler.schedule()
        scheduler.schedule()

        assertEquals(1, infos().size)
    }

    @Test
    fun cancelling_withdrawsTheJob() {
        scheduler.schedule()

        scheduler.cancel()

        assertTrue(infos().all { it.state == WorkInfo.State.CANCELLED })
    }
}

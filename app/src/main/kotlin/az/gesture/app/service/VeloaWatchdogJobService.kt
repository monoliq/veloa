package az.gesture.app.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * System-managed JobScheduler watchdog that persists across RAM cleans and reboots,
 * ensuring VeloaKeepAliveService automatically revives every time it is killed by an OEM cleaner.
 */
class VeloaWatchdogJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        try {
            if (VeloaKeepAliveService.shouldStayAlive(this)) {
                VeloaKeepAliveService.start(this)
                if (GestureAccessibilityService.instance != null) {
                    GestureAccessibilityService.instance?.refreshOverlays()
                } else {
                    VeloaKeepAliveService.activateFallbackIfNeeded()
                }
            } else {
                cancelAll(this)
            }
        } catch (_: Throwable) {
        }
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val PERIODIC_JOB_ID = 3101
        private const val RECOVERY_JOB_ID = 3102

        fun schedulePeriodic(context: Context) {
            try {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
                if (scheduler.getPendingJob(PERIODIC_JOB_ID) != null) return
                val job = JobInfo.Builder(
                    PERIODIC_JOB_ID,
                    ComponentName(context.applicationContext, VeloaWatchdogJobService::class.java)
                )
                    .setPeriodic(15L * 60L * 1000L)
                    .setPersisted(true)
                    .build()
                scheduler.schedule(job)
            } catch (_: Throwable) {
            }
        }

        fun scheduleQuickRecovery(context: Context) {
            armDeadManSwitch(context, minLatencyMs = 1500L, maxDelayMs = 3500L)
        }

        /**
         * Replaces the pending recovery job with a fresh window in system_server.
         * While VeloaKeepAliveService is alive, it keeps pushing this window forward so the job
         * never executes; the moment SIGKILL kills the process, the job fires automatically.
         */
        fun armDeadManSwitch(
            context: Context,
            minLatencyMs: Long = 6000L,
            maxDelayMs: Long = 7500L
        ) {
            try {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
                val job = JobInfo.Builder(
                    RECOVERY_JOB_ID,
                    ComponentName(context.applicationContext, VeloaWatchdogJobService::class.java)
                )
                    .setMinimumLatency(minLatencyMs)
                    .setOverrideDeadline(maxDelayMs)
                    .build()
                scheduler.schedule(job)
            } catch (_: Throwable) {
            }
        }

        fun cancelDeadManSwitch(context: Context) {
            try {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
                scheduler.cancel(RECOVERY_JOB_ID)
            } catch (_: Throwable) {
            }
        }

        fun cancelAll(context: Context) {
            try {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
                scheduler.cancel(PERIODIC_JOB_ID)
                scheduler.cancel(RECOVERY_JOB_ID)
            } catch (_: Throwable) {
            }
        }
    }
}

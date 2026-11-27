package com.reiux.lumen.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.reiux.lumen.LumenApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MediaContentJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchJob: Job? = null
    @Volatile
    private var wasStoppedBySystem = false

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "MediaContentJobService triggered by OS content URI change")
        val app = applicationContext as? LumenApplication ?: return false
        if (!app.settingsRepository.settings.value.autoBackupEnabled) {
            isJobRunning = false
            cancelMediaWatcher(this)
            return false
        }

        watchJob?.cancel()
        wasStoppedBySystem = false
        isJobRunning = true
        val scanJob = app.checkAndEnqueueRecentMedia(fromBoot = false, clearBootFlag = true)

        // Hold the JobService execution lease until the MediaStore scan finishes and any resulting
        // background upload completes so the OS doesn't freeze the process prematurely.
        // Reschedule ONLY after jobFinished() in finally so scheduling with the same JOB_ID does not
        // self-cancel the active job immediately.
        lateinit var launchedJob: Job
        launchedJob = serviceScope.launch(start = CoroutineStart.LAZY) {
            try {
                scanJob.join()
                delay(300L)
                while (isActive && app.uploadRepository.isUploading.value) {
                    delay(1000L)
                }
            } finally {
                val isCurrentJob = watchJob === launchedJob
                if (isCurrentJob) {
                    isJobRunning = false
                }
                if (!wasStoppedBySystem && isCurrentJob) {
                    if (params != null) {
                        jobFinished(params, false)
                    }
                    if (app.settingsRepository.settings.value.autoBackupEnabled) {
                        scheduleMediaWatcher(this@MediaContentJobService, force = true)
                    }
                }
            }
        }
        watchJob = launchedJob
        launchedJob.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        wasStoppedBySystem = true
        isJobRunning = false
        watchJob?.cancel()
        watchJob = null
        return true
    }

    override fun onDestroy() {
        isJobRunning = false
        watchJob?.cancel()
        watchJob = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MediaContentJobService"
        // High non-colliding ID outside WorkManager's default low sequential JobScheduler range
        private const val JOB_ID = 0x4C554D4E
        private const val LEGACY_JOB_ID = 4001
        @Volatile
        private var isJobRunning: Boolean = false

        fun ensureMediaWatcherScheduled(context: Context) {
            scheduleMediaWatcher(context, force = false)
        }

        @JvmOverloads
        fun scheduleMediaWatcher(context: Context, force: Boolean = false) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            if (!force && (isJobRunning || scheduler.getPendingJob(JOB_ID) != null)) {
                Log.d(TAG, "MediaStore JobScheduler watcher already active or pending; skipping reschedule")
                return
            }
            try {
                scheduler.cancel(LEGACY_JOB_ID)
            } catch (_: Exception) {}

            val component = ComponentName(context, MediaContentJobService::class.java)

            val builder = JobInfo.Builder(JOB_ID, component)
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                    )
                )
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                    )
                )
                .setTriggerContentUpdateDelay(1500)
                .setTriggerContentMaxDelay(4000)

            try {
                scheduler.schedule(builder.build())
                Log.d(TAG, "JobScheduler registered for MediaStore triggers")
            } catch (e: Exception) {
                Log.w(TAG, "Failed scheduling JobService: ${e.message}")
            }
        }

        fun cancelMediaWatcher(context: Context) {
            isJobRunning = false
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            try {
                scheduler.cancel(JOB_ID)
                scheduler.cancel(LEGACY_JOB_ID)
                Log.d(TAG, "JobScheduler cancelled MediaStore watcher")
            } catch (e: Exception) {
                Log.w(TAG, "Failed cancelling JobService: ${e.message}")
            }
        }
    }
}

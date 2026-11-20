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

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "MediaContentJobService triggered by OS content URI change")
        val app = applicationContext as? LumenApplication ?: return false
        if (!app.settingsRepository.settings.value.autoBackupEnabled) {
            cancelMediaWatcher(this)
            return false
        }

        watchJob?.cancel()
        val scanJob = app.checkAndEnqueueRecentMedia(fromBoot = false)

        // Reschedule to continuously observe subsequent media additions while auto-backup is on
        scheduleMediaWatcher(this)

        // Hold the JobService execution lease until the MediaStore scan finishes and any resulting
        // background upload completes so the OS doesn't freeze the process prematurely.
        watchJob = serviceScope.launch {
            try {
                scanJob.join()
                delay(300L)
                while (isActive && app.uploadRepository.isUploading.value) {
                    delay(1000L)
                }
            } finally {
                if (params != null && isActive) {
                    jobFinished(params, false)
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        watchJob?.cancel()
        watchJob = null
        return true
    }

    override fun onDestroy() {
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

        fun scheduleMediaWatcher(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
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

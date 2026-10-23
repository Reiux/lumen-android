package com.reiux.lumen.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.reiux.lumen.LumenApplication

class MediaContentJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "MediaContentJobService triggered by OS content URI change")
        val app = applicationContext as? LumenApplication ?: return false
        app.checkAndEnqueueRecentMedia()

        // Reschedule to continuously observe subsequent media additions
        scheduleMediaWatcher(this)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }

    companion object {
        private const val TAG = "MediaContentJobService"
        private const val JOB_ID = 4001

        fun scheduleMediaWatcher(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
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
    }
}

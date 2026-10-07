package com.hades.safepath.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hades.safepath.family.SafeBroadcastRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SafeBroadcastWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val safeBroadcastRepository: SafeBroadcastRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sent = safeBroadcastRepository.flushPendingQueue()
        return if (sent >= 0) Result.success() else Result.retry()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "safe_broadcast_flush"
    }
}

package com.hades.safepath.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hades.safepath.mesh.MeshNotesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MeshSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val meshNotesRepository: MeshNotesRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            meshNotesRepository.flushUnsyncedToCloud()
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "mesh_notes_sync"
    }
}

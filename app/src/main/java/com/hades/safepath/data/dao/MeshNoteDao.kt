package com.hades.safepath.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hades.safepath.data.entity.MeshNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeshNoteDao {

    @Query("SELECT * FROM mesh_notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MeshNoteEntity>>

    @Query("SELECT * FROM mesh_notes WHERE syncedToCloud = 0 ORDER BY createdAt ASC")
    suspend fun getUnsynced(): List<MeshNoteEntity>

    @Query("SELECT * FROM mesh_notes WHERE noteId = :noteId LIMIT 1")
    suspend fun getByNoteId(noteId: String): MeshNoteEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(note: MeshNoteEntity): Long

    @Query("UPDATE mesh_notes SET syncedToCloud = 1 WHERE noteId = :noteId")
    suspend fun markSynced(noteId: String)

    @Query("DELETE FROM mesh_notes WHERE createdAt + ttlMs < :now")
    suspend fun deleteExpired(now: Long)

    @Query("SELECT COUNT(*) FROM mesh_notes WHERE syncedToCloud = 0")
    suspend fun countUnsynced(): Int
}

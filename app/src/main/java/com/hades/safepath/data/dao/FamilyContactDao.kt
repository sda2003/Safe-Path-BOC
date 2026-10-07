package com.hades.safepath.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hades.safepath.data.entity.FamilyContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyContactDao {

    @Query("SELECT * FROM family_contacts WHERE ownerId = :ownerId ORDER BY contactName ASC")
    fun observeByOwner(ownerId: String): Flow<List<FamilyContactEntity>>

    @Query("SELECT COUNT(*) FROM family_contacts WHERE ownerId = :ownerId")
    suspend fun countByOwner(ownerId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(contacts: List<FamilyContactEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: FamilyContactEntity)

    @Query("DELETE FROM family_contacts WHERE id = :contactId AND ownerId = :ownerId")
    suspend fun delete(contactId: String, ownerId: String)

    @Query("DELETE FROM family_contacts WHERE ownerId = :ownerId")
    suspend fun deleteAllForOwner(ownerId: String)
}

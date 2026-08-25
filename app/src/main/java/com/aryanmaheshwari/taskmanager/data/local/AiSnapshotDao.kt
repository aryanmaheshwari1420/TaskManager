package com.aryanmaheshwari.taskmanager.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface AiSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: AiSnapshot): Long

    @Update
    suspend fun update(snapshot: AiSnapshot)

    @Delete
    suspend fun delete(snapshot: AiSnapshot)

    @Query("DELETE FROM ai_snapshot_table WHERE id = :id")
    suspend fun deleteById(id: Int)

    // Returns newest first, showing all drafts and snapshots
    @Query("SELECT * FROM ai_snapshot_table ORDER BY createdAt DESC")
    fun getAllSnapshots(): LiveData<List<AiSnapshot>>

    // Returns all pending drafts that are waiting for sync/generation
    @Query("SELECT * FROM ai_snapshot_table WHERE isPending = 1 ORDER BY createdAt ASC")
    suspend fun getPendingSnapshots(): List<AiSnapshot>
}

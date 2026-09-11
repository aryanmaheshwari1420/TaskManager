package com.aryanmaheshwari.taskmanager.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ChecklistItemDao {

    @Insert
    suspend fun insertChecklistItem(item: ChecklistItem): Long

    @Insert
    suspend fun insertChecklistItems(items: List<ChecklistItem>)

    @Update
    suspend fun updateChecklistItem(item: ChecklistItem)

    @Delete
    suspend fun deleteChecklistItem(item: ChecklistItem)

    @Query("SELECT * FROM checklist_item_table WHERE taskId = :taskId ORDER BY id ASC")
    fun getChecklistItemsForTask(taskId: Int): LiveData<List<ChecklistItem>>

    @Query("DELETE FROM checklist_item_table WHERE taskId = :taskId")
    suspend fun deleteChecklistItemsForTask(taskId: Int)
}

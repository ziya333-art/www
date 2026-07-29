package com.jegly.www.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteUrl(url: String)

    @Query("SELECT * FROM bookmarks ORDER BY folder IS NULL DESC, folder ASC, position ASC, createdAt DESC")
    fun all(): Flow<List<BookmarkEntity>>

    /** Drives the filled/outlined bookmark toggle in the toolbar. */
    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun isBookmarked(url: String): Flow<Boolean>

    @Query("SELECT DISTINCT folder FROM bookmarks WHERE folder IS NOT NULL ORDER BY folder ASC")
    fun folders(): Flow<List<String>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM bookmarks WHERE folder IS :folder")
    suspend fun nextPosition(folder: String?): Int

    @Query("DELETE FROM bookmarks")
    suspend fun clear()
}

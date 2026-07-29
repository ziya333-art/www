package com.jegly.www.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    /**
     * Upsert-and-increment in one statement. Done as SQLite's native `ON CONFLICT DO UPDATE`
     * rather than a read-then-write pair so a rapid redirect chain can't interleave two visits
     * and lose a count — the whole thing is one atomic statement, no explicit transaction needed.
     *
     * The title is only overwritten when the new one is non-empty: `onPageStarted` fires with no
     * title yet, and we don't want that blanking a title we already recorded.
     */
    @Query(
        """
        INSERT INTO history (url, title, host, lastVisitedAt, visitCount)
        VALUES (:url, :title, :host, :now, 1)
        ON CONFLICT(url) DO UPDATE SET
            lastVisitedAt = :now,
            visitCount = visitCount + 1,
            title = CASE WHEN :title != '' THEN :title ELSE title END
        """
    )
    suspend fun recordVisit(url: String, title: String, host: String, now: Long)

    /** Title arrives after the navigation commits (`onReceivedTitle`), so it lands as a follow-up. */
    @Query("UPDATE history SET title = :title WHERE url = :url AND :title != ''")
    suspend fun updateTitle(url: String, title: String)

    @Query("SELECT * FROM history ORDER BY lastVisitedAt DESC LIMIT :limit")
    fun recent(limit: Int = 500): Flow<List<HistoryEntity>>

    /**
     * Omnibox autocomplete. Ranked by visit count first so a site you use constantly outranks
     * something you opened once yesterday, which is what makes type-two-letters-and-hit-enter work.
     */
    @Query(
        """
        SELECT * FROM history
        WHERE url LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%'
        ORDER BY visitCount DESC, lastVisitedAt DESC
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int = 8): List<HistoryEntity>

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteUrl(url: String)

    @Query("DELETE FROM history WHERE host = :host")
    suspend fun deleteHost(host: String)

    @Query("DELETE FROM history WHERE lastVisitedAt >= :since")
    suspend fun deleteSince(since: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}

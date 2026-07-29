package com.jegly.www.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainSettingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: DomainSettingEntity): Long

    @Update
    suspend fun update(setting: DomainSettingEntity)

    @Delete
    suspend fun delete(setting: DomainSettingEntity)

    @Query("SELECT * FROM domain_settings ORDER BY domain ASC")
    fun all(): Flow<List<DomainSettingEntity>>

    /**
     * Candidates for a host, matched in the DB rather than in memory.
     *
     * Returns both an exact match and any wildcard entry covering a parent domain, ordered longest
     * domain first so the most specific rule wins — "*.example.com" must not beat a rule written
     * for "cdn.example.com" exactly.
     */
    @Query(
        """
        SELECT * FROM domain_settings
        WHERE domain = :host
           OR (domain LIKE '*.%' AND (:host = SUBSTR(domain, 3) OR :host LIKE '%.' || SUBSTR(domain, 3)))
        ORDER BY LENGTH(domain) DESC
        """
    )
    suspend fun matching(host: String): List<DomainSettingEntity>

    @Query("SELECT * FROM domain_settings WHERE domain = :domain LIMIT 1")
    suspend fun byDomain(domain: String): DomainSettingEntity?

    @Query("DELETE FROM domain_settings")
    suspend fun clear()
}

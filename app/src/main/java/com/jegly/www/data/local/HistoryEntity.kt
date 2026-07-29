package com.jegly.www.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per visited URL, not per visit. Revisiting an existing URL bumps [lastVisitedAt] and
 * increments [visitCount] rather than inserting a duplicate — see [HistoryDao.recordVisit].
 *
 * Collapsing visits this way matters more here than in a plaintext browser: every row lands in the
 * SQLCipher-encrypted DB, so a naive append-per-navigation would grow the encrypted page set (and
 * the cost of purging it) without giving the user anything the collapsed view doesn't already show.
 */
@Entity(
    tableName = "history",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["lastVisitedAt"]),
        Index(value = ["host"])
    ]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    /** Registrable host, denormalised so "clear everything from this site" is a single indexed delete. */
    val host: String,
    val lastVisitedAt: Long,
    val visitCount: Int = 1
)

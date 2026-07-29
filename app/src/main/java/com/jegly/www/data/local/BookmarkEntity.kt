package com.jegly.www.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved bookmark. [folder] is a flat label rather than a self-referencing parent id — nested
 * folders would need recursive queries and a move/reparent UI, which isn't worth it until there's
 * a reason to have them.
 */
@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["folder"])
    ]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val folder: String? = null,
    val createdAt: Long,
    /** Manual ordering within a folder; ties break on [createdAt]. */
    val position: Int = 0
)

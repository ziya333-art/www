package com.jegly.www.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Version 1 with no migrations: `www` ships under a new applicationId, so there is no installed
 * installed base to migrate from.
 *
 * Every table here lives in the SQLCipher-encrypted file opened in AppModule, keyed by the
 * passphrase PassphraseGate releases after auth. Browsing history in particular only exists
 * behind that key.
 */
@Database(
    entities = [HistoryEntity::class, BookmarkEntity::class, DomainSettingEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun domainSettingDao(): DomainSettingDao
}

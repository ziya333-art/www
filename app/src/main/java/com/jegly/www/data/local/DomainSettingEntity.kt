package com.jegly.www.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-domain overrides of the global privacy toggles — the feature that makes a deny-by-default
 * browser usable, because it lets a site that genuinely needs JavaScript have it without turning
 * JavaScript on everywhere.
 *
 * Every override is a nullable tri-state: null means "inherit the global setting", true/false mean
 * an explicit override. A non-null false is therefore meaningfully different from null even when
 * the global default is already false — it pins the value so a later change to the global default
 * does not silently loosen this domain.
 */
@Entity(
    tableName = "domain_settings",
    indices = [Index(value = ["domain"], unique = true)]
)
data class DomainSettingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /**
     * Stored lowercase, without scheme or port. A leading "*." makes it match subdomains too, so
     * "*.example.com" covers cdn.example.com — see [DomainSettingsResolver] for match precedence.
     */
    val domain: String,

    val javaScriptEnabled: Boolean? = null,
    val domStorageEnabled: Boolean? = null,
    val cookiePolicy: String? = null,
    val blockThirdPartyRequests: Boolean? = null,
    val blockTrackers: Boolean? = null,
    val stripTrackingQueries: Boolean? = null,
    val userAgentKey: String? = null,
    val displayImages: Boolean? = null,

    val createdAt: Long = System.currentTimeMillis()
)

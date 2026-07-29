package com.jegly.www.di

import android.content.Context
import androidx.room.Room
import com.jegly.www.data.local.AppDatabase
import com.jegly.www.data.local.BookmarkDao
import com.jegly.www.data.local.DomainSettingDao
import com.jegly.www.data.local.HistoryDao
import com.jegly.www.network.GuardedDns
import com.jegly.www.network.ResponseSizeInterceptor
import com.jegly.www.network.SwitchableDohDns
import com.jegly.www.security.EncryptionManager
import com.jegly.www.security.PassphraseGate
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Bare-bones OkHttp used ONLY to bootstrap DoH. Must not itself use DoH (would recurse) and
     * shouldn't carry the response-size interceptor (DoH responses can spike).
     */
    @Provides
    @Singleton
    @Named("bootstrap")
    fun provideBootstrapOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Main OkHttp: HTTPS-only, MODERN_TLS, bounded body, explicit timeouts, user-selectable DoH. */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        @Named("bootstrap") bootstrapClient: OkHttpClient,
        encryptionManager: EncryptionManager
    ): OkHttpClient {
        val dns = GuardedDns(
            SwitchableDohDns(
                bootstrapClient = bootstrapClient,
                providerKeyLookup = { encryptionManager.getString("doh_provider") }
            )
        )
        return OkHttpClient.Builder()
            // Defense-in-depth: upgrade plain HTTP requests to HTTPS before they leave the process.
            // network-security-config already blocks cleartext at the OS layer.
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url
                if (!url.isHttps) {
                    val upgraded = url.newBuilder().scheme("https").build()
                    chain.proceed(request.newBuilder().url(upgraded).build())
                } else {
                    chain.proceed(request)
                }
            }
            // Cap response bodies to prevent OOM via giant feeds.
            .addNetworkInterceptor(ResponseSizeInterceptor())
            // Only modern TLS (1.2 / 1.3); no cleartext.
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            // Explicit timeouts — callTimeout defaults to 0 (infinite). Hostile servers can hang us forever.
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .dns(dns)
            .build()
    }

    /**
     * Blocks on PassphraseGate.await() — MainActivity opens the gate after auth (plain or biometric),
     * before it composes anything that can reach the graph. The block is a no-op once the gate is
     * open, so subsequent Hilt fan-out is non-blocking.
     *
     * Note for the VIEW-intent path: a link fired at `www` from another app while it is cold still
     * lands here behind auth. That is deliberate — history lives in this file, and unlocking it is
     * the point of the gate — but it does mean an external link tap can sit on the biometric prompt
     * before the page starts loading.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext appContext: Context,
        gate: PassphraseGate
    ): AppDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = runBlocking { gate.await() }
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(appContext, AppDatabase::class.java, "www.db")
            .openHelperFactory(factory)
            .build()
    }

    @Provides
    @Singleton
    fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides
    @Singleton
    fun provideBookmarkDao(db: AppDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    @Singleton
    fun provideDomainSettingDao(db: AppDatabase): DomainSettingDao = db.domainSettingDao()
}

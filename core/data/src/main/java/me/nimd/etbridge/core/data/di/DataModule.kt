package me.nimd.etbridge.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import me.nimd.etbridge.core.data.db.CacheDao
import me.nimd.etbridge.core.data.db.EtbridgeDatabase
import me.nimd.etbridge.core.data.db.PendingDutyLeaveDao
import me.nimd.etbridge.core.data.network.EtlabHeadersInterceptor
import me.nimd.etbridge.core.data.session.SessionStore
import me.nimd.etbridge.core.model.EtlabJson
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /** One client for API calls, images (Coil) and downloads, sharing connections and cache. */
    @Provides
    @Singleton
    fun okHttpClient(@ApplicationContext context: Context, sessionStore: SessionStore): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(EtlabHeadersInterceptor(sessionStore))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cache(Cache(File(context.cacheDir, "http"), 50L * 1024 * 1024))
            .build()

    @Provides
    @Singleton
    fun httpClient(okHttp: OkHttpClient): HttpClient = HttpClient(OkHttp) {
        engine { preconfigured = okHttp }
        install(ContentNegotiation) { json(EtlabJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 45_000
            connectTimeoutMillis = 15_000
        }
        expectSuccess = false
    }

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): EtbridgeDatabase =
        Room.databaseBuilder(context, EtbridgeDatabase::class.java, "etbridge.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun cacheDao(db: EtbridgeDatabase): CacheDao = db.cacheDao()

    @Provides
    fun pendingDutyLeaveDao(db: EtbridgeDatabase): PendingDutyLeaveDao = db.pendingDutyLeaveDao()
}

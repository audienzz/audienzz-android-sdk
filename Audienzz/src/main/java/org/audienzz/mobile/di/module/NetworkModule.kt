package org.audienzz.mobile.di.module

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import org.audienzz.BuildConfig
import org.audienzz.mobile.api.config.RemoteConfigApi
import org.audienzz.mobile.event.network.EventApi
import retrofit2.Converter.Factory
import retrofit2.Retrofit
import javax.inject.Singleton
import java.util.concurrent.TimeUnit

@Module
internal class NetworkModule {

    @Singleton
    @Provides
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    @Singleton
    @Provides
    fun provideJsonConverterFactory(json: Json): Factory {
        val jsonType = "application/json".toMediaType()
        return json.asConverterFactory(jsonType)
    }

    @Provides
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) Level.BODY else Level.NONE
        }

    @Provides
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

    @Provides
    fun provideAuthApiService(
        converterFactory: Factory,
        okHttpClient: OkHttpClient,
    ): EventApi = Retrofit.Builder()
        .addConverterFactory(converterFactory)
        // Bound a complete analytics attempt, including a server that never finishes its reply.
        // The derived client shares the connection pool with the supplied client.
        .client(okHttpClient.newBuilder().callTimeout(30, TimeUnit.SECONDS).build())
        .baseUrl(BuildConfig.EVENTS_BASE_URL)
        .build()
        .create(EventApi::class.java)

    @Provides
    fun provideRemoteConfigApi(
        converterFactory: Factory,
        okHttpClient: OkHttpClient,
    ): RemoteConfigApi = Retrofit.Builder()
        .addConverterFactory(converterFactory)
        .client(okHttpClient)
        .baseUrl("https://api.adnz.co/api/ws-sdk-config/public/v1/")
        .build()
        .create(RemoteConfigApi::class.java)
}

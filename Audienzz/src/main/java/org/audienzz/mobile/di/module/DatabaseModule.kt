package org.audienzz.mobile.di.module

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import org.audienzz.mobile.event.database.AudienzzDatabase
import javax.inject.Singleton

@Module
internal class DatabaseModule {

    @Singleton
    @Provides
    fun provideDatabase(context: Context): AudienzzDatabase =
        Room.databaseBuilder(context, AudienzzDatabase::class.java, "audienzz")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideRemoteConfigDao(
        database: AudienzzDatabase,
    ): org.audienzz.mobile.event.database.dao.RemoteConfigDao =
        database.remoteConfigDao()

    @Provides
    fun providePublisherConfigDao(
        database: AudienzzDatabase,
    ): org.audienzz.mobile.event.database.dao.PublisherConfigDao =
        database.publisherConfigDao()
}

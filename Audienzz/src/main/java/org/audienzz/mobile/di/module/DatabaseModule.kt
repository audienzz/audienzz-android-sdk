package org.audienzz.mobile.di.module

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import org.audienzz.mobile.event.database.AudienzzDatabase
import org.audienzz.mobile.event.database.dao.EventDao
import javax.inject.Singleton

@Module
internal class DatabaseModule {

    @Singleton
    @Provides
    fun provideDatabase(context: Context): AudienzzDatabase =
        Room.databaseBuilder(context, AudienzzDatabase::class.java, "audienzz")
            .build()

    @Provides
    fun provideEventDao(database: AudienzzDatabase): EventDao = database.eventDao()
}

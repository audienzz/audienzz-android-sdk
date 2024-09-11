package org.audienzz.mobile.di.module

import android.content.Context
import android.content.SharedPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.di.qualifier.IO
import org.audienzz.mobile.event.id.AdIdProvider
import org.audienzz.mobile.event.id.AdIdProviderImpl
import org.audienzz.mobile.event.id.CompanyIdProvider
import org.audienzz.mobile.event.id.CompanyIdProviderImpl
import org.audienzz.mobile.event.preferences.EventPreferences
import org.audienzz.mobile.event.preferences.EventPreferencesImpl
import org.audienzz.mobile.util.CurrentActivityTracker
import javax.inject.Singleton

@Module
internal interface UtilModule {

    @Singleton
    @Binds
    fun bindEventPreferences(preferencesImpl: EventPreferencesImpl): EventPreferences

    @Singleton
    @Binds
    fun bindAdIdProvider(adIdProvider: AdIdProviderImpl): AdIdProvider

    @Singleton
    @Binds
    fun bindCompanyIdProvider(provider: CompanyIdProviderImpl): CompanyIdProvider

    @Module
    companion object {

        @Provides
        @Singleton
        fun provideCurrentActivityTracker(): CurrentActivityTracker =
            AudienzzPrebidMobile.CURRENT_ACTIVITY_TRACKER

        @Provides
        @Singleton
        fun providePreferences(context: Context): SharedPreferences =
            context.getSharedPreferences("audienzz_preferences", Context.MODE_PRIVATE)

        @IO
        @Provides
        fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}

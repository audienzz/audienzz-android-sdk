package org.audienzz.mobile.di

import android.content.Context
import android.util.Log
import dagger.BindsInstance
import dagger.Component
import org.audienzz.mobile.di.module.DatabaseModule
import org.audienzz.mobile.di.module.LoggerModule
import org.audienzz.mobile.di.module.NetworkModule
import org.audienzz.mobile.di.module.RepositoryModule
import org.audienzz.mobile.di.module.UtilModule
import org.audienzz.mobile.event.EventLogger
import org.audienzz.mobile.util.PpidManager
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        NetworkModule::class,
        DatabaseModule::class,
        RepositoryModule::class,
        LoggerModule::class,
        UtilModule::class,
    ],
)
internal interface MainComponent {

    fun getEventLogger(): EventLogger
    fun getPpidManager(): PpidManager

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun applicationContext(context: Context): Builder

        fun build(): MainComponent
    }

    companion object {

        private const val TAG = "MainComponent"

        private var instance: MainComponent? = null

        val eventLogger: EventLogger?
            get() = instance?.getEventLogger().also {
                if (it == null) {
                    Log.e(TAG, "MainComponent is not initialized")
                }
            }

        val ppidManager: PpidManager?
            get() = instance?.getPpidManager().also {
                if (it == null) {
                    Log.e(TAG, "MainComponent is not initialized")
                }
            }

        fun init(context: Context) {
            instance = DaggerMainComponent.builder()
                .applicationContext(context)
                .build()
        }
    }
}

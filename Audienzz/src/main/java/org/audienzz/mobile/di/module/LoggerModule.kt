package org.audienzz.mobile.di.module

import dagger.Binds
import dagger.Module
import org.audienzz.mobile.event.EventLogger
import org.audienzz.mobile.event.EventLoggerImpl

@Module
internal interface LoggerModule {

    @Binds
    fun bindLogger(logger: EventLoggerImpl): EventLogger
}

package org.audienzz.mobile.di.module

import dagger.Binds
import dagger.Module
import org.audienzz.mobile.event.repository.remote.RemoteEventRepository
import org.audienzz.mobile.event.repository.remote.RemoteEventRepositoryImpl
import org.audienzz.mobile.repository.RemoteConfigRepository
import org.audienzz.mobile.repository.RemoteConfigRepositoryImpl

@Module
internal interface RepositoryModule {

    @Binds
    fun bindRemoteEventRepository(repository: RemoteEventRepositoryImpl): RemoteEventRepository

    @Binds
    fun bindRemoteConfigRepository(repository: RemoteConfigRepositoryImpl): RemoteConfigRepository
}

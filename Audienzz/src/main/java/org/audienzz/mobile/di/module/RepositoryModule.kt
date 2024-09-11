package org.audienzz.mobile.di.module

import dagger.Binds
import dagger.Module
import org.audienzz.mobile.event.repository.local.LocalEventRepository
import org.audienzz.mobile.event.repository.local.LocalEventRepositoryImpl
import org.audienzz.mobile.event.repository.remote.RemoteEventRepository
import org.audienzz.mobile.event.repository.remote.RemoteEventRepositoryImpl

@Module
internal interface RepositoryModule {

    @Binds
    fun bindLocalEventRepository(repository: LocalEventRepositoryImpl): LocalEventRepository

    @Binds
    fun bindRemoteEventRepository(repository: RemoteEventRepositoryImpl): RemoteEventRepository
}

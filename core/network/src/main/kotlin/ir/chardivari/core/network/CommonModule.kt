package ir.chardivari.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ir.chardivari.core.common.AppDispatchers
import ir.chardivari.core.common.DefaultAppDispatchers

@Module
@InstallIn(SingletonComponent::class)
object CommonModule {

    @Provides
    fun provideDispatchers(): AppDispatchers = DefaultAppDispatchers()
}

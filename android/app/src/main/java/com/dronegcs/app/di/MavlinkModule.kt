package com.dronegcs.app.di

import com.dronegcs.app.data.bluetooth.BluetoothLink
import com.dronegcs.app.data.mavlink.MavlinkFlightRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MavlinkModule {

    @Provides
    @Singleton
    fun provideMavlinkFlightRepository(
        link: BluetoothLink,
        scope: CoroutineScope
    ): MavlinkFlightRepository {
        return MavlinkFlightRepository(link, scope)
    }
}
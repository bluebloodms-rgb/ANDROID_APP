package com.dronegcs.app.di

import android.bluetooth.BluetoothManager
import android.content.Context
import com.dronegcs.app.data.bluetooth.BluetoothSppService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {

    @Provides
    @Singleton
    fun provideBluetoothManager(@ApplicationContext context: Context): BluetoothManager {
        return context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    // BluetoothSppService is a Foreground Service, not a singleton
    // It will be started via Context.startForegroundService()
    // We provide a factory for creating intents to communicate with it
    @Provides
    fun provideBluetoothServiceIntentFactory(@ApplicationContext context: Context): BluetoothServiceIntentFactory {
        return BluetoothServiceIntentFactory(context)
    }
}

class BluetoothServiceIntentFactory @Inject constructor(
    private val context: Context
) {
    fun createConnectIntent(device: android.bluetooth.BluetoothDevice): android.content.Intent {
        return android.content.Intent(context, BluetoothSppService::class.java).apply {
            action = BluetoothSppService.ACTION_CONNECT
            putExtra(BluetoothSppService.EXTRA_DEVICE, device)
        }
    }

    fun createDisconnectIntent(): android.content.Intent {
        return android.content.Intent(context, BluetoothSppService::class.java).apply {
            action = BluetoothSppService.ACTION_DISCONNECT
        }
    }

    fun createSendBytesIntent(bytes: ByteArray): android.content.Intent {
        return android.content.Intent(context, BluetoothSppService::class.java).apply {
            action = BluetoothSppService.ACTION_SEND_BYTES
            putExtra(BluetoothSppService.EXTRA_BYTES, bytes)
        }
    }
}
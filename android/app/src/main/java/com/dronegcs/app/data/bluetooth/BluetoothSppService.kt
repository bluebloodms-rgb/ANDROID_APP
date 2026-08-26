package com.dronegcs.app.data.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.dronegcs.app.MainActivity
import com.dronegcs.app.R
import com.dronegcs.app.domain.model.Command
import com.dronegcs.app.domain.model.ConnectionState
import com.dronegcs.app.domain.protocol.MavlinkProtocol
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.IOException
import java.util.UUID

/**
 * Foreground Service for Bluetooth SPP (RFCOMM) communication
 * Handles connection, reading, and writing to the flight controller
 */
@AndroidEntryPoint
class BluetoothSppService : Service() {

    @Inject lateinit var link: BluetoothLink

    private val binder = LocalBinder()

    // Coroutine scope for service operations
    private var serviceScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var readJob: Job? = null
    private var writeJob: Job? = null
    private var connectJob: Job? = null

    // Bluetooth objects
    private var bluetoothSocket: BluetoothSocket? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var isConnected = false

    // Notification
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "bluetooth_spp_channel"

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothSppService = this@BluetoothSppService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Must enter foreground immediately after startForegroundService()
        // or Android 12+ throws ForegroundServiceDidNotStartInTimeException
        startForeground(NOTIFICATION_ID, buildNotification())
        startWriteWorker()
        Timber.d("BluetoothSppService created")
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_CONNECT -> {
                val device = intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
                device?.let { connect(it) }
            }
            ACTION_DISCONNECT -> disconnect()
            ACTION_SEND_BYTES -> {
                val bytes = intent.getByteArrayExtra(EXTRA_BYTES)
                bytes?.let { link.sendRaw(it) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectInternal()
        serviceScope.cancel()
        stopForeground(true)
        Timber.d("BluetoothSppService destroyed")
    }

    // --- Public API ---

    fun connect(device: BluetoothDevice) {
        if (isConnected) {
            Timber.w("Already connected, disconnecting first")
            disconnectInternal()
        }

        try {
            link.publishState(ConnectionState.Connecting(device.name))
            bluetoothDevice = device

            connectJob = serviceScope.launch {
                val socket = try {
                    withTimeoutOrNull(10000) {
                        device.createRfcommSocketToServiceRecord(UUID.fromString(MavlinkProtocol.SPP_UUID))
                    }
                } catch (e: SecurityException) {
                    Timber.e(e, "Missing BLUETOOTH_CONNECT permission")
                    link.publishState(ConnectionState.Error("Bluetooth permission missing"))
                    return@launch
                }

                if (socket == null) {
                    Timber.e("Failed to create RFCOMM socket (timeout)")
                    link.publishState(ConnectionState.Error("Failed to create socket"))
                    return@launch
                }

                bluetoothSocket = socket

                try {
                    socket.connect()
                    isConnected = true
                    link.publishState(ConnectionState.Connected(device.name ?: "Unknown", device.address))
                    Timber.d("Connected to ${device.name} (${device.address})")
                    startReadLoop()
                    updateNotification()
                } catch (e: IOException) {
                    Timber.e(e, "Connection failed")
                    link.publishState(ConnectionState.Error("Connection failed: ${e.message}"))
                    closeSocket()
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException in connect()")
            link.publishState(ConnectionState.Error("Bluetooth permission missing"))
        }
    }

    fun disconnect() {
        disconnectInternal()
        link.publishState(ConnectionState.Disconnected("User disconnected"))
        updateNotification()
    }

    fun sendCommand(command: Command) {
        val text = command.toStatustextString()
        val frame = MavlinkProtocol.encodeStatustext(text)
        link.sendRaw(frame)
        Timber.d("Sent command: $text")
    }

    fun sendRaw(bytes: ByteArray) {
        link.sendRaw(bytes)
    }

    // --- Private Implementation ---

    private fun startReadLoop() {
        readJob = serviceScope.launch(Dispatchers.IO) {
            val inputStream = bluetoothSocket?.inputStream ?: return@launch
            val buffer = ByteArray(1024)
            val accumulatedBuffer = java.io.ByteArrayOutputStream()
            var loggedFirstRx = false

            while (isConnected && coroutineContext.isActive) {
                try {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        if (!loggedFirstRx) {
                            Timber.d("First data received from flight controller: $bytesRead bytes")
                            loggedFirstRx = true
                        }
                        accumulatedBuffer.write(buffer, 0, bytesRead)
                        // Emit accumulated data for MAVLink parsing
                        val data = accumulatedBuffer.toByteArray()
                        link.publishData(data)
                        accumulatedBuffer.reset()
                    }
                } catch (e: IOException) {
                    if (isConnected) {
                        Timber.e(e, "Read error")
                        handleDisconnect("Read error: ${e.message}")
                    }
                    break
                }
            }
        }
    }

    private fun startWriteWorker() {
        writeJob = serviceScope.launch(Dispatchers.IO) {
            for (bytes in link.outgoing) {
                if (!isConnected) continue
                try {
                    bluetoothSocket?.outputStream?.write(bytes)
                    bluetoothSocket?.outputStream?.flush()
                } catch (e: IOException) {
                    Timber.e(e, "Write error")
                    handleDisconnect("Write error: ${e.message}")
                    break
                }
            }
        }
    }

    private fun handleDisconnect(reason: String) {
        if (!isConnected) return
        isConnected = false
        closeSocket()
        link.publishState(ConnectionState.Disconnected(reason))
        updateNotification()
    }

    private fun disconnectInternal() {
        isConnected = false
        connectJob?.cancel()
        readJob?.cancel()
        writeJob?.cancel()
        closeSocket()
        bluetoothDevice = null
    }

    private fun closeSocket() {
        bluetoothSocket?.let {
            try {
                it.close()
            } catch (e: IOException) {
                Timber.w(e, "Error closing socket")
            }
        }
        bluetoothSocket = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bluetooth SPP Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows Bluetooth connection status to flight controller"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val state = link.currentState()
        val contentTitle = when (state) {
            is ConnectionState.Connected -> "Connected to ${state.deviceName}"
            is ConnectionState.Connecting -> "Connecting to ${state.deviceName}..."
            is ConnectionState.Error -> "Connection Error"
            else -> "Drone GCS ready"
        }

        val contentText = when (state) {
            is ConnectionState.Connected -> "Flight controller link active"
            is ConnectionState.Connecting -> "Establishing RFCOMM connection..."
            is ConnectionState.Error -> state.message
            else -> "Waiting for Bluetooth connection"
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    // --- Intent Actions ---
    companion object {
        const val ACTION_CONNECT = "com.dronegcs.app.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.dronegcs.app.ACTION_DISCONNECT"
        const val ACTION_SEND_COMMAND = "com.dronegcs.app.ACTION_SEND_COMMAND"
        const val ACTION_SEND_BYTES = "com.dronegcs.app.ACTION_SEND_BYTES"
        const val EXTRA_DEVICE = "device"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_BYTES = "bytes"
    }
}
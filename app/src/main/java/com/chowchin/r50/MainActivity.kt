package com.chowchin.r50

import android.Manifest
import android.bluetooth.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.chowchin.r50.database.*
import com.chowchin.r50.fit.FitFileGenerator
import com.chowchin.r50.ftms.FTMSManager
import com.chowchin.r50.ui.FTMSConfigurationScreen
import com.chowchin.r50.ui.theme.R50ConnectorTheme
import com.google.gson.Gson
import kotlinx.coroutines.*
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors

data class RowingData(
    val hex: String,
    val timestamp: Long,
    val elapsedSecond: Int? = null,
    val strokeCount: Int? = null,
    val strokePerMinute: Int? = null,
    val distance: Int? = null,
    val calories: Int? = null,
    val heartbeat: Int? = null,
    val power: Int? = null,
    val gear: Int? = null,
)

data class BluetoothDeviceInfo(
    val name: String?,
    val address: String,
    val rssi: Int? = null,
)

class MainActivity : ComponentActivity() {
    companion object {
        const val APP_VERSION = "v1.3.1"
    }

    private var deviceMac = ""
    private val uuidFFF1 = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    private val uuidFFF2 = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    private val uuidCCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var gattConnection: BluetoothGatt? = null
    private var fff2Char: BluetoothGattCharacteristic? = null
    private val handler = Handler(Looper.getMainLooper())

    // SharedPreferences for saving settings
    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "R50ConnectorSettings"
    private val KEY_DEVICE_MAC = "device_mac"
    private val KEY_MQTT_URI = "mqtt_uri"
    private val KEY_MQTT_USERNAME = "mqtt_username"
    private val KEY_MQTT_PASSWORD = "mqtt_password"
    private val KEY_MQTT_ENABLED = "mqtt_enabled"
    private val KEY_MQTT_TOPIC = "mqtt_topic"
    private val KEY_FTMS_ENABLED = "ftms_enabled"
    private val KEY_FTMS_CADENCE_RATIO = "ftms_cadence_ratio"

    // MQTT Configuration
    private lateinit var mqttClient: MqttAsyncClient
    private var mqttServerUri = ""
    private var mqttUsername = ""
    private var mqttPassword = ""
    private var mqttEnabled = false
    private val mqttClientId = "R50Connector_${System.currentTimeMillis()}"
    private var mqttTopic = "r50/rowing_data"

    // MQTT Auto-reconnection
    private var mqttReconnectAttempts = 0
    private val maxMqttReconnectAttempts = 10
    private var mqttReconnectJob: Job? = null
    private val mqttReconnectDelays = listOf(1000L, 2000L, 5000L, 10000L, 30000L) // Progressive delays in milliseconds

    // State for current rowing data
    private var currentRowingData: MutableState<RowingData?> = mutableStateOf(null)

    // MQTT connection status
    private var mqttConnectionStatus = mutableStateOf("Disconnected")

    // Bluetooth connection status
    private var bluetoothConnectionStatus = mutableStateOf("Disconnected")

    // FTMS (Fitness Machine Service) support
    private lateinit var ftmsManager: FTMSManager
    private var ftmsEnabled = false
    private var ftmsCadenceRatio = 1.0f
    private var ftmsConnectionStatus = mutableStateOf("Stopped")

    // Database components
    private lateinit var database: RowingDatabase
    lateinit var repository: RowingRepository
    private var currentSessionId: Long? = null
    private var dataRecordingJob: Job? = null
    val databaseScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Bluetooth scanning
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isScanning = mutableStateOf(false)
    private var discoveredDevices = mutableStateOf<List<BluetoothDeviceInfo>>(emptyList())

    private val bluetoothReceiver =
        object : android.content.BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()

                        device?.let {
                            val deviceInfo =
                                BluetoothDeviceInfo(
                                    name =
                                        if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) ==
                                            PackageManager.PERMISSION_GRANTED
                                        ) {
                                            it.name
                                        } else {
                                            null
                                        },
                                    address = it.address,
                                    rssi = rssi,
                                )

                            val currentList = discoveredDevices.value.toMutableList()
                            if (!currentList.any { existing -> existing.address == deviceInfo.address }) {
                                currentList.add(deviceInfo)
                                discoveredDevices.value = currentList
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        isScanning.value = false
                        Log.i("BLE", "Discovery finished")
                    }
                }
            }
        }

    private val initialPayloads =
        listOf(
            hexStringToBytes("f0a5440104de"),
            hexStringToBytes("f0a04401d5"),
            hexStringToBytes("f0a001e879"),
            hexStringToBytes("f0a501e80280"),
        )
    private val repeatingPayload = hexStringToBytes("f0a201e87b")

    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(
        allOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_ADVERTISE],
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Bluetooth adapter
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedSettings = loadSettings()

        // Initialize MQTT settings from saved preferences
        mqttServerUri = savedSettings[KEY_MQTT_URI]!!
        mqttUsername = savedSettings[KEY_MQTT_USERNAME]!!
        mqttPassword = savedSettings[KEY_MQTT_PASSWORD]!!
        mqttEnabled = savedSettings[KEY_MQTT_ENABLED]?.toBoolean() ?: false
        mqttTopic = savedSettings[KEY_MQTT_TOPIC] ?: "r50/rowing_data"
        ftmsEnabled = savedSettings[KEY_FTMS_ENABLED]?.toBoolean() ?: false
        ftmsCadenceRatio = savedSettings[KEY_FTMS_CADENCE_RATIO]?.toFloatOrNull() ?: 1.0f

        // Initialize database
        database = RowingDatabase.getDatabase(this)
        repository = RowingRepository(database.rowingSessionDao(), database.rowingDataPointDao())

        // Initialize FTMS Manager
        ftmsManager = FTMSManager(this)
        ftmsManager.setCadenceRatio(ftmsCadenceRatio)
        ftmsManager.onServiceStarted = {
            ftmsConnectionStatus.value = "Advertising"
        }
        ftmsManager.onServiceStopped = {
            ftmsConnectionStatus.value = "Stopped"
        }
        ftmsManager.onDeviceConnected = { deviceCount ->
            ftmsConnectionStatus.value = ftmsManager.getServiceStatus()
        }

        // Initialize MQTT status
        mqttConnectionStatus.value = "Disconnected"

        // Initialize Bluetooth status
        bluetoothConnectionStatus.value = "Disconnected"

        setContent {
            R50ConnectorTheme {
                var showDataScreen by remember { mutableStateOf(false) }
                var showRecordsScreen by remember { mutableStateOf(false) }
                var showMqttConfigScreen by remember { mutableStateOf(false) }
                var showFtmsConfigScreen by remember { mutableStateOf(false) }

                // Manage wake lock based on showDataScreen state
                LaunchedEffect(showDataScreen) {
                    if (showDataScreen) {
                        acquireWakeLock()
                    } else {
                        releaseWakeLock()
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when {
                        showRecordsScreen -> {
                            RecordsScreen(
                                modifier = Modifier.padding(innerPadding),
                                onBack = { showRecordsScreen = false },
                            )
                        }

                        showMqttConfigScreen -> {
                            MqttConfigurationScreen(
                                modifier = Modifier.padding(innerPadding),
                                initialMqttBroker = savedSettings[KEY_MQTT_URI]!!,
                                initialMqttUsername = savedSettings[KEY_MQTT_USERNAME]!!,
                                initialMqttPassword = savedSettings[KEY_MQTT_PASSWORD]!!,
                                initialMqttEnabled = savedSettings[KEY_MQTT_ENABLED]?.toBoolean() ?: true,
                                initialMqttTopic = savedSettings[KEY_MQTT_TOPIC] ?: "r50/rowing_data",
                                onBack = { showMqttConfigScreen = false },
                                onSettingsChanged = { broker, username, password, mqttEn, topic ->
                                    mqttServerUri = broker
                                    mqttUsername = username
                                    mqttPassword = password
                                    mqttEnabled = mqttEn
                                    mqttTopic = topic
                                    saveSettings(deviceMac, broker, username, password, mqttEn, topic)
                                },
                            )
                        }

                        showFtmsConfigScreen -> {
                            FTMSConfigurationScreen(
                                modifier = Modifier.padding(innerPadding),
                                initialFtmsEnabled = savedSettings[KEY_FTMS_ENABLED]?.toBoolean() ?: false,
                                initialCadenceRatio = savedSettings[KEY_FTMS_CADENCE_RATIO]?.toFloatOrNull() ?: 1.0f,
                                onBack = { showFtmsConfigScreen = false },
                                onSaveConfig = { ftmsEn, cadenceRatio ->
                                    ftmsEnabled = ftmsEn
                                    ftmsCadenceRatio = cadenceRatio
                                    saveSettings(deviceMac, mqttServerUri, mqttUsername, mqttPassword, mqttEnabled, mqttTopic, ftmsEn, cadenceRatio)

                                    // Update cadence ratio if service is running
                                    if (ftmsManager.isRunning()) {
                                        ftmsManager.setCadenceRatio(cadenceRatio)
                                    }

                                    // Start or stop FTMS service based on the new setting
                                    if (ftmsEn && !ftmsManager.isRunning()) {
                                        // Only start if we have data (R50 is connected)
                                        if (currentRowingData.value != null) {
                                            ftmsManager.setCadenceRatio(cadenceRatio)
                                            if (ftmsManager.startService()) {
                                                ftmsConnectionStatus.value = "Advertising"
                                                ftmsManager.resetSession()
                                            } else {
                                                ftmsConnectionStatus.value = "Error"
                                            }
                                        } else {
                                            ftmsConnectionStatus.value = "Waiting for R50 connection"
                                        }
                                    } else if (!ftmsEn && ftmsManager.isRunning()) {
                                        ftmsManager.stopService()
                                        ftmsConnectionStatus.value = "Stopped"
                                    }
                                },
                            )
                        }

                        showDataScreen -> {
                            RowingDataScreen(
                                modifier = Modifier.padding(innerPadding),
                                rowingData = currentRowingData.value,
                                mqttEnabled = mqttEnabled,
                                mqttStatus = mqttConnectionStatus.value,
                                bluetoothStatus = bluetoothConnectionStatus.value,
                                ftmsEnabled = ftmsEnabled,
                                ftmsStatus = ftmsConnectionStatus.value,
                                ftmsConnectedDevices = if (ftmsEnabled && ftmsManager.isRunning()) {
                                    try {
                                        ftmsManager.getConnectedDevices()
                                    } catch (e: SecurityException) {
                                        emptyList()
                                    }
                                } else {
                                    emptyList()
                                },
                                onSendTestFTMS = @androidx.annotation.RequiresPermission(
                                    android.Manifest.permission.BLUETOOTH_CONNECT
                                ) {
                                    sendTestFTMSData()
                                },
                                onBack = @androidx.annotation.RequiresPermission(
                                    allOf = [android.Manifest.permission.BLUETOOTH_ADVERTISE, android.Manifest.permission.BLUETOOTH_CONNECT],
                                ) {
                                    showDataScreen = false
                                    disconnectAll()
                                },
                            )
                        }
                        else -> {
                            ConfigurationScreen(
                                modifier = Modifier.padding(innerPadding),
                                initialDeviceMac = savedSettings[KEY_DEVICE_MAC]!!,
                                onConnect = { mac ->
                                    deviceMac = mac
                                    saveSettings(mac, mqttServerUri, mqttUsername, mqttPassword, mqttEnabled, mqttTopic)
                                    checkPermissions()
                                    if (mqttEnabled) {
                                        mqttConnectionStatus.value = "Connecting..."
                                        initializeMqtt()
                                    } else {
                                        mqttConnectionStatus.value = "Disabled"
                                    }

                                    // Start FTMS if enabled
                                    if (ftmsEnabled) {
                                        ftmsManager.setCadenceRatio(ftmsCadenceRatio)
                                        if (ftmsManager.startService()) {
                                            ftmsConnectionStatus.value = "Advertising"
                                            ftmsManager.resetSession()
                                        } else {
                                            ftmsConnectionStatus.value = "Error"
                                        }
                                    } else {
                                        ftmsConnectionStatus.value = "Disabled"
                                    }

                                    connectToDevice()
                                    startDatabaseRecording()
                                    showDataScreen = true
                                },
                                onViewRecords = { showRecordsScreen = true },
                                onMqttConfig = { showMqttConfigScreen = true },
                                onFtmsConfig = { showFtmsConfigScreen = true },
                                onSettingsChanged = { mac ->
                                    saveSettings(mac, mqttServerUri, mqttUsername, mqttPassword, mqttEnabled, mqttTopic)
                                },
                                onScanDevices = {
                                    startBluetoothScan()
                                },
                                discoveredDevices = discoveredDevices.value,
                                isScanning = isScanning.value,
                            )
                        }
                    }
                }
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE])
    private fun disconnectAll() {
        // Stop repeating payloads
        handler.removeCallbacksAndMessages(null)

        // Stop database recording
        stopDatabaseRecording()

        // Disconnect Bluetooth
        try {
            gattConnection?.disconnect()
            gattConnection?.close()
            gattConnection = null
            fff2Char = null
            bluetoothConnectionStatus.value = "Disconnected"
            Log.i("BLE", "Bluetooth disconnected")
        } catch (e: Exception) {
            Log.e("BLE", "Error disconnecting Bluetooth", e)
            bluetoothConnectionStatus.value = "Disconnected"
        }

        // Disconnect MQTT
        stopMqttReconnection() // Stop any ongoing reconnection attempts
        try {
            if (mqttEnabled && ::mqttClient.isInitialized && mqttClient.isConnected) {
                mqttClient.disconnect()
                Log.i("MQTT", "MQTT client disconnected")
                mqttConnectionStatus.value = "Disconnected"
            }
        } catch (e: MqttException) {
            Log.e("MQTT", "Error disconnecting MQTT client", e)
            mqttConnectionStatus.value = "Disconnected"
        }

        // Stop FTMS service
        if (ftmsManager.isRunning()) {
            ftmsManager.stopService()
            ftmsConnectionStatus.value = "Stopped"
            Log.i("FTMS", "FTMS service stopped")
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION])
    private fun startBluetoothScan() {
        // Check permissions first
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("BLE", "Missing required permissions for Bluetooth scanning")
            checkPermissions()
            return
        }

        if (bluetoothAdapter == null) {
            Log.e("BLE", "Bluetooth adapter is null")
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            Log.e("BLE", "Bluetooth is not enabled")
            return
        }

        // Clear previous results
        discoveredDevices.value = emptyList()

        // Register broadcast receiver
        val filter =
            android.content.IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
        registerReceiver(bluetoothReceiver, filter)

        // Start discovery
        isScanning.value = true
        val scanStarted = bluetoothAdapter!!.startDiscovery()
        Log.i("BLE", "Bluetooth scan started: $scanStarted")

        // Stop scanning after 30 seconds
        handler.postDelayed({
            stopBluetoothScan()
        }, 30000)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun stopBluetoothScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter?.cancelDiscovery()
        }
        isScanning.value = false
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
    }

    private fun saveSettings(
        mac: String,
        uri: String,
        username: String,
        password: String,
        mqttEnabled: Boolean,
        topic: String,
        ftmsEnabled: Boolean = this.ftmsEnabled,
        ftmsCadenceRatio: Float = this.ftmsCadenceRatio,
    ) {
        with(sharedPreferences.edit()) {
            putString(KEY_DEVICE_MAC, mac)
            putString(KEY_MQTT_URI, uri)
            putString(KEY_MQTT_USERNAME, username)
            putString(KEY_MQTT_PASSWORD, password)
            putBoolean(KEY_MQTT_ENABLED, mqttEnabled)
            putString(KEY_MQTT_TOPIC, topic)
            putBoolean(KEY_FTMS_ENABLED, ftmsEnabled)
            putFloat(KEY_FTMS_CADENCE_RATIO, ftmsCadenceRatio)
            apply()
        }
        Log.i("Settings", "Settings saved successfully")
    }

    private fun loadSettings(): Map<String, String> =
        mapOf(
            KEY_DEVICE_MAC to sharedPreferences.getString(KEY_DEVICE_MAC, "")!!,
            KEY_MQTT_URI to sharedPreferences.getString(KEY_MQTT_URI, "")!!,
            KEY_MQTT_USERNAME to sharedPreferences.getString(KEY_MQTT_USERNAME, "")!!,
            KEY_MQTT_PASSWORD to sharedPreferences.getString(KEY_MQTT_PASSWORD, "")!!,
            KEY_MQTT_ENABLED to sharedPreferences.getBoolean(KEY_MQTT_ENABLED, false).toString(),
            KEY_MQTT_TOPIC to sharedPreferences.getString(KEY_MQTT_TOPIC, "r50/rowing_data")!!,
            KEY_FTMS_ENABLED to sharedPreferences.getBoolean(KEY_FTMS_ENABLED, false).toString(),
            KEY_FTMS_CADENCE_RATIO to sharedPreferences.getFloat(KEY_FTMS_CADENCE_RATIO, 1.0f).toString(),
        )

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
        }
    }

    private fun acquireWakeLock() {
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.i("WakeLock", "Screen will stay on during rowing session")
        } catch (e: Exception) {
            Log.e("WakeLock", "Failed to keep screen on", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.i("WakeLock", "Screen can sleep normally")
        } catch (e: Exception) {
            Log.e("WakeLock", "Failed to clear screen flags", e)
        }
    }

    private fun initializeMqtt() {
        mqttClient = MqttAsyncClient(mqttServerUri, mqttClientId, null)

        mqttClient.setCallback(
            object : MqttCallbackExtended {
                override fun connectComplete(
                    reconnect: Boolean,
                    serverURI: String?,
                ) {
                    Log.i("MQTT", "Connected to MQTT broker: $serverURI (reconnect: $reconnect)")
                    mqttConnectionStatus.value = "Connected"
                    // Reset reconnection attempts on successful connection
                    mqttReconnectAttempts = 0
                    stopMqttReconnection()
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.w("MQTT", "MQTT connection lost", cause)
                    mqttConnectionStatus.value = "Reconnecting..."
                    if (mqttEnabled) {
                        startMqttReconnection()
                    }
                }

                override fun messageArrived(
                    topic: String?,
                    message: MqttMessage?,
                ) {
                    Log.d("MQTT", "Message arrived on topic $topic: ${message?.payload?.let { String(it) }}")
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Log.d("MQTT", "Message delivery complete")
                }
            },
        )

        connectToMqttBroker()
    }

    private fun connectToMqttBroker() {
        try {
            val options =
                MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 10
                    keepAliveInterval = 20
                    userName = mqttUsername
                    password = mqttPassword.toCharArray()
                }

            mqttClient.connect(
                options,
                null,
                object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i("MQTT", "Successfully connected to MQTT broker")
                        mqttConnectionStatus.value = "Connected"
                    }

                    override fun onFailure(
                        asyncActionToken: IMqttToken?,
                        exception: Throwable?,
                    ) {
                        Log.e("MQTT", "Failed to connect to MQTT broker", exception)
                        mqttConnectionStatus.value = "Reconnecting..."
                        if (mqttEnabled) {
                            startMqttReconnection()
                        }
                    }
                },
            )
        } catch (e: MqttException) {
            Log.e("MQTT", "MQTT connection error", e)
            mqttConnectionStatus.value = "Reconnecting..."
            if (mqttEnabled) {
                startMqttReconnection()
            }
        }
    }

    private fun publishToMqtt(message: String) {
        if (!mqttEnabled) {
            Log.d("MQTT", "MQTT publishing disabled, skipping message: $message")
            return
        }

        try {
            if (mqttClient.isConnected) {
                val mqttMessage = MqttMessage(message.toByteArray())
                mqttMessage.qos = 1 // QoS level 1 (at least once delivery)

                mqttClient.publish(
                    mqttTopic,
                    mqttMessage,
                    null,
                    object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.d("MQTT", "Message published successfully: $message")
                        }

                        override fun onFailure(
                            asyncActionToken: IMqttToken?,
                            exception: Throwable?,
                        ) {
                            Log.e("MQTT", "Failed to publish message: $message", exception)
                        }
                    },
                )
            } else {
                Log.w("MQTT", "MQTT client not connected, cannot publish message: $message")
            }
        } catch (e: MqttException) {
            Log.e("MQTT", "Error publishing MQTT message", e)
        }
    }

    private fun startMqttReconnection() {
        // Cancel any existing reconnection job
        stopMqttReconnection()

        if (!mqttEnabled || mqttReconnectAttempts >= maxMqttReconnectAttempts) {
            if (mqttReconnectAttempts >= maxMqttReconnectAttempts) {
                Log.w("MQTT", "Max reconnection attempts reached, giving up")
                mqttConnectionStatus.value = "Failed to Connect"
            }
            return
        }

        mqttReconnectJob =
            databaseScope.launch {
                try {
                    // Calculate delay using progressive backoff
                    val delayIndex = minOf(mqttReconnectAttempts, mqttReconnectDelays.size - 1)
                    val delay = mqttReconnectDelays[delayIndex]

                    Log.i(
                        "MQTT",
                        "Attempting MQTT reconnection in ${delay}ms (attempt ${mqttReconnectAttempts + 1}/$maxMqttReconnectAttempts)",
                    )
                    delay(delay)

                    if (mqttEnabled && isActive) {
                        mqttReconnectAttempts++
                        Log.i("MQTT", "Reconnection attempt $mqttReconnectAttempts/$maxMqttReconnectAttempts")

                        try {
                            // Close existing client if it exists
                            if (::mqttClient.isInitialized) {
                                try {
                                    mqttClient.close()
                                } catch (e: Exception) {
                                    Log.d("MQTT", "Error closing old MQTT client: ${e.message}")
                                }
                            }

                            // Create new client and attempt connection
                            initializeMqtt()
                        } catch (e: Exception) {
                            Log.e("MQTT", "Error during MQTT reconnection attempt", e)
                            if (mqttEnabled) {
                                startMqttReconnection() // Try again
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MQTT", "Error in MQTT reconnection job", e)
                }
            }
    }

    private fun stopMqttReconnection() {
        mqttReconnectJob?.cancel()
        mqttReconnectJob = null
    }

    // Database recording functions
    private fun startDatabaseRecording() {
        databaseScope.launch {
            try {
                // Create new session
                currentSessionId = repository.createNewSession()
                Log.i("Database", "Started new session with ID: $currentSessionId")

                // Start periodic recording job
                startPeriodicDataRecording()
            } catch (e: Exception) {
                Log.e("Database", "Error starting database recording", e)
            }
        }
    }

    private fun stopDatabaseRecording() {
        // Cancel the periodic recording job
        dataRecordingJob?.cancel()
        dataRecordingJob = null

        // Complete the current session
        currentSessionId?.let { sessionId ->
            databaseScope.launch {
                try {
                    repository.completeSession(sessionId)
                    Log.i("Database", "Completed session: $sessionId")

                    currentSessionId = null
                } catch (e: Exception) {
                    Log.e("Database", "Error completing session", e)
                }
            }
        }
    }

    private fun startPeriodicDataRecording() {
        dataRecordingJob =
            databaseScope.launch {
                while (isActive && currentSessionId != null) {
                    try {
                        currentRowingData.value?.let { rowingData ->
                            currentSessionId?.let { sessionId ->
                                val dataPoint =
                                    RowingDataPoint(
                                        sessionId = sessionId,
                                        timestamp = java.util.Date(),
                                        rawHex = rowingData.hex,
                                        elapsedSecond = rowingData.elapsedSecond,
                                        strokeCount = rowingData.strokeCount,
                                        strokePerMinute = rowingData.strokePerMinute,
                                        distance = rowingData.distance,
                                        calories = rowingData.calories,
                                        heartbeat = rowingData.heartbeat,
                                        power = rowingData.power,
                                        gear = rowingData.gear,
                                    )
                                repository.addDataPoint(sessionId, dataPoint)
                                Log.d("Database", "Recorded data point for session: $sessionId")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("Database", "Error recording data point", e)
                    }

                    // Wait 5 seconds before next recording
                    delay(5000)
                }
            }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToDevice() {
        bluetoothConnectionStatus.value = "Connecting..."
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val device = bluetoothManager.adapter.getRemoteDevice(deviceMac)

        device.connectGatt(
            this,
            false,
            object : BluetoothGattCallback() {
                @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                override fun onConnectionStateChange(
                    gatt: BluetoothGatt,
                    status: Int,
                    newState: Int,
                ) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Log.i("BLE", "Connected to device")
                            bluetoothConnectionStatus.value = "Connected"
                            gatt.discoverServices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Log.i("BLE", "Disconnected from device")
                            bluetoothConnectionStatus.value = "Disconnected"
                        }
                        BluetoothProfile.STATE_CONNECTING -> {
                            Log.i("BLE", "Connecting to device")
                            bluetoothConnectionStatus.value = "Connecting..."
                        }
                        else -> {
                            Log.i("BLE", "Connection state changed: $newState")
                            bluetoothConnectionStatus.value = "Connection Error"
                        }
                    }
                }

                @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                override fun onServicesDiscovered(
                    gatt: BluetoothGatt,
                    status: Int,
                ) {
                    gattConnection = gatt

                    val charFFF1 =
                        gatt.getService(uuidFFF1)?.getCharacteristic(uuidFFF1)
                            ?: gatt.services
                                .flatMap { it.characteristics }
                                .firstOrNull { it.uuid == uuidFFF1 }

                    charFFF1?.let { char ->
                        Log.i("BLE", "Subscribing to fff1")
                        gatt.setCharacteristicNotification(char, true)
                        val descriptor =
                            char.getDescriptor(uuidCCCD)
                                ?: BluetoothGattDescriptor(uuidCCCD, BluetoothGattDescriptor.PERMISSION_WRITE)
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }

                    val charFFF2 =
                        gatt.services
                            .flatMap { it.characteristics }
                            .firstOrNull { it.uuid == uuidFFF2 }

                    fff2Char = charFFF2

                    if (charFFF2 != null) {
                        Log.i("BLE", "Found fff2 - Starting to write payloads")
                        writeInitialPayloadsSequentially()
                    } else {
                        Log.w("BLE", "fff2 not found")
                    }
                }

                @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                ) {
                    if (characteristic.uuid == uuidFFF1) {
                        val values = characteristic.value
                        val hex = values.joinToString("") { "%02x".format(it) }
                        Log.i("BLE", "Notification from fff1: $hex")

                        val rowingData =
                            if (values.size == 23) {
                                RowingData(
                                    hex = hex,
                                    timestamp = System.currentTimeMillis(),
                                    elapsedSecond = (values[4] - 1) * 60 + values[5] - 1,
                                    strokeCount = (values[6] - 1) * 99 + (values[7] - 1),
                                    strokePerMinute = (values[8] - 1) * 99 + (values[9] - 1),
                                    distance = (values[10] - 1) * 99 + (values[11] - 1),
                                    calories = (values[12] - 1) * 99 + (values[13] - 1),
                                    heartbeat = (values[14] - 1) * 99 + (values[15] - 1),
                                    power = (values[16] - 1) * 99 + (values[17] - 1),
                                    gear = values[20] - 1,
                                )
                            } else {
                                RowingData(
                                    hex = hex,
                                    timestamp = System.currentTimeMillis(),
                                )
                            }

                        // convert to JSON
                        val jsonMessage = Gson().toJson(rowingData)

                        // Update the current rowing data state
                        currentRowingData.value = rowingData

                        // Update FTMS data if enabled
                        if (ftmsEnabled && ftmsManager.isRunning()) {
                            ftmsManager.updateRowingData(rowingData)
                        }

                        // Publish to MQTT
                        publishToMqtt(jsonMessage)
                    }
                }

                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int,
                ) {
                    Log.d("BLE", "Write to ${characteristic.uuid}, status=$status")
                }

                override fun onDescriptorWrite(
                    gatt: BluetoothGatt,
                    descriptor: BluetoothGattDescriptor,
                    status: Int,
                ) {
                    Log.d("BLE", "Descriptor write complete: ${descriptor.uuid}, status=$status")
                }
            },
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun writeInitialPayloadsSequentially(index: Int = 0) {
        if (index >= initialPayloads.size) {
            startRepeatingPayload()
            return
        }

        fff2Char?.let { char ->
            char.value = initialPayloads[index]
            val result = gattConnection?.writeCharacteristic(char)
            Log.d("BLE", "Sending initial payload $index: ${initialPayloads[index].toHexString()} → result=$result")
        }

        handler.postDelayed({ writeInitialPayloadsSequentially(index + 1) }, 1000)
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun startRepeatingPayload() {
        handler.post(
            object : Runnable {
                @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                override fun run() {
                    fff2Char?.let { char ->
                        char.value = repeatingPayload
                        val result = gattConnection?.writeCharacteristic(char)
                        Log.d("BLE", "Sending repeating payload: ${repeatingPayload.toHexString()} → result=$result")
                    }
                    handler.postDelayed(this, 1000)
                }
            },
        )
    }

    private fun hexStringToBytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHexString(): String = this.joinToString(" ") { "%02x".format(it) }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE],
    )
    override fun onDestroy() {
        super.onDestroy()

        // Clear screen flags
        releaseWakeLock()

        // Stop MQTT reconnection if running
        stopMqttReconnection()

        // Stop Bluetooth scanning
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            stopBluetoothScan()
        }

        try {
            if (mqttEnabled && ::mqttClient.isInitialized && mqttClient.isConnected) {
                mqttClient.disconnect()
                Log.i("MQTT", "MQTT client disconnected")
            }
        } catch (e: MqttException) {
            Log.e("MQTT", "Error disconnecting MQTT client", e)
        }

        // Stop FTMS service
        if (::ftmsManager.isInitialized) {
            ftmsManager.stopService()
        }

        gattConnection?.close()
    }

    fun exportSessionToFit(
        session: RowingSession,
        dataPoints: List<RowingDataPoint>,
    ) {
        databaseScope.launch {
            try {
                val fitGenerator = FitFileGenerator()
                val fitData = fitGenerator.generateFitFile(session, dataPoints)

                // Generate filename
                val formatter = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                val filename = "rowing_session_${session.id}_${formatter.format(session.startTime)}.fit"

                // Save file to Downloads folder
                val resolver = contentResolver
                val contentValues =
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
                    }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { fileUri ->
                    resolver.openOutputStream(fileUri)?.use { outputStream ->
                        outputStream.write(fitData)
                        outputStream.flush()
                    }

                    // Show success message on main thread
                    runOnUiThread {
                        Toast
                            .makeText(
                                this@MainActivity,
                                "FIT file exported to Downloads: $filename",
                                Toast.LENGTH_LONG,
                            ).show()
                    }
                    Log.i("FitExport", "Successfully exported FIT file: $filename")
                } ?: run {
                    runOnUiThread {
                        Toast
                            .makeText(
                                this@MainActivity,
                                "Failed to create file in Downloads folder",
                                Toast.LENGTH_LONG,
                            ).show()
                    }
                    Log.e("FitExport", "Failed to create file URI")
                }
            } catch (e: IOException) {
                Log.e("FitExport", "IO error during FIT export", e)
                runOnUiThread {
                    Toast
                        .makeText(
                            this@MainActivity,
                            "Error writing FIT file: ${e.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                }
            } catch (e: Exception) {
                Log.e("FitExport", "Error generating FIT file", e)
                runOnUiThread {
                    Toast
                        .makeText(
                            this@MainActivity,
                            "Error exporting FIT file: ${e.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendTestFTMSData() {
        if (!ftmsEnabled || !ftmsManager.isRunning()) {
            Toast.makeText(this, "FTMS service is not running", Toast.LENGTH_SHORT).show()
            return
        }

        // Create test rowing data in the app's RowingData format
        val testRowingData = RowingData(
            hex = "TEST_DATA",
            timestamp = System.currentTimeMillis(),
            elapsedSecond = 360,         // 6 minutes
            strokeCount = 150,           // 150 total strokes
            strokePerMinute = 25,        // 25 strokes per minute
            distance = 1000,             // 1000 meters
            calories = 85,               // 85 calories
            heartbeat = 145,             // 145 BPM
            power = 180,                 // 180 watts
            gear = 5                     // Gear level 5
        )

        // Send test data to FTMS (it will be converted internally)
        ftmsManager.updateRowingData(testRowingData)

        // Show confirmation
        Toast.makeText(this, "Test FTMS data sent successfully!", Toast.LENGTH_SHORT).show()
        Log.i("FTMS", "Test data sent: $testRowingData")
    }
}

@Composable
fun RowingDataScreen(
    modifier: Modifier = Modifier,
    rowingData: RowingData?,
    onBack: () -> Unit,
    mqttEnabled: Boolean = false,
    mqttStatus: String = "Disconnected",
    bluetoothStatus: String = "Disconnected",
    ftmsEnabled: Boolean = false,
    ftmsStatus: String = "Stopped",
    ftmsConnectedDevices: List<BluetoothDevice> = emptyList(),
    onSendTestFTMS: () -> Unit = {},
) {
    var showDisconnectDialog by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "R50 Rowing Data",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        if (rowingData != null) {
            // Main workout metrics in a grid layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                DataCard(
                    title = "Time",
                    value = formatTime(rowingData.elapsedSecond),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                DataCard(
                    title = "Distance",
                    value = "${rowingData.distance ?: 0}m",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                DataCard(
                    title = "Strokes",
                    value = "${rowingData.strokeCount ?: 0}",
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                DataCard(
                    title = "SPM",
                    value = "${rowingData.strokePerMinute ?: 0}",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                DataCard(
                    title = "Calories",
                    value = "${rowingData.calories ?: 0}",
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                DataCard(
                    title = "Power",
                    value = "${rowingData.power ?: 0}W",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                DataCard(
                    title = "Heart Rate",
                    value = "${rowingData.heartbeat ?: 0} BPM",
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                DataCard(
                    title = "Gear",
                    value = "${rowingData.gear ?: 0}",
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Waiting for data...",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Make sure the rowing machine is connected and active",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Technical details
        Text(
            text = "Technical Details",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (rowingData != null) {
            Text(
                text = "Last Update: ${java.text.SimpleDateFormat(
                    "HH:mm:ss",
                    java.util.Locale.getDefault(),
                ).format(java.util.Date(rowingData.timestamp))}",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "Raw Data: ${rowingData.hex}",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (mqttEnabled) {
            Text(
                text = "MQTT: $mqttStatus",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color =
                    when (mqttStatus) {
                        "Connected" -> androidx.compose.material3.MaterialTheme.colorScheme.primary
                        "Connecting..." -> androidx.compose.material3.MaterialTheme.colorScheme.secondary
                        "Failed to Connect", "Connection Lost" -> androidx.compose.material3.MaterialTheme.colorScheme.error
                        else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text(
                text = "MQTT: Disabled",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Text(
            text = "Bluetooth: $bluetoothStatus",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color =
                when (bluetoothStatus) {
                    "Connected" -> androidx.compose.material3.MaterialTheme.colorScheme.primary
                    "Connecting..." -> androidx.compose.material3.MaterialTheme.colorScheme.secondary
                    "Connection Error" -> androidx.compose.material3.MaterialTheme.colorScheme.error
                    else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.padding(top = 4.dp),
        )

        if (ftmsEnabled) {
            Text(
                text = "FTMS: $ftmsStatus",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color =
                    when (ftmsStatus) {
                        "Advertising" -> androidx.compose.material3.MaterialTheme.colorScheme.secondary
                        "Connected (1 device)", "Connected" -> androidx.compose.material3.MaterialTheme.colorScheme.primary
                        "Error" -> androidx.compose.material3.MaterialTheme.colorScheme.error
                        else ->
                            when {
                                ftmsStatus.contains("Connected") -> androidx.compose.material3.MaterialTheme.colorScheme.primary
                                else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            }
                    },
                modifier = Modifier.padding(top = 4.dp),
            )

            // Display connected devices if any
            if (ftmsConnectedDevices.isNotEmpty()) {
                Text(
                    text = "Connected Devices:",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                ftmsConnectedDevices.forEach { device ->
                    Text(
                        text = "• ${device.name ?: "Unknown Device"} (${device.address})",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                    )
                }
            }
            
        } else {
            Text(
                text = "FTMS: Disabled",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Test FTMS button (only show if FTMS is enabled)
        if (false && ftmsEnabled) {
            OutlinedButton(
                onClick = onSendTestFTMS,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Send Test Data to FTMS")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = { showDisconnectDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Disconnect & Back to Settings")
        }

        // Version display at the bottom
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = MainActivity.APP_VERSION,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }

    // Confirmation dialog for disconnect
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Confirm Disconnect") },
            text = { Text("Are you sure you want to disconnect from the rowing machine and return to settings?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        onBack()
                    },
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDisconnectDialog = false },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun DataCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Card(
        modifier = modifier.padding(4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            )
        }
    }
}

fun formatTime(seconds: Int?): String {
    if (seconds == null || seconds < 0) return "00:00"
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}

@Composable
fun MqttConfigurationScreen(
    modifier: Modifier = Modifier,
    initialMqttBroker: String = "",
    initialMqttUsername: String = "",
    initialMqttPassword: String = "",
    initialMqttEnabled: Boolean = false,
    initialMqttTopic: String = "r50/rowing_data",
    onBack: () -> Unit = {},
    onSettingsChanged: (String, String, String, Boolean, String) -> Unit = { _, _, _, _, _ -> },
) {
    var mqttBroker by remember { mutableStateOf(initialMqttBroker) }
    var mqttUsername by remember { mutableStateOf(initialMqttUsername) }
    var mqttPassword by remember { mutableStateOf(initialMqttPassword) }
    var mqttEnabled by remember { mutableStateOf(initialMqttEnabled) }
    var mqttTopic by remember { mutableStateOf(initialMqttTopic) }
    var showAutoSaveIndicator by remember { mutableStateOf(false) }

    // Helper function to trigger auto-save with visual feedback
    fun triggerAutoSave() {
        onSettingsChanged(mqttBroker, mqttUsername, mqttPassword, mqttEnabled, mqttTopic)
        showAutoSaveIndicator = true
    }

    // Auto-hide the auto-save indicator after 1.5 seconds
    LaunchedEffect(showAutoSaveIndicator) {
        if (showAutoSaveIndicator) {
            kotlinx.coroutines.delay(1500)
            showAutoSaveIndicator = false
        }
    }

    Column(
        modifier =
            modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBack,
            ) {
                Text("← Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "MQTT Configuration",
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // MQTT Enable/Disable Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Enable MQTT Publishing",
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = mqttEnabled,
                onCheckedChange = {
                    mqttEnabled = it
                    triggerAutoSave()
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = mqttBroker,
            onValueChange = {
                mqttBroker = it
                triggerAutoSave()
            },
            label = { Text("MQTT Broker URI") },
            placeholder = { Text("tcp://mqtt.example.com:1883") },
            modifier = Modifier.fillMaxWidth(),
            enabled = mqttEnabled,
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = mqttUsername,
            onValueChange = {
                mqttUsername = it
                triggerAutoSave()
            },
            label = { Text("MQTT Username") },
            placeholder = { Text("username") },
            modifier = Modifier.fillMaxWidth(),
            enabled = mqttEnabled,
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = mqttPassword,
            onValueChange = {
                mqttPassword = it
                triggerAutoSave()
            },
            label = { Text("MQTT Password") },
            placeholder = { Text("password") },
            modifier = Modifier.fillMaxWidth(),
            enabled = mqttEnabled,
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = mqttTopic,
            onValueChange = {
                mqttTopic = it
                triggerAutoSave()
            },
            label = { Text("MQTT Topic") },
            placeholder = { Text("r50/rowing_data") },
            modifier = Modifier.fillMaxWidth(),
            enabled = mqttEnabled,
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Auto-save indicator
        if (showAutoSaveIndicator) {
            Text(
                text = "⚡ Settings saved automatically",
                color = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Information about MQTT
        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = "About MQTT",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = "MQTT allows you to publish rowing data to an external broker for integration with other systems. When enabled, rowing data will be published in JSON format to the specified topic.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Version display at the bottom
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = MainActivity.APP_VERSION,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

// Helper function to validate MAC address format
fun isValidMacAddress(macAddress: String): Boolean {
    // MAC address should be in format XX:XX:XX:XX:XX:XX or XX-XX-XX-XX-XX-XX
    val macPattern = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"
    return macAddress.matches(Regex(macPattern))
}

@Composable
fun ConfigurationScreen(
    modifier: Modifier = Modifier,
    initialDeviceMac: String = "",
    onConnect: (String) -> Unit,
    onViewRecords: () -> Unit = {},
    onMqttConfig: () -> Unit = {},
    onFtmsConfig: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    onSettingsChanged: (String) -> Unit = { _ -> },
    onScanDevices: () -> Unit = {},
    discoveredDevices: List<BluetoothDeviceInfo> = emptyList(),
    isScanning: Boolean = false,
) {
    var deviceMac by remember { mutableStateOf(initialDeviceMac) }
    var isConnected by remember { mutableStateOf(false) }
    var showSavedMessage by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var showAutoSaveIndicator by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showMacRequiredDialog by remember { mutableStateOf(false) }

    // Helper function to trigger auto-save with visual feedback
    fun triggerAutoSave() {
        onSettingsChanged(deviceMac)
        showAutoSaveIndicator = true
    }

    // Auto-hide the auto-save indicator after 1.5 seconds
    LaunchedEffect(showAutoSaveIndicator) {
        if (showAutoSaveIndicator) {
            kotlinx.coroutines.delay(1500)
            showAutoSaveIndicator = false
        }
    }

    Column(
        modifier =
            modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "R50 Connector Configuration",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        Text(
            text = "Device Configuration",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = deviceMac,
                onValueChange = {
                    deviceMac = it
                    triggerAutoSave()
                },
                label = { Text("Device MAC Address") },
                placeholder = { Text("") },
                modifier = Modifier.weight(1f),
                enabled = !isConnected,
                singleLine = true,
            )

            OutlinedButton(
                onClick = {
                    showDeviceDialog = true
                    onScanDevices()
                },
                enabled = !isConnected,
                modifier = Modifier.width(100.dp),
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Scan")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Auto-save indicator
        if (showAutoSaveIndicator) {
            Text(
                text = "⚡ Settings saved automatically",
                color = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Button(
            onClick = {
                if (!isConnected) {
                    // Validate MAC address is not empty and has proper format
                    val trimmedMac = deviceMac.trim()
                    if (trimmedMac.isEmpty()) {
                        showMacRequiredDialog = true
                    } else if (!isValidMacAddress(trimmedMac)) {
                        showMacRequiredDialog = true
                    } else {
                        isConnected = true
                        showSavedMessage = true
                        onConnect(trimmedMac)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnected,
        ) {
            Text(if (isConnected) "Connected" else "Connect")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onViewRecords,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("View Records")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onMqttConfig,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("MQTT Configuration")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onFtmsConfig,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("FTMS Configuration")
        }

        // Auto-hide the saved message after 3 seconds
        LaunchedEffect(showSavedMessage) {
            if (showSavedMessage) {
                kotlinx.coroutines.delay(3000)
                showSavedMessage = false
            }
        }

        if (showSavedMessage) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "✓ Settings saved",
                color = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
            )
        }

        if (isConnected) {
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showDisconnectDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Disconnect")
            }
        }

        if (isConnected) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "✓ Connected to device",
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp),
            )
        }

        // Version display at the bottom
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = MainActivity.APP_VERSION,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }

    // Device selection dialog
    if (showDeviceDialog) {
        DeviceSelectionDialog(
            devices = discoveredDevices,
            isScanning = isScanning,
            onDeviceSelected = { device ->
                deviceMac = device.address
                triggerAutoSave()
                showDeviceDialog = false
            },
            onDismiss = { showDeviceDialog = false },
            onRescan = onScanDevices,
        )
    }

    // Confirmation dialog for disconnect
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Confirm Disconnect") },
            text = { Text("Are you sure you want to disconnect from the rowing machine?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        isConnected = false
                        onDisconnect()
                    },
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDisconnectDialog = false },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    // MAC address required dialog
    if (showMacRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showMacRequiredDialog = false },
            title = { Text("Invalid MAC Address") },
            text = {
                Text(
                    "Please enter a valid Bluetooth MAC address for your R50 rowing machine.\n\nFormat: XX:XX:XX:XX:XX:XX (e.g., 12:34:56:78:9A:BC)\n\nYou can use the 'Scan' button to discover nearby devices or check your rowing machine's settings for the MAC address.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showMacRequiredDialog = false },
                ) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
fun DeviceSelectionDialog(
    devices: List<BluetoothDeviceInfo>,
    isScanning: Boolean,
    onDeviceSelected: (BluetoothDeviceInfo) -> Unit,
    onDismiss: () -> Unit,
    onRescan: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Select Bluetooth Device")
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        },
        text = {
            Column {
                if (devices.isEmpty() && !isScanning) {
                    Text(
                        text = "No devices found. Make sure your device is discoverable and try scanning again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (devices.isEmpty() && isScanning) {
                    Text(
                        text = "Scanning for devices...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(300.dp),
                    ) {
                        items(devices) { device ->
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { onDeviceSelected(device) },
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                ) {
                                    Text(
                                        text = device.name ?: "Unknown Device",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = device.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    device.rssi?.let { rssi ->
                                        Text(
                                            text = "Signal: ${rssi}dBm",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isScanning) {
                TextButton(onClick = onRescan) {
                    Text("Rescan")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun Greeting(
    name: String,
    modifier: Modifier = Modifier,
) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun RowingDataScreenPreview() {
    R50ConnectorTheme {
        RowingDataScreen(
            rowingData =
                RowingData(
                    hex = "f0a5440104de...",
                    timestamp = System.currentTimeMillis(),
                    elapsedSecond = 125,
                    strokeCount = 50,
                    strokePerMinute = 24,
                    distance = 250,
                    calories = 35,
                    heartbeat = 150,
                    power = 180,
                    gear = 5,
                ),
            mqttEnabled = true,
            mqttStatus = "Connected",
            bluetoothStatus = "Connected",
            ftmsEnabled = true,
            ftmsStatus = "Connected (1 device)",
            ftmsConnectedDevices = emptyList(),
            onBack = { },
            onSendTestFTMS = { },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ConfigurationScreenPreview() {
    R50ConnectorTheme {
        ConfigurationScreen(
            onConnect = { _ -> },
            onDisconnect = { },
            onSettingsChanged = { _ -> },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MqttConfigurationScreenPreview() {
    R50ConnectorTheme {
        MqttConfigurationScreen(
            onBack = { },
            onSettingsChanged = { _, _, _, _, _ -> },
        )
    }
}

@Composable
fun RecordsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    var sessions by remember { mutableStateOf<List<RowingSession>>(emptyList()) }
    var selectedSession by remember { mutableStateOf<RowingSession?>(null) }
    var dataPoints by remember { mutableStateOf<List<RowingDataPoint>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<RowingSession?>(null) }

    // Get the MainActivity instance to access repository
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as MainActivity

    // Load sessions when screen is displayed
    LaunchedEffect(Unit) {
        activity.repository.getAllSessions().collect {
            sessions = it
        }
    }

    // Load data points when session is selected
    LaunchedEffect(selectedSession) {
        selectedSession?.let { session ->
            activity.repository.getDataPointsBySession(session.id).collect {
                dataPoints = it
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Rowing Records",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = onBack) {
                Text("Back")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedSession == null) {
            // Show sessions list
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Sessions",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                var showInfo by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { showInfo = true },
                ) {
                    Text("ℹ️ FIT Export")
                }

                if (showInfo) {
                    AlertDialog(
                        onDismissRequest = { showInfo = false },
                        title = { Text("FIT File Export") },
                        text = {
                            Column {
                                Text(
                                    text = "Export your rowing sessions as FIT files to import into fitness platforms like Strava, Garmin Connect, or other training apps.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "• Files are saved to your Downloads folder",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = "• Includes distance, power, heart rate, and stroke data",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = "• Compatible with most fitness platforms",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showInfo = false }) {
                                Text("Got it")
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (sessions.isEmpty()) {
                Text(
                    text = "No rowing sessions recorded yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn {
                    items(sessions) { session ->
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedSession = session },
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Session ${session.id}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = if (session.isCompleted) "✓" else "In Progress",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color =
                                            if (session.isCompleted) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            },
                                    )
                                }

                                Text(
                                    text = "Started: ${java.text.SimpleDateFormat(
                                        "MMM dd, yyyy HH:mm",
                                        java.util.Locale.getDefault(),
                                    ).format(session.startTime)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )

                                session.endTime?.let { endTime ->
                                    Text(
                                        text = "Ended: ${java.text.SimpleDateFormat(
                                            "MMM dd, yyyy HH:mm",
                                            java.util.Locale.getDefault(),
                                        ).format(endTime)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )

                                    val duration = endTime.time - session.startTime.time
                                    val totalSeconds = (duration / 1000).toInt()
                                    val durationText =
                                        if (totalSeconds < 60) {
                                            "${totalSeconds}s"
                                        } else {
                                            val minutes = totalSeconds / 60
                                            val seconds = totalSeconds % 60
                                            if (seconds > 0) "${minutes}m ${seconds}s" else "${minutes}m"
                                        }
                                    Text(
                                        text = "Duration: $durationText",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Distance: ${session.totalDistance}m",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        text = "Strokes: ${session.totalStrokes}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        text = "Calories: ${session.totalCalories}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            // Export to FIT file
                                            activity.databaseScope.launch {
                                                val dataPoints = activity.repository.getDataPointsBySessionSync(session.id)
                                                activity.exportSessionToFit(session, dataPoints)
                                            }
                                        },
                                        modifier = Modifier.padding(end = 8.dp),
                                    ) {
                                        Text("Export FIT")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            sessionToDelete = session
                                            showDeleteDialog = true
                                        },
                                        colors =
                                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error,
                                            ),
                                    ) {
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Show session details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Session ${selectedSession?.id} Details",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row {
                    OutlinedButton(
                        onClick = {
                            // Export current session to FIT file
                            selectedSession?.let { session ->
                                activity.exportSessionToFit(session, dataPoints)
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text("Export FIT")
                    }

                    OutlinedButton(
                        onClick = {
                            sessionToDelete = selectedSession
                            showDeleteDialog = true
                        },
                        colors =
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text("Delete")
                    }

                    Button(onClick = { selectedSession = null }) {
                        Text("Back")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (dataPoints.isEmpty()) {
                Text(
                    text = "No data points recorded for this session",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn {
                    items(dataPoints) { dataPoint ->
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Text(
                                    text =
                                        java.text
                                            .SimpleDateFormat(
                                                "HH:mm:ss",
                                                java.util.Locale.getDefault(),
                                            ).format(dataPoint.timestamp),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    dataPoint.distance?.let {
                                        Text(
                                            text = "Dist: ${it}m",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    dataPoint.strokePerMinute?.let {
                                        Text(
                                            text = "SPM: $it",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    dataPoint.power?.let {
                                        Text(
                                            text = "Power: ${it}W",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    dataPoint.heartbeat?.let {
                                        Text(
                                            text = "HR: $it",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog && sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                sessionToDelete = null
            },
            title = {
                Text("Delete Session")
            },
            text = {
                Text("Are you sure you want to delete Session ${sessionToDelete?.id}? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        sessionToDelete?.let { session ->
                            activity.databaseScope.launch {
                                activity.repository.deleteSession(session.id)
                                // If we're deleting the currently selected session, go back to list
                                if (selectedSession?.id == session.id) {
                                    selectedSession = null
                                }
                            }
                        }
                        showDeleteDialog = false
                        sessionToDelete = null
                    },
                ) {
                    Text(
                        "Delete",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        sessionToDelete = null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    R50ConnectorTheme {
        Greeting("Android")
    }
}

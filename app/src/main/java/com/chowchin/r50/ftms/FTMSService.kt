package com.chowchin.r50.ftms

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.*
import kotlinx.coroutines.*

/**
 * FTMS (Fitness Machine Service) implementation for rowing machine
 * This service allows the app to act as a Bluetooth LE rowing machine
 * that can be discovered and connected to by fitness apps like Zwift
 */
class FTMSService(private val context: Context) {
    
    companion object {
        private const val TAG = "FTMSService"
        
        // FTMS Service UUID
        val FTMS_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805F9B34FB")
        
        // FTMS Characteristics
        val FTMS_FEATURE_UUID: UUID = UUID.fromString("00002ACC-0000-1000-8000-00805F9B34FB")
        val ROWER_DATA_UUID: UUID = UUID.fromString("00002AD1-0000-1000-8000-00805F9B34FB")
        val FTMS_CONTROL_POINT_UUID: UUID = UUID.fromString("00002AD9-0000-1000-8000-00805F9B34FB")
        val FTMS_STATUS_UUID: UUID = UUID.fromString("00002ADA-0000-1000-8000-00805F9B34FB")
        
        // Client Characteristic Configuration Descriptor
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        
        // Device Information Service
        val DEVICE_INFO_SERVICE_UUID: UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
        val MANUFACTURER_NAME_UUID: UUID = UUID.fromString("00002A29-0000-1000-8000-00805F9B34FB")
        val MODEL_NUMBER_UUID: UUID = UUID.fromString("00002A24-0000-1000-8000-00805F9B34FB")
    }
    
    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    
    private var bluetoothGattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false
    private var connectedDevices = mutableSetOf<BluetoothDevice>()
    
    // Characteristics
    private lateinit var ftmsFeatureCharacteristic: BluetoothGattCharacteristic
    private lateinit var rowerDataCharacteristic: BluetoothGattCharacteristic
    private lateinit var controlPointCharacteristic: BluetoothGattCharacteristic
    private lateinit var statusCharacteristic: BluetoothGattCharacteristic
    
    // Current rowing data
    private var currentRowingData: FTMSRowingData? = null
    
    fun startService(): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "Bluetooth not available or not enabled")
            return false
        }
        
        bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        if (bluetoothGattServer == null) {
            Log.e(TAG, "Failed to open GATT server")
            return false
        }
        
        setupServices()
        startAdvertising()
        
        Log.i(TAG, "FTMS Service started successfully")
        return true
    }
    
    fun stopService() {
        stopAdvertising()
        bluetoothGattServer?.close()
        bluetoothGattServer = null
        connectedDevices.clear()
        isAdvertising = false
        Log.i(TAG, "FTMS Service stopped")
    }
    
    private fun setupServices() {
        // FTMS Service
        val ftmsService = BluetoothGattService(FTMS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        // FTMS Feature Characteristic
        ftmsFeatureCharacteristic = BluetoothGattCharacteristic(
            FTMS_FEATURE_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        // Rower Data Characteristic (notifications)
        rowerDataCharacteristic = BluetoothGattCharacteristic(
            ROWER_DATA_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )
        rowerDataCharacteristic.addDescriptor(
            BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
        )
        
        // Control Point Characteristic
        controlPointCharacteristic = BluetoothGattCharacteristic(
            FTMS_CONTROL_POINT_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        controlPointCharacteristic.addDescriptor(
            BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
        )
        
        // Status Characteristic
        statusCharacteristic = BluetoothGattCharacteristic(
            FTMS_STATUS_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )
        statusCharacteristic.addDescriptor(
            BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
        )
        
        ftmsService.addCharacteristic(ftmsFeatureCharacteristic)
        ftmsService.addCharacteristic(rowerDataCharacteristic)
        ftmsService.addCharacteristic(controlPointCharacteristic)
        ftmsService.addCharacteristic(statusCharacteristic)
        
        // Device Information Service
        val deviceInfoService = BluetoothGattService(DEVICE_INFO_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        val manufacturerCharacteristic = BluetoothGattCharacteristic(
            MANUFACTURER_NAME_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        val modelCharacteristic = BluetoothGattCharacteristic(
            MODEL_NUMBER_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        deviceInfoService.addCharacteristic(manufacturerCharacteristic)
        deviceInfoService.addCharacteristic(modelCharacteristic)
        
        bluetoothGattServer?.addService(ftmsService)
        bluetoothGattServer?.addService(deviceInfoService)
        
        // Set initial values
        ftmsFeatureCharacteristic.value = getFTMSFeatureValue()
        manufacturerCharacteristic.value = "R50 Connector".toByteArray()
        modelCharacteristic.value = "R50 FTMS Bridge".toByteArray()
    }
    
    private fun startAdvertising() {
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "BLE Advertiser not available")
            return
        }
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(FTMS_SERVICE_UUID))
            .build()
        
        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }
    
    private fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
        isAdvertising = false
    }
    
    fun updateRowingData(rowingData: FTMSRowingData) {
        this.currentRowingData = rowingData
        
        if (connectedDevices.isNotEmpty()) {
            val data = encodeRowerData(rowingData)
            for (device in connectedDevices) {
                bluetoothGattServer?.notifyCharacteristicChanged(
                    device,
                    rowerDataCharacteristic,
                    false,
                    data
                )
            }
        }
    }
    
    private fun getFTMSFeatureValue(): ByteArray {
        // FTMS Feature flags for rowing machine
        // Bit 0: Average Stroke Rate Supported
        // Bit 1: Total Distance Supported
        // Bit 2: Instantaneous Pace Supported
        // Bit 3: Average Pace Supported
        // Bit 4: Total Energy Supported
        // Bit 5: Energy Per Hour Supported
        // Bit 6: Energy Per Minute Supported
        // Bit 7: Heart Rate Measurement Supported
        val features = 0x000000FF // Enable basic rower features
        
        return byteArrayOf(
            (features and 0xFF).toByte(),
            ((features shr 8) and 0xFF).toByte(),
            ((features shr 16) and 0xFF).toByte(),
            ((features shr 24) and 0xFF).toByte(),
            0x00, 0x00, 0x00, 0x00 // Extended features (not used)
        )
    }
    
    private fun encodeRowerData(data: FTMSRowingData): ByteArray {
        val buffer = mutableListOf<Byte>()
        
        // Flags (2 bytes) - indicate which data fields are present
        var flags = 0x0000
        
        // Always include stroke rate and stroke count
        flags = flags or 0x0001 // Stroke Rate present
        flags = flags or 0x0002 // Stroke Count present
        
        if (data.totalDistance != null) flags = flags or 0x0004
        if (data.instantaneousPace != null) flags = flags or 0x0008
        if (data.averagePace != null) flags = flags or 0x0010
        if (data.totalEnergy != null) flags = flags or 0x0020
        if (data.energyPerHour != null) flags = flags or 0x0040
        if (data.energyPerMinute != null) flags = flags or 0x0080
        if (data.heartRate != null) flags = flags or 0x0100
        
        buffer.add((flags and 0xFF).toByte())
        buffer.add(((flags shr 8) and 0xFF).toByte())
        
        // Stroke Rate (uint8) - strokes per minute
        buffer.add((data.strokeRate ?: 0).toByte())
        
        // Stroke Count (uint16) - total strokes
        val strokeCount = data.strokeCount ?: 0
        buffer.add((strokeCount and 0xFF).toByte())
        buffer.add(((strokeCount shr 8) and 0xFF).toByte())
        
        // Total Distance (uint24) - in meters
        if (data.totalDistance != null) {
            val distance = data.totalDistance
            buffer.add((distance and 0xFF).toByte())
            buffer.add(((distance shr 8) and 0xFF).toByte())
            buffer.add(((distance shr 16) and 0xFF).toByte())
        }
        
        // Instantaneous Pace (uint16) - time per 500m in seconds
        if (data.instantaneousPace != null) {
            val pace = data.instantaneousPace
            buffer.add((pace and 0xFF).toByte())
            buffer.add(((pace shr 8) and 0xFF).toByte())
        }
        
        // Average Pace (uint16) - time per 500m in seconds
        if (data.averagePace != null) {
            val pace = data.averagePace
            buffer.add((pace and 0xFF).toByte())
            buffer.add(((pace shr 8) and 0xFF).toByte())
        }
        
        // Total Energy (uint16) - in calories
        if (data.totalEnergy != null) {
            val energy = data.totalEnergy
            buffer.add((energy and 0xFF).toByte())
            buffer.add(((energy shr 8) and 0xFF).toByte())
        }
        
        // Energy Per Hour (uint16) - in calories per hour
        if (data.energyPerHour != null) {
            val energyPerHour = data.energyPerHour
            buffer.add((energyPerHour and 0xFF).toByte())
            buffer.add(((energyPerHour shr 8) and 0xFF).toByte())
        }
        
        // Energy Per Minute (uint8) - in calories per minute
        if (data.energyPerMinute != null) {
            buffer.add(data.energyPerMinute.toByte())
        }
        
        // Heart Rate (uint8) - in BPM
        if (data.heartRate != null) {
            buffer.add(data.heartRate.toByte())
        }
        
        return buffer.toByteArray()
    }
    
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            
            device?.let {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i(TAG, "Device connected: ${it.address}")
                        connectedDevices.add(it)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i(TAG, "Device disconnected: ${it.address}")
                        connectedDevices.remove(it)
                    }
                }
            }
        }
        
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            
            Log.d(TAG, "Read request for characteristic: ${characteristic?.uuid}")
            
            bluetoothGattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                characteristic?.value
            )
        }
        
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            
            Log.d(TAG, "Write request for characteristic: ${characteristic?.uuid}")
            
            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    null
                )
            }
        }
        
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
            
            Log.d(TAG, "Descriptor write request: ${descriptor?.uuid}")
            
            if (descriptor?.uuid == CCCD_UUID) {
                val isNotificationEnabled = value?.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == true
                Log.d(TAG, "Notifications ${if (isNotificationEnabled) "enabled" else "disabled"} for ${descriptor.characteristic.uuid}")
            }
            
            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    null
                )
            }
        }
    }
    
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            isAdvertising = true
            Log.i(TAG, "BLE advertising started successfully")
        }
        
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            isAdvertising = false
            Log.e(TAG, "BLE advertising failed with error code: $errorCode")
        }
    }
    
    fun isRunning(): Boolean = bluetoothGattServer != null && isAdvertising
    
    fun getConnectedDevicesCount(): Int = connectedDevices.size
}

package com.chowchin.r50.ftms

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.chowchin.r50.RowingData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/**
 * FTMS (Fitness Machine Service) Server for Zwift compatibility
 * Implements Bluetooth Low Energy GATT server to advertise as a rowing machine or bike
 */
class FTMSServer(private val context: Context) {

    companion object {
        private const val TAG = "FTMSServer"
        
        // FTMS Service UUID
        val FTMS_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805F9B34FB")
        
        // FTMS Characteristics
        val FTMS_FEATURE_UUID: UUID = UUID.fromString("00002ACC-0000-1000-8000-00805F9B34FB")
        val ROWER_DATA_UUID: UUID = UUID.fromString("00002AD1-0000-1000-8000-00805F9B34FB")
        val INDOOR_BIKE_DATA_UUID: UUID = UUID.fromString("00002AD2-0000-1000-8000-00805F9B34FB")
        val FITNESS_MACHINE_CONTROL_POINT_UUID: UUID = UUID.fromString("00002AD9-0000-1000-8000-00805F9B34FB")
        val FITNESS_MACHINE_STATUS_UUID: UUID = UUID.fromString("00002ADA-0000-1000-8000-00805F9B34FB")
        
        // Client Characteristic Configuration Descriptor
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        
        // Device Information Service
        val DEVICE_INFORMATION_SERVICE_UUID: UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
        val MANUFACTURER_NAME_UUID: UUID = UUID.fromString("00002A29-0000-1000-8000-00805F9B34FB")
        val MODEL_NUMBER_UUID: UUID = UUID.fromString("00002A24-0000-1000-8000-00805F9B34FB")
        val SERIAL_NUMBER_UUID: UUID = UUID.fromString("00002A25-0000-1000-8000-00805F9B34FB")
        
        // FTMS Feature bits for Rower
        private const val FTMS_FEATURE_ROWER_SUPPORTED = 0x00000008L
        private const val FTMS_FEATURE_INDOOR_BIKE_SUPPORTED = 0x00000002L
        private const val FTMS_FEATURE_POWER_MEASUREMENT_SUPPORTED = 0x00000001L
        private const val FTMS_FEATURE_ENERGY_MEASUREMENT_SUPPORTED = 0x00000002L
        private const val FTMS_FEATURE_HEART_RATE_MEASUREMENT_SUPPORTED = 0x00000004L
        
        // Rower Data Flags
        private const val ROWER_DATA_STROKE_RATE_PRESENT = 0x01
        private const val ROWER_DATA_STROKE_COUNT_PRESENT = 0x02
        private const val ROWER_DATA_DISTANCE_PRESENT = 0x04
        private const val ROWER_DATA_PACE_PRESENT = 0x08
        private const val ROWER_DATA_POWER_PRESENT = 0x10
        private const val ROWER_DATA_RESISTANCE_LEVEL_PRESENT = 0x20
        private const val ROWER_DATA_EXPENDITURE_PRESENT = 0x40
        private const val ROWER_DATA_HEART_RATE_PRESENT = 0x80
        private const val ROWER_DATA_ELAPSED_TIME_PRESENT = 0x100
        private const val ROWER_DATA_REMAINING_TIME_PRESENT = 0x200
        
        // Indoor Bike Data Flags
        private const val BIKE_DATA_SPEED_PRESENT = 0x01
        private const val BIKE_DATA_CADENCE_PRESENT = 0x02
        private const val BIKE_DATA_DISTANCE_PRESENT = 0x04
        private const val BIKE_DATA_RESISTANCE_LEVEL_PRESENT = 0x08
        private const val BIKE_DATA_POWER_PRESENT = 0x10
        private const val BIKE_DATA_ENERGY_PRESENT = 0x20
        private const val BIKE_DATA_HEART_RATE_PRESENT = 0x40
        private const val BIKE_DATA_ELAPSED_TIME_PRESENT = 0x80
    }

    enum class MachineType {
        ROWER,
        BIKE
    }

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGattServer: BluetoothGattServer? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    
    private var isAdvertising = false
    private var isServerStarted = false
    private var machineType = MachineType.ROWER // Default to rower
    
    // Connected devices
    private val connectedDevices = mutableSetOf<BluetoothDevice>()
    
    // Characteristics
    private var rowerDataCharacteristic: BluetoothGattCharacteristic? = null
    private var bikeDataCharacteristic: BluetoothGattCharacteristic? = null
    private var fitnessControlPointCharacteristic: BluetoothGattCharacteristic? = null
    private var fitnessStatusCharacteristic: BluetoothGattCharacteristic? = null
    
    // Current rowing data
    private var currentRowingData: RowingData? = null

    /**
     * Set the machine type (Rower or Bike)
     */
    fun setMachineType(type: MachineType) {
        machineType = type
        Log.i(TAG, "Machine type set to: $type")
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    fun startServer(): Boolean {
        if (isServerStarted) {
            Log.w(TAG, "FTMS Server already started")
            return true
        }

        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "Bluetooth not available or not enabled")
            return false
        }

        if (!bluetoothAdapter!!.isMultipleAdvertisementSupported) {
            Log.e(TAG, "Device does not support BLE advertising")
            return false
        }

        bluetoothLeAdvertiser = bluetoothAdapter!!.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            Log.e(TAG, "BLE advertiser not available")
            return false
        }

        return setupGattServer()
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    private fun setupGattServer(): Boolean {
        bluetoothGattServer = bluetoothManager!!.openGattServer(context, gattServerCallback)
        
        if (bluetoothGattServer == null) {
            Log.e(TAG, "Failed to open GATT server")
            return false
        }

        // Add FTMS Service
        val ftmsService = BluetoothGattService(FTMS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        // FTMS Feature Characteristic (mandatory, read-only)
        val ftmsFeatureChar = BluetoothGattCharacteristic(
            FTMS_FEATURE_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        ftmsService.addCharacteristic(ftmsFeatureChar)
        
        // Add data characteristic based on machine type
        when (machineType) {
            MachineType.ROWER -> {
                // Rower Data Characteristic (mandatory for rower, notify)
                rowerDataCharacteristic = BluetoothGattCharacteristic(
                    ROWER_DATA_UUID,
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ
                )
                val rowerDataDescriptor = BluetoothGattDescriptor(
                    CLIENT_CHARACTERISTIC_CONFIG_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
                rowerDataCharacteristic!!.addDescriptor(rowerDataDescriptor)
                ftmsService.addCharacteristic(rowerDataCharacteristic!!)
            }
            MachineType.BIKE -> {
                // Indoor Bike Data Characteristic (mandatory for bike, notify)
                bikeDataCharacteristic = BluetoothGattCharacteristic(
                    INDOOR_BIKE_DATA_UUID,
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ
                )
                val bikeDataDescriptor = BluetoothGattDescriptor(
                    CLIENT_CHARACTERISTIC_CONFIG_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
                bikeDataCharacteristic!!.addDescriptor(bikeDataDescriptor)
                ftmsService.addCharacteristic(bikeDataCharacteristic!!)
            }
        }
        
        // Fitness Machine Control Point (optional, write, indicate)
        fitnessControlPointCharacteristic = BluetoothGattCharacteristic(
            FITNESS_MACHINE_CONTROL_POINT_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val controlPointDescriptor = BluetoothGattDescriptor(
            CLIENT_CHARACTERISTIC_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        fitnessControlPointCharacteristic!!.addDescriptor(controlPointDescriptor)
        ftmsService.addCharacteristic(fitnessControlPointCharacteristic!!)
        
        // Fitness Machine Status (optional, notify)
        fitnessStatusCharacteristic = BluetoothGattCharacteristic(
            FITNESS_MACHINE_STATUS_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val statusDescriptor = BluetoothGattDescriptor(
            CLIENT_CHARACTERISTIC_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        fitnessStatusCharacteristic!!.addDescriptor(statusDescriptor)
        ftmsService.addCharacteristic(fitnessStatusCharacteristic!!)
        
        bluetoothGattServer!!.addService(ftmsService)
        
        // Add Device Information Service
        val deviceInfoService = BluetoothGattService(DEVICE_INFORMATION_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        val manufacturerNameChar = BluetoothGattCharacteristic(
            MANUFACTURER_NAME_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        deviceInfoService.addCharacteristic(manufacturerNameChar)
        
        val modelNumberChar = BluetoothGattCharacteristic(
            MODEL_NUMBER_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        deviceInfoService.addCharacteristic(modelNumberChar)
        
        val serialNumberChar = BluetoothGattCharacteristic(
            SERIAL_NUMBER_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        deviceInfoService.addCharacteristic(serialNumberChar)
        
        bluetoothGattServer!!.addService(deviceInfoService)
        
        isServerStarted = true
        Log.i(TAG, "GATT server started successfully")
        return true
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun startAdvertising(): Boolean {
        if (isAdvertising) {
            Log.w(TAG, "Already advertising")
            return true
        }

        if (!isServerStarted) {
            Log.e(TAG, "GATT server not started")
            return false
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(FTMS_SERVICE_UUID))
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        bluetoothLeAdvertiser!!.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
        
        return true
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopAdvertising() {
        if (isAdvertising && bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser!!.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Log.i(TAG, "Stopped advertising")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun stopServer() {
        stopAdvertising()
        
        bluetoothGattServer?.let { server ->
            server.clearServices()
            server.close()
            bluetoothGattServer = null
        }
        
        connectedDevices.clear()
        isServerStarted = false
        Log.i(TAG, "GATT server stopped")
    }

    /**
     * Update rowing data and notify connected clients
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun updateRowingData(rowingData: RowingData) {
        currentRowingData = rowingData
        
        if (connectedDevices.isNotEmpty()) {
            when (machineType) {
                MachineType.ROWER -> {
                    rowerDataCharacteristic?.let { char ->
                        val data = buildRowerDataPacket(rowingData)
                        char.value = data
                        
                        connectedDevices.forEach { device ->
                            try {
                                bluetoothGattServer?.notifyCharacteristicChanged(device, char, false)
                                Log.d(TAG, "Notified device ${device.address} with rower data")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to notify device ${device.address}", e)
                            }
                        }
                    }
                }
                MachineType.BIKE -> {
                    bikeDataCharacteristic?.let { char ->
                        val data = buildBikeDataPacket(rowingData)
                        char.value = data
                        
                        connectedDevices.forEach { device ->
                            try {
                                bluetoothGattServer?.notifyCharacteristicChanged(device, char, false)
                                Log.d(TAG, "Notified device ${device.address} with bike data")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to notify device ${device.address}", e)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Build FTMS Rower Data packet according to specification
     */
    private fun buildRowerDataPacket(data: RowingData): ByteArray {
        val buffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        
        // Flags (2 bytes) - indicating what data is present
        var flags = 0
        
        if (data.strokePerMinute != null) flags = flags or ROWER_DATA_STROKE_RATE_PRESENT
        if (data.strokeCount != null) flags = flags or ROWER_DATA_STROKE_COUNT_PRESENT
        if (data.distance != null) flags = flags or ROWER_DATA_DISTANCE_PRESENT
        if (data.power != null) flags = flags or ROWER_DATA_POWER_PRESENT
        if (data.calories != null) flags = flags or ROWER_DATA_EXPENDITURE_PRESENT
        if (data.heartbeat != null) flags = flags or ROWER_DATA_HEART_RATE_PRESENT
        if (data.elapsedSecond != null) flags = flags or ROWER_DATA_ELAPSED_TIME_PRESENT
        
        buffer.putShort(flags.toShort())
        
        // Stroke Rate (1 byte) - strokes per minute / 2
        if (data.strokePerMinute != null) {
            buffer.put((data.strokePerMinute!! / 2).toByte())
        }
        
        // Stroke Count (2 bytes)
        if (data.strokeCount != null) {
            buffer.putShort(data.strokeCount!!.toShort())
        }
        
        // Distance (3 bytes) - distance in meters
        if (data.distance != null) {
            val distanceBytes = ByteArray(3)
            val distance = data.distance!!
            distanceBytes[0] = (distance and 0xFF).toByte()
            distanceBytes[1] = ((distance shr 8) and 0xFF).toByte()
            distanceBytes[2] = ((distance shr 16) and 0xFF).toByte()
            buffer.put(distanceBytes)
        }
        
        // Instantaneous Power (2 bytes) - power in watts
        if (data.power != null) {
            buffer.putShort(data.power!!.toShort())
        }
        
        // Total Energy (2 bytes) - total energy in kcal
        if (data.calories != null) {
            buffer.putShort(data.calories!!.toShort())
        }
        
        // Heart Rate (1 byte) - heart rate in BPM
        if (data.heartbeat != null && data.heartbeat!! > 0) {
            buffer.put(data.heartbeat!!.toByte())
        }
        
        // Elapsed Time (2 bytes) - time in seconds
        if (data.elapsedSecond != null) {
            buffer.putShort(data.elapsedSecond!!.toShort())
        }
        
        // Create final array with actual size
        val result = ByteArray(buffer.position())
        buffer.rewind()
        buffer.get(result)
        
        Log.d(TAG, "Built rower data packet: ${result.joinToString(" ") { "%02x".format(it) }}")
        return result
    }

    /**
     * Build FTMS Indoor Bike Data packet according to specification
     * Maps rowing data to bike equivalent values
     */
    private fun buildBikeDataPacket(data: RowingData): ByteArray {
        val buffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        
        // Flags (2 bytes) - indicating what data is present
        var flags = 0
        
        // Always include speed (derived from stroke rate)
        flags = flags or BIKE_DATA_SPEED_PRESENT
        
        if (data.strokePerMinute != null) flags = flags or BIKE_DATA_CADENCE_PRESENT
        if (data.distance != null) flags = flags or BIKE_DATA_DISTANCE_PRESENT
        if (data.power != null) flags = flags or BIKE_DATA_POWER_PRESENT
        if (data.calories != null) flags = flags or BIKE_DATA_ENERGY_PRESENT
        if (data.heartbeat != null) flags = flags or BIKE_DATA_HEART_RATE_PRESENT
        if (data.elapsedSecond != null) flags = flags or BIKE_DATA_ELAPSED_TIME_PRESENT
        
        buffer.putShort(flags.toShort())
        
        // Instantaneous Speed (2 bytes) - speed in km/h * 100
        // Convert stroke rate to simulated bike speed (rough conversion)
        val simulatedSpeed = (data.strokePerMinute ?: 0) * 120 // ~24 km/h at 20 SPM
        buffer.putShort(simulatedSpeed.toShort())
        
        // Instantaneous Cadence (2 bytes) - RPM * 2
        // Use stroke rate as cadence directly
        if (data.strokePerMinute != null) {
            buffer.putShort((data.strokePerMinute!! * 2).toShort())
        }
        
        // Total Distance (3 bytes) - distance in meters
        if (data.distance != null) {
            val distanceBytes = ByteArray(3)
            val distance = data.distance!!
            distanceBytes[0] = (distance and 0xFF).toByte()
            distanceBytes[1] = ((distance shr 8) and 0xFF).toByte()
            distanceBytes[2] = ((distance shr 16) and 0xFF).toByte()
            buffer.put(distanceBytes)
        }
        
        // Instantaneous Power (2 bytes) - power in watts
        if (data.power != null) {
            buffer.putShort(data.power!!.toShort())
        }
        
        // Total Energy (2 bytes) - total energy in kcal
        if (data.calories != null) {
            buffer.putShort(data.calories!!.toShort())
        }
        
        // Heart Rate (1 byte) - heart rate in BPM
        if (data.heartbeat != null && data.heartbeat!! > 0) {
            buffer.put(data.heartbeat!!.toByte())
        }
        
        // Elapsed Time (2 bytes) - time in seconds
        if (data.elapsedSecond != null) {
            buffer.putShort(data.elapsedSecond!!.toShort())
        }
        
        // Create final array with actual size
        val result = ByteArray(buffer.position())
        buffer.rewind()
        buffer.get(result)
        
        Log.d(TAG, "Built bike data packet: ${result.joinToString(" ") { "%02x".format(it) }}")
        return result
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "BLE advertising started successfully")
            isAdvertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE advertising failed with error code: $errorCode")
            isAdvertising = false
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            
            device?.let {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connectedDevices.add(it)
                        Log.i(TAG, "Device connected: ${it.address}")
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connectedDevices.remove(it)
                        Log.i(TAG, "Device disconnected: ${it.address}")
                    }
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            
            when (characteristic?.uuid) {
                FTMS_FEATURE_UUID -> {
                    // Return FTMS features based on machine type
                    val features = when (machineType) {
                        MachineType.ROWER -> {
                            (FTMS_FEATURE_ROWER_SUPPORTED or 
                             FTMS_FEATURE_POWER_MEASUREMENT_SUPPORTED or
                             FTMS_FEATURE_ENERGY_MEASUREMENT_SUPPORTED or
                             FTMS_FEATURE_HEART_RATE_MEASUREMENT_SUPPORTED).toInt()
                        }
                        MachineType.BIKE -> {
                            (FTMS_FEATURE_INDOOR_BIKE_SUPPORTED or 
                             FTMS_FEATURE_POWER_MEASUREMENT_SUPPORTED or
                             FTMS_FEATURE_ENERGY_MEASUREMENT_SUPPORTED or
                             FTMS_FEATURE_HEART_RATE_MEASUREMENT_SUPPORTED).toInt()
                        }
                    }
                    val featureBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(features)
                        .putInt(0) // Reserved bytes
                        .array()
                    
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, featureBytes)
                    Log.d(TAG, "Sent FTMS features for ${machineType} to ${device?.address}")
                }
                
                MANUFACTURER_NAME_UUID -> {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, "R50 Connector".toByteArray())
                }
                
                MODEL_NUMBER_UUID -> {
                    val modelName = when (machineType) {
                        MachineType.ROWER -> "R50 Rowing Machine"
                        MachineType.BIKE -> "R50 Indoor Bike"
                    }
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, modelName.toByteArray())
                }
                
                SERIAL_NUMBER_UUID -> {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, "R50-001".toByteArray())
                }
                
                else -> {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                    Log.w(TAG, "Unknown characteristic read request: ${characteristic?.uuid}")
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
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
            
            when (characteristic?.uuid) {
                FITNESS_MACHINE_CONTROL_POINT_UUID -> {
                    // Handle fitness machine control commands
                    value?.let { handleControlPointWrite(device, requestId, it, responseNeeded) }
                }
                
                else -> {
                    if (responseNeeded) {
                        bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                    }
                    Log.w(TAG, "Unknown characteristic write request: ${characteristic?.uuid}")
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
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
            
            if (descriptor?.uuid == CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                // Client is enabling/disabling notifications
                val enabled = value?.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == true ||
                             value?.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) == true
                
                Log.d(TAG, "Client ${device?.address} ${if (enabled) "enabled" else "disabled"} notifications for ${descriptor.characteristic?.uuid}")
                
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            } else {
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private fun handleControlPointWrite(device: BluetoothDevice?, requestId: Int, value: ByteArray, responseNeeded: Boolean) {
            if (value.isEmpty()) {
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
                return
            }
            
            val opCode = value[0].toInt() and 0xFF
            Log.d(TAG, "Received control point command: $opCode from ${device?.address}")
            
            // Response: [Response Code (0x80), Original OpCode, Status]
            val response = byteArrayOf(0x80.toByte(), value[0], 0x01) // 0x01 = Success
            
            when (opCode) {
                0x00 -> { // Request Control
                    Log.d(TAG, "Control requested by ${device?.address}")
                }
                0x01 -> { // Reset
                    Log.d(TAG, "Reset requested by ${device?.address}")
                }
                0x02 -> { // Set Target Speed
                    Log.d(TAG, "Set target speed requested by ${device?.address}")
                }
                0x04 -> { // Set Target Power
                    Log.d(TAG, "Set target power requested by ${device?.address}")
                }
                0x07 -> { // Start or Resume
                    Log.d(TAG, "Start/Resume requested by ${device?.address}")
                }
                0x08 -> { // Stop or Pause
                    Log.d(TAG, "Stop/Pause requested by ${device?.address}")
                }
                else -> {
                    Log.w(TAG, "Unknown control point command: $opCode")
                    response[2] = 0x02.toByte() // 0x02 = OpCode Not Supported
                }
            }
            
            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            
            // Send indication response if control point characteristic supports it
            fitnessControlPointCharacteristic?.let { char ->
                char.value = response
                try {
                    bluetoothGattServer?.notifyCharacteristicChanged(device, char, true) // true for indication
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send control point response", e)
                }
            }
        }
    }

    fun isAdvertising(): Boolean = isAdvertising
    fun isServerStarted(): Boolean = isServerStarted
    fun getConnectedDevicesCount(): Int = connectedDevices.size
}

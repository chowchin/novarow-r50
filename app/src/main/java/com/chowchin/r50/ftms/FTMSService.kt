package com.chowchin.r50.ftms

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import java.util.*

/**
 * FTMS (Fitness Machine Service) implementation for rowing machine
 * This service allows the app to act as a Bluetooth LE rowing machine
 * that can be discovered and connected to by fitness apps like Zwift
 */
class FTMSService(
    private val context: Context,
) {
    companion object {
        private const val TAG = "FTMSService"
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
    private lateinit var indoorBikeDataCharacteristic: BluetoothGattCharacteristic

    // Current rowing data
    private var currentRowingData: FTMSRowingData? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
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

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE])
    fun stopService() {
        stopAdvertising()
        bluetoothGattServer?.close()
        bluetoothGattServer = null
        connectedDevices.clear()
        isAdvertising = false
        Log.i(TAG, "FTMS Service stopped")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun setupServices() {
        // Create FTMS Service
        val ftmsService = BluetoothGattService(FTMSConstants.FTMS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        // FTMS Feature Characteristic (Read)
        val ftmsFeatureCharacteristic = BluetoothGattCharacteristic(
            FTMSConstants.FTMS_FEATURE_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        // Indoor Bike Data Characteristic (Notify)
        indoorBikeDataCharacteristic = BluetoothGattCharacteristic(
            FTMSConstants.INDOOR_BIKE_DATA_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        // Add CCC descriptor for notifications
        val indoorBikeDataDescriptor = BluetoothGattDescriptor(
            FTMSConstants.CCC_DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        indoorBikeDataCharacteristic.addDescriptor(indoorBikeDataDescriptor)
        
        // FTMS Control Point Characteristic (Write, Indicate)
        val controlPointCharacteristic = BluetoothGattCharacteristic(
            FTMSConstants.FTMS_CONTROL_POINT_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        
        // Add CCC descriptor for indications
        val controlPointDescriptor = BluetoothGattDescriptor(
            FTMSConstants.CCC_DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        controlPointCharacteristic.addDescriptor(controlPointDescriptor)
        
        // FTMS Status Characteristic (Notify)
        val statusCharacteristic = BluetoothGattCharacteristic(
            FTMSConstants.FTMS_STATUS_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        // Add characteristics to service
        Log.d(TAG, "Adding characteristics to FTMS service...")
        ftmsService.addCharacteristic(ftmsFeatureCharacteristic)
        ftmsService.addCharacteristic(indoorBikeDataCharacteristic)
        ftmsService.addCharacteristic(controlPointCharacteristic)
        ftmsService.addCharacteristic(statusCharacteristic)
        
        // Add service to server
        Log.d(TAG, "Adding FTMS service to GATT server...")
        val success = bluetoothGattServer?.addService(ftmsService) ?: false
        Log.d(TAG, "FTMS Service added to server: $success")
        
        if (!success) {
            Log.e(TAG, "Failed to add FTMS service to GATT server")
        }
    }

    private fun startAdvertising() {
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "BLE Advertiser not available")
            return
        }

        val settings =
            AdvertiseSettings
                .Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .build()

        val data =
            AdvertiseData
                .Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid(FTMSConstants.FTMS_SERVICE_UUID))
                .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
        isAdvertising = false
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun updateRowingData(rowingData: FTMSRowingData) {
        this.currentRowingData = rowingData

        if (connectedDevices.isNotEmpty()) {
            val data = createIndoorBikeDataPacket(rowingData)
            indoorBikeDataCharacteristic.value = data
            for (device in connectedDevices) {
                bluetoothGattServer?.notifyCharacteristicChanged(
                    device,
                    indoorBikeDataCharacteristic,
                    false,
                )
            }
        }
    }

    private fun getFTMSFeatureValue(): ByteArray {
        // FTMS Feature characteristic data - indicates supported features
        val features = FTMSUtils.getIndoorBikeFeatures()
        return FTMSUtils.uint32ToBytes(features)
    }

    private fun createIndoorBikeDataPacket(data: FTMSRowingData): ByteArray {
        // Indoor Bike Data packet format according to FTMS specification
        val packet = mutableListOf<Byte>()

        // Get flags for the data we're including
        val flags = FTMSUtils.getIndoorBikeDataFlags()
        packet.addAll(FTMSUtils.uint16ToBytes(flags).toList())

        // Instantaneous Speed (uint16, 0.01 km/h resolution) - ALWAYS present
        val speedValue = FTMSUtils.speedToFTMS(data.instantaneousPace?.toDouble() ?: 0.0)
        packet.addAll(FTMSUtils.uint16ToBytes(speedValue).toList())

        // Instantaneous Cadence (uint16, 0.5 1/min resolution) - only if flag is set
        if ((flags and FTMSConstants.IBD_FLAG_INSTANTANEOUS_CADENCE_PRESENT) != 0) {
            val cadenceValue = FTMSUtils.cadenceToFTMS(data.strokeRate?.toDouble() ?: 0.0)
            packet.addAll(FTMSUtils.uint16ToBytes(cadenceValue).toList())
        }

        // Instantaneous Power (sint16, 1 Watt resolution) - only if flag is set
        if ((flags and FTMSConstants.IBD_FLAG_INSTANTANEOUS_POWER_PRESENT) != 0) {
            val powerValue = FTMSUtils.powerToFTMS(data.energyPerHour ?: 0)
            packet.addAll(FTMSUtils.sint16ToBytes(powerValue).toList())
        }

        // Heart Rate (uint8, 1 bpm resolution) - only if flag is set
        if ((flags and FTMSConstants.IBD_FLAG_HEART_RATE_PRESENT) != 0) {
            val heartRateValue = FTMSUtils.heartRateToFTMS(data.heartRate ?: 0)
            packet.add(heartRateValue.toByte())
        }

        return packet.toByteArray()
    }

    private val gattServerCallback =
        object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(
                device: BluetoothDevice?,
                status: Int,
                newState: Int,
            ) {
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

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onCharacteristicReadRequest(
                device: BluetoothDevice?,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic?,
            ) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic)

                Log.d(TAG, "Read request for characteristic: ${characteristic?.uuid}")

                bluetoothGattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    characteristic?.value,
                )
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?,
            ) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)

                Log.d(TAG, "Write request for characteristic: ${characteristic?.uuid}")

                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        null,
                    )
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
                value: ByteArray?,
            ) {
                super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)

                Log.d(TAG, "Descriptor write request: ${descriptor?.uuid}")

                if (descriptor?.uuid == FTMSConstants.CCC_DESCRIPTOR_UUID) {
                    val isNotificationEnabled = value?.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == true
                    Log.d(
                        TAG,
                        "Notifications ${if (isNotificationEnabled) "enabled" else "disabled"} for ${descriptor.characteristic.uuid}",
                    )
                }

                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        null,
                    )
                }
            }
        }

    private val advertiseCallback =
        object : AdvertiseCallback() {
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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getConnectedDevices(): List<BluetoothDevice> = connectedDevices.toList()
}

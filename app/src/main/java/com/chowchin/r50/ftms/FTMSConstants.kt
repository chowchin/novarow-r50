package com.chowchin.r50.ftms

import java.util.*

/**
 * Constants and utilities for FTMS (Fitness Machine Service) implementation
 */
object FTMSConstants {
    // Device Information Service
    val DEVICE_INFO_SERVICE_UUID: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    val MANUFACTURER_NAME_UUID: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
    val MODEL_NUMBER_UUID: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")

    // FTMS Service and Characteristic UUIDs
    val FTMS_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    val FTMS_FEATURE_UUID: UUID = UUID.fromString("00002ACC-0000-1000-8000-00805f9b34fb")
    val INDOOR_BIKE_DATA_UUID: UUID = UUID.fromString("00002AD2-0000-1000-8000-00805f9b34fb")
    val FTMS_CONTROL_POINT_UUID: UUID = UUID.fromString("00002AD9-0000-1000-8000-00805f9b34fb")
    val FTMS_STATUS_UUID: UUID = UUID.fromString("00002ADA-0000-1000-8000-00805f9b34fb")
    val SUPPORTED_SPEED_RANGE_UUID: UUID = UUID.fromString("00002AD4-0000-1000-8000-00805f9b34fb")
    val SUPPORTED_INCLINATION_RANGE_UUID: UUID = UUID.fromString("00002AD5-0000-1000-8000-00805f9b34fb")
    val SUPPORTED_RESISTANCE_LEVEL_RANGE_UUID: UUID = UUID.fromString("00002AD6-0000-1000-8000-00805f9b34fb")
    val SUPPORTED_POWER_RANGE_UUID: UUID = UUID.fromString("00002AD8-0000-1000-8000-00805f9b34fb")

    // Standard Bluetooth UUIDs
    val CCC_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // FTMS Feature Bits
    const val FEATURE_AVERAGE_SPEED_SUPPORTED = 0x00000001L
    const val FEATURE_CADENCE_SUPPORTED = 0x00000002L
    const val FEATURE_TOTAL_DISTANCE_SUPPORTED = 0x00000004L
    const val FEATURE_INCLINATION_SUPPORTED = 0x00000008L
    const val FEATURE_ELEVATION_GAIN_SUPPORTED = 0x00000010L
    const val FEATURE_PACE_SUPPORTED = 0x00000020L
    const val FEATURE_STEP_COUNT_SUPPORTED = 0x00000040L
    const val FEATURE_RESISTANCE_LEVEL_SUPPORTED = 0x00000080L
    const val FEATURE_STRIDE_COUNT_SUPPORTED = 0x00000100L
    const val FEATURE_EXPENDED_ENERGY_SUPPORTED = 0x00000200L
    const val FEATURE_HEART_RATE_MEASUREMENT_SUPPORTED = 0x00000400L
    const val FEATURE_METABOLIC_EQUIVALENT_SUPPORTED = 0x00000800L
    const val FEATURE_ELAPSED_TIME_SUPPORTED = 0x00001000L
    const val FEATURE_REMAINING_TIME_SUPPORTED = 0x00002000L
    const val FEATURE_POWER_MEASUREMENT_SUPPORTED = 0x00004000L
    const val FEATURE_FORCE_ON_BELT_AND_POWER_OUTPUT_SUPPORTED = 0x00008000L
    const val FEATURE_USER_DATA_RETENTION_SUPPORTED = 0x00010000L

    // Indoor Bike Data Flags
    const val IBD_FLAG_MORE_DATA = 0x0001
    const val IBD_FLAG_AVERAGE_SPEED_PRESENT = 0x0002
    const val IBD_FLAG_INSTANTANEOUS_CADENCE_PRESENT = 0x0004
    const val IBD_FLAG_AVERAGE_CADENCE_PRESENT = 0x0008
    const val IBD_FLAG_TOTAL_DISTANCE_PRESENT = 0x0010
    const val IBD_FLAG_RESISTANCE_LEVEL_PRESENT = 0x0020
    const val IBD_FLAG_INSTANTANEOUS_POWER_PRESENT = 0x0040
    const val IBD_FLAG_AVERAGE_POWER_PRESENT = 0x0080
    const val IBD_FLAG_EXPENDED_ENERGY_PRESENT = 0x0100
    const val IBD_FLAG_HEART_RATE_PRESENT = 0x0200
    const val IBD_FLAG_METABOLIC_EQUIVALENT_PRESENT = 0x0400
    const val IBD_FLAG_ELAPSED_TIME_PRESENT = 0x0800
    const val IBD_FLAG_REMAINING_TIME_PRESENT = 0x1000

    // Control Point Op Codes
    const val CONTROL_POINT_REQUEST_CONTROL = 0x00
    const val CONTROL_POINT_RESET = 0x01
    const val CONTROL_POINT_SET_TARGET_SPEED = 0x02
    const val CONTROL_POINT_SET_TARGET_INCLINATION = 0x03
    const val CONTROL_POINT_SET_TARGET_RESISTANCE_LEVEL = 0x04
    const val CONTROL_POINT_SET_TARGET_POWER = 0x05
    const val CONTROL_POINT_SET_TARGET_HEART_RATE = 0x06
    const val CONTROL_POINT_START_OR_RESUME = 0x07
    const val CONTROL_POINT_STOP_OR_PAUSE = 0x08
    const val CONTROL_POINT_SET_TARGETED_EXPENDED_ENERGY = 0x09
    const val CONTROL_POINT_SET_TARGETED_NUMBER_OF_STEPS = 0x0A
    const val CONTROL_POINT_SET_TARGETED_NUMBER_OF_STRIDES = 0x0B
    const val CONTROL_POINT_SET_TARGETED_DISTANCE = 0x0C
    const val CONTROL_POINT_SET_TARGETED_TRAINING_TIME = 0x0D
    const val CONTROL_POINT_SET_TARGETED_TIME_IN_TWO_HEART_RATE_ZONES = 0x0E
    const val CONTROL_POINT_SET_TARGETED_TIME_IN_THREE_HEART_RATE_ZONES = 0x0F
    const val CONTROL_POINT_SET_TARGETED_TIME_IN_FIVE_HEART_RATE_ZONES = 0x10
    const val CONTROL_POINT_SET_INDOOR_BIKE_SIMULATION_PARAMETERS = 0x11
    const val CONTROL_POINT_SET_WHEEL_CIRCUMFERENCE = 0x12
    const val CONTROL_POINT_SPIN_DOWN_CONTROL = 0x13
    const val CONTROL_POINT_SET_TARGETED_CADENCE = 0x14
    const val CONTROL_POINT_RESPONSE_CODE = 0x80

    // Control Point Result Codes
    const val RESULT_CODE_SUCCESS = 0x01
    const val RESULT_CODE_OP_CODE_NOT_SUPPORTED = 0x02
    const val RESULT_CODE_INVALID_PARAMETER = 0x03
    const val RESULT_CODE_OPERATION_FAILED = 0x04
    const val RESULT_CODE_CONTROL_NOT_PERMITTED = 0x05

    // Machine Status
    const val MACHINE_STATUS_RESET = 0x00
    const val MACHINE_STATUS_IDLE = 0x01
    const val MACHINE_STATUS_WARMUP = 0x02
    const val MACHINE_STATUS_LOW_INTENSITY_INTERVAL = 0x03
    const val MACHINE_STATUS_HIGH_INTENSITY_INTERVAL = 0x04
    const val MACHINE_STATUS_RECOVERY_INTERVAL = 0x05
    const val MACHINE_STATUS_ISOMETRIC = 0x06
    const val MACHINE_STATUS_HEART_RATE_CONTROL = 0x07
    const val MACHINE_STATUS_FITNESS_TEST = 0x08
    const val MACHINE_STATUS_QUICK_START = 0x09
    const val MACHINE_STATUS_PRE_WORKOUT = 0x0A
    const val MACHINE_STATUS_POST_WORKOUT = 0x0B
}

/**
 * Utility functions for FTMS data manipulation
 */
object FTMSUtils {
    /**
     * Convert speed from km/h to FTMS uint16 format (0.01 km/h resolution)
     */
    fun speedToFTMS(speedKmh: Double): Int = (speedKmh * 100).toInt().coerceIn(0, 65535)

    /**
     * Convert speed from FTMS uint16 format to km/h
     */
    fun speedFromFTMS(ftmsSpeed: Int): Double = ftmsSpeed / 100.0

    /**
     * Convert cadence from RPM to FTMS uint16 format (0.5 1/min resolution)
     */
    fun cadenceToFTMS(cadenceRpm: Double): Int = (cadenceRpm * 2).toInt().coerceIn(0, 65535)

    /**
     * Convert cadence from FTMS uint16 format to RPM
     */
    fun cadenceFromFTMS(ftmsCadence: Int): Double = ftmsCadence / 2.0

    /**
     * Convert power to FTMS sint16 format (1 Watt resolution)
     */
    fun powerToFTMS(powerWatts: Int): Int = powerWatts.coerceIn(-32768, 32767)

    /**
     * Convert power from FTMS sint16 format to Watts
     */
    fun powerFromFTMS(ftmsPower: Int): Int = ftmsPower

    /**
     * Convert heart rate to FTMS uint8 format (1 BPM resolution)
     */
    fun heartRateToFTMS(heartRateBpm: Int): Int = heartRateBpm.coerceIn(0, 255)

    /**
     * Convert heart rate from FTMS uint8 format to BPM
     */
    fun heartRateFromFTMS(ftmsHeartRate: Int): Int = ftmsHeartRate

    /**
     * Create a little-endian uint16 byte array
     */
    fun uint16ToBytes(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
        )

    /**
     * Create a little-endian sint16 byte array
     */
    fun sint16ToBytes(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
        )

    /**
     * Create a little-endian uint32 byte array
     */
    fun uint32ToBytes(value: Long): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )

    /**
     * Parse a little-endian uint16 from byte array
     */
    fun bytesToUint16(
        bytes: ByteArray,
        offset: Int = 0,
    ): Int {
        if (bytes.size < offset + 2) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    /**
     * Parse a little-endian sint16 from byte array
     */
    fun bytesToSint16(
        bytes: ByteArray,
        offset: Int = 0,
    ): Int {
        if (bytes.size < offset + 2) return 0
        val value =
            (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8)
        return if (value > 32767) value - 65536 else value
    }

    /**
     * Get supported FTMS features for an indoor bike
     */
    fun getIndoorBikeFeatures(): Long =
        FTMSConstants.FEATURE_AVERAGE_SPEED_SUPPORTED or
            FTMSConstants.FEATURE_CADENCE_SUPPORTED or
            FTMSConstants.FEATURE_RESISTANCE_LEVEL_SUPPORTED or
            //FTMSConstants.FEATURE_HEART_RATE_MEASUREMENT_SUPPORTED or
            FTMSConstants.FEATURE_POWER_MEASUREMENT_SUPPORTED

    /**
     * Get Indoor Bike Data flags for the data we're sending
     */
    fun getIndoorBikeDataFlags(): Int =
        FTMSConstants.IBD_FLAG_INSTANTANEOUS_CADENCE_PRESENT or
            FTMSConstants.IBD_FLAG_INSTANTANEOUS_POWER_PRESENT
            // or FTMSConstants.IBD_FLAG_HEART_RATE_PRESENT
}

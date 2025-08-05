package com.chowchin.r50.fit

import com.chowchin.r50.database.RowingDataPoint
import com.chowchin.r50.database.RowingSession
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class FitFileGenerator {
    
    companion object {
        // FIT protocol constants
        private const val FIT_PROTOCOL_VERSION: Byte = 0x10
        private const val FIT_PROFILE_VERSION: Short = 2128
        private const val FIT_HEADER_SIZE = 14
        
        // Message types
        private const val MSG_TYPE_FILE_ID = 0
        private const val MSG_TYPE_SESSION = 18
        private const val MSG_TYPE_RECORD = 20
        private const val MSG_TYPE_ACTIVITY = 34
        
        // Field definitions for File ID message
        private const val FIELD_TYPE = 0
        private const val FIELD_MANUFACTURER = 1
        private const val FIELD_PRODUCT = 2
        private const val FIELD_SERIAL_NUMBER = 3
        private const val FIELD_TIME_CREATED = 4
        
        // Field definitions for Session message
        private const val FIELD_SESSION_TIMESTAMP = 253
        private const val FIELD_SESSION_START_TIME = 2
        private const val FIELD_SESSION_TOTAL_ELAPSED_TIME = 7
        private const val FIELD_SESSION_TOTAL_DISTANCE = 9
        private const val FIELD_SESSION_SPORT = 5
        private const val FIELD_SESSION_TOTAL_CALORIES = 11
        private const val FIELD_SESSION_AVG_POWER = 20
        private const val FIELD_SESSION_MAX_POWER = 21
        private const val FIELD_SESSION_TOTAL_STROKES = 34
        
        // Field definitions for Record message
        private const val FIELD_RECORD_TIMESTAMP = 253
        private const val FIELD_RECORD_DISTANCE = 5
        private const val FIELD_RECORD_POWER = 7
        private const val FIELD_RECORD_HEART_RATE = 3
        private const val FIELD_RECORD_CADENCE = 4
        
        // Field definitions for Activity message
        private const val FIELD_ACTIVITY_TIMESTAMP = 253
        private const val FIELD_ACTIVITY_TOTAL_TIMER_TIME = 0
        private const val FIELD_ACTIVITY_NUM_SESSIONS = 1
        private const val FIELD_ACTIVITY_TYPE = 2
        
        // Sport and activity type constants
        private const val SPORT_ROWING = 15
        private const val ACTIVITY_TYPE_ROWING = 15
        
        // FIT timestamp epoch (seconds between 31 Dec 1989 and 1 Jan 1970)
        private const val FIT_EPOCH_OFFSET = 631065600L
    }
    
    fun generateFitFile(session: RowingSession, dataPoints: List<RowingDataPoint>): ByteArray {
        val dataStream = ByteArrayOutputStream()
        val output = DataOutputStream(dataStream)
        
        // Write FIT header (placeholder, will be updated later)
        writeFitHeader(output, 0)
        
        // Write messages
        writeFileIdMessage(output, session)
        writeSessionMessage(output, session)
        writeRecordMessages(output, dataPoints)
        writeActivityMessage(output, session)
        
        val dataBytes = dataStream.toByteArray()
        val dataSize = dataBytes.size - FIT_HEADER_SIZE
        
        // Calculate CRC
        val crc = calculateCRC(dataBytes, FIT_HEADER_SIZE, dataSize)
        
        // Write CRC
        output.writeShort(crc.toInt())
        
        val finalBytes = dataStream.toByteArray()
        
        // Update header with correct data size
        val headerBytes = ByteArray(FIT_HEADER_SIZE)
        val headerBuffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        headerBuffer.put(FIT_HEADER_SIZE.toByte()) // Header size
        headerBuffer.put(FIT_PROTOCOL_VERSION) // Protocol version
        headerBuffer.putShort(FIT_PROFILE_VERSION) // Profile version
        headerBuffer.putInt(dataSize) // Data size
        headerBuffer.put(".FIT".toByteArray()) // Data type
        
        // Calculate header CRC
        val headerCrc = calculateCRC(headerBytes, 0, 12)
        headerBuffer.putShort(headerCrc)
        
        // Combine header and data
        val result = ByteArray(headerBytes.size + finalBytes.size - FIT_HEADER_SIZE)
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.size)
        System.arraycopy(finalBytes, FIT_HEADER_SIZE, result, headerBytes.size, finalBytes.size - FIT_HEADER_SIZE)
        
        return result
    }
    
    private fun writeFitHeader(output: DataOutputStream, dataSize: Int) {
        output.writeByte(FIT_HEADER_SIZE) // Header size
        output.writeByte(FIT_PROTOCOL_VERSION.toInt()) // Protocol version
        output.writeShort(FIT_PROFILE_VERSION.toInt()) // Profile version (little endian)
        output.writeInt(Integer.reverseBytes(dataSize)) // Data size (little endian)
        output.write(".FIT".toByteArray()) // Data type
        output.writeShort(0) // CRC placeholder
    }
    
    private fun writeFileIdMessage(output: DataOutputStream, session: RowingSession) {
        val messageHeader = createMessageHeader(MSG_TYPE_FILE_ID, 5)
        output.write(messageHeader)
        
        // Type (activity file)
        writeField(output, FIELD_TYPE, 4) // Activity file type
        
        // Manufacturer (custom)
        writeField(output, FIELD_MANUFACTURER, 255) // Development manufacturer
        
        // Product
        writeField(output, FIELD_PRODUCT, 1) // Custom product
        
        // Serial number
        writeField(output, FIELD_SERIAL_NUMBER, 12345)
        
        // Time created
        writeField(output, FIELD_TIME_CREATED, dateToFitTimestamp(session.startTime))
    }
    
    private fun writeSessionMessage(output: DataOutputStream, session: RowingSession) {
        val messageHeader = createMessageHeader(MSG_TYPE_SESSION, 9)
        output.write(messageHeader)
        
        // Timestamp
        writeField(output, FIELD_SESSION_TIMESTAMP, dateToFitTimestamp(session.endTime ?: session.startTime))
        
        // Start time
        writeField(output, FIELD_SESSION_START_TIME, dateToFitTimestamp(session.startTime))
        
        // Total elapsed time (in seconds * 1000)
        val duration = if (session.totalDuration > 0) session.totalDuration else {
            // Calculate duration if not stored
            val endTime = session.endTime ?: Date()
            ((endTime.time - session.startTime.time) / 1000).toInt()
        }
        writeField(output, FIELD_SESSION_TOTAL_ELAPSED_TIME, duration * 1000)
        
        // Total distance (in meters * 100)
        writeField(output, FIELD_SESSION_TOTAL_DISTANCE, maxOf(session.totalDistance, 0) * 100)
        
        // Sport
        writeField(output, FIELD_SESSION_SPORT, SPORT_ROWING)
        
        // Total calories
        writeField(output, FIELD_SESSION_TOTAL_CALORIES, maxOf(session.totalCalories, 0))
        
        // Average power
        writeField(output, FIELD_SESSION_AVG_POWER, maxOf(session.averagePower.toInt(), 0))
        
        // Max power
        writeField(output, FIELD_SESSION_MAX_POWER, maxOf(session.maxPower, 0))
        
        // Total strokes
        writeField(output, FIELD_SESSION_TOTAL_STROKES, maxOf(session.totalStrokes, 0))
    }
    
    private fun writeRecordMessages(output: DataOutputStream, dataPoints: List<RowingDataPoint>) {
        dataPoints.forEach { dataPoint ->
            // Only write records that have meaningful data
            if (dataPoint.distance != null || dataPoint.power != null || dataPoint.heartbeat != null) {
                val messageHeader = createMessageHeader(MSG_TYPE_RECORD, 5)
                output.write(messageHeader)
                
                // Timestamp
                writeField(output, FIELD_RECORD_TIMESTAMP, dateToFitTimestamp(dataPoint.timestamp))
                
                // Distance (meters * 100)
                dataPoint.distance?.let { distance ->
                    if (distance > 0) {
                        writeField(output, FIELD_RECORD_DISTANCE, distance * 100)
                    }
                }
                
                // Power
                dataPoint.power?.let { power ->
                    if (power > 0) {
                        writeField(output, FIELD_RECORD_POWER, power)
                    }
                }
                
                // Heart rate
                dataPoint.heartbeat?.let { hr ->
                    if (hr > 0 && hr < 255) { // Valid heart rate range
                        writeField(output, FIELD_RECORD_HEART_RATE, hr)
                    }
                }
                
                // Cadence (stroke rate)
                dataPoint.strokePerMinute?.let { spm ->
                    if (spm > 0 && spm < 255) { // Valid stroke rate range
                        writeField(output, FIELD_RECORD_CADENCE, spm)
                    }
                }
            }
        }
    }
    
    private fun writeActivityMessage(output: DataOutputStream, session: RowingSession) {
        val messageHeader = createMessageHeader(MSG_TYPE_ACTIVITY, 4)
        output.write(messageHeader)
        
        // Timestamp
        writeField(output, FIELD_ACTIVITY_TIMESTAMP, dateToFitTimestamp(session.endTime ?: session.startTime))
        
        // Total timer time (in seconds * 1000)
        writeField(output, FIELD_ACTIVITY_TOTAL_TIMER_TIME, session.totalDuration * 1000)
        
        // Number of sessions
        writeField(output, FIELD_ACTIVITY_NUM_SESSIONS, 1)
        
        // Activity type
        writeField(output, FIELD_ACTIVITY_TYPE, ACTIVITY_TYPE_ROWING)
    }
    
    private fun createMessageHeader(messageType: Int, fieldCount: Int): ByteArray {
        // Simple message header: normal header (1 byte) + message type (2 bytes)
        val header = ByteArray(3)
        header[0] = 0x40.toByte() // Normal header
        header[1] = (messageType and 0xFF).toByte()
        header[2] = ((messageType shr 8) and 0xFF).toByte()
        return header
    }
    
    private fun writeField(output: DataOutputStream, fieldNum: Int, value: Int) {
        // Write field definition: field number (1 byte) + field size (1 byte) + base type (1 byte)
        output.writeByte(fieldNum)
        output.writeByte(4) // 4 bytes for int
        output.writeByte(0x84.toInt()) // uint32 base type
        
        // Write field data (little endian)
        output.writeInt(Integer.reverseBytes(value))
    }
    
    private fun dateToFitTimestamp(date: Date): Int {
        return ((date.time / 1000) - FIT_EPOCH_OFFSET).toInt()
    }
    
    private fun calculateCRC(data: ByteArray, offset: Int, length: Int): Short {
        var crc = 0
        for (i in offset until offset + length) {
            val byte = data[i].toInt() and 0xFF
            for (j in 0 until 8) {
                if ((crc and 0x8000) != 0) {
                    crc = (crc shl 1) xor 0x1021
                } else {
                    crc = crc shl 1
                }
                if ((byte and (0x80 shr j)) != 0) {
                    crc = crc xor 0x1021
                }
            }
        }
        return (crc and 0xFFFF).toShort()
    }
}

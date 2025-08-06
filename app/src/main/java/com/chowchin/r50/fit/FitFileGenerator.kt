package com.chowchin.r50.fit

import com.chowchin.r50.database.RowingDataPoint
import com.chowchin.r50.database.RowingSession
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class FitFileGenerator {
    companion object {
        // FIT protocol constants
        private const val FIT_HEADER_SIZE = 14
        private const val FIT_PROTOCOL_VERSION_MAJOR = 2
        private const val FIT_PROTOCOL_VERSION_MINOR = 0
        private const val FIT_PROFILE_VERSION = 2113

        // Message numbers (from FIT SDK)
        private const val MESG_NUM_FILE_ID = 0
        private const val MESG_NUM_FILE_CREATOR = 49
        private const val MESG_NUM_SESSION = 18
        private const val MESG_NUM_RECORD = 20
        private const val MESG_NUM_ACTIVITY = 34

        // File types
        private const val FILE_ACTIVITY = 4

        // Sport types
        private const val SPORT_ROWING = 15

        // Manufacturer IDs
        private const val GARMIN_MANUFACTURER_ID = 1
        private const val DEVELOPMENT_MANUFACTURER_ID = 255

        // FIT base types
        private const val FIT_UINT8 = 0x02
        private const val FIT_UINT16 = 0x84
        private const val FIT_UINT32 = 0x86
        private const val FIT_STRING = 0x07

        // FIT timestamp epoch (seconds between 31 Dec 1989 and 1 Jan 1970)
        private const val FIT_EPOCH_OFFSET = 631065600L

        // Invalid values
        private const val UINT8_INVALID = 0xFF
        private const val UINT16_INVALID = 0xFFFF
        private const val UINT32_INVALID = 0xFFFFFFFF.toInt()
    }

    fun generateFitFile(
        session: RowingSession,
        dataPoints: List<RowingDataPoint>,
    ): ByteArray {
        val messages = mutableListOf<ByteArray>()

        // Create all messages
        messages.add(createFileIdMessage(session))
        messages.add(createFileCreatorMessage())

        // Create record messages from data points
        dataPoints.forEach { dataPoint ->
            messages.add(createRecordMessage(dataPoint, session.startTime))
        }

        messages.add(createSessionMessage(session, dataPoints))
        messages.add(createActivityMessage(session))

        // Calculate total data size
        val dataSize = messages.sumOf { it.size }

        // Create final file
        val buffer = ByteBuffer.allocate(FIT_HEADER_SIZE + dataSize + 2)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // Write header
        writeHeader(buffer, dataSize)

        // Write all messages
        messages.forEach { message ->
            buffer.put(message)
        }

        // Calculate and write CRC
        val dataBytes = buffer.array()
        val crc = calculateCRC(dataBytes, FIT_HEADER_SIZE, dataSize)
        buffer.putShort(crc)

        return buffer.array()
    }

    private fun writeHeader(
        buffer: ByteBuffer,
        dataSize: Int,
    ) {
        val headerStart = buffer.position()

        buffer.put(FIT_HEADER_SIZE.toByte()) // Header size
        buffer.put((FIT_PROTOCOL_VERSION_MAJOR and 0x0F or (FIT_PROTOCOL_VERSION_MINOR shl 4)).toByte())
        buffer.putShort(FIT_PROFILE_VERSION.toShort())
        buffer.putInt(dataSize)
        buffer.put(".FIT".toByteArray())

        // Calculate header CRC (excluding the CRC field itself)
        val headerBytes = ByteArray(12)
        buffer.position(headerStart)
        buffer.get(headerBytes)
        val headerCrc = calculateCRC(headerBytes, 0, 12)
        buffer.putShort(headerCrc)
    }

    private fun createFileIdMessage(session: RowingSession): ByteArray {
        val buffer = ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN)

        // Definition message
        writeDefinitionMessage(
            buffer,
            MESG_NUM_FILE_ID,
            listOf(
                FieldDef(0, 1, FIT_UINT8), // type
                FieldDef(1, 2, FIT_UINT16), // manufacturer
                FieldDef(2, 2, FIT_UINT16), // product
                FieldDef(3, 4, FIT_UINT32), // serial_number
                FieldDef(4, 4, FIT_UINT32), // time_created
                FieldDef(8, 2, FIT_UINT16), // number (file number)
            ),
        )

        // Data message
        writeDataMessageHeader(buffer, MESG_NUM_FILE_ID)
        buffer.put(FILE_ACTIVITY.toByte()) // type
        buffer.putShort(DEVELOPMENT_MANUFACTURER_ID.toShort()) // manufacturer
        buffer.putShort(1) // product
        buffer.putInt(12345) // serial_number
        buffer.putInt(dateToFitTimestamp(session.startTime)) // time_created
        buffer.putShort(1) // number

        return buffer.array().copyOfRange(0, buffer.position())
    }

    private fun createFileCreatorMessage(): ByteArray {
        val buffer = ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN)

        // Definition message
        writeDefinitionMessage(
            buffer,
            MESG_NUM_FILE_CREATOR,
            listOf(
                FieldDef(0, 2, FIT_UINT16), // software_version
                FieldDef(1, 1, FIT_UINT8), // hardware_version
            ),
        )

        // Data message
        writeDataMessageHeader(buffer, MESG_NUM_FILE_CREATOR)
        buffer.putShort(100) // software_version
        buffer.put(1) // hardware_version

        return buffer.array().copyOfRange(0, buffer.position())
    }

    private fun createRecordMessage(
        dataPoint: RowingDataPoint,
        sessionStart: Date,
    ): ByteArray {
        val buffer = ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN)

        // Definition message (only write once per file, but simplified for this implementation)
        writeDefinitionMessage(
            buffer,
            MESG_NUM_RECORD,
            listOf(
                FieldDef(253, 4, FIT_UINT32), // timestamp
                FieldDef(5, 4, FIT_UINT32), // distance (in cm)
                FieldDef(7, 2, FIT_UINT16), // power (watts)
                FieldDef(3, 1, FIT_UINT8), // heart_rate (bpm)
                FieldDef(4, 1, FIT_UINT8), // cadence (stroke rate)
            ),
        )

        // Data message
        writeDataMessageHeader(buffer, MESG_NUM_RECORD)

        // Calculate timestamp relative to session start
        val timestampOffset = (dataPoint.timestamp.time - sessionStart.time) / 1000
        val timestamp = dateToFitTimestamp(sessionStart) + timestampOffset.toInt()
        buffer.putInt(timestamp)

        // Distance in centimeters
        val distanceCm = (dataPoint.distance ?: 0) * 100
        buffer.putInt(distanceCm)

        // Power in watts
        buffer.putShort((dataPoint.power ?: UINT16_INVALID).toShort())

        // Heart rate
        buffer.put((dataPoint.heartbeat ?: UINT8_INVALID).toByte())

        // Cadence (stroke rate)
        buffer.put((dataPoint.strokePerMinute ?: UINT8_INVALID).toByte())

        return buffer.array().copyOfRange(0, buffer.position())
    }

    private fun createSessionMessage(
        session: RowingSession,
        dataPoints: List<RowingDataPoint>,
    ): ByteArray {
        val buffer = ByteBuffer.allocate(200).order(ByteOrder.LITTLE_ENDIAN)

        // Definition message
        writeDefinitionMessage(
            buffer,
            MESG_NUM_SESSION,
            listOf(
                FieldDef(253, 4, FIT_UINT32), // timestamp
                FieldDef(2, 4, FIT_UINT32), // start_time
                FieldDef(7, 4, FIT_UINT32), // total_elapsed_time (ms)
                FieldDef(9, 4, FIT_UINT32), // total_distance (cm)
                FieldDef(5, 1, FIT_UINT8), // sport
                FieldDef(11, 2, FIT_UINT16), // total_calories
                FieldDef(20, 2, FIT_UINT16), // avg_power
                FieldDef(21, 2, FIT_UINT16), // max_power
                FieldDef(34, 2, FIT_UINT16), // total_strokes
            ),
        )

        // Data message
        writeDataMessageHeader(buffer, MESG_NUM_SESSION)
        buffer.putInt(dateToFitTimestamp(session.endTime ?: session.startTime)) // timestamp
        buffer.putInt(dateToFitTimestamp(session.startTime)) // start_time
        buffer.putInt(session.totalDuration * 1000) // total_elapsed_time
        buffer.putInt(session.totalDistance * 100) // total_distance (cm)
        buffer.put(SPORT_ROWING.toByte()) // sport
        buffer.putShort(session.totalCalories.toShort()) // total_calories
        buffer.putShort(session.averagePower.toInt().toShort()) // avg_power
        buffer.putShort(session.maxPower.toShort()) // max_power
        buffer.putShort(session.totalStrokes.toShort()) // total_strokes

        return buffer.array().copyOfRange(0, buffer.position())
    }

    private fun createActivityMessage(session: RowingSession): ByteArray {
        val buffer = ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN)

        // Definition message
        writeDefinitionMessage(
            buffer,
            MESG_NUM_ACTIVITY,
            listOf(
                FieldDef(253, 4, FIT_UINT32), // timestamp
                FieldDef(0, 4, FIT_UINT32), // total_timer_time (ms)
                FieldDef(1, 2, FIT_UINT16), // num_sessions
                FieldDef(2, 1, FIT_UINT8), // type
                FieldDef(5, 1, FIT_UINT8), // event
            ),
        )

        // Data message
        writeDataMessageHeader(buffer, MESG_NUM_ACTIVITY)
        buffer.putInt(dateToFitTimestamp(session.endTime ?: session.startTime)) // timestamp
        buffer.putInt(session.totalDuration * 1000) // total_timer_time
        buffer.putShort(1) // num_sessions
        buffer.put(0.toByte()) // type (manual)
        buffer.put(26.toByte()) // event (activity)

        return buffer.array().copyOfRange(0, buffer.position())
    }

    private fun writeDefinitionMessage(
        buffer: ByteBuffer,
        messageNumber: Int,
        fields: List<FieldDef>,
    ) {
        buffer.put((0x40 or (messageNumber and 0x0F)).toByte()) // Definition message header
        buffer.put(0.toByte()) // Reserved
        buffer.put(0.toByte()) // Architecture (little endian)
        buffer.putShort((messageNumber and 0xFFFF).toShort()) // Global message number
        buffer.put(fields.size.toByte()) // Number of fields

        fields.forEach { field ->
            buffer.put(field.fieldDefNum.toByte())
            buffer.put(field.size.toByte())
            buffer.put(field.baseType.toByte())
        }
    }

    private fun writeDataMessageHeader(
        buffer: ByteBuffer,
        messageNumber: Int,
    ) {
        buffer.put((messageNumber and 0x0F).toByte()) // Data message header
    }

    private fun dateToFitTimestamp(date: Date): Int = ((date.time / 1000) - FIT_EPOCH_OFFSET).toInt()

    private fun calculateCRC(
        data: ByteArray,
        offset: Int,
        length: Int,
    ): Short {
        val crcTable =
            IntArray(16) {
                val c = it
                var result = 0
                for (j in 0 until 8) {
                    result =
                        if ((result and 1) == 1) {
                            (result ushr 1) xor 0xA001
                        } else {
                            result ushr 1
                        }
                }
                if ((c and 0x01) != 0) result = result xor 0xA001
                if ((c and 0x02) != 0) result = result xor 0xA001
                if ((c and 0x04) != 0) result = result xor 0xA001
                if ((c and 0x08) != 0) result = result xor 0xA001
                result
            }

        var crc = 0
        for (i in offset until offset + length) {
            val tbl_idx = (crc xor (data[i].toInt() and 0xFF)) and 0x0F
            crc = (crc ushr 4) xor crcTable[tbl_idx]
            val tbl_idx2 = (crc xor ((data[i].toInt() and 0xFF) ushr 4)) and 0x0F
            crc = (crc ushr 4) xor crcTable[tbl_idx2]
        }

        return (crc and 0xFFFF).toShort()
    }

    private data class FieldDef(
        val fieldDefNum: Int,
        val size: Int,
        val baseType: Int,
    )
}

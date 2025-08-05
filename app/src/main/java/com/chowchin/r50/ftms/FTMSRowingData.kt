package com.chowchin.r50.ftms

/**
 * Data class representing rowing machine data in FTMS format
 * This structure maps the R50 rowing machine data to FTMS standard fields
 */
data class FTMSRowingData(
    val strokeRate: Int? = null,              // Strokes per minute (SPM)
    val strokeCount: Int? = null,             // Total stroke count
    val totalDistance: Int? = null,           // Total distance in meters
    val instantaneousPace: Int? = null,       // Current pace in seconds per 500m
    val averagePace: Int? = null,             // Average pace in seconds per 500m
    val totalEnergy: Int? = null,             // Total energy in calories
    val energyPerHour: Int? = null,           // Energy expenditure rate per hour
    val energyPerMinute: Int? = null,         // Energy expenditure rate per minute
    val heartRate: Int? = null,               // Heart rate in BPM
    val elapsedTime: Int? = null,             // Elapsed time in seconds
    val resistanceLevel: Int? = null          // Resistance/gear level
) {
    companion object {
        /**
         * Convert R50 RowingData to FTMS format
         */
        fun fromRowingData(rowingData: com.chowchin.r50.RowingData, elapsedTime: Int? = null): FTMSRowingData {
            // Calculate pace from distance and time if available
            val instantaneousPace = if (rowingData.distance != null && rowingData.distance > 0 && elapsedTime != null && elapsedTime > 0) {
                // Calculate pace as seconds per 500m
                val pacePerMeter = elapsedTime.toFloat() / rowingData.distance.toFloat()
                (pacePerMeter * 500).toInt()
            } else null
            
            // Calculate energy per minute from total calories and elapsed time
            val energyPerMinute = if (rowingData.calories != null && elapsedTime != null && elapsedTime > 0) {
                val minutesElapsed = elapsedTime / 60.0
                if (minutesElapsed > 0) {
                    (rowingData.calories / minutesElapsed).toInt()
                } else null
            } else null
            
            // Calculate energy per hour
            val energyPerHour = energyPerMinute?.let { it * 60 }
            
            return FTMSRowingData(
                strokeRate = rowingData.strokePerMinute,
                strokeCount = rowingData.strokeCount,
                totalDistance = rowingData.distance,
                instantaneousPace = instantaneousPace,
                averagePace = instantaneousPace, // Use instantaneous as average for now
                totalEnergy = rowingData.calories,
                energyPerHour = energyPerHour,
                energyPerMinute = energyPerMinute,
                heartRate = rowingData.heartbeat,
                elapsedTime = elapsedTime,
                resistanceLevel = rowingData.gear
            )
        }
    }
}

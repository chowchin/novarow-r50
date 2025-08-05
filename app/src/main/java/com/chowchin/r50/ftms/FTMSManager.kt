package com.chowchin.r50.ftms

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import com.chowchin.r50.RowingData

/**
 * Manager class for FTMS (Fitness Machine Service) functionality
 * Handles the lifecycle and data updates for the FTMS BLE service
 */
class FTMSManager(private val context: Context) {
    
    companion object {
        private const val TAG = "FTMSManager"
    }
    
    private var ftmsService: FTMSService? = null
    private var isServiceRunning = false
    private var lastRowingData: RowingData? = null
    private var sessionStartTime: Long? = null
    
    // Status callbacks
    var onServiceStarted: (() -> Unit)? = null
    var onServiceStopped: (() -> Unit)? = null
    var onDeviceConnected: ((deviceCount: Int) -> Unit)? = null
    
    /**
     * Start the FTMS service
     */
    fun startService(): Boolean {
        if (isServiceRunning) {
            Log.w(TAG, "FTMS service is already running")
            return true
        }
        
        ftmsService = FTMSService(context)
        val success = ftmsService?.startService() ?: false
        
        if (success) {
            isServiceRunning = true
            sessionStartTime = System.currentTimeMillis()
            onServiceStarted?.invoke()
            Log.i(TAG, "FTMS Manager started successfully")
        } else {
            Log.e(TAG, "Failed to start FTMS service")
        }
        
        return success
    }
    
    /**
     * Stop the FTMS service
     */
    fun stopService() {
        ftmsService?.stopService()
        ftmsService = null
        isServiceRunning = false
        sessionStartTime = null
        lastRowingData = null
        onServiceStopped?.invoke()
        Log.i(TAG, "FTMS Manager stopped")
    }
    
    /**
     * Update rowing data and broadcast to connected devices
     */
    fun updateRowingData(rowingData: RowingData) {
        if (!isServiceRunning) {
            Log.w(TAG, "FTMS service not running, cannot update data")
            return
        }
        
        lastRowingData = rowingData
        
        // Calculate elapsed time since session start
        val elapsedTime = sessionStartTime?.let { 
            ((System.currentTimeMillis() - it) / 1000).toInt()
        } ?: rowingData.elapsedSecond
        
        // Convert to FTMS format
        val ftmsData = FTMSRowingData.fromRowingData(rowingData, elapsedTime)
        
        // Update the service
        ftmsService?.updateRowingData(ftmsData)
        
        // Notify about connected devices (for UI updates)
        val deviceCount = ftmsService?.getConnectedDevicesCount() ?: 0
        onDeviceConnected?.invoke(deviceCount)
        
        Log.d(TAG, "Updated FTMS data: SPM=${ftmsData.strokeRate}, Distance=${ftmsData.totalDistance}m, HR=${ftmsData.heartRate}")
    }
    
    /**
     * Check if the FTMS service is currently running
     */
    fun isRunning(): Boolean = isServiceRunning && (ftmsService?.isRunning() == true)
    
    /**
     * Get the number of currently connected devices
     */
    fun getConnectedDevicesCount(): Int = ftmsService?.getConnectedDevicesCount() ?: 0
    
    /**
     * Get current service status for UI display
     */
    fun getServiceStatus(): String {
        return when {
            !isServiceRunning -> "Stopped"
            ftmsService?.isRunning() == true -> {
                val deviceCount = getConnectedDevicesCount()
                if (deviceCount > 0) {
                    "Connected ($deviceCount device${if (deviceCount == 1) "" else "s"})"
                } else {
                    "Advertising"
                }
            }
            else -> "Error"
        }
    }
    
    /**
     * Reset session timing (useful when starting a new rowing session)
     */
    fun resetSession() {
        sessionStartTime = System.currentTimeMillis()
        lastRowingData = null
        Log.i(TAG, "FTMS session reset")
    }
}

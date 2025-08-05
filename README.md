"# R50 Connector - Rowing Machine to Zwift Bridge

An Android application that connects to R50 rowing machines via Bluetooth and save the records.

## Features

- **Bluetooth Connection**: Connects to R50 rowing machines via Bluetooth Low Energy
- **MQTT Publishing**: Optional data publishing to MQTT brokers
- **Real-time Data**: Displays live rowing metrics including:
  - Elapsed time
  - Stroke count and rate (SPM)
  - Distance
  - Power output
  - Calories burned
  - Heart rate
  - Resistance level

## Setup Instructions

### 1. Configure the App

1. **Device MAC Address**: Enter your R50 rowing machine's Bluetooth MAC address, or use the sca n feature to discover nearby devices
2. **MQTT (Optional)**: Configure MQTT broker settings if you want to publish data to external systems

## Permissions

The app requires the following Android permissions:

- `BLUETOOTH` - Basic Bluetooth access
- `BLUETOOTH_ADMIN` - Bluetooth administration
- `BLUETOOTH_CONNECT` - Connect to Bluetooth devices (Android 12+)
- `BLUETOOTH_SCAN` - Scan for Bluetooth devices (Android 12+)
- `BLUETOOTH_ADVERTISE` - Advertise as BLE device (Android 12+)
- `ACCESS_FINE_LOCATION` - Required for Bluetooth scanning
- `INTERNET` - For MQTT connectivity
- `ACCESS_NETWORK_STATE` - Network state monitoring
- `WAKE_LOCK` - Keep device awake during workouts

## Troubleshooting

### Connection Issues

1. Verify the correct MAC address for your rowing machine
2. Ensure the rowing machine is powered on and in pairing mode
3. Check Bluetooth permissions are granted
4. Try clearing the app's Bluetooth cache in Android settings

### MQTT Not Working

1. Verify the MQTT broker URL and credentials
2. Check network connectivity
3. Ensure MQTT is enabled in the app settings

## Development

### Building the Project

1. Open the project in Android Studio
2. Sync Gradle dependencies
3. Build and run on an Android device (API level 24+)

### Key Components

- `MainActivity.kt` - Main application logic and UI
- `RowingData.kt` - Data structure for rowing metrics

### Dependencies

- Jetpack Compose for UI
- Eclipse Paho for MQTT
- Android Bluetooth APIs
- Gson for JSON serialization

## License

This project is open source. Please ensure compliance with any applicable rowing machine manufacturer APIs and protocols." 

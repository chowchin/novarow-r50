"# R50 Connector - Rowing Machine to Zwift Bridge

An Android application that connects to R50 rowing machines via Bluetooth and makes the data available to Zwift through FTMS (Fitness Machine Service) protocol. The app can advertise as either a rowing machine or indoor bike for maximum Zwift compatibility.

## Features

- **Bluetooth Connection**: Connects to R50 rowing machines via Bluetooth Low Energy
- **FTMS Protocol**: Implements BLE GATT server with FTMS service for Zwift compatibility
- **Multi-Device Support**: Can advertise as either a rowing machine or indoor bike
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
2. **FTMS for Zwift**: Enable this to advertise the rowing machine data to Zwift
3. **Machine Type**: Choose between:
   - **Rower**: Advertises as a rowing machine (for Zwift rowing workouts)
   - **Bike**: Advertises as an indoor bike (for Zwift cycling with rowing data converted to bike metrics)
4. **MQTT (Optional)**: Configure MQTT broker settings if you want to publish data to external systems

### 2. Connect to Zwift

#### As a Rowing Machine:
1. Select "Rower" as machine type in the app
2. Connect to your rowing machine in the app
3. Open Zwift and go to device pairing
4. Look for "R50 Rowing Machine" in the rowing machine section
5. Pair the device and start your rowing workout

#### As an Indoor Bike:
1. Select "Bike" as machine type in the app
2. Connect to your rowing machine in the app
3. Open Zwift and go to device pairing
4. Look for "R50 Indoor Bike" in the bike trainer/power meter section
5. Pair the device and start cycling in Zwift with rowing data converted to bike metrics

## Technical Details

### FTMS Implementation

The app implements the Bluetooth Low Energy Fitness Machine Service (FTMS) specification supporting both machine types:

- **Service UUID**: `0x1826` (Fitness Machine Service)
- **Characteristics**:
  - `0x2ACC`: Fitness Machine Feature (supported features)
  - `0x2AD1`: Rower Data (rowing metrics notifications) - when in rower mode
  - `0x2AD2`: Indoor Bike Data (bike metrics notifications) - when in bike mode
  - `0x2AD9`: Fitness Machine Control Point (workout control)
  - `0x2ADA`: Fitness Machine Status (machine status)

### Supported FTMS Features

#### Rower Mode:
- ✅ Stroke rate (SPM)
- ✅ Stroke count
- ✅ Distance measurement
- ✅ Instantaneous power
- ✅ Total energy (calories)
- ✅ Heart rate measurement
- ✅ Elapsed time
- ✅ Fitness machine control (basic commands)

#### Bike Mode:
- ✅ Speed (derived from stroke rate)
- ✅ Cadence (mapped from stroke rate)
- ✅ Distance measurement
- ✅ Instantaneous power
- ✅ Total energy (calories)
- ✅ Heart rate measurement
- ✅ Elapsed time
- ✅ Fitness machine control (basic commands)

### Data Mapping

#### Rower Mode (Direct Mapping)
| R50 Data | FTMS Field | Notes |
|----------|------------|-------|
| Stroke rate | Stroke Rate | SPM ÷ 2 (FTMS uses half-stroke units) |
| Stroke count | Stroke Count | Direct mapping |
| Distance | Distance | Meters |
| Power | Instantaneous Power | Watts |
| Calories | Total Energy | kcal |
| Heart rate | Heart Rate | BPM |
| Elapsed time | Elapsed Time | Seconds |

#### Bike Mode (Converted Mapping)
| R50 Data | FTMS Field | Conversion |
|----------|------------|------------|
| Stroke rate | Speed | SPM × 120 (simulated speed in cm/s) |
| Stroke rate | Cadence | SPM × 2 (doubled for bike cadence) |
| Distance | Distance | Meters (direct) |
| Power | Instantaneous Power | Watts (direct) |
| Calories | Total Energy | kcal (direct) |
| Heart rate | Heart Rate | BPM (direct) |
| Elapsed time | Elapsed Time | Seconds (direct) |

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

### Zwift Cannot Find the Device

1. Ensure FTMS is enabled in the app
2. Check that the correct machine type is selected (bike vs rower)
3. Check that Bluetooth is enabled and permissions are granted
4. Make sure the rowing machine is connected in the app first
5. Try restarting the Zwift pairing process
6. Check Android logs for FTMS-related errors

### Using Bike Mode for Better Zwift Compatibility

If you want to use Zwift's cycling features (routes, group rides, etc.) while rowing:
1. Set machine type to "Bike" in the app
2. Your rowing stroke rate will be converted to bike cadence
3. Power readings will remain accurate
4. Speed will be simulated based on your stroke rate
5. You can participate in Zwift cycling events while rowing

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
- `FTMSServer.kt` - FTMS protocol implementation with dual machine type support
- `RowingData.kt` - Data structure for rowing metrics

### Dependencies

- Jetpack Compose for UI
- Eclipse Paho for MQTT
- Android Bluetooth APIs
- Gson for JSON serialization

## License

This project is open source. Please ensure compliance with any applicable rowing machine manufacturer APIs and protocols." 

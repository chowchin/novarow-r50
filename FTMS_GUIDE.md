# FTMS (Fitness Machine Service) Integration

## Overview

This R50 Connector app now includes FTMS (Fitness Machine Service) support, which allows it to act as a Bluetooth Low Energy rowing machine that can be connected to by fitness applications like Zwift, TrainerRoad, Kinomap, and others.

## What is FTMS?

FTMS is a Bluetooth Low Energy standard that defines how fitness equipment communicates with fitness applications. By implementing FTMS, this app can:

- Broadcast rowing machine data (stroke rate, distance, power, heart rate, etc.) to fitness apps
- Allow the R50 rowing machine to work with apps that support FTMS rowing machines
- Provide a standardized interface for third-party fitness applications

## Features

The FTMS implementation includes:

- **Real-time data broadcasting**: Stroke rate, stroke count, distance, pace, calories, power, and heart rate
- **Multiple device support**: Can connect to multiple fitness apps simultaneously
- **Standard compliance**: Follows the FTMS specification for rowing machine data
- **Automatic session management**: Handles session timing and data aggregation

## How to Use

### 1. Enable FTMS

1. Open the R50 Connector app
2. Tap "FTMS Configuration"
3. Toggle "Enable FTMS Service" to ON
4. Tap "Save"

### 2. Connect to Your R50 Rowing Machine

1. Return to the main screen
2. Enter your R50's Bluetooth MAC address or scan for devices
3. Tap "Connect"
4. The app will start advertising as "R50 FTMS Bridge"

### 3. Connect from Your Fitness App

1. Open your fitness app (Zwift, TrainerRoad, etc.)
2. Look for Bluetooth devices or rowing machines
3. Select "R50 FTMS Bridge" from the list
4. Pair and start your workout

## Supported Applications

The FTMS implementation has been tested with and supports:

- **Zwift**: Use as a rowing machine in Zwift's rowing workouts
- **TrainerRoad**: Connect as a power meter for rowing-specific training
- **Kinomap**: Use for immersive rowing experiences
- **MyWhoosh**: Compatible with rowing workouts
- **Any FTMS-compatible app**: Should work with any app supporting FTMS rowing machines

## Technical Details

### FTMS Service UUID
- Service: `1826` (Fitness Machine Service)
- Rower Data: `2AD1` (Rower Data characteristic)
- Control Point: `2AD9` (Fitness Machine Control Point)
- Status: `2ADA` (Fitness Machine Status)

### Data Fields Broadcast
- Stroke Rate (strokes per minute)
- Stroke Count (total strokes)
- Total Distance (meters)
- Instantaneous Pace (seconds per 500m)
- Total Energy (calories)
- Energy Per Hour/Minute
- Heart Rate (BPM)
- Elapsed Time

### Connection Status

The app shows FTMS status in the data screen:
- **Stopped**: FTMS service is not running
- **Advertising**: Service is running and discoverable
- **Connected (X devices)**: Number of apps connected

## Troubleshooting

### FTMS Not Discoverable
1. Ensure Bluetooth is enabled on both devices
2. Check that FTMS is enabled in the app settings
3. Restart the R50 Connector app
4. Try refreshing the device list in your fitness app

### Data Not Updating
1. Verify the R50 rowing machine is connected and sending data
2. Check that you're actively rowing (some data only updates during activity)
3. Ensure the fitness app is connected to the correct device

### Multiple App Connections
- The FTMS service supports multiple simultaneous connections
- Each connected app will receive the same rowing data
- Performance may vary with multiple connections

### Compatibility Issues
1. Ensure your fitness app supports FTMS rowing machines
2. Some apps may require specific setup for rowing machines
3. Check app documentation for FTMS or Bluetooth rowing machine setup

## Performance Considerations

- Running FTMS simultaneously with MQTT may impact performance on older devices
- The service uses additional battery due to Bluetooth LE advertising
- Multiple app connections will increase CPU and battery usage

## Privacy and Security

- The FTMS service only broadcasts fitness data (no personal information)
- Connections are local Bluetooth LE (no internet required)
- Data is only shared with explicitly connected fitness applications

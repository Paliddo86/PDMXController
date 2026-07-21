# PDMXController - Project Overview

## Description
Android application for DMX lighting control via Art-Net protocol. Connects to ESP32-based P-DMX controller over WiFi to send DMX512 data for professional lighting control.

## Repository
- **URL**: `https://github.com/Paliddo86/PDMXController.git`
- **IDE**: Android Studio (Kotlin + Jetpack Compose)
- **Build System**: Gradle with Kotlin DSL
- **Min SDK**: Android (standard)

## ESP32 Firmware
- **Location**: `/Users/paliddo/Documents/Arduino/p-dmx/src/p-dmx.cpp`
- **Current Version**: 4.4.0
- **WiFi AP**: SSID `P-DMX GG`, password `regialuci2026`
- **Protocol**: Art-Net on UDP port 6454 + custom handshake `P-DMX:START`

## Core Architecture

### Network Layer
```
┌─────────────────┐     UDP:6454     ┌──────────────┐
│  Android App    │ ───────────────→ │  ESP32 P-DMX │
│  (ArtNetService)│ ←─────────────── │  Controller  │
└─────────────────┘                  └──────────────┘
```

### Handshake Protocol
- App → ESP32: `P-DMX:START` (11 bytes ASCII)
- ESP32 → App: `P-DMX:OK|device_name|fw_version|mac|developer`
- After handshake → bidirectional Art-Net DMX streaming

### Packet Format (Art-Net standard)
- Buffer: 531 bytes (18 header + 1 Start Code 0x00 + 512 DMX data)
- OpCode: 0x5000 (ArtDmx)
- Protocol: `Art-Net\0` header

## Technology Stack
- **UI**: Jetpack Compose (Material3)
- **Architecture**: MVVM (AndroidViewModel + StateFlow)
- **Network**: Raw UDP sockets (DatagramSocket)
- **Persistence**: JSON files via internal storage
- **Concurrency**: Kotlin Coroutines + Flow
# PDMXController - Connection Layer

## Overview
The connection layer handles WiFi communication with the ESP32 P-DMX controller via UDP, implementing:
1. Custom handshake protocol for activation
2. Art-Net DMX streaming
3. Keepalive monitoring
4. Connection state machine

## Components

### ConnectionState (ConnectionState.kt)
Sealed class state machine with 8 states:

```
                    ┌─────────┐
                    │  Idle   │
                    └────┬────┘
                         │ connect()
                    ┌────▼────┐
                    │ Scanning│ ← if IP unknown
                    └────┬────┘
                    ┌────▼──────┐
                    │ Connecting│ ← if IP known
                    └────┬──────┘
                    ┌────▼────────┐
                    │ Handshaking │  → "P-DMX:START"
                    └────┬────────┘
                         │ "P-DMX:OK"
                    ┌────▼──────┐
                    │ Connected │  → streaming DMX
                    └────┬──────┘
                         │ timeout
                    ┌────▼────────┐
                    │ Disconnected│
                    └────┬────────┘
                         │ error
                    ┌────▼───┐
                    │  Error │
                    └────────┘
```

### ConnectionManager (ConnectionManager.kt)
- **Scope**: `CoroutineScope(Dispatchers.IO + SupervisorJob())`
- **Discovery**: Broadcast `P-DMX:START` on subnet `192.168.4.x`
- **Handshake**: 5 retries with 3s timeout, response format `P-DMX:OK|dev|fw|mac|devname`
- **Keepalive**: Every 2.5s sends "P-DMX:START", expects response
- **Socket**: `DatagramSocket` with 800ms timeout
- **Thread safety**: Uses `synchronized(opLock)` and `NonCancellable` context

Key functions:
- `connect(targetIp)` - Start connection (with discovery if empty)
- `disconnect()` - Manual disconnect
- `reconnect()` - Reconnect to last known IP

### ArtNetService (ArtNetService.kt)
- **Buffer**: 531 bytes (18 header + 1 Start Code + 512 data)
- **Port**: 6454 (standard Art-Net)
- **ArtDmx**: OpCode 0x5000
- **ArtPoll**: OpCode 0x2000 (for controller discovery)
- **Handshake**: `"P-DMX:START"` (ASCII 11 bytes)

```kotlin
Packet Structure:
[0-7]    "Art-Net\0" header
[8-9]    0x00 0x50 (OpCode ArtDmx LE)
[10-11]  0x00 0x0E (Version)
[12]     0x00 (Sequence)
[13]     0x00 (Physical)
[14-15]  Universe (LE)
[16-17]  Length (BE, 0x0201 = 513)
[18]     0x00 (Start Code)
[19-530] 512 DMX values
Total: 531 bytes
```

### ArtNetForegroundService (ArtNetForegroundService.kt)
Android foreground service that:
- **WakeLock**: `PARTIAL_WAKE_LOCK` to keep CPU active
- **WiFi Binding**: `ConnectivityManager.bindProcessToNetwork()` to force WiFi interface
- **DMX Streaming**: 30fps loop (33ms interval)
- **Grand Master**: Applies master dimmer to all DIMMER channels
- **Monitoring**: Checks controller alive every 2.5s
- **Notification**: "PDMX Regia Attiva" channel

## Connection Flow (Complete)
```
1. App starts → ArtNetForegroundService.onCreate()
2. setupNetworkBinding() → register network callback for WiFi
3. ConnectionManager.connect(targetIp)
4. If IP known: → Connecting(ip) → Handshaking(ip)
   If IP unknown: → Scanning → broadcast "P-DMX:START" → listen
5. Send "P-DMX:START" (3x with 50ms delay)
6. Wait for "P-DMX:OK|..." response (3s timeout)
7. On success: → Connected → startKeepAlive → startNetworkLoop
8. Network loop: DMX streaming at 30fps + monitoring every 2.5s
9. On keepalive fail: → Disconnected → notification to ViewModel
```

## ESP32 Firmware Integration
- AP mode: SSID `P-DMX GG`, password `regialuci2026`
- Default IP: `192.168.4.1`
- Port: 6454 UDP
- Requires explicit wake-up: `"P-DMX:START"` unlocks DMX TX mode
- Responds to ArtPoll/ArtPollReply for discovery
- Keepalive: ~1s timeout, then re-handshake
- Response format (FW 4.4.0):
  ```
  P-DMX:OK|P-DMX GG|4.4.0|F4:2D:C9:87:86:A8|Paliddo ©2026
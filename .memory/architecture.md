# PDMXController - Architecture

## MVVM Layers

```
┌─────────────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose)                          │
│  ┌──────────────────────────────────────────────┐   │
│  │  FaderScreen.kt (Main composable)            │   │
│  │  ├── ColorControlSection (color picker)      │   │
│  │  ├── PositionControlSection (pan/tilt pad)   │   │
│  │  ├── FaderControlSection (channel faders)    │   │
│  │  └── ConnectionPanel (connection UI)         │   │
│  └──────────────────────────────────────────────┘   │
│  FixtureEditorScreen.kt (profile editor)            │
├─────────────────────────────────────────────────────┤
│  ViewModel Layer                                     │
│  ┌──────────────────────────────────────────────┐   │
│  │  MainViewModel.kt                            │   │
│  │  ├── DMX State (512 bytes)                   │   │
│  │  ├── Showfile (current show)                 │   │
│  │  ├── Connection State                        │   │
│  │  ├── Cue Playback (fade engine)              │   │
│  │  └── Library Management                      │   │
│  └──────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────┤
│  Data Layer                                          │
│  ┌──────────────────────────────────────────────┐   │
│  │  Repositories                                │   │
│  │  ├── ShowRepository (show JSON persistence)  │   │
│  │  └── FixtureLibraryRepository (profiles)     │   │
│  ├──────────────────────────────────────────────┤   │
│  │  Network                                     │   │
│  │  ├── ArtNetService (UDP socket)              │   │
│  │  ├── ConnectionManager (state machine)       │   │
│  │  └── ArtNetForegroundService (foreground)    │   │
│  └──────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

## Package Structure
```
com.paliddo.pdmxcontroller
├── data/
│   ├── model/
│   │   ├── Showfile.kt              # Data classes (Scene, Cue, Fixture, etc.)
│   │   └── DefaultFixtureLibrary.kt # Built-in fixture profiles
│   └── repository/
│       ├── ShowRepository.kt        # CRUD showfiles
│       └── FixtureLibraryRepository.kt # User profiles persistence
├── network/
│   ├── ArtNetService.kt             # UDP Art-Net packet sender
│   ├── ArtNetForegroundService.kt   # Android foreground service
│   ├── ConnectionState.kt           # State machine sealed class
│   └── ConnectionManager.kt        # Connection lifecycle manager
├── ui/
│   ├── screens/
│   │   ├── FaderScreen.kt           # Main workspace screen
│   │   └── FixtureEditorScreen.kt   # Profile editor
│   ├── screens/components/
│   │   ├── ConnectionPanel.kt       # Connection UI panel
│   │   ├── ProfileSelectorDialog.kt # Profile picker dialog
│   │   └── ShowManagementDialogs.kt # Show creation dialogs
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   └── viewmodel/
│       └── MainViewModel.kt         # Central ViewModel
└── MainActivity.kt
```

## Data Flow
```
User Action → ViewModel (StateFlow) → UI recomposition
                  ↕
            Repository (JSON files)
                  ||
            ArtNetService (UDP → ESP32)
```

## Key Design Decisions
- **Single ViewModel**: `MainViewModel` handles all state (DMX, showfile, connection, playback)
- **Foreground Service**: `ArtNetForegroundService` keeps DMX streaming alive, binds to WiFi network
- **JSON Persistence**: Showfiles and user profiles stored as JSON in `context.filesDir`
- **Sealed Class State Machine**: `ConnectionState` for connection lifecycle (Idle → Scanning → Handshake → Connected)
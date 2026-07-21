# PDMXController - TODO List

## Completed Features ✅

### Connection Layer
- [x] ConnectionState sealed class (8 states)
- [x] ConnectionManager (discovery, handshake, keepalive)
- [x] ArtNetService (UDP socket, ArtDmx packets)
- [x] ArtNetForegroundService (WiFi binding, streaming loop)
- [x] ConnectionPanel UI (connect/disconnect/scan, IP input, logs)
- [x] Handshake response parsing (deviceName, firmwareVersion, developer)

### DMX Protocol
- [x] Art-Net standard compliance (Start Code 0x00, 531 byte packets)
- [x] DMX streaming at 30fps
- [x] Grand Master dimmer control
- [x] RESET button for all channels
- [x] PRESET/FADER toggle for preset-enabled channels

### Data Model & Persistence
- [x] Showfile data classes (Showfile, Scene, Cue, FixtureInstance, etc.)
- [x] ShowRepository (save/load/export/import JSON)
- [x] FixtureLibraryRepository (serialize/deserialize, export/import URI)
- [x] DefaultFixtureLibrary (5 built-in profiles)
- [x] Export showfile with libraryProfiles section
- [x] Import showfile with libraryProfiles support
- [x] Export/import fixture library (file picker)
- [x] saveCurrentShow() + auto-save onCleared()

### UI
- [x] Main workspace layout (3 columns)
- [x] Master fader + BLACKOUT button
- [x] Group selection grid
- [x] Fixture selection grid
- [x] Color control (ColorWheel + palettes + 8 base colors)
- [x] Pan/Tilt pad
- [x] Fader columns with RESET and PRESET/FADER toggle
- [x] Unifiend CUE panel (record + fade input + cue list)
- [x] Cue playback (GO/STOP, NEXT, SINGLE, AUTOLOOP)
- [x] Fade engine (0s snap or smooth transition)
- [x] Settings panel (Controller, Fixtures, Backup, About)
- [x] PATCH dialog
- [x] Connection indicator always visible
- [x] EDIT/LIVE mode toggle

### Bug Fixes
- [x] animateItemPlacement compilation error
- [x] Coroutine cancellation in ConnectionManager
- [x] Icon status in FaderScreen (connectionState instead of isConnected)
- [x] Fade cue not applying (buffer sync fix)
- [x] Color wheel oversized (reduced to 0.25 weight)
- [x] Slider fade UX (replaced with numeric input)

## Pending Features 🔲

### High Priority
- [ ] **Show import merge**: when importing a show, if a profile with the same ID already exists in the user library, show a diff/merge dialog instead of just saving to the show's customProfiles
- [ ] **Multiple universe support**: current Art-Net implementation only handles universe 0
- [ ] **DMX monitor view**: live readout of all 512 channels (currently only in ESP32 web interface)

### Medium Priority
- [ ] **Fixture profile editor improvements**: 
  - Add channel reordering (drag & drop)
  - Import/export single profile as JSON
  - Preview channel footprint in the editor
- [ ] **Cue list improvements**:
  - Drag to reorder cues in the list
  - Multi-select cues for batch delete
  - Cue timing display (show fade time in the list)
- [ ] **Group improvements**:
  - Show fixture count in group buttons
  - Allow adding fixtures to existing groups
  - Group color coding

### Low Priority
- [ ] **Undo/Redo system**: track showfile modifications for undo support
- [ ] **Showfile templates**: save current state as template for new shows
- [ ] **MIDI/OSC control**: external controller support
- [ ] **Dark/Light theme toggle**
- [ ] **Tablet landscape optimization**: current layout assumes landscape, but could be improved for portrait/split-screen
- [ ] **Backup to cloud**: Google Drive / Dropbox integration for showfiles
- [ ] **Multiple device management**: store configurations for multiple ESP32 controllers

### Known Issues
- [ ] **Color wheel HSV still uses full canvas size** in its Modifier (`.fillMaxSize()`) even though parent Box is constrained — this works but could be optimized
- [ ] **No loading indicator** when loading a showfile from disk (could freeze UI briefly for large shows)
- [ ] **No confirmation dialog** before deleting a cue (immediate deletion with ✕)
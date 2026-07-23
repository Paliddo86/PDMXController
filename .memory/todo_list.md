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
- [x] Grand Master dimmer control (scales DIMMER directly, or COLOR_* if no DIMMER)
- [x] RESET button for all channels
- [x] PRESET/FADER toggle for preset-enabled channels
- [x] BLACKOUT mode: forza tutti i 512 canali a 0 via flag isBlackout

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
- [x] Master fader + BLACKOUT button (indipendente dal master)
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
- [x] Splashscreen animato con icona, titolo e crediti
- [x] Nuova icona app vettoriale (tema viola/ciano/bianco/nero)

### About Screen & Auto-update
- [x] Schermata About completa con icona app, nome, versione, sviluppatore
- [x] Link cliccabile al repository GitHub
- [x] Pulsante "Cerca aggiornamenti" che interroga le GitHub Releases
- [x] Dialog con risultato (aggiornamento disponibile / già all'ultima versione / errore)
- [x] Apertura browser alla pagina di release se aggiornamento disponibile

### GitHub Release Workflow
- [x] Workflow `.github/workflows/release.yml` per build automatica su tag v*
- [x] Supporto keystore opzionale via GitHub Secrets
- [x] Upload APK come asset della release

### Bug Fixes
- [x] animateItemPlacement compilation error
- [x] Coroutine cancellation in ConnectionManager
- [x] Icon status in FaderScreen (connectionState instead of isConnected)
- [x] Fade cue not applying (buffer sync fix)
- [x] Color wheel — gradienti invertiti corretti, fix resize
- [x] Slider fade UX (replaced with numeric input)
- [x] RGB→RGBW: sottrazione bianco da canali R,G,B
- [x] Master Dimmer: scala canali COLOR_* se non c'è DIMMER
- [x] BLACKOUT: invia buffer di zeri a tutti i 512 canali
- [x] Live mode: mostra griglia cue cards + lista cue + GO/STOP
- [x] Altezza campi edit cue: Num, Nome, Fade portati a 64dp

## Pending Features 🔲

### Medium Priority
- [ ] **Show import merge**: when importing a show, if a profile with the same ID already exists in the user library, show a diff/merge dialog
- [ ] **Multiple universe support**: current Art-Net implementation only handles universe 0

### Low Priority
- [ ] **Undo/Redo system**: track showfile modifications for undo support
- [ ] **Showfile templates**: save current state as template for new shows
- [ ] **Dark/Light theme toggle**
- [ ] **Tablet landscape optimization**: improve for portrait/split-screen
- [ ] **Backup to cloud**: Google Drive / Dropbox integration

### Known Issues
- [ ] **No loading indicator** when loading a showfile from disk (could freeze UI briefly)
- [ ] **No confirmation dialog** before deleting a cue (immediate deletion with ✕)
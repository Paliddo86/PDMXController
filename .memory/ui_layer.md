# PDMXController - UI Layer

## Main Workspace: FaderScreen.kt

The main screen is organized in 3 columns:

```
┌────────────┬───────────────────────────────────┬──────────────────┐
│  MASTER    │  CENTRAL WORKSPACE               │  CUE PANEL       │
│  (75dp)    │  (weight 0.6-0.65)               │  (weight 0.35)   │
├────────────┼───────────────────────────────────┤ (toggleable)     │
│  Master    │  Groups row                       │  ─────────────── │
│  Slider    │  Selection grid                   │  Edit CUE form   │
│  (90°)     │  ────────────────────────────     │  Fade input      │
│            │  Controls Area:                   │  ─────────────── │
│  BLACKOUT  │  [COLORS] [POSITION] [FADERS]     │  Scene selector  │
│  Button    │  ┌───────────┬──────────┐         │  Cue List        │
│            │  │ Color Pck │ Info+Pal│         │  NEXT/AUTOLOOP   │
│            │  ├───────────┴──────────┤         │  GO/STOP Button  │
│            │  │ Fader Columns (110dp)|         │                  │
│            │  └──────────────────────┘         │                  │
└────────────┴───────────────────────────────────┴──────────────────┘
```

## Top Bar Buttons (left → right)
- **SHOWFILE: name ▾** - Show selector dropdown
- **🎬 CUE** - Toggle CUE panel (EDIT mode only)
- **💾 SALVA** - Save current show (green)
- **💾 GRUPPO** - Save selected fixtures as group
- **🗑 ELIMINA** - Delete selected group
- **🛠 PATCH** - Open patch dialog
- **⚙️ SETTINGS** - Open settings panel
- **🔒 LIVE / 📝 EDIT** - Toggle live/edit mode

## Connection Indicator
Always visible under top bar, right-aligned, shows:
- 🟢 ARTNET - Connected
- 🟠 SCAN.../CONN.../HANDSHAKE - Busy
- 🔴 OFFLINE/ERRORE/NON TROVATO - Disconnected

## Controls Area (selected fixtures)
Three tabs for fixture control:

### COLOR Tab
- **ColorWheel** (Canvas with HSV, 0.25 weight, 0.7 aspect ratio)
- **Current Color** display + hex input
- **Save Palette** button
- **COLORI BASE** - 8 quick colors (Rosso, Verde, Blu, Giallo, Ciano, Magenta, Bianco, Arancione)
- **PALETTE SALVATE** - User saved palettes (LazyRow)

### POSITION Tab
- Pan/Tilt pad (220dp square, drag gestures)
- Mirino with crosshair lines

### FADERS Tab
- **LazyRow** of channel columns (110dp each)
- Each channel has: name, value, RESET button
- For preset channels: toggle PRESET/FADER + preset buttons or vertical slider
- For raw channels: value + vertical slider + RESET

## Settings Panel
Four tabs:
- **CONTROLLER**: ConnectionPanel + Art-Net config (port, universe, auto-connect)
- **FIXTURES**: Built-in + user profiles list, export/import library, create new profile
- **BACKUP**: Export/import showfile (.json)
- **ABOUT**: Version info

## FixtureEditorScreen.kt
- Create/edit fixture profiles
- Add/remove channels with offset, name, type, presets
- Duplicate offset detection
- Save to user library

## ConnectionPanel.kt
- State indicator card (colored)
- Connect/Disconnect/Scan buttons
- Manual IP input
- Device info card (IP, name, firmware, developer)
- Diagnostic log console

## Dialogs
- **PATCH**: Select profile, name, address, quantity
- **GROUP**: Name input for new group
- **DELETE**: Confirm delete for show/scene/fixture/group
- **ProfileSelector**: Full library browser with recent
- **CreateShow/CopyScene**: Name + source picker
- **Connection Error**: Troubleshooting guide
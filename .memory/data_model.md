# PDMXController - Data Model

## Core Data Classes (Showfile.kt)

### DmxValueRange
```kotlin
data class DmxValueRange(from: Int, to: Int, label: String)
```
Range of DMX values with label for preset macros (e.g., "Open" = 0-127, "Closed" = 128-255).

### ChannelType
```kotlin
enum class ChannelType {
    DIMMER, COLOR_R, COLOR_G, COLOR_B, COLOR_W,
    PAN, TILT, SHUTTER_STROBE, GOBO, PRISM,
    EFFECT_MACRO, OTHER
}
```
Typology of each DMX channel for smart control features.

### ChannelDefinition
```kotlin
data class ChannelDefinition(
    val offset: Int,           // 0-based offset from fixture start address
    val name: String,          // Human-readable name
    val hasPresets: Boolean,   // If true, show preset buttons instead of fader
    val presets: List<DmxValueRange>,  // Preset values for quick control
    val type: ChannelType      // Channel typology
)
```

### FixtureProfile
```kotlin
data class FixtureProfile(
    val id: String,
    val manufacturer: String,
    val modelName: String,
    val channelCount: Int,
    val channels: List<ChannelDefinition>
)
```
Template/definition of a lighting fixture. Built-in profiles in `DefaultFixtureLibrary`, user profiles in `FixtureLibraryRepository`.

### FixtureInstance
```kotlin
data class FixtureInstance(
    val id: String,
    val userGivenName: String,
    val profileId: String,
    val startAddress: Int       // 1-based DMX address
)
```
Actual patched fixture in the show, referencing a profile.

### FixtureGroup
```kotlin
data class FixtureGroup(
    val id: String,
    val name: String,
    val fixtureIds: List<String>
)
```
User-defined group for quick selection of multiple fixtures.

### Cue
```kotlin
data class Cue(
    val number: Float,
    val name: String,
    val dmxValues: List<Int>,   // 512 values (0-255)
    val fadeTimeMs: Long        // Fade duration in milliseconds
)
```
Snapshot of all 512 DMX channels at a given moment.

### Scene
```kotlin
data class Scene(
    val name: String,
    val cueList: List<Cue> = emptyList()
)
```
Named collection of cues (a cue list).

### ColorPalette
```kotlin
data class ColorPalette(
    val id: String, val name: String,
    val hexCode: String,
    val r: Int, val g: Int, val b: Int, val w: Int
)
```

### Showfile
```kotlin
data class Showfile(
    val showName: String,
    val version: Int = 6,
    val fixtureInstances: List<FixtureInstance> = emptyList(),
    val customProfiles: List<FixtureProfile> = emptyList(),
    val fixtureGroups: List<FixtureGroup> = emptyList(),
    val colorPalettes: List<ColorPalette> = emptyList(),
    val scenes: List<Scene> = listOf(Scene("Scena Principale"))
)
```
Root data object for a complete show.

## Default Fixture Library (5 built-in profiles)
| ID | Name | Channels | Type |
|----|------|----------|------|
| std_dimmer | Dimmer 1Ch | 1 | DIMMER |
| rgb_generic | RGB LED 3Ch | 3 | COLOR_R, COLOR_G, COLOR_B |
| rgbw_generic | RGBW LED 4Ch | 4 | COLOR_R, COLOR_G, COLOR_B, COLOR_W |
| pro_spot | Moving Head 6Ch | 6 | DIMMER, PAN, TILT, COLOR_R, COLOR_G, COLOR_B |
| pro_spot_gobo | Moving Head Gobo 8Ch | 8 | DIMMER, PAN, TILT, COLOR_R, COLOR_G, COLOR_B, GOBO, SHUTTER_STROBE |

## NetworkSettings
```kotlin
data class NetworkSettings(
    val ipAddress: String = "192.168.4.1",
    val port: Int = 6454,
    val universe: Int = 0,
    val autoConnect: Boolean = true
)
```

## ControllerInfo
```kotlin
data class ControllerInfo(
    val shortName: String, val longName: String,
    val ipAddress: String, val firmwareVersion: String,
    val status: String
)
```

## JSON Serialization (ShowRepository)
- Showfiles saved as `{showName}.json` in `context.filesDir`
- Serialization: manual JSON via `org.json` (JSONObject, JSONArray)
- Export includes `libraryProfiles` section for profiles used by fixtures
- Version field: `6`

## JSON Serialization (FixtureLibraryRepository)
- User profiles saved in `global_user_profiles.json`
- Same JSON format as showfile profiles section
- Export/import via `ContentResolver` (file picker URIs)
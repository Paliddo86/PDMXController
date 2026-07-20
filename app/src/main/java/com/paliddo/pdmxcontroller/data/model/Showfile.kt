package com.paliddo.pdmxcontroller.data.model

import kotlinx.serialization.Serializable

// Range di valori per canali discreti (es. Preset Ruota Colori)
@Serializable
data class DmxValueRange(
    val from: Int,
    val to: Int,
    val label: String
)

// AGGIORNATA: Aggiungiamo 'type' mantenendo intatti i preset esistenti
@Serializable
data class ChannelDefinition(
    val offset: Int,
    val name: String,
    val hasPresets: Boolean = false,
    val presets: List<DmxValueRange> = emptyList(),
    val type: ChannelType = ChannelType.OTHER
)

@Serializable
enum class ChannelType {
    DIMMER,
    COLOR_R,
    COLOR_G,
    COLOR_B,
    COLOR_W,
    PAN,
    TILT,
    SHUTTER_STROBE,
    GOBO,
    PRISM,
    EFFECT_MACRO,
    OTHER
}

// Profilo/Libreria della Fixture
@Serializable
data class FixtureProfile(
    val id: String,
    val manufacturer: String,
    val modelName: String,
    val channelCount: Int,
    val channels: List<ChannelDefinition>
)

// Assegnazione fisica della Fixture (Patch)
@Serializable
data class FixtureInstance(
    val id: String,
    val userGivenName: String,
    val profileId: String,
    val startAddress: Int
)

// NUOVO: Rappresenta un grupo di selezione rapida delle Fixture
@Serializable
data class FixtureGroup(
    val id: String,
    val name: String,
    val fixtureIds: List<String>
)

// Singola memoria DMX (Cue Point)
@Serializable
data class Cue(
    val number: Float,
    val name: String,
    val dmxValues: List<Int>,
    val fadeTimeMs: Long
)

// Una Cue List indipendente (Scena)
@Serializable
data class Scene(
    val name: String,
    val cueList: List<Cue> = emptyList()
)

@Serializable
data class ColorPalette(
    val id: String,
    val name: String,
    val hexCode: String,
    val r: Int,
    val g: Int,
    val b: Int,
    val w: Int = 0
)

// Showfile generale con supporto a Fixture e Gruppi
@Serializable
data class Showfile(
    val showName: String,
    val version: Int = 6,
    val fixtureInstances: List<FixtureInstance> = emptyList(),
    val customProfiles: List<FixtureProfile> = emptyList(),
    val fixtureGroups: List<FixtureGroup> = emptyList(),
    val colorPalettes: List<ColorPalette> = emptyList(),
    val scenes: List<Scene> = listOf(Scene("Scena Principale"))
)

@Serializable
data class NetworkSettings(
    val ipAddress: String = "192.168.4.1",
    val port: Int = 6454,
    val universe: Int = 0,
    val autoConnect: Boolean = true
)

@Serializable
data class ControllerInfo(
    val shortName: String = "",
    val longName: String = "",
    val ipAddress: String = "",
    val firmwareVersion: String = "",
    val status: String = ""
)

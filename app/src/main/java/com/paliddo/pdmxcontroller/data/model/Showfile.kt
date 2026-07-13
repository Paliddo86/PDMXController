package com.paliddo.pdmxcontroller.data.model

import kotlinx.serialization.Serializable

// Range di valori per canali discreti (es. Preset Ruota Colori)
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
    val type: ChannelType = ChannelType.OTHER // <-- Nuovo campo con valore di default per retrocompatibilità
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
data class FixtureInstance(
    val id: String,
    val userGivenName: String,
    val profileId: String,
    val startAddress: Int
)

// NUOVO: Rappresenta un gruppo di selezione rapida delle Fixture
data class FixtureGroup(
    val id: String,
    val name: String,
    val fixtureIds: List<String> // Elenco degli ID delle fixture appartenenti a questo gruppo
)

// Singola memoria DMX (Cue Point)
data class Cue(
    val number: Float,
    val name: String,
    val dmxValues: List<Int>,
    val fadeTimeMs: Long
)

// Una Cue List indipendente (Scena)
data class Scene(
    val name: String,
    val cueList: List<Cue> = emptyList()
)

// Showfile generale con supporto a Fixture e Gruppi
data class Showfile(
    val showName: String,
    val version: Int = 5,
    val fixtureInstances: List<FixtureInstance> = emptyList(),
    val customProfiles: List<FixtureProfile> = emptyList(),
    val fixtureGroups: List<FixtureGroup> = emptyList(), // NUOVO: Lista dei gruppi salvati
    val scenes: List<Scene> = listOf(Scene("Scena Principale"))
)
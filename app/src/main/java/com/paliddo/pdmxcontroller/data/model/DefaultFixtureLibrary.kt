package com.paliddo.pdmxcontroller.data.repository

import com.paliddo.pdmxcontroller.data.model.ChannelType
import com.paliddo.pdmxcontroller.data.model.ChannelDefinition
import com.paliddo.pdmxcontroller.data.model.DmxValueRange
import com.paliddo.pdmxcontroller.data.model.FixtureProfile

object DefaultFixtureLibrary {
    val profiles = listOf(
        FixtureProfile(
            id = "std_dimmer",
            manufacturer = "Generic",
            modelName = "Dimmer 1Ch",
            channelCount = 1,
            channels = listOf(
                ChannelDefinition(0, "Dimmer", type = ChannelType.DIMMER)
            )
        ),
        FixtureProfile(
            id = "rgb_generic",
            manufacturer = "Generic",
            modelName = "RGB LED 3Ch",
            channelCount = 3,
            channels = listOf(
                ChannelDefinition(0, "Rosso", type = ChannelType.COLOR_R),
                ChannelDefinition(1, "Verde", type = ChannelType.COLOR_G),
                ChannelDefinition(2, "Blu", type = ChannelType.COLOR_B)
            )
        ),
        FixtureProfile(
            id = "rgbw_generic",
            manufacturer = "Generic",
            modelName = "RGBW LED 4Ch",
            channelCount = 4,
            channels = listOf(
                ChannelDefinition(0, "Rosso", type = ChannelType.COLOR_R),
                ChannelDefinition(1, "Verde", type = ChannelType.COLOR_G),
                ChannelDefinition(2, "Blu", type = ChannelType.COLOR_B),
                ChannelDefinition(3, "Bianco", type = ChannelType.COLOR_W)
            )
        ),
        FixtureProfile(
            id = "pro_spot",
            manufacturer = "SpotLight",
            modelName = "Moving Head 6Ch",
            channelCount = 6,
            channels = listOf(
                ChannelDefinition(0, "Dimmer", type = ChannelType.DIMMER),
                ChannelDefinition(1, "Pan", type = ChannelType.PAN),
                ChannelDefinition(2, "Tilt", type = ChannelType.TILT),
                ChannelDefinition(
                    offset = 3,
                    name = "Ruota Colori",
                    hasPresets = true,
                    presets = listOf(
                        DmxValueRange(0, 20, "Bianco"), DmxValueRange(21, 60, "Rosso"),
                        DmxValueRange(61, 100, "Giallo"), DmxValueRange(101, 140, "Blu"),
                        DmxValueRange(141, 180, "Verde"), DmxValueRange(181, 255, "Auto")
                    ),
                    type = ChannelType.OTHER
                ),
                ChannelDefinition(
                    offset = 4,
                    name = "Gobo",
                    hasPresets = true,
                    presets = listOf(
                        DmxValueRange(0, 30, "Open"), DmxValueRange(31, 120, "Gobo 1"),
                        DmxValueRange(121, 255, "Strobo")
                    ),
                    type = ChannelType.GOBO
                ),
                ChannelDefinition(5, "Shutter", type = ChannelType.SHUTTER_STROBE)
            )
        )
    )
}
package com.paliddo.pdmxcontroller.data.repository

import android.content.Context
import android.util.Log
import com.paliddo.pdmxcontroller.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class FixtureLibraryRepository(private val context: Context) {
    private val fileName = "global_user_profiles.json"
    private val file = File(context.filesDir, fileName)

    fun saveUserProfiles(profiles: List<FixtureProfile>): Boolean {
        return try {
            val jsonArray = JSONArray()
            for (profile in profiles) {
                val jsonProfile = JSONObject().apply {
                    put("id", profile.id)
                    put("manufacturer", profile.manufacturer)
                    put("modelName", profile.modelName)
                    put("channelCount", profile.channelCount)

                    val jsonChannels = JSONArray()
                    for (ch in profile.channels) {
                        val jsonCh = JSONObject().apply {
                            put("offset", ch.offset)
                            put("name", ch.name)
                            put("hasPresets", ch.hasPresets)
                            put("type", ch.type.name)

                            val jsonPresets = JSONArray()
                            for (preset in ch.presets) {
                                val jsonPreset = JSONObject().apply {
                                    put("from", preset.from)
                                    put("to", preset.to)
                                    put("label", preset.label)
                                }
                                jsonPresets.put(jsonPreset)
                            }
                            put("presets", jsonPresets)
                        }
                        jsonChannels.put(jsonCh)
                    }
                    put("channels", jsonChannels)
                }
                jsonArray.put(jsonProfile)
            }
            file.writeText(jsonArray.toString(4))
            true
        } catch (e: Exception) {
            Log.e("FixtureLibraryRepo", "Errore salvataggio profili utente", e)
            false
        }
    }

    fun loadUserProfiles(): List<FixtureProfile> {
        if (!file.exists()) return emptyList()
        return try {
            val jsonString = file.readText()
            val jsonArray = JSONArray(jsonString)
            val profiles = mutableListOf<FixtureProfile>()

            for (i in 0 until jsonArray.length()) {
                val pObj = jsonArray.getJSONObject(i)
                val chList = mutableListOf<ChannelDefinition>()
                val jsonChs = pObj.getJSONArray("channels")

                for (j in 0 until jsonChs.length()) {
                    val cObj = jsonChs.getJSONObject(j)
                    val preList = mutableListOf<DmxValueRange>()
                    val jsonPres = cObj.getJSONArray("presets")

                    for (k in 0 until jsonPres.length()) {
                        val rObj = jsonPres.getJSONObject(k)
                        preList.add(DmxValueRange(rObj.getInt("from"), rObj.getInt("to"), rObj.getString("label")))
                    }
                    
                    val typeStr = if (cObj.has("type")) cObj.getString("type") else ChannelType.OTHER.name
                    val type = try { ChannelType.valueOf(typeStr) } catch (e: Exception) { ChannelType.OTHER }
                    
                    chList.add(ChannelDefinition(cObj.getInt("offset"), cObj.getString("name"), cObj.getBoolean("hasPresets"), preList, type))
                }
                profiles.add(FixtureProfile(pObj.getString("id"), pObj.getString("manufacturer"), pObj.getString("modelName"), pObj.getInt("channelCount"), chList))
            }
            profiles
        } catch (e: Exception) {
            Log.e("FixtureLibraryRepo", "Errore caricamento profili utente", e)
            emptyList()
        }
    }
}

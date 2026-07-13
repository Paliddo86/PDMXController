package com.paliddo.pdmxcontroller.data.repository

import android.content.Context
import android.util.Log
import com.paliddo.pdmxcontroller.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ShowRepository(private val context: Context) {

    fun saveShowfile(showfile: Showfile): Boolean {
        return try {
            val jsonObject = JSONObject()
            jsonObject.put("showName", showfile.showName)
            jsonObject.put("version", showfile.version)

            // 1. SALVATAGGIO PROFILI
            val jsonProfilesArray = JSONArray()
            for (profile in showfile.customProfiles) {
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
                jsonProfilesArray.put(jsonProfile)
            }
            jsonObject.put("customProfiles", jsonProfilesArray)

            // 2. SALVATAGGIO PATCH (ISTANZE FIXTURE)
            val jsonInstancesArray = JSONArray()
            for (instance in showfile.fixtureInstances) {
                val jsonInstance = JSONObject().apply {
                    put("id", instance.id)
                    put("userGivenName", instance.userGivenName)
                    put("profileId", instance.profileId)
                    put("startAddress", instance.startAddress)
                }
                jsonInstancesArray.put(jsonInstance)
            }
            jsonObject.put("fixtureInstances", jsonInstancesArray)

            // 3. NUOVO: SALVATAGGIO GRUPPI
            val jsonGroupsArray = JSONArray()
            for (group in showfile.fixtureGroups) {
                val jsonGroup = JSONObject().apply {
                    put("id", group.id)
                    put("name", group.name)
                    val jsonIds = JSONArray()
                    group.fixtureIds.forEach { jsonIds.put(it) }
                    put("fixtureIds", jsonIds)
                }
                jsonGroupsArray.put(jsonGroup)
            }
            jsonObject.put("fixtureGroups", jsonGroupsArray)

            // 4. SALVATAGGIO SCENE E CUE POINT
            val jsonScenesArray = JSONArray()
            for (scene in showfile.scenes) {
                val jsonScene = JSONObject()
                jsonScene.put("sceneName", scene.name)

                val jsonCueList = JSONArray()
                for (cue in scene.cueList) {
                    val jsonCue = JSONObject().apply {
                        put("number", cue.number.toDouble())
                        put("name", cue.name)
                        put("fadeTimeMs", cue.fadeTimeMs)

                        val jsonDmxArray = JSONArray()
                        cue.dmxValues.forEach { jsonDmxArray.put(it) }
                        put("dmxValues", jsonDmxArray)
                    }
                    jsonCueList.put(jsonCue)
                }
                jsonScene.put("cueList", jsonCueList)
                jsonScenesArray.put(jsonScene)
            }
            jsonObject.put("scenes", jsonScenesArray)

            val file = File(context.filesDir, "${showfile.showName}.json")
            file.writeText(jsonObject.toString(4))
            true
        } catch (e: Exception) {
            Log.e("ShowRepository", "Errore salvataggio con gruppi", e)
            false
        }
    }

    fun loadShowfile(showName: String): Showfile? {
        return try {
            val file = File(context.filesDir, "$showName.json")
            if (!file.exists()) return null

            val jsonString = file.readText()
            val jsonObject = JSONObject(jsonString)
            val name = jsonObject.getString("showName")
            val version = jsonObject.getInt("version")

            // 1. CARICAMENTO PROFILI
            val customProfiles = mutableListOf<FixtureProfile>()
            if (jsonObject.has("customProfiles")) {
                val jsonProfiles = jsonObject.getJSONArray("customProfiles")
                for (i in 0 until jsonProfiles.length()) {
                    val pObj = jsonProfiles.getJSONObject(i)
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
                        chList.add(ChannelDefinition(cObj.getInt("offset"), cObj.getString("name"), cObj.getBoolean("hasPresets"), preList))
                    }
                    customProfiles.add(FixtureProfile(pObj.getString("id"), pObj.getString("manufacturer"), pObj.getString("modelName"), pObj.getInt("channelCount"), chList))
                }
            }

            // 2. CARICAMENTO PATCH
            val fixtureInstances = mutableListOf<FixtureInstance>()
            if (jsonObject.has("fixtureInstances")) {
                val jsonInstances = jsonObject.getJSONArray("fixtureInstances")
                for (i in 0 until jsonInstances.length()) {
                    val iObj = jsonInstances.getJSONObject(i)
                    fixtureInstances.add(FixtureInstance(iObj.getString("id"), iObj.getString("userGivenName"), iObj.getString("profileId"), iObj.getInt("startAddress")))
                }
            }

            // 3. NUOVO: CARICAMENTO GRUPPI
            val fixtureGroups = mutableListOf<FixtureGroup>()
            if (jsonObject.has("fixtureGroups")) {
                val jsonGroups = jsonObject.getJSONArray("fixtureGroups")
                for (i in 0 until jsonGroups.length()) {
                    val gObj = jsonGroups.getJSONObject(i)
                    val idList = mutableListOf<String>()
                    val jsonIds = gObj.getJSONArray("fixtureIds")
                    for (j in 0 until jsonIds.length()) {
                        idList.add(jsonIds.getString(j))
                    }
                    fixtureGroups.add(FixtureGroup(gObj.getString("id"), gObj.getString("name"), idList))
                }
            }

            // 4. CARICAMENTO SCENE E CUE
            val jsonScenes = jsonObject.getJSONArray("scenes")
            val sceneList = mutableListOf<Scene>()

            for (i in 0 until jsonScenes.length()) {
                val jsonScene = jsonScenes.getJSONObject(i)
                val sceneName = jsonScene.getString("sceneName")
                val jsonCueList = jsonScene.getJSONArray("cueList")

                val cueList = mutableListOf<Cue>()
                for (j in 0 until jsonCueList.length()) {
                    val jsonCue = jsonCueList.getJSONObject(j)
                    val number = jsonCue.getDouble("number").toFloat()
                    val cueName = jsonCue.getString("name")
                    val fadeTimeMs = jsonCue.getLong("fadeTimeMs")

                    val jsonDmxArray = jsonCue.getJSONArray("dmxValues")
                    val dmxValues = mutableListOf<Int>()
                    for (k in 0 until jsonDmxArray.length()) {
                        dmxValues.add(jsonDmxArray.getInt(k))
                    }
                    cueList.add(Cue(number, cueName, dmxValues, fadeTimeMs))
                }
                sceneList.add(Scene(sceneName, cueList))
            }

            Showfile(showName = name, version = version, fixtureInstances = fixtureInstances, customProfiles = customProfiles, fixtureGroups = fixtureGroups, scenes = sceneList)
        } catch (e: Exception) {
            Log.e("ShowRepository", "Errore caricamento con gruppi", e)
            null
        }
    }

    fun getAvailableShows(): List<String> {
        val dir = context.filesDir
        return dir.listFiles { _, name -> name.endsWith(".json") }
            ?.map { it.name.substringBefore(".json") } ?: emptyList()
    }
}
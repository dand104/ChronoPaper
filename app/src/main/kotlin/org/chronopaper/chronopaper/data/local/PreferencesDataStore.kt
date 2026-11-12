package org.chronopaper.chronopaper.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.chronopaper.chronopaper.domain.model.DynamicHeicPack
import org.chronopaper.chronopaper.domain.model.GeoCoordinate
import org.chronopaper.chronopaper.domain.model.PairedWallpaperSet
import org.chronopaper.chronopaper.domain.model.PhotoCrop
import org.chronopaper.chronopaper.domain.model.SolarFrame
import org.chronopaper.chronopaper.domain.model.UserSettings
import org.chronopaper.chronopaper.domain.model.WallpaperMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PreferencesDataStore(
    context: Context
) {
    private val rawAppContext = context.applicationContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private class StoreState(
        val prefs: SharedPreferences,
        val collectionsFile: File,
        val settings: MutableStateFlow<UserSettings>,
        val pairedSets: MutableStateFlow<List<PairedWallpaperSet>>,
        val heicPacks: MutableStateFlow<List<DynamicHeicPack>>
    )

    private val storeState = ioScope.async {
        val deContext = rawAppContext.createDeviceProtectedStorageContext()
        deContext.moveSharedPreferencesFrom(rawAppContext, PREFS_NAME)
        val prefs = deContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val collectionsFile = File(deContext.filesDir, COLLECTIONS_FILE_NAME)

        val (pairs, packs) = loadCollectionsSnapshot(collectionsFile)
        StoreState(
            prefs = prefs,
            collectionsFile = collectionsFile,
            settings = MutableStateFlow(loadSettings(prefs)),
            pairedSets = MutableStateFlow(pairs),
            heicPacks = MutableStateFlow(packs)
        )
    }

    val settings: Flow<UserSettings> = flow {
        emitAll(storeState.await().settings)
    }

    val pairedSets: Flow<List<PairedWallpaperSet>> = flow {
        emitAll(storeState.await().pairedSets)
    }

    val heicPacks: Flow<List<DynamicHeicPack>> = flow {
        emitAll(storeState.await().heicPacks)
    }

    suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        writeMutex.withLock {
            val state = storeState.await()
            val updated = transform(state.settings.value)
            saveSettings(state.prefs, updated)
            state.settings.value = updated
        }
    }

    suspend fun upsertPairedSet(set: PairedWallpaperSet) {
        writeMutex.withLock {
            val state = storeState.await()
            val current = state.pairedSets.value.toMutableList()
            val index = current.indexOfFirst {
                it.id == set.id
            }
            if (index >= 0) {
                current[index] = set
            } else {
                current.add(set)
            }
            state.pairedSets.value = current
            persistCollectionsToDisk(state.collectionsFile, current, state.heicPacks.value)
        }
    }

    suspend fun removePairedSet(pairId: String) {
        writeMutex.withLock {
            val state = storeState.await()
            val updated = state.pairedSets.value.filterNot {
                it.id == pairId
            }
            state.pairedSets.value = updated
            persistCollectionsToDisk(state.collectionsFile, updated, state.heicPacks.value)
        }
    }

    suspend fun upsertHeicPack(pack: DynamicHeicPack) {
        writeMutex.withLock {
            val state = storeState.await()
            val current = state.heicPacks.value.toMutableList()
            val index = current.indexOfFirst {
                it.id == pack.id
            }
            if (index >= 0) {
                current[index] = pack
            } else {
                current.add(pack)
            }
            state.heicPacks.value = current
            persistCollectionsToDisk(state.collectionsFile, state.pairedSets.value, current)
        }
    }

    suspend fun removeHeicPack(packId: String) {
        writeMutex.withLock {
            val state = storeState.await()
            val updated = state.heicPacks.value.filterNot {
                it.id == packId
            }
            state.heicPacks.value = updated
            persistCollectionsToDisk(state.collectionsFile, state.pairedSets.value, updated)
        }
    }

    private fun loadSettings(prefs: SharedPreferences): UserSettings {
        val mode = WallpaperMode.fromNameOrDefault(prefs.getString(KEY_MODE, null))
        val activePairId = prefs.getString(KEY_ACTIVE_PAIR_ID, null)
        val activeHeicId = prefs.getString(KEY_ACTIVE_HEIC_ID, null)
        val autoRotate = prefs.getBoolean(KEY_AUTO_ROTATE_ENABLED, false)
        val shuffleEnabled = prefs.getBoolean(KEY_SHUFFLE_ENABLED, false)
        val intervalHours = prefs.getInt(KEY_ROTATION_INTERVAL_HOURS, 24).coerceIn(1, 168)

        val lat = Double.fromBits(
            prefs.getLong(KEY_LATITUDE_BITS, UserSettings.DEFAULT_LOCATION.latitude.toRawBits())
        )
        val lon = Double.fromBits(
            prefs.getLong(KEY_LONGITUDE_BITS, UserSettings.DEFAULT_LOCATION.longitude.toRawBits())
        )
        val location = runCatching { GeoCoordinate(lat, lon) }
            .getOrDefault(UserSettings.DEFAULT_LOCATION)

        return UserSettings(
            mode = mode,
            activePairId = activePairId,
            activeHeicPackId = activeHeicId,
            autoRotateEnabled = autoRotate,
            shuffleEnabled = shuffleEnabled,
            rotationIntervalHours = intervalHours,
            location = location
        )
    }

    private suspend fun saveSettings(prefs: SharedPreferences, settings: UserSettings) = withContext(Dispatchers.IO) {
        prefs.edit {
            putString(KEY_MODE, settings.mode.name)
                .putString(KEY_ACTIVE_PAIR_ID, settings.activePairId)
                .putString(KEY_ACTIVE_HEIC_ID, settings.activeHeicPackId)
                .putBoolean(KEY_AUTO_ROTATE_ENABLED, settings.autoRotateEnabled)
                .putBoolean(KEY_SHUFFLE_ENABLED, settings.shuffleEnabled)
                .putInt(KEY_ROTATION_INTERVAL_HOURS, settings.rotationIntervalHours)
                .putLong(KEY_LATITUDE_BITS, settings.location.latitude.toRawBits())
                .putLong(KEY_LONGITUDE_BITS, settings.location.longitude.toRawBits())
        }
    }

    private fun loadCollectionsSnapshot(
        collectionsFile: File
    ): Pair<List<PairedWallpaperSet>, List<DynamicHeicPack>> {
        if (!collectionsFile.exists()) return emptyList<PairedWallpaperSet>() to emptyList()

        return runCatching {
            val root = JSONObject(collectionsFile.readText())
            val pairsArray = root.optJSONArray("pairs") ?: JSONArray()
            val loadedPairs = buildList(pairsArray.length()) {
                for (i in 0..<pairsArray.length()) {
                    val obj = pairsArray.getJSONObject(i)
                    val pathsJson = obj.getJSONArray("imagePaths")
                    val imagePaths = List(pathsJson.length()) { j -> pathsJson.getString(j) }
                    val cropsJson = obj.optJSONArray("imageCrops")
                    val imageCrops = List(imagePaths.size) { j ->
                        val cObj = cropsJson?.optJSONObject(j)
                        if (cObj != null) {
                            PhotoCrop(
                                zoom = cObj.optDouble("zoom", 1.0).toFloat(),
                                panX = cObj.optDouble("panX", 0.0).toFloat(),
                                panY = cObj.optDouble("panY", 0.0).toFloat()
                            )
                        } else {
                            PhotoCrop.DEFAULT
                        }
                    }

                    add(
                        PairedWallpaperSet(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            imagePaths = imagePaths,
                            imageCrops = imageCrops,
                            createdAtEpochMillis = obj.optLong("createdAt", 0L)
                        )
                    )
                }
            }

            val heicArray = root.optJSONArray("heicPacks") ?: JSONArray()

            val loadedPacks = buildList(heicArray.length()) {
                for (i in 0..<heicArray.length()) {
                    val obj = heicArray.getJSONObject(i)
                    val framesJson = obj.optJSONArray("frames") ?: JSONArray()
                    val frames = buildList(framesJson.length()) {
                        for (j in 0..<framesJson.length()) {
                            val f = framesJson.getJSONObject(j)
                            add(
                                SolarFrame(
                                    frameIndex = f.getInt("frameIndex"),
                                    azimuth = f.getDouble("azimuth"),
                                    altitude = f.getDouble("altitude"),
                                    filePath = f.getString("filePath"),
                                    isLightDefault = f.optBoolean("isLightDefault", false),
                                    isDarkDefault = f.optBoolean("isDarkDefault", false)
                                )
                            )
                        }
                    }

                    add(
                        DynamicHeicPack(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            frames = frames,
                            lightDefaultIndex = if (obj.has("lightDefaultIndex")) {
                                obj.getInt("lightDefaultIndex")
                            } else {
                                null
                            },
                            darkDefaultIndex = if (obj.has("darkDefaultIndex")) {
                                obj.getInt("darkDefaultIndex")
                            } else {
                                null
                            },
                            createdAtEpochMillis = obj.optLong("createdAt", 0L)
                        )
                    )
                }
            }

            loadedPairs to loadedPacks
        }.getOrElse { emptyList<PairedWallpaperSet>() to emptyList() }
    }

    private suspend fun persistCollectionsToDisk(
        collectionsFile: File,
        pairs: List<PairedWallpaperSet>,
        packs: List<DynamicHeicPack>
    ) = withContext(Dispatchers.IO) {
        val root = JSONObject()
        val pairsArray = JSONArray()

        for (pair in pairs) {
            val pathsArray = JSONArray()
            pair.imagePaths.forEach { pathsArray.put(it) }
            val cropsArray = JSONArray()
            for (idx in pair.imagePaths.indices) {
                val crop = pair.cropForIndex(idx)
                cropsArray.put(
                    JSONObject()
                        .put("zoom", crop.sanitizedZoom.toDouble())
                        .put("panX", crop.sanitizedPanX.toDouble())
                        .put("panY", crop.sanitizedPanY.toDouble())
                )
            }
            pairsArray.put(
                JSONObject()
                    .put("id", pair.id)
                    .put("title", pair.title)
                    .put("imagePaths", pathsArray)
                    .put("imageCrops", cropsArray)
                    .put("createdAt", pair.createdAtEpochMillis)
            )
        }
        root.put("pairs", pairsArray)

        val heicArray = JSONArray()
        for (pack in packs) {
            val framesArray = JSONArray()
            for (frame in pack.frames) {
                framesArray.put(
                    JSONObject()
                        .put("frameIndex", frame.frameIndex)
                        .put("azimuth", frame.azimuth)
                        .put("altitude", frame.altitude)
                        .put("filePath", frame.filePath)
                        .put("isLightDefault", frame.isLightDefault)
                        .put("isDarkDefault", frame.isDarkDefault)
                )
            }
            val packObj = JSONObject()
                .put("id", pack.id)
                .put("title", pack.title)
                .put("frames", framesArray)
                .put("createdAt", pack.createdAtEpochMillis)
            pack.lightDefaultIndex?.let { packObj.put("lightDefaultIndex", it) }
            pack.darkDefaultIndex?.let { packObj.put("darkDefaultIndex", it) }
            heicArray.put(packObj)
        }
        root.put("heicPacks", heicArray)
        val tmpFile = File(collectionsFile.parentFile, "$COLLECTIONS_FILE_NAME.tmp")
        tmpFile.writeText(root.toString())
        if (!tmpFile.renameTo(collectionsFile)) {
            tmpFile.copyTo(collectionsFile, overwrite = true)
            tmpFile.delete()
        }
    }

    companion object {
        private const val PREFS_NAME = "chronopaper_prefs"
        private const val COLLECTIONS_FILE_NAME = "wallpaper_collections.json"
        private const val KEY_MODE = "wallpaper_mode"
        private const val KEY_ACTIVE_PAIR_ID = "active_pair_id"
        private const val KEY_ACTIVE_HEIC_ID = "active_heic_id"
        private const val KEY_AUTO_ROTATE_ENABLED = "auto_rotate_enabled"
        private const val KEY_SHUFFLE_ENABLED = "shuffle_enabled"
        private const val KEY_ROTATION_INTERVAL_HOURS = "rotation_interval_hours"
        private const val KEY_LATITUDE_BITS = "location_lat_bits"
        private const val KEY_LONGITUDE_BITS = "location_lon_bits"
    }
}
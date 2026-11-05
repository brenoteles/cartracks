package de.dennisguse.opentracks.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import de.dennisguse.opentracks.data.ContentProviderUtils
import de.dennisguse.opentracks.data.FuelDatabaseHelper
import de.dennisguse.opentracks.data.models.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import kotlin.math.abs

object CarTracksBackupHelper {

    data class ImportResult(
        val tripsCount: Int,
        val fuelCount: Int,
        val vehicleName: String?,
        val vehiclePlate: String?,
        val skippedCount: Int = 0
    )

    fun generateBackupJsonObject(context: Context): JSONObject {
        val root = JSONObject()
        root.put("version", 1)
        root.put("appName", "CarTracks")
        root.put("exportDateEpochMs", System.currentTimeMillis())

        // 1. Vehicle Config & Preferences
        val sp = context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE)
        val vehicleObj = JSONObject().apply {
            put("name", sp.getString("vehicle_name", "Toyota Etios"))
            put("plate", sp.getString("vehicle_plate", ""))
            put("tankCapacity", sp.getFloat("tank_capacity_liters", 50f).toDouble())
            put("primaryFuel", sp.getString("primary_fuel_type", "Gasolina Comum"))
            put("odometerBaseKm", sp.getFloat("odometer_base_km", 0f).toDouble())
            put("kmSinceLastFull", sp.getFloat("km_since_last_full", 0f).toDouble())
            put("tankRefTimeMs", sp.getLong("tank_ref_time_ms", 0L))
            de.dennisguse.opentracks.util.FuelFamily.values().forEach { f ->
                if (sp.contains(f.prefKey)) put(f.prefKey, sp.getFloat(f.prefKey, 0f).toDouble())
            }
            put("consumptionUsePump", sp.getBoolean("consumption_use_pump", true))
        }
        root.put("vehicle", vehicleObj)
        root.put("maintenance", de.dennisguse.opentracks.util.MaintenanceStore.toJson(
            de.dennisguse.opentracks.util.MaintenanceStore.load(context)))

        // 2. Custom Locations (Casa & Trabalho)
        val spLoc = context.getSharedPreferences("CarTracksLocations", Context.MODE_PRIVATE)
        val locObj = JSONObject().apply {
            val homeObj = JSONObject().apply {
                put("lat", spLoc.getString("home_lat", "-23.6015")?.toDoubleOrNull() ?: -23.6015)
                put("lon", spLoc.getString("home_lon", "-46.6900")?.toDoubleOrNull() ?: -46.6900)
                put("label", spLoc.getString("home_label", "Casa") ?: "Casa")
                put("isSet", spLoc.contains("home_lat"))
            }
            put("home", homeObj)

            val workObj = JSONObject().apply {
                put("lat", spLoc.getString("work_lat", "-23.5615")?.toDoubleOrNull() ?: -23.5615)
                put("lon", spLoc.getString("work_lon", "-46.6559")?.toDoubleOrNull() ?: -46.6559)
                put("label", spLoc.getString("work_label", "Trabalho") ?: "Trabalho")
                put("isSet", spLoc.contains("work_lat"))
            }
            put("work", workObj)
        }
        root.put("locations", locObj)

        // 3. Fuel Logs
        val fuelDb = FuelDatabaseHelper.getInstance(context)
        val fuelArray = JSONArray()
        fuelDb.allEntries.forEach { f ->
            val fObj = JSONObject().apply {
                put("stationName", f.stationName)
                put("liters", f.liters)
                put("pricePerLiter", f.pricePerLiter)
                put("totalCost", f.totalCost)
                put("odometerKm", f.odometerKm)
                put("dateTime", f.dateTime)
                put("notes", f.notes)
                put("isFullTank", f.isFullTank)
                put("fuelType", f.fuelType)
                if (f.latitude != null) put("latitude", f.latitude)
                if (f.longitude != null) put("longitude", f.longitude)
            }
            fuelArray.put(fObj)
        }
        root.put("fuelEntries", fuelArray)

        // 4. Trips with full GPS TrackPoints
        val provider = ContentProviderUtils(context)
        val tripsArray = JSONArray()

        provider.searchTracks(null)?.use { iterator ->
            while (iterator.hasNext()) {
                val item = iterator.next()
                val track = provider.getTrack(item.id())

                val tObj = JSONObject().apply {
                    put("id", item.id().id())
                    put("name", item.name() ?: "Viagem")
                    put("description", item.description() ?: "")
                    put("activityType", item.activityTypeLocalized() ?: "Condução")
                    put("startTimeEpochMs", item.startTime().toEpochMilli())
                    put("totalDistanceMeters", item.totalDistance().toM())
                    put("totalDurationSeconds", item.totalTime().seconds)

                    val stats = track?.statistics()
                    if (stats != null) {
                        put("stopTimeEpochMs", stats.stopTime().toEpochMilli())
                        if (stats.maxSpeed() != null) {
                            put("maxSpeedMps", stats.maxSpeed().toMPS())
                        }
                    }

                    // Collect TrackPoints
                    val ptsArray = JSONArray()
                    try {
                        provider.getTrackPointIterator(item.id(), null)?.use { ptIter ->
                            while (ptIter.hasNext()) {
                                val pt = ptIter.next()
                                val pos = pt?.position()
                                if (pos != null && pos.hasLocation()) {
                                    val pObj = JSONObject().apply {
                                        put("lat", pos.latitude() ?: 0.0)
                                        put("lon", pos.longitude() ?: 0.0)
                                        if (pos.hasAltitude() && pos.altitude() != null) {
                                            put("alt", pos.altitude()!!.toM())
                                        }
                                        if (pos.hasSpeed() && pos.speed() != null) {
                                            put("spd", pos.speed()!!.toMPS())
                                        }
                                        if (pos.time() != null) {
                                            put("timeMs", pos.time()!!.toEpochMilli())
                                        }
                                    }
                                    ptsArray.put(pObj)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    put("points", ptsArray)
                }
                tripsArray.put(tObj)
            }
        }
        root.put("trips", tripsArray)
        return root
    }

    /**
     * Automatic background backup called when a trip completes.
     * Retains the latest backup and keeps up to 5 historical rotating backups.
     */
    fun saveAutoBackup(context: Context): File? {
        val sp = context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE)
        val isAutoEnabled = sp.getBoolean("auto_backup_enabled", true)
        if (!isAutoEnabled) return null

        return try {
            val root = generateBackupJsonObject(context)
            val jsonStr = root.toString(2)

            // 1. Internal latest
            val internalDir = File(context.filesDir, "backups").apply { mkdirs() }
            val latestFile = File(internalDir, "auto_backup_latest.json")
            latestFile.writeText(jsonStr, Charsets.UTF_8)

            // 2. External app storage (accessible to user)
            val externalDir = context.getExternalFilesDir("backups")?.apply { mkdirs() }
            if (externalDir != null) {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val backupFile = File(externalDir, "CarTracks_AutoBackup_$timeStamp.json")
                backupFile.writeText(jsonStr, Charsets.UTF_8)

                // Clean up older than last 5
                val files = externalDir.listFiles { _, name -> name.startsWith("CarTracks_AutoBackup_") && name.endsWith(".json") }
                if (files != null && files.size > 5) {
                    files.sortBy { it.lastModified() }
                    for (i in 0 until (files.size - 5)) {
                        files[i].delete()
                    }
                }
                backupFile
            } else {
                latestFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportBackup(context: Context): Boolean {
        try {
            val root = generateBackupJsonObject(context)

            // Write to File in Cache/exports
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportFile = File(exportDir, "CarTracks_Backup_$timeStamp.json")
            exportFile.writeText(root.toString(2), Charsets.UTF_8)

            // Share via Android Sharesheet
            val fileUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "Backup CarTracks $timeStamp")
                putExtra(Intent.EXTRA_TEXT, "Arquivo de backup do CarTracks contendo viagens e abastecimentos.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Salvar ou Compartilhar Backup JSON"))
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erro ao exportar backup: ${e.message}", Toast.LENGTH_LONG).show()
            return false
        }
    }

    /** Writes the full JSON backup to a document the user picked (e.g. a Google Drive folder). */
    fun writeBackupTo(context: Context, uri: Uri) {
        val json = generateBackupJsonObject(context).toString()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("Não foi possível gravar no destino escolhido.")
        context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE).edit()
            .putLong("last_drive_backup_ms", System.currentTimeMillis()).apply()
    }

    fun importBackup(context: Context, uri: Uri): ImportResult {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Não foi possível abrir o arquivo selecionado.")

        val jsonStr = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(jsonStr)

        var vehicleName: String? = null
        var vehiclePlate: String? = null

        // 1. Restore Vehicle Info
        if (root.has("vehicle")) {
            val vObj = root.getJSONObject("vehicle")
            vehicleName = vObj.optString("name", "Toyota Etios")
            vehiclePlate = vObj.optString("plate", "")
            val tankCap = vObj.optDouble("tankCapacity", 50.0)
            val primaryFuel = vObj.optString("primaryFuel", "Gasolina Comum")

            val edit = context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE).edit()
                .putString("vehicle_name", vehicleName)
                .putString("vehicle_plate", vehiclePlate)
                .putFloat("tank_capacity_liters", tankCap.toFloat())
                .putString("primary_fuel_type", primaryFuel)
            if (vObj.has("odometerBaseKm")) edit.putFloat("odometer_base_km", vObj.getDouble("odometerBaseKm").toFloat())
            if (vObj.has("kmSinceLastFull")) edit.putFloat("km_since_last_full", vObj.getDouble("kmSinceLastFull").toFloat())
            if (vObj.has("tankRefTimeMs")) edit.putLong("tank_ref_time_ms", vObj.getLong("tankRefTimeMs"))
            de.dennisguse.opentracks.util.FuelFamily.values().forEach { f ->
                if (vObj.has(f.prefKey)) edit.putFloat(f.prefKey, vObj.getDouble(f.prefKey).toFloat())
            }
            if (vObj.has("consumptionUsePump")) edit.putBoolean("consumption_use_pump", vObj.getBoolean("consumptionUsePump"))
            edit.apply()
        }
        if (root.has("maintenance")) {
            de.dennisguse.opentracks.util.MaintenanceStore.save(context,
                de.dennisguse.opentracks.util.MaintenanceStore.fromJson(root.getJSONArray("maintenance")))
        }

        // 2. Restore Locations
        if (root.has("locations")) {
            val lObj = root.getJSONObject("locations")
            val spLoc = context.getSharedPreferences("CarTracksLocations", Context.MODE_PRIVATE).edit()
            if (lObj.has("home")) {
                val h = lObj.getJSONObject("home")
                spLoc.putString("home_lat", h.optDouble("lat", -23.6015).toString())
                spLoc.putString("home_lon", h.optDouble("lon", -46.6900).toString())
                spLoc.putString("home_label", h.optString("label", "Casa"))
            }
            if (lObj.has("work")) {
                val w = lObj.getJSONObject("work")
                spLoc.putString("work_lat", w.optDouble("lat", -23.5615).toString())
                spLoc.putString("work_lon", w.optDouble("lon", -46.6559).toString())
                spLoc.putString("work_label", w.optString("label", "Trabalho"))
            }
            spLoc.apply()
        }

        // 3. Restore Fuel Entries
        var fuelCount = 0
        if (root.has("fuelEntries")) {
            val fuelDb = FuelDatabaseHelper.getInstance(context)
            val existing: Set<Long> = fuelDb.allEntries.map { it.dateTime }.toSet()
            val fArray = root.getJSONArray("fuelEntries")

            for (i in 0 until fArray.length()) {
                val fObj = fArray.getJSONObject(i)
                val dateTime = fObj.optLong("dateTime", 0L)
                if (dateTime == 0L || !existing.contains(dateTime)) {
                    val station = fObj.optString("stationName", "Posto")
                    val liters = fObj.optDouble("liters", 40.0)
                    val price = fObj.optDouble("pricePerLiter", 5.89)
                    val total = fObj.optDouble("totalCost", liters * price)
                    val odo = fObj.optDouble("odometerKm", 0.0)
                    val notes = fObj.optString("notes", "")
                    val isFull = fObj.optBoolean("isFullTank", true)
                    val fuelType = fObj.optString("fuelType", "Gasolina Comum")
                    val lat = if (fObj.has("latitude")) fObj.getDouble("latitude") else null
                    val lon = if (fObj.has("longitude")) fObj.getDouble("longitude") else null

                    fuelDb.addEntry(station, liters, price, total, odo, notes, isFull, fuelType, lat, lon)
                    fuelCount++
                }
            }
        }

        // 4. Restore Trips & TrackPoints with Smart Deduplication
        var tripsCount = 0
        var skippedCount = 0
        if (root.has("trips")) {
            val provider = ContentProviderUtils(context)
            val tArray = root.getJSONArray("trips")

            // Index existing trips by start time and distance
            val existingTrips = mutableListOf<Pair<Long, Double>>() // startMs to distM
            provider.searchTracks(null)?.use { iter ->
                while (iter.hasNext()) {
                    val itm = iter.next()
                    existingTrips.add(Pair(itm.startTime().toEpochMilli(), itm.totalDistance().toM()))
                }
            }

            for (i in 0 until tArray.length()) {
                val tObj = tArray.getJSONObject(i)
                val name = tObj.optString("name", "Viagem Importada")
                val desc = tObj.optString("description", "")
                val cat = tObj.optString("activityType", "Condução")
                val distM = tObj.optDouble("totalDistanceMeters", 10000.0)
                val durSec = tObj.optLong("totalDurationSeconds", 900)
                val startMs = tObj.optLong("startTimeEpochMs", System.currentTimeMillis())
                val maxSpd = tObj.optDouble("maxSpeedMps", 20.0)

                // DEDUPLICATION CHECK:
                // If a trip already exists with same startTime (within 3s) or same distance & close time, skip
                val isDuplicate = existingTrips.any { (exTime, exDist) ->
                    abs(exTime - startMs) < 3000L || (abs(exTime - startMs) < 60000L && abs(exDist - distM) < 50.0)
                }

                if (isDuplicate) {
                    skippedCount++
                    continue
                }

                val startInstant = Instant.ofEpochMilli(startMs)
                val stopInstant = startInstant.plusSeconds(durSec)

                val stats = Statistics(
                    startInstant,
                    stopInstant,
                    Duration.ofSeconds(durSec),
                    Duration.ofSeconds(durSec),
                    Distance.of(distM),
                    Speed.of(maxSpd),
                    null,
                    null,
                    null,
                    null
                )

                val track = Track(
                    Track.Id(startMs),
                    UUID.randomUUID(),
                    name,
                    desc,
                    cat,
                    ActivityType.DRIVING,
                    ZoneOffset.ofHours(-3),
                    stats
                )

                val newTrackId = provider.insertTrack(track)
                tripsCount++
                existingTrips.add(Pair(startMs, distM))

                // Restore Points
                if (tObj.has("points")) {
                    val ptsArray = tObj.getJSONArray("points")
                    val trackPointsList = mutableListOf<TrackPoint>()

                    for (p in 0 until ptsArray.length()) {
                        val pObj = ptsArray.getJSONObject(p)
                        val lat = pObj.getDouble("lat")
                        val lon = pObj.getDouble("lon")
                        val alt = pObj.optDouble("alt", 760.0)
                        val spd = pObj.optDouble("spd", 10.0)
                        val ptTime = Instant.ofEpochMilli(pObj.optLong("timeMs", startMs))

                        val type = when (p) {
                            0 -> TrackPoint.Type.SEGMENT_START_MANUAL
                            ptsArray.length() - 1 -> TrackPoint.Type.SEGMENT_END_MANUAL
                            else -> TrackPoint.Type.TRACKPOINT
                        }

                        val pos = Position(
                            ptTime,
                            lat,
                            lon,
                            Distance.of(3.0),
                            Altitude.WGS84.of(alt),
                            null,
                            0.0f,
                            Speed.of(spd)
                        )
                        trackPointsList.add(TrackPoint(type, pos))
                    }

                    if (trackPointsList.isNotEmpty() && newTrackId != null) {
                        provider.bulkInsertTrackPoint(trackPointsList, newTrackId)
                    }
                }
            }
        }

        return ImportResult(tripsCount, fuelCount, vehicleName, vehiclePlate, skippedCount)
    }
}
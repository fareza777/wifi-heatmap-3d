package com.sinyal.app.data

import android.content.Context
import com.sinyal.app.ar.FloorBounds
import com.sinyal.app.ar.GridCell
import com.sinyal.app.ar.RoomModel
import com.sinyal.app.ar.SampleGrid
import com.sinyal.app.ar.PathPoint
import com.sinyal.app.ar.WalkPath
import com.sinyal.app.ar.WallSegment
import com.sinyal.app.core.GeoAnchor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.sinyal.app.ar.ApReading
import com.sinyal.app.ar.ApSurvey
import com.sinyal.app.ar.RoamEvent
import com.sinyal.app.ar.SpeedReading
import com.sinyal.app.ar.SpeedSurvey

/** Enough of a scan to list it without parsing the whole measurement set. */
data class ScanSummary(
    val id: String,
    val savedAtMs: Long,
    val ssid: String?,
    val areaSqM: Float,
    val cellCount: Int,
    val weakestDbm: Int?,
)

/**
 * Scans persisted as one JSON file each, under the app's private storage.
 *
 * Hand-rolled JSON via [org.json] rather than a database or a serialization
 * plugin: the shape is a few dozen numbers per scan, entirely owned by this app,
 * and never queried by anything but "list them, load one".
 */
class ScanRepository(context: Context) {

    private val directory = File(context.applicationContext.filesDir, DIR_NAME)

    fun save(scan: CompletedScan) {
        if (!directory.exists()) directory.mkdirs()
        runCatching {
            File(directory, "${scan.id}.json").writeText(scan.toJson().toString())
            pruneBeyondLimit()
        }
    }

    /** Newest first. Unreadable files are skipped rather than failing the list. */
    fun list(): List<ScanSummary> = scanFiles()
        .mapNotNull { file -> runCatching { summaryOf(JSONObject(file.readText())) }.getOrNull() }
        .sortedByDescending { it.savedAtMs }

    fun load(id: String): CompletedScan? = runCatching {
        val file = File(directory, "$id.json")
        if (!file.exists()) null else scanFrom(JSONObject(file.readText()))
    }.getOrNull()

    fun delete(id: String) {
        runCatching { File(directory, "$id.json").delete() }
    }

    private fun scanFiles(): List<File> =
        directory.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.toList().orEmpty()

    /** Keeps storage bounded; the oldest scans go first. */
    private fun pruneBeyondLimit() {
        val files = scanFiles().sortedByDescending { it.lastModified() }
        files.drop(MAX_SCANS).forEach { it.delete() }
    }

    // ---- serialisation ----

    private fun CompletedScan.photosJson(): JSONArray {
        val array = JSONArray()
        photos.forEach { photo ->
            array.put(
                JSONObject()
                    .put("id", photo.id)
                    .put("label", photo.label)
                    .put("x", photo.x.toDouble())
                    .put("y", photo.y.toDouble())
                    .put("z", photo.z.toDouble())
                    .put("file", photo.fileName)
                    .put("at", photo.capturedAtMs),
            )
        }
        return array
    }

    private fun CompletedScan.toJson(): JSONObject {
        val cells = JSONArray()
        grid.occupiedCells.forEach { cell ->
            cells.put(
                JSONObject()
                    .put("ix", cell.ix).put("iy", cell.iy).put("iz", cell.iz)
                    .put("x", cell.x.toDouble()).put("y", cell.y.toDouble())
                    .put("z", cell.z.toDouble())
                    .put("rssi", cell.rssiDbm).put("n", cell.sampleCount),
            )
        }

        val walls = JSONArray()
        room.walls.forEach { wall ->
            walls.put(
                JSONObject()
                    .put("ax", wall.ax.toDouble()).put("az", wall.az.toDouble())
                    .put("bx", wall.bx.toDouble()).put("bz", wall.bz.toDouble()),
            )
        }

        val pathJson = JSONArray()
        path.points.forEach { point ->
            val entry = JSONObject()
                .put("x", point.x.toDouble())
                .put("z", point.z.toDouble())
            // Only written when true, so the common case stays compact and files
            // from before the field still read back correctly.
            if (point.startsRun) entry.put("startsRun", true)
            pathJson.put(entry)
        }

        val apJson = JSONArray()
        apSurvey.readings.forEach { r ->
            apJson.put(
                JSONObject()
                    .put("b", r.bssid).put("s", r.ssid)
                    .put("x", r.x.toDouble()).put("z", r.z.toDouble())
                    .put("r", r.rssiDbm).put("t", r.atMs),
            )
        }
        val roamJson = JSONArray()
        apSurvey.roams.forEach { r ->
            roamJson.put(
                JSONObject()
                    .put("t", r.atMs)
                    .put("from", r.fromBssid ?: JSONObject.NULL)
                    .put("to", r.toBssid)
                    .put("leave", r.leavingDbm ?: JSONObject.NULL)
                    .put("join", r.joiningDbm),
            )
        }

        val speedJson = JSONArray()
        speedSurvey.readings.forEach { r ->
            speedJson.put(
                JSONObject()
                    .put("x", r.x.toDouble()).put("z", r.z.toDouble())
                    .put("m", r.mbps).put("t", r.atMs),
            )
        }

        return JSONObject()
            .put("id", id)
            .put("speeds", speedJson)
            .put("aps", apJson)
            .put("roams", roamJson)
            .put("path", pathJson)
            .put("azimuth", startAzimuthDegrees?.toDouble() ?: JSONObject.NULL)
            .put("lat", geoAnchor?.latitude ?: JSONObject.NULL)
            .put("lon", geoAnchor?.longitude ?: JSONObject.NULL)
            .put("geoAccuracy", geoAnchor?.accuracyMeters?.toDouble() ?: JSONObject.NULL)
            .put("savedAt", savedAtMs)
            .put("ssid", ssid ?: JSONObject.NULL)
            .put("durationMs", durationMs)
            .put("cellSize", grid.cellSizeMeters.toDouble())
            .put("floorY", room.floorY.toDouble())
            .put("wallHeight", room.wallHeight.toDouble())
            .put("wallsEstimated", room.wallsAreEstimated)
            .put("minX", room.bounds.minX.toDouble())
            .put("minZ", room.bounds.minZ.toDouble())
            .put("maxX", room.bounds.maxX.toDouble())
            .put("maxZ", room.bounds.maxZ.toDouble())
            .put("cells", cells)
            .put("walls", walls)
            .put("photos", photosJson())
    }

    private fun scanFrom(json: JSONObject): CompletedScan {
        val cellsJson = json.getJSONArray("cells")
        val cells = (0 until cellsJson.length()).map { index ->
            val c = cellsJson.getJSONObject(index)
            GridCell(
                ix = c.getInt("ix"), iy = c.getInt("iy"), iz = c.getInt("iz"),
                x = c.getDouble("x").toFloat(),
                y = c.getDouble("y").toFloat(),
                z = c.getDouble("z").toFloat(),
                rssiDbm = c.getInt("rssi"),
                sampleCount = c.getInt("n"),
            )
        }

        val wallsJson = json.getJSONArray("walls")
        val walls = (0 until wallsJson.length()).map { index ->
            val w = wallsJson.getJSONObject(index)
            WallSegment(
                ax = w.getDouble("ax").toFloat(),
                az = w.getDouble("az").toFloat(),
                bx = w.getDouble("bx").toFloat(),
                bz = w.getDouble("bz").toFloat(),
            )
        }

        // Older files predate the path and anchor fields; absence is normal.
        val pathJson = json.optJSONArray("path")
        val pathPoints = (0 until (pathJson?.length() ?: 0)).map { index ->
            val point = pathJson!!.getJSONObject(index)
            PathPoint(
                x = point.getDouble("x").toFloat(),
                z = point.getDouble("z").toFloat(),
                startsRun = point.optBoolean("startsRun", false),
            )
        }
        // Absent in files written before the multi-AP survey existed.
        val apJson = json.optJSONArray("aps")
        val apReadings = (0 until (apJson?.length() ?: 0)).map { i ->
            val r = apJson!!.getJSONObject(i)
            ApReading(
                bssid = r.getString("b"),
                ssid = r.optString("s"),
                x = r.getDouble("x").toFloat(),
                z = r.getDouble("z").toFloat(),
                rssiDbm = r.getInt("r"),
                atMs = r.optLong("t"),
            )
        }
        val roamJson = json.optJSONArray("roams")
        val roamEvents = (0 until (roamJson?.length() ?: 0)).map { i ->
            val r = roamJson!!.getJSONObject(i)
            RoamEvent(
                atMs = r.optLong("t"),
                fromBssid = if (r.isNull("from")) null else r.getString("from"),
                toBssid = r.getString("to"),
                leavingDbm = if (r.isNull("leave")) null else r.getInt("leave"),
                joiningDbm = r.getInt("join"),
            )
        }

        val speedJson = json.optJSONArray("speeds")
        val speedReadings = (0 until (speedJson?.length() ?: 0)).map { i ->
            val r = speedJson!!.getJSONObject(i)
            SpeedReading(
                x = r.getDouble("x").toFloat(),
                z = r.getDouble("z").toFloat(),
                mbps = r.getDouble("m"),
                atMs = r.optLong("t"),
            )
        }

        val anchor = if (json.isNull("lat")) null else GeoAnchor(
            latitude = json.getDouble("lat"),
            longitude = json.getDouble("lon"),
            accuracyMeters = json.optDouble("geoAccuracy", 0.0).toFloat(),
        )

        return CompletedScan(
            id = json.getString("id"),
            savedAtMs = json.getLong("savedAt"),
            room = RoomModel(
                bounds = FloorBounds(
                    minX = json.getDouble("minX").toFloat(),
                    minZ = json.getDouble("minZ").toFloat(),
                    maxX = json.getDouble("maxX").toFloat(),
                    maxZ = json.getDouble("maxZ").toFloat(),
                ),
                walls = walls,
                floorY = json.getDouble("floorY").toFloat(),
                wallHeight = json.getDouble("wallHeight").toFloat(),
                wallsAreEstimated = json.getBoolean("wallsEstimated"),
            ),
            grid = SampleGrid.fromCells(json.getDouble("cellSize").toFloat(), cells),
            ssid = if (json.isNull("ssid")) null else json.getString("ssid"),
            durationMs = json.getLong("durationMs"),
            path = WalkPath(pathPoints),
            startAzimuthDegrees = if (json.isNull("azimuth")) {
                null
            } else {
                json.getDouble("azimuth").toFloat()
            },
            geoAnchor = anchor,
            photos = photosFrom(json),
            apSurvey = ApSurvey(apReadings, roamEvents),
            speedSurvey = SpeedSurvey(speedReadings),
        )
    }

    private fun photosFrom(json: JSONObject): List<PlacePhoto> {
        val array = json.optJSONArray("photos") ?: return emptyList()
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            PlacePhoto(
                id = item.getString("id"),
                label = item.getString("label"),
                x = item.getDouble("x").toFloat(),
                y = item.getDouble("y").toFloat(),
                z = item.getDouble("z").toFloat(),
                fileName = item.getString("file"),
                capturedAtMs = item.getLong("at"),
            )
        }
    }

    /** Absolute path of a photo belonging to [scanId]. */
    fun photoFile(scanId: String, fileName: String): File =
        File(File(File(directory.parentFile, "photos"), scanId), fileName)

    /** Renames one landmark in place, leaving the rest of the scan untouched. */
    fun relabelPhoto(scanId: String, photoId: String, label: String) {
        val scan = load(scanId) ?: return
        save(
            scan.copy(
                photos = scan.photos.map { photo ->
                    if (photo.id == photoId) photo.copy(label = label) else photo
                },
            ),
        )
    }

    private fun summaryOf(json: JSONObject): ScanSummary {
        val cellsJson = json.getJSONArray("cells")
        val cellSize = json.getDouble("cellSize").toFloat()

        val footprint = HashSet<Long>()
        var weakest: Int? = null
        for (index in 0 until cellsJson.length()) {
            val c = cellsJson.getJSONObject(index)
            footprint += (c.getInt("ix").toLong() shl 32) or (c.getInt("iz").toLong() and 0xFFFFFFFFL)
            val rssi = c.getInt("rssi")
            if (weakest == null || rssi < weakest!!) weakest = rssi
        }

        return ScanSummary(
            id = json.getString("id"),
            savedAtMs = json.getLong("savedAt"),
            ssid = if (json.isNull("ssid")) null else json.getString("ssid"),
            areaSqM = footprint.size * cellSize * cellSize,
            cellCount = cellsJson.length(),
            weakestDbm = weakest,
        )
    }

    private companion object {
        const val DIR_NAME = "scans"
        const val MAX_SCANS = 30
    }
}

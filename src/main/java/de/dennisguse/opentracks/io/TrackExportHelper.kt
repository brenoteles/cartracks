package de.dennisguse.opentracks.io

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.ui.model.TripPoint
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TrackExportHelper {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun exportAndShareGPX(context: Context, trip: TripItem, points: List<TripPoint>) {
        val safeName = "CarTracks_${trip.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")}_${trip.id}.gpx"
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val gpxFile = File(exportsDir, safeName)

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"CarTracks Android App\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <metadata>\n")
        sb.append("    <name>${escapeXml(trip.name)}</name>\n")
        sb.append("    <time>${isoFormat.format(Date(trip.startTimeMs))}</time>\n")
        sb.append("  </metadata>\n")
        sb.append("  <trk>\n")
        sb.append("    <name>${escapeXml(trip.name)}</name>\n")
        sb.append("    <desc>Distância: ${String.format(Locale.US, "%.2f", trip.distanceKm)} km | Duração: ${trip.durationMin} min | Vel. Média: ${String.format(Locale.US, "%.1f", trip.avgSpeedKmh)} km/h</desc>\n")
        sb.append("    <trkseg>\n")

        for (pt in points) {
            sb.append(String.format(Locale.US, "      <trkpt lat=\"%.6f\" lon=\"%.6f\">\n", pt.lat, pt.lon))
            if (pt.altitudeM > 0) {
                sb.append(String.format(Locale.US, "        <ele>%.1f</ele>\n", pt.altitudeM))
            }
            if (pt.timeMs > 0) {
                sb.append("        <time>${isoFormat.format(Date(pt.timeMs))}</time>\n")
            }
            if (pt.speedKmh > 0) {
                sb.append(String.format(Locale.US, "        <extensions><speed>%.2f</speed></extensions>\n", pt.speedKmh / 3.6))
            }
            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")

        FileOutputStream(gpxFile).use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
        shareFile(context, gpxFile, "application/gpx+xml", "Compartilhar Trajeto GPX")
    }

    fun exportAndShareKML(context: Context, trip: TripItem, points: List<TripPoint>) {
        val safeName = "CarTracks_${trip.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")}_${trip.id}.kml"
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val kmlFile = File(exportsDir, safeName)

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("  <Document>\n")
        sb.append("    <name>${escapeXml(trip.name)}</name>\n")
        sb.append("    <description>Trajeto CarTracks - ${String.format(Locale.US, "%.1f", trip.distanceKm)} km</description>\n")
        sb.append("    <Style id=\"carTracksStyle\">\n")
        sb.append("      <LineStyle>\n")
        sb.append("        <color>ffff840a</color>\n") // AABBGGRR: Electric Blue #0A84FF
        sb.append("        <width>6</width>\n")
        sb.append("      </LineStyle>\n")
        sb.append("    </Style>\n")
        sb.append("    <Placemark>\n")
        sb.append("      <name>Rota Percorrida</name>\n")
        sb.append("      <styleUrl>#carTracksStyle</styleUrl>\n")
        sb.append("      <LineString>\n")
        sb.append("        <extrude>1</extrude>\n")
        sb.append("        <tessellate>1</tessellate>\n")
        sb.append("        <coordinates>\n")

        for (pt in points) {
            sb.append(String.format(Locale.US, "          %.6f,%.6f,%.1f\n", pt.lon, pt.lat, pt.altitudeM))
        }

        sb.append("        </coordinates>\n")
        sb.append("      </LineString>\n")
        sb.append("    </Placemark>\n")
        sb.append("  </Document>\n")
        sb.append("</kml>\n")

        FileOutputStream(kmlFile).use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
        shareFile(context, kmlFile, "application/vnd.google-earth.kml+xml", "Compartilhar Trajeto no Google Earth (KML)")
    }

    private fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}

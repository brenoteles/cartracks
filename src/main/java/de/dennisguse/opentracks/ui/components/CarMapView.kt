package de.dennisguse.opentracks.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.preference.PreferenceManager
import de.dennisguse.opentracks.ui.model.MapMarkerItem
import de.dennisguse.opentracks.ui.model.TripPoint
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

private fun ensureOsmDroidConfigured(context: Context) {
    val appCtx = context.applicationContext
    val config = Configuration.getInstance()
    config.load(appCtx, PreferenceManager.getDefaultSharedPreferences(appCtx))

    // Set writable internal cache directory AFTER load() so any stale/unwritable path in SharedPreferences is overridden
    val baseDir = File(appCtx.cacheDir, "osmdroid_hd_v6").apply { mkdirs() }
    val tileDir = File(baseDir, "tiles").apply { mkdirs() }
    config.osmdroidBasePath = baseDir
    config.osmdroidTileCache = tileDir

    // Valid browser/app User-Agent required by tile.openstreetmap.org
    config.userAgentValue = "Mozilla/5.0 (Linux; Android 16; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 CarTracks/1.0.6"
    config.tileDownloadThreads = 4.toShort()
    config.tileFileSystemThreads = 4.toShort()
}

@Composable
fun CarMapView(
    modifier: Modifier = Modifier,
    points: List<TripPoint> = emptyList(),
    multiRoutes: List<List<TripPoint>> = emptyList(),
    extraMarkers: List<MapMarkerItem> = emptyList(),
    currentLocation: Pair<Double, Double>? = null,
    pinnedLocation: Pair<Double, Double>? = null,
    pinnedTitle: String = "Local Selecionado",
    onMapTapped: ((Double, Double) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        ensureOsmDroidConfigured(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK) // Official OpenStreetMap standard {z}/{x}/{y} tiles
            setUseDataConnection(true)
            isTilesScaledToDpi = true               // Crisp HD DPI scaling on QHD+ screens
            setDestroyMode(false)                   // Keep tile provider alive across Compose recompositions
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 4.0
            maxZoomLevel = 20.0
            controller.setZoom(15.5)
            controller.setCenter(GeoPoint(-23.5672, -46.6685)) // Default São Paulo (Jardins)
            onResume()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { mapView },
        update = { map ->
            map.setUseDataConnection(true)
            map.overlays.clear()

            // 1. Map Events Receiver for Tapping / Pinning Location
            if (onMapTapped != null) {
                val eventsReceiver = object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        onMapTapped(p.latitude, p.longitude)
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint): Boolean {
                        onMapTapped(p.latitude, p.longitude)
                        return true
                    }
                }
                map.overlays.add(MapEventsOverlay(eventsReceiver))
            }

            // 2. Single Route Polyline
            if (points.isNotEmpty()) {
                val geoPoints = points.map { GeoPoint(it.lat, it.lon) }
                val polyline = Polyline(map).apply {
                    setPoints(geoPoints)
                    outlinePaint.color = AndroidColor.parseColor("#0A84FF") // Apple Electric Blue
                    outlinePaint.strokeWidth = 14f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                }
                map.overlays.add(polyline)

                // Start Marker
                val startPt = points.first()
                val startMarker = Marker(map).apply {
                    position = GeoPoint(startPt.lat, startPt.lon)
                    title = "Partida"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(startMarker)

                // End Marker
                if (points.size > 1) {
                    val endPt = points.last()
                    val endMarker = Marker(map).apply {
                        position = GeoPoint(endPt.lat, endPt.lon)
                        title = "Chegada"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    map.overlays.add(endMarker)
                }

                try {
                    val box = BoundingBox.fromGeoPoints(geoPoints)
                    map.post {
                        map.zoomToBoundingBox(box.increaseByScale(1.35f), true)
                    }
                } catch (e: Exception) {
                    map.controller.setCenter(geoPoints.first())
                }
            }

            // 3. Multi-Routes (for Period Overview)
            if (multiRoutes.isNotEmpty()) {
                val routeColors = listOf(
                    "#0A84FF", "#30D158", "#FF9F0A", "#BF5AF2", "#FF453A", "#64D2FF"
                )
                val allPoints = mutableListOf<GeoPoint>()

                multiRoutes.forEachIndexed { index, route ->
                    if (route.isNotEmpty()) {
                        val geoPts = route.map { GeoPoint(it.lat, it.lon) }
                        allPoints.addAll(geoPts)
                        val colorHex = routeColors[index % routeColors.size]
                        val poly = Polyline(map).apply {
                            setPoints(geoPts)
                            outlinePaint.color = AndroidColor.parseColor(colorHex)
                            outlinePaint.strokeWidth = 10f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                        }
                        map.overlays.add(poly)
                    }
                }

                if (allPoints.isNotEmpty()) {
                    try {
                        val box = BoundingBox.fromGeoPoints(allPoints)
                        map.post {
                            map.zoomToBoundingBox(box.increaseByScale(1.35f), true)
                        }
                    } catch (e: Exception) {
                        map.controller.setCenter(allPoints.first())
                    }
                }
            }

            // 4. Extra Markers (e.g. Fuel Stations on Map)
            if (extraMarkers.isNotEmpty()) {
                val markerGeoPoints = mutableListOf<GeoPoint>()
                extraMarkers.forEach { item ->
                    val pt = GeoPoint(item.lat, item.lon)
                    markerGeoPoints.add(pt)
                    val m = Marker(map).apply {
                        position = pt
                        title = item.title
                        snippet = item.snippet
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    map.overlays.add(m)
                }
                if (points.isEmpty() && multiRoutes.isEmpty() && pinnedLocation == null) {
                    if (markerGeoPoints.size == 1) {
                        map.controller.setZoom(16.0)
                        map.controller.setCenter(markerGeoPoints.first())
                    } else {
                        try {
                            val box = BoundingBox.fromGeoPoints(markerGeoPoints)
                            map.post {
                                map.zoomToBoundingBox(box.increaseByScale(1.45f), true)
                            }
                        } catch (e: Exception) {
                            map.controller.setCenter(markerGeoPoints.first())
                        }
                    }
                }
            }

            // 5. Current Location Marker
            currentLocation?.let { (lat, lon) ->
                val curPoint = GeoPoint(lat, lon)
                val curMarker = Marker(map).apply {
                    position = curPoint
                    title = "Posição Atual"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(curMarker)
                if (points.isEmpty() && multiRoutes.isEmpty() && pinnedLocation == null && extraMarkers.isEmpty()) {
                    map.controller.animateTo(curPoint)
                }
            }

            // 6. Pinned Location Marker (for Casa / Trabalho / Posto picker)
            pinnedLocation?.let { (lat, lon) ->
                val pinPt = GeoPoint(lat, lon)
                val pinMarker = Marker(map).apply {
                    position = pinPt
                    title = pinnedTitle
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(pinMarker)
                map.controller.animateTo(pinPt)
            }

            map.invalidate()
        }
    )
}

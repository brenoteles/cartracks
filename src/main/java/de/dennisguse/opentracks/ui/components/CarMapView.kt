package de.dennisguse.opentracks.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.preference.PreferenceManager
import de.dennisguse.opentracks.ui.model.MapMarkerItem
import de.dennisguse.opentracks.ui.model.TripPoint
import de.dennisguse.opentracks.ui.theme.*
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import java.io.File

/**
 * High Definition ESRI Map Tile Sources.
 * StreetComplete & Wanda inspired map view:
 * - Proper tile scale factor (1.35x) for balanced readability on high-DPI screens.
 * - Dynamic metric ScaleBarOverlay showing real distances (50m, 100m, 500m, 1km, etc.).
 * - Street navigation default zoom 17.2.
 */
val EsriWorldStreetMapHD = object : OnlineTileSourceBase(
    "EsriStreetHD",
    0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/"),
    "© Esri, OpenStreetMap contributors"
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl$zoom/$y/$x.jpg"
    }
}

val EsriDarkCanvasHD = object : OnlineTileSourceBase(
    "EsriDarkHD",
    0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/"),
    "© Esri, HERE, Garmin"
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl$zoom/$y/$x.jpg"
    }
}

val EsriSatelliteHD = object : OnlineTileSourceBase(
    "EsriSatelliteHD",
    0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "© Esri, Maxar, Earthstar Geographics"
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl$zoom/$y/$x.jpg"
    }
}

enum class MapLayer(val label: String) {
    STREET("Ruas HD"),
    DARK("Noturno"),
    SATELLITE("Satélite")
}

private fun ensureOsmDroidConfigured(context: Context) {
    val appCtx = context.applicationContext
    val config = Configuration.getInstance()
    config.load(appCtx, PreferenceManager.getDefaultSharedPreferences(appCtx))

    val baseDir = File(appCtx.cacheDir, "osmdroid_hd_v8").apply { mkdirs() }
    val tileDir = File(baseDir, "tiles").apply { mkdirs() }
    config.osmdroidBasePath = baseDir
    config.osmdroidTileCache = tileDir

    config.userAgentValue = "Mozilla/5.0 (Linux; Android 16; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 CarTracks/1.0.8"
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
    onMapTapped: ((Double, Double) -> Unit)? = null,
    showLayerSwitcher: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedLayer by remember { mutableStateOf(MapLayer.STREET) }

    val mapView = remember {
        ensureOsmDroidConfigured(context)
        MapView(context).apply {
            setTileSource(EsriWorldStreetMapHD)
            setUseDataConnection(true)

            // StreetComplete & Wanda inspired scale configuration:
            // 1.35x tile scale factor provides legible street labels and roads on high-density AMOLED screens.
            isTilesScaledToDpi = false
            tilesScaleFactor = 1.35f
            setDestroyMode(false)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 4.0
            maxZoomLevel = 20.0
            controller.setZoom(17.2) // Street-level default zoom
            controller.setCenter(GeoPoint(-23.5672, -46.6685)) // São Paulo (Jardins / Paulista)

            // Dynamic Metric Scale Bar Overlay (Distance scale directly on map)
            val scaleBarOverlay = ScaleBarOverlay(this).apply {
                setCentred(false)
                setAlignBottom(true)
                setAlignRight(false)
                setScaleBarOffset(24, 32)
                setUnitsOfMeasure(ScaleBarOverlay.UnitsOfMeasure.metric)
                setLineWidth(4f)
                setTextSize(34f)
                barPaint.color = AndroidColor.parseColor("#458BD0") // Wanda Blue 30
                textPaint.color = AndroidColor.parseColor("#FFFFFF") // Wanda White
                textPaint.typeface = Typeface.DEFAULT_BOLD
                setBackgroundPaint(Paint().apply { color = AndroidColor.parseColor("#99101114") })
            }
            overlays.add(scaleBarOverlay)

            onResume()
        }
    }

    // Switch layer when user changes selection
    LaunchedEffect(selectedLayer) {
        val newSource = when (selectedLayer) {
            MapLayer.STREET -> EsriWorldStreetMapHD
            MapLayer.DARK -> EsriDarkCanvasHD
            MapLayer.SATELLITE -> EsriSatelliteHD
        }
        mapView.setTileSource(newSource)
        mapView.invalidate()
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

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { map ->
                map.setUseDataConnection(true)

                // Preserve scale bar overlay when refreshing overlays
                val scaleBar = map.overlays.filterIsInstance<ScaleBarOverlay>().firstOrNull()
                map.overlays.clear()
                if (scaleBar != null) {
                    map.overlays.add(scaleBar)
                }

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
                        outlinePaint.color = AndroidColor.parseColor("#458BD0") // Wanda Primary Blue
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
                            // Tighter margin 1.15f for comfortable route framing
                            map.zoomToBoundingBox(box.increaseByScale(1.15f), true)
                        }
                    } catch (e: Exception) {
                        map.controller.setCenter(geoPoints.first())
                    }
                }

                // 3. Multi-Routes (for Period Overview)
                if (multiRoutes.isNotEmpty()) {
                    val routeColors = listOf(
                        "#458BD0", "#2CBF6F", "#F5A957", "#C480C7", "#D02A43", "#3BABBA"
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
                                map.zoomToBoundingBox(box.increaseByScale(1.15f), true)
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
                            map.controller.setZoom(17.2)
                            map.controller.setCenter(markerGeoPoints.first())
                        } else {
                            try {
                                val box = BoundingBox.fromGeoPoints(markerGeoPoints)
                                map.post {
                                    map.zoomToBoundingBox(box.increaseByScale(1.2f), true)
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

                // 6. Pinned Location Marker
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

        // Wanda Layer Switcher (Top End)
        if (showLayerSwitcher) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(WandaCardElevated.copy(alpha = 0.94f))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MapLayer.values().forEach { layer ->
                    val isSelected = layer == selectedLayer
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) WandaPrimary else Color.Transparent)
                            .clickable { selectedLayer = layer }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = layer.label,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Floating One-Handed Zoom Controls (+ and - buttons, Bottom End)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(WandaCardElevated.copy(alpha = 0.94f))
                    .clickable { mapView.controller.zoomIn() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Aproximar",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(WandaCardElevated.copy(alpha = 0.94f))
                    .clickable { mapView.controller.zoomOut() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Afastar",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

package de.dennisguse.opentracks.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.ui.unit.Dp
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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
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
import android.graphics.Color as AndroidColor

/**
 * Modern Vector-Grade Marker Factory for OpenStreetMap / OsmDroid.
 * Replaces legacy 2011 pushpins with high-DPI automotive tokens.
 */
object ModernMarkerFactory {
    private var cachedStart: Drawable? = null
    private var cachedEnd: Drawable? = null
    private var cachedPin: Drawable? = null
    private var cachedGps: Drawable? = null
    private var cachedFuel: Drawable? = null

    fun getStartMarker(context: Context): Drawable {
        if (cachedStart != null) return cachedStart!!
        val density = context.resources.displayMetrics.density
        val size = (32 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = AndroidColor.parseColor("#332CBF6F")
        }
        canvas.drawCircle(center, center, center - 2 * density, paint)

        paint.color = AndroidColor.WHITE
        canvas.drawCircle(center, center, center - 5 * density, paint)

        paint.color = AndroidColor.parseColor("#2CBF6F") // Wanda Green
        canvas.drawCircle(center, center, center - 8 * density, paint)

        paint.color = AndroidColor.WHITE
        canvas.drawCircle(center, center, 3 * density, paint)

        cachedStart = BitmapDrawable(context.resources, bitmap)
        return cachedStart!!
    }

    fun getEndMarker(context: Context): Drawable {
        if (cachedEnd != null) return cachedEnd!!
        val density = context.resources.displayMetrics.density
        val size = (32 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = AndroidColor.parseColor("#33D02A43")
        }
        canvas.drawCircle(center, center, center - 2 * density, paint)

        paint.color = AndroidColor.WHITE
        canvas.drawCircle(center, center, center - 5 * density, paint)

        paint.color = AndroidColor.parseColor("#D02A43") // Wanda Red
        canvas.drawCircle(center, center, center - 8 * density, paint)

        paint.color = AndroidColor.WHITE
        val rectSize = 5 * density
        canvas.drawRect(center - rectSize, center - rectSize, center + rectSize, center + rectSize, paint)

        cachedEnd = BitmapDrawable(context.resources, bitmap)
        return cachedEnd!!
    }

    fun getPinnedMarker(context: Context): Drawable {
        if (cachedPin != null) return cachedPin!!
        val density = context.resources.displayMetrics.density
        val width = (36 * density).toInt()
        val height = (46 * density).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = AndroidColor.parseColor("#0062FF") // Wanda Interactive
        }

        val path = AndroidPath()
        val r = width / 2.3f
        path.moveTo(width / 2f, height.toFloat() - 2 * density)
        path.cubicTo(
            0f, height * 0.65f,
            0f, r,
            width / 2f, 2 * density
        )
        path.cubicTo(
            width.toFloat(), 2 * density,
            width.toFloat(), height * 0.65f,
            width / 2f, height.toFloat() - 2 * density
        )
        path.close()
        canvas.drawPath(path, paint)

        // White border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        paint.color = AndroidColor.WHITE
        canvas.drawPath(path, paint)

        // Inner white dot
        paint.style = Paint.Style.FILL
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(width / 2f, height * 0.38f, 5 * density, paint)

        cachedPin = BitmapDrawable(context.resources, bitmap)
        return cachedPin!!
    }

    fun getMyLocationMarker(context: Context): Drawable {
        if (cachedGps != null) return cachedGps!!
        val density = context.resources.displayMetrics.density
        val size = (30 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = AndroidColor.parseColor("#3357C3D1")
        }
        canvas.drawCircle(center, center, center - 1 * density, paint)

        paint.color = AndroidColor.WHITE
        canvas.drawCircle(center, center, center - 6 * density, paint)

        paint.color = AndroidColor.parseColor("#0062FF")
        canvas.drawCircle(center, center, center - 8.5f * density, paint)

        cachedGps = BitmapDrawable(context.resources, bitmap)
        return cachedGps!!
    }

    fun getFuelMarker(context: Context): Drawable {
        if (cachedFuel != null) return cachedFuel!!
        val density = context.resources.displayMetrics.density
        val size = (28 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = AndroidColor.parseColor("#F5A957") // Wanda Amber
        }
        canvas.drawCircle(center, center, center - 2 * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(center, center, center - 2 * density, paint)

        paint.style = Paint.Style.FILL
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(center, center, 4 * density, paint)

        cachedFuel = BitmapDrawable(context.resources, bitmap)
        return cachedFuel!!
    }
}

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
    STREET("Ruas"),
    DARK("Escuro"),
    SATELLITE("Satélite")
}

private fun ensureOsmDroidConfigured(context: Context) {
    val appCtx = context.applicationContext
    val config = Configuration.getInstance()
    config.load(appCtx, PreferenceManager.getDefaultSharedPreferences(appCtx))

    val baseDir = File(appCtx.cacheDir, "osmdroid_v11").apply { mkdirs() }
    val tileDir = File(baseDir, "tiles").apply { mkdirs() }
    config.osmdroidBasePath = baseDir
    config.osmdroidTileCache = tileDir

    config.userAgentValue = "Mozilla/5.0 (Linux; Android 16; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 CarTracks/1.0.11"

    config.tileDownloadThreads = 8.toShort()
    config.tileFileSystemThreads = 8.toShort()
    config.cacheMapTileCount = 200.toShort()
    config.tileDownloadMaxQueueSize = 80.toShort()
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
    showLayerSwitcher: Boolean = true,
    showZoomControls: Boolean = true,
    floatingControlsBottomPadding: Dp = 24.dp,
    onCenterGpsClicked: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedLayer by remember { mutableStateOf(MapLayer.STREET) }

    // Memoization keys to eliminate redundant overlay clearing & re-draws on telemetry ticks
    var lastPointsHash by remember { mutableStateOf(0) }
    var lastPinnedCoord by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var hasAutoCentered by remember { mutableStateOf(false) }

    val mapView = remember {
        ensureOsmDroidConfigured(context)
        MapView(context).apply {
            setTileSource(EsriWorldStreetMapHD)
            setUseDataConnection(true)

            // High Definition Native GIS Resolution:
            // 1.0f scale factor eliminates blurry digital upscaling on 1440p/1080p screens
            isTilesScaledToDpi = false
            tilesScaleFactor = 1.0f

            setDestroyMode(false)
            setMultiTouchControls(true)
            isFlingEnabled = true
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false

            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 4.0
            maxZoomLevel = 20.0

            post {
                val initialPt = currentLocation?.let { GeoPoint(it.first, it.second) }
                    ?: pinnedLocation?.let { GeoPoint(it.first, it.second) }
                    ?: GeoPoint(-23.5672, -46.6685)
                controller.setZoom(17.2)
                controller.setCenter(initialPt)
            }

            // StreetComplete dynamic scale bar overlay
            val scaleBarOverlay = ScaleBarOverlay(this).apply {
                setCentred(false)
                unitsOfMeasure = ScaleBarOverlay.UnitsOfMeasure.metric
                setScaleBarOffset(36, 40)
                setTextSize(26f)
                setBackgroundPaint(Paint().apply {
                    color = AndroidColor.parseColor("#B3101114")
                    style = Paint.Style.FILL
                })
                textPaint.apply {
                    color = AndroidColor.WHITE
                    isAntiAlias = true
                    isFakeBoldText = true
                }
                barPaint.apply {
                    color = AndroidColor.WHITE
                    strokeWidth = 4f
                    isAntiAlias = true
                }
            }
            overlays.add(scaleBarOverlay)
        }
    }

    LaunchedEffect(currentLocation) {
        if (currentLocation != null && (!hasAutoCentered || points.size <= 2)) {
            hasAutoCentered = true
            mapView.post {
                mapView.controller.setZoom(17.5)
                mapView.controller.animateTo(GeoPoint(currentLocation.first, currentLocation.second))
            }
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
        }
    }

    LaunchedEffect(selectedLayer) {
        when (selectedLayer) {
            MapLayer.STREET -> mapView.setTileSource(EsriWorldStreetMapHD)
            MapLayer.DARK -> mapView.setTileSource(EsriDarkCanvasHD)
            MapLayer.SATELLITE -> mapView.setTileSource(EsriSatelliteHD)
        }
        mapView.invalidate()
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                val currentPointsHash = points.hashCode() xor multiRoutes.hashCode() xor extraMarkers.hashCode() xor (currentLocation?.hashCode() ?: 0)
                val needsOverlayUpdate = (currentPointsHash != lastPointsHash) || (pinnedLocation != lastPinnedCoord)

                if (needsOverlayUpdate) {
                    lastPointsHash = currentPointsHash
                    lastPinnedCoord = pinnedLocation

                    // Preserve base ScaleBarOverlay (index 0) and rebuild dynamic route/marker overlays
                    val baseScale = if (map.overlays.isNotEmpty() && map.overlays[0] is ScaleBarOverlay) map.overlays[0] else null
                    map.overlays.clear()
                    if (baseScale != null) map.overlays.add(baseScale)

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

                    // 2. High-Precision Dual-Stroke Polyline (StreetComplete / Apple Maps style)
                    if (points.isNotEmpty()) {
                        val geoPoints = points.map { GeoPoint(it.lat, it.lon) }

                        // 2a. Outer Casing / Contrast Outline (usePath = true connects all points continuously!)
                        val casing = Polyline(map, true).apply {
                            setPoints(geoPoints)
                            outlinePaint.color = AndroidColor.parseColor("#142238")
                            outlinePaint.strokeWidth = 20f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                            outlinePaint.isAntiAlias = true
                        }
                        map.overlays.add(casing)

                        // 2b. Inner Vibrant Core Stroke
                        val core = Polyline(map, true).apply {
                            setPoints(geoPoints)
                            outlinePaint.color = AndroidColor.parseColor("#0084FF") // High-contrast Wanda Blue
                            outlinePaint.strokeWidth = 12f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                            outlinePaint.isAntiAlias = true
                        }
                        map.overlays.add(core)

                        // Start Modern Marker
                        val startPt = points.first()
                        val startMarker = Marker(map).apply {
                            position = GeoPoint(startPt.lat, startPt.lon)
                            title = "Partida"
                            icon = ModernMarkerFactory.getStartMarker(map.context)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        }
                        map.overlays.add(startMarker)

                        // End Modern Marker
                        if (points.size > 1) {
                            val endPt = points.last()
                            val endMarker = Marker(map).apply {
                                position = GeoPoint(endPt.lat, endPt.lon)
                                title = "Chegada"
                                icon = ModernMarkerFactory.getEndMarker(map.context)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            }
                            map.overlays.add(endMarker)
                        }

                        try {
                            val box = BoundingBox.fromGeoPoints(geoPoints)
                            map.post {
                                map.zoomToBoundingBox(box.increaseByScale(1.15f), true)
                            }
                        } catch (e: Exception) {
                            map.controller.setCenter(geoPoints.first())
                        }
                    }

                    // 3. Multi-Routes (for Period Overview) with continuous double-stroke
                    if (multiRoutes.isNotEmpty()) {
                        val routeColors = listOf(
                            "#0084FF", "#2CBF6F", "#F5A957", "#C480C7", "#D02A43", "#3BABBA"
                        )
                        val allPoints = mutableListOf<GeoPoint>()

                        multiRoutes.forEachIndexed { index, route ->
                            if (route.isNotEmpty()) {
                                val geoPts = route.map { GeoPoint(it.lat, it.lon) }
                                allPoints.addAll(geoPts)
                                val colorHex = routeColors[index % routeColors.size]

                                // Casing
                                val polyCasing = Polyline(map, true).apply {
                                    setPoints(geoPts)
                                    outlinePaint.color = AndroidColor.parseColor("#142238")
                                    outlinePaint.strokeWidth = 16f
                                    outlinePaint.strokeCap = Paint.Cap.ROUND
                                    outlinePaint.strokeJoin = Paint.Join.ROUND
                                    outlinePaint.isAntiAlias = true
                                }
                                map.overlays.add(polyCasing)

                                // Core
                                val polyCore = Polyline(map, true).apply {
                                    setPoints(geoPts)
                                    outlinePaint.color = AndroidColor.parseColor(colorHex)
                                    outlinePaint.strokeWidth = 10f
                                    outlinePaint.strokeCap = Paint.Cap.ROUND
                                    outlinePaint.strokeJoin = Paint.Join.ROUND
                                    outlinePaint.isAntiAlias = true
                                }
                                map.overlays.add(polyCore)
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
                                icon = ModernMarkerFactory.getFuelMarker(map.context)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            }
                            map.overlays.add(m)
                        }
                        if (points.isEmpty() && multiRoutes.isEmpty() && pinnedLocation == null) {
                            if (markerGeoPoints.size == 1) {
                                map.controller.setZoom(17.0)
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
                            icon = ModernMarkerFactory.getMyLocationMarker(map.context)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        }
                        map.overlays.add(curMarker)
                    }

                    // 6. Pinned Location Marker
                    pinnedLocation?.let { (lat, lon) ->
                        val pinPt = GeoPoint(lat, lon)
                        val pinMarker = Marker(map).apply {
                            position = pinPt
                            title = pinnedTitle
                            icon = ModernMarkerFactory.getPinnedMarker(map.context)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        map.overlays.add(pinMarker)
                        map.controller.animateTo(pinPt)
                    }

                    map.invalidate()
                }
            }
        )

        // Wanda Layer Switcher - PLACED AT TOP-START (TOP LEFT)
        if (showLayerSwitcher) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp, start = 12.dp)
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

        // Floating One-Handed Controls (Zoom & GPS) - Bottom End
        // Configurable bottom padding ensures they are NEVER covered by overlays or bottom sheets!
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = floatingControlsBottomPadding, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onCenterGpsClicked != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(WandaCardElevated.copy(alpha = 0.94f))
                        .clickable {
                            if (currentLocation != null) {
                                mapView.controller.setZoom(17.5)
                                mapView.controller.animateTo(GeoPoint(currentLocation.first, currentLocation.second))
                            }
                            onCenterGpsClicked()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Meu GPS",
                        tint = WandaCyan20,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (showZoomControls) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(WandaCardElevated.copy(alpha = 0.94f))
                        .clickable { mapView.controller.zoomIn() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Aproximar",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(WandaCardElevated.copy(alpha = 0.94f))
                        .clickable { mapView.controller.zoomOut() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Afastar",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

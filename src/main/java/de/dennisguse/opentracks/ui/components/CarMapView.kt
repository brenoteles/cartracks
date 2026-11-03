package de.dennisguse.opentracks.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import de.dennisguse.opentracks.ui.model.MapMarkerItem
import de.dennisguse.opentracks.ui.model.TripPoint
import de.dennisguse.opentracks.ui.theme.*
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import android.graphics.Color as AndroidColor

/**
 * Modern High-DPI Marker Factory for CarTracks Automotive UI.
 */
object ModernMarkerFactory {
    private var cachedStart: Bitmap? = null
    private var cachedEnd: Bitmap? = null
    private var cachedPin: Bitmap? = null
    private var cachedGps: Bitmap? = null
    private var cachedFuel: Bitmap? = null

    fun getStartMarkerBitmap(context: Context): Bitmap {
        if (cachedStart != null) return cachedStart!!
        val density = context.resources.displayMetrics.density
        val size = (32 * density).toInt().coerceAtLeast(48)
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

        cachedStart = bitmap
        return bitmap
    }

    fun getEndMarkerBitmap(context: Context): Bitmap {
        if (cachedEnd != null) return cachedEnd!!
        val density = context.resources.displayMetrics.density
        val size = (32 * density).toInt().coerceAtLeast(48)
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
        canvas.drawRect(center - 3 * density, center - 3 * density, center + 3 * density, center + 3 * density, paint)

        cachedEnd = bitmap
        return bitmap
    }

    fun getPinnedMarkerBitmap(context: Context): Bitmap {
        if (cachedPin != null) return cachedPin!!
        val density = context.resources.displayMetrics.density
        val width = (32 * density).toInt().coerceAtLeast(48)
        val height = (42 * density).toInt().coerceAtLeast(64)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val centerX = width / 2f
        val circleRadius = (width / 2f) - 3 * density

        val path = AndroidPath().apply {
            moveTo(centerX, height.toFloat() - 2 * density)
            lineTo(centerX - circleRadius * 0.7f, circleRadius + 4 * density)
            arcTo(RectF(centerX - circleRadius, 2 * density, centerX + circleRadius, 2 * density + 2 * circleRadius), 150f, 240f)
            close()
        }

        paint.style = Paint.Style.FILL
        paint.color = AndroidColor.parseColor("#0084FF") // Wanda Blue
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        paint.color = AndroidColor.WHITE
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.FILL
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(centerX, circleRadius + 2 * density, circleRadius * 0.45f, paint)

        cachedPin = bitmap
        return bitmap
    }

    fun getGpsVehicleBitmap(context: Context): Bitmap {
        if (cachedGps != null) return cachedGps!!
        val density = context.resources.displayMetrics.density
        val size = (36 * density).toInt().coerceAtLeast(54)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = AndroidColor.parseColor("#333BABBA") // Wanda Cyan halo
        }
        canvas.drawCircle(center, center, center - 2 * density, paint)

        paint.color = AndroidColor.WHITE
        canvas.drawCircle(center, center, 9 * density, paint)

        paint.color = AndroidColor.parseColor("#0084FF") // Automotive Blue Core
        canvas.drawCircle(center, center, 6.5f * density, paint)

        cachedGps = bitmap
        return bitmap
    }

    fun getFuelMarkerBitmap(context: Context): Bitmap {
        if (cachedFuel != null) return cachedFuel!!
        val density = context.resources.displayMetrics.density
        val size = (28 * density).toInt().coerceAtLeast(42)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = AndroidColor.parseColor("#F5A957") // Wanda Amber
        }
        canvas.drawCircle(center, center, center - 2 * density, paint)

        paint.color = AndroidColor.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        canvas.drawCircle(center, center, center - 2 * density, paint)

        cachedFuel = bitmap
        return bitmap
    }
}

enum class MapLayer(val label: String) {
    STREET("Ruas"),
    DARK("Escuro"),
    SATELLITE("Satélite")
}

/**
 * Modern High-Performance MapLibre GL Surface Map Composable for CarTracks.
 * Features:
 * - Hardware-accelerated OpenGL / Vulkan vector & raster rendering.
 * - Dynamic Automotive layer switching (Wanda Dark, Esri Streets HD, Esri Satellite).
 * - Multi-colored speed gradient polylines (OsmAnd style).
 * - Full interactive camera framing, gestures, pinch zoom, center GPS.
 */
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

    var mapLibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var isMapStyleLoaded by remember { mutableStateOf(false) }
    var userInteracted by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
        }
    }

    // Lifecycle binding
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Helper to get style asset URL
    fun getStyleUri(layer: MapLayer): String = when (layer) {
        MapLayer.DARK -> "asset://maplibre/style_dark.json"
        MapLayer.STREET -> "asset://maplibre/style_streets.json"
        MapLayer.SATELLITE -> "asset://maplibre/style_satellite.json"
    }

    // Initialize Map and load style
    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            mapLibreMapInstance = map
            map.uiSettings.isAttributionEnabled = false
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isCompassEnabled = true

            map.setStyle(Style.Builder().fromUri(getStyleUri(selectedLayer))) { _ ->
                isMapStyleLoaded = true
            }

            map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                override fun onMoveBegin(detector: org.maplibre.android.gestures.MoveGestureDetector) {
                    userInteracted = true
                }
                override fun onMove(detector: org.maplibre.android.gestures.MoveGestureDetector) {}
                override fun onMoveEnd(detector: org.maplibre.android.gestures.MoveGestureDetector) {}
            })

            map.addOnMapClickListener { latLng ->
                onMapTapped?.invoke(latLng.latitude, latLng.longitude)
                true
            }
        }
    }

    // Layer switch effect
    LaunchedEffect(selectedLayer) {
        val map = mapLibreMapInstance ?: return@LaunchedEffect
        isMapStyleLoaded = false
        map.setStyle(Style.Builder().fromUri(getStyleUri(selectedLayer))) {
            isMapStyleLoaded = true
        }
    }

    // Update markers, polylines, and camera when state changes or style loads
    LaunchedEffect(isMapStyleLoaded, points, multiRoutes, extraMarkers, pinnedLocation, currentLocation) {
        val map = mapLibreMapInstance ?: return@LaunchedEffect
        if (!isMapStyleLoaded) return@LaunchedEffect

        map.clear()
        val iconFactory = IconFactory.getInstance(context)

        // 1. Draw Single Route with Speed Gradient (OsmAnd style)
        if (points.size >= 2) {
            val speedColoredSegments = mutableListOf<Pair<Int, MutableList<LatLng>>>()
            var currentSpeedCategory = -1
            var currentSegment = mutableListOf<LatLng>()

            for (i in points.indices) {
                val pt = points[i]
                val latLng = LatLng(pt.lat, pt.lon)
                val speed = pt.speedKmh

                val speedCategory = when {
                    speed < 20.0 -> AndroidColor.parseColor("#D02A43") // Red
                    speed < 40.0 -> AndroidColor.parseColor("#F5A957") // Amber
                    speed < 70.0 -> AndroidColor.parseColor("#2CBF6F") // Green
                    speed < 100.0 -> AndroidColor.parseColor("#0084FF") // Blue
                    else -> AndroidColor.parseColor("#C480C7") // Purple
                }

                if (speedCategory != currentSpeedCategory) {
                    if (currentSegment.size >= 2) {
                        speedColoredSegments.add(Pair(currentSpeedCategory, currentSegment))
                    }
                    val newSegment = mutableListOf<LatLng>()
                    if (currentSegment.isNotEmpty()) {
                        newSegment.add(currentSegment.last()) // Bridge connection
                    }
                    newSegment.add(latLng)
                    currentSegment = newSegment
                    currentSpeedCategory = speedCategory
                } else {
                    currentSegment.add(latLng)
                }
            }
            if (currentSegment.size >= 2) {
                speedColoredSegments.add(Pair(currentSpeedCategory, currentSegment))
            }

            // Fallback to solid cyan/blue if no distinct speed chunks
            if (speedColoredSegments.isEmpty()) {
                val allLatLngs = points.map { LatLng(it.lat, it.lon) }
                map.addPolyline(
                    PolylineOptions()
                        .addAll(allLatLngs)
                        .color(AndroidColor.parseColor("#0084FF"))
                        .width(5.5f)
                )
            } else {
                for (seg in speedColoredSegments) {
                    map.addPolyline(
                        PolylineOptions()
                            .addAll(seg.second)
                            .color(seg.first)
                            .width(5.5f)
                    )
                }
            }

            // Start & End Markers
            val first = points.first()
            val last = points.last()
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(first.lat, first.lon))
                    .icon(iconFactory.fromBitmap(ModernMarkerFactory.getStartMarkerBitmap(context)))
                    .title("Partida")
            )
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(last.lat, last.lon))
                    .icon(iconFactory.fromBitmap(ModernMarkerFactory.getEndMarkerBitmap(context)))
                    .title("Chegada")
            )

            // Auto-frame route camera
            if (!userInteracted) {
                try {
                    val boundsBuilder = LatLngBounds.Builder()
                    points.forEach { boundsBuilder.include(LatLng(it.lat, it.lon)) }
                    val bounds = boundsBuilder.build()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 130))
                } catch (ignored: Exception) {}
            }
        }

        // 2. Draw Multi-Routes (e.g. Period Screen)
        if (multiRoutes.isNotEmpty()) {
            val colors = listOf("#0084FF", "#2CBF6F", "#F5A957", "#C480C7", "#3BABBA")
            var idx = 0
            val boundsBuilder = LatLngBounds.Builder()
            var totalPts = 0

            for (route in multiRoutes) {
                if (route.size >= 2) {
                    val color = AndroidColor.parseColor(colors[idx % colors.size])
                    val latLngs = route.map { LatLng(it.lat, it.lon) }
                    map.addPolyline(PolylineOptions().addAll(latLngs).color(color).width(4.5f))
                    latLngs.forEach { boundsBuilder.include(it) }
                    totalPts += latLngs.size
                    idx++
                }
            }
            if (totalPts > 0 && !userInteracted) {
                try {
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
                } catch (ignored: Exception) {}
            }
        }

        // 3. Pinned Location Marker (Home, Work, or Selected Location)
        if (pinnedLocation != null) {
            val pinLatLng = LatLng(pinnedLocation.first, pinnedLocation.second)
            map.addMarker(
                MarkerOptions()
                    .position(pinLatLng)
                    .icon(iconFactory.fromBitmap(ModernMarkerFactory.getPinnedMarkerBitmap(context)))
                    .title(pinnedTitle)
            )
            if (!userInteracted && points.isEmpty()) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(pinLatLng, 16.0))
            }
        }

        // 4. Extra Markers (e.g., Gas Stations)
        for (m in extraMarkers) {
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(m.lat, m.lon))
                    .icon(iconFactory.fromBitmap(ModernMarkerFactory.getFuelMarkerBitmap(context)))
                    .title(m.title)
                    .snippet(m.snippet)
            )
        }

        // 5. Current Vehicle Location Marker
        if (currentLocation != null) {
            val gpsLatLng = LatLng(currentLocation.first, currentLocation.second)
            map.addMarker(
                MarkerOptions()
                    .position(gpsLatLng)
                    .icon(iconFactory.fromBitmap(ModernMarkerFactory.getGpsVehicleBitmap(context)))
                    .title("Veículo")
            )
            if (!userInteracted && points.isEmpty() && pinnedLocation == null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(gpsLatLng, 16.5))
            }
        }
    }

    Box(modifier = modifier) {
        // MapLibre Hardware-Accelerated GL Surface
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Speed Gradient Legend Overlay (when showing a route)
        if (points.size >= 2) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(WandaCardElevated.copy(alpha = 0.92f))
                    .border(1.dp, WandaBorderSubtle, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Velocidade (km/h)", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFD02A43)))
                        Text("<20", color = TextPrimary, fontSize = 9.sp)
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF5A957)))
                        Text("20-40", color = TextPrimary, fontSize = 9.sp)
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF2CBF6F)))
                        Text("40-70", color = TextPrimary, fontSize = 9.sp)
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF0084FF)))
                        Text("70+", color = TextPrimary, fontSize = 9.sp)
                    }
                }
            }
        }

        // Layer Switcher (Top Right)
        if (showLayerSwitcher) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(WandaCardElevated.copy(alpha = 0.94f))
                    .border(1.dp, WandaBorderSubtle, RoundedCornerShape(14.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MapLayer.values().forEach { layer ->
                    val isSelected = selectedLayer == layer
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) WandaBlue20 else Color.Transparent)
                            .clickable { selectedLayer = layer }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = layer.label,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Floating Zoom and GPS Recenter Controls (Bottom Right)
        if (showZoomControls) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = floatingControlsBottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Zoom In Button (+)
                Surface(
                    onClick = {
                        userInteracted = true
                        mapLibreMapInstance?.animateCamera(CameraUpdateFactory.zoomIn())
                    },
                    shape = CircleShape,
                    color = WandaCardElevated.copy(alpha = 0.94f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WandaBorderSubtle),
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = TextPrimary, modifier = Modifier.size(20.dp))
                    }
                }

                // Zoom Out Button (-)
                Surface(
                    onClick = {
                        userInteracted = true
                        mapLibreMapInstance?.animateCamera(CameraUpdateFactory.zoomOut())
                    },
                    shape = CircleShape,
                    color = WandaCardElevated.copy(alpha = 0.94f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WandaBorderSubtle),
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = TextPrimary, modifier = Modifier.size(20.dp))
                    }
                }

                // Recenter GPS Button
                Surface(
                    onClick = {
                        userInteracted = false
                        onCenterGpsClicked?.invoke()
                        if (currentLocation != null) {
                            mapLibreMapInstance?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(currentLocation.first, currentLocation.second),
                                    16.5
                                )
                            )
                        } else if (points.isNotEmpty()) {
                            try {
                                val boundsBuilder = LatLngBounds.Builder()
                                points.forEach { boundsBuilder.include(LatLng(it.lat, it.lon)) }
                                mapLibreMapInstance?.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 130))
                            } catch (ignored: Exception) {}
                        }
                    },
                    shape = CircleShape,
                    color = WandaCardElevated.copy(alpha = 0.94f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WandaBorderSubtle),
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Centralizar", tint = WandaCyan20, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

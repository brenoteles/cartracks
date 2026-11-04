package de.dennisguse.opentracks.ui.components

import androidx.compose.ui.graphics.RectangleShape
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
 * Square, high-DPI map markers in the Cyberpunk palette (hard ink edge + neon core).
 */
object ModernMarkerFactory {
    private val cache = mutableMapOf<String, Bitmap>()

    private fun square(context: Context, key: String, sizeDp: Int, fill: String, inner: String? = null): Bitmap =
        cache.getOrPut(key) {
            val density = context.resources.displayMetrics.density
            val size = (sizeDp * density).toInt().coerceAtLeast(36)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val shadow = 3 * density
            // hard offset shadow
            paint.color = AndroidColor.parseColor("#990E0D1A")
            canvas.drawRect(shadow, shadow, size.toFloat(), size.toFloat(), paint)
            // ink frame + neon fill
            paint.color = AndroidColor.parseColor("#0E0D1A")
            canvas.drawRect(0f, 0f, size - shadow, size - shadow, paint)
            val b = 2.5f * density
            paint.color = AndroidColor.parseColor(fill)
            canvas.drawRect(b, b, size - shadow - b, size - shadow - b, paint)
            if (inner != null) {
                val c = (size - shadow) / 2f
                val r = (size - shadow) * 0.16f
                paint.color = AndroidColor.parseColor(inner)
                canvas.drawRect(c - r, c - r, c + r, c + r, paint)
            }
            bitmap
        }

    fun getStartMarkerBitmap(context: Context): Bitmap = square(context, "start", 26, "#E8FF3A", "#0E0D1A")

    fun getEndMarkerBitmap(context: Context): Bitmap = square(context, "end", 26, "#C5259C", "#FBEFF8")

    fun getGpsVehicleBitmap(context: Context): Bitmap = square(context, "gps", 24, "#00B9B0", "#0E0D1A")

    fun getFuelMarkerBitmap(context: Context): Bitmap = square(context, "fuel", 22, "#E3881E")

    fun getPinnedMarkerBitmap(context: Context): Bitmap = cache.getOrPut("pin") {
        val density = context.resources.displayMetrics.density
        val width = (30 * density).toInt().coerceAtLeast(44)
        val height = (42 * density).toInt().coerceAtLeast(62)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val head = width.toFloat()
        val cx = width / 2f
        // stem
        paint.color = AndroidColor.parseColor("#0E0D1A")
        canvas.drawRect(cx - 2.5f * density, head - 4 * density, cx + 2.5f * density, height.toFloat(), paint)
        // square head
        canvas.drawRect(0f, 0f, head, head, paint)
        val b = 3f * density
        paint.color = AndroidColor.parseColor("#C5259C")
        canvas.drawRect(b, b, head - b, head - b, paint)
        paint.color = AndroidColor.parseColor("#E8FF3A")
        val r = head * 0.17f
        canvas.drawRect(cx - r, head / 2 - r, cx + r, head / 2 + r, paint)
        bitmap
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
                    speed < 20.0 -> AndroidColor.parseColor("#E14822") // red
                    speed < 40.0 -> AndroidColor.parseColor("#E3881E") // orange
                    speed < 70.0 -> AndroidColor.parseColor("#E8FF3A") // acid
                    speed < 100.0 -> AndroidColor.parseColor("#00B9B0") // cyan
                    else -> AndroidColor.parseColor("#F478D9") // pink
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

            // Ink casing under the route keeps the neon line readable on light and satellite tiles
            map.addPolyline(
                PolylineOptions()
                    .addAll(points.map { LatLng(it.lat, it.lon) })
                    .color(AndroidColor.parseColor("#0E0D1A"))
                    .width(9f)
            )

            if (speedColoredSegments.isEmpty()) {
                val allLatLngs = points.map { LatLng(it.lat, it.lon) }
                map.addPolyline(
                    PolylineOptions()
                        .addAll(allLatLngs)
                        .color(AndroidColor.parseColor("#E8FF3A"))
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
            val colors = listOf("#E8FF3A", "#C5259C", "#00B9B0", "#2A5CFF", "#E3881E")
            var idx = 0
            val boundsBuilder = LatLngBounds.Builder()
            var totalPts = 0

            for (route in multiRoutes) {
                if (route.size >= 2) {
                    val color = AndroidColor.parseColor(colors[idx % colors.size])
                    val latLngs = route.map { LatLng(it.lat, it.lon) }
                    map.addPolyline(PolylineOptions().addAll(latLngs).color(AndroidColor.parseColor("#0E0D1A")).width(8f))
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

        // Layer switcher + speed legend stacked at the top end so they never overlap
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = 12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showLayerSwitcher) {
                Row(
                    modifier = Modifier
                        .background(CyberInk.copy(alpha = 0.94f), RectangleShape)
                        .border(1.dp, CyberAcid, RectangleShape)
                ) {
                    MapLayer.values().forEach { layer ->
                        val isSelected = selectedLayer == layer
                        Box(
                            modifier = Modifier
                                .background(if (isSelected) CyberAcid else Color.Transparent)
                                .clickable { selectedLayer = layer }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = layer.label.uppercase(),
                                style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 1.sp),
                                color = if (isSelected) CyberOnAcid else TextSecondary
                            )
                        }
                    }
                }
            }

            if (points.size >= 2) {
                Column(
                    modifier = Modifier
                        .background(CyberInk.copy(alpha = 0.9f), RectangleShape)
                        .border(1.dp, CyberLine, RectangleShape)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text("KM/H", style = EyebrowStyle.copy(fontSize = 9.sp), color = TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        listOf(
                            CyberRed to "0",
                            CyberOrange to "20",
                            CyberAcid to "40",
                            CyberCyan to "70",
                            CyberPink to "100"
                        ).forEach { (c, label) ->
                            Column(horizontalAlignment = Alignment.Start) {
                                Box(Modifier.size(width = 24.dp, height = 5.dp).background(c))
                                Text(label, style = monoStyle(9, FontWeight.Normal), color = TextPrimary)
                            }
                        }
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
                    shape = RectangleShape,
                    color = CyberInk.copy(alpha = 0.94f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberAcid),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = CyberAcid, modifier = Modifier.size(20.dp))
                    }
                }

                // Zoom Out Button (-)
                Surface(
                    onClick = {
                        userInteracted = true
                        mapLibreMapInstance?.animateCamera(CameraUpdateFactory.zoomOut())
                    },
                    shape = RectangleShape,
                    color = CyberInk.copy(alpha = 0.94f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberAcid),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = CyberAcid, modifier = Modifier.size(20.dp))
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
                    shape = RectangleShape,
                    color = CyberInk.copy(alpha = 0.94f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberAcid),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Centralizar", tint = CyberCyan, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

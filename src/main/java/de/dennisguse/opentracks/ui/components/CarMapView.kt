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
import de.dennisguse.opentracks.util.RouteAnalysis
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

/** Sequential encoding for the frequency map: (alpha, line width) by number of trips. */
fun heatStyle(trips: Int): Pair<Float, Float> = when {
    trips >= 7 -> 1f to 9f
    trips >= 4 -> 0.85f to 7f
    trips >= 2 -> 0.6f to 5f
    else -> 0.32f to 3.2f
}

/** Vector styles (Noite, Ruas) stay sharp at any zoom and screen density. */
enum class MapLayer(val label: String, val styleUri: String) {
    DARK("Noite", "asset://maplibre/style_night.json"),
    STREET("Ruas", "asset://maplibre/style_streets.json"),
    SATELLITE("Satélite", "asset://maplibre/style_satellite.json");

    companion object {
        private const val PREF = "map_layer"

        fun load(context: Context): MapLayer =
            context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE).getString(PREF, null)
                ?.let { name -> values().firstOrNull { it.name == name } } ?: DARK

        fun save(context: Context, layer: MapLayer) =
            context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE).edit().putString(PREF, layer.name).apply()
    }
}

/**
 * Modern High-Performance MapLibre GL Surface Map Composable for CarTracks.
 * Features:
 * - Hardware-accelerated OpenGL / Vulkan vector & raster rendering.
 * - Layer switching: Noite and Ruas (OpenFreeMap vector), Satélite (Esri raster).
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
    onCenterGpsClicked: (() -> Unit)? = null,
    /** Trips drawn by how many of them share each stretch (repeated commutes become one strong line). */
    heatRoutes: List<List<TripPoint>> = emptyList(),
    /** Trips highlighted on top (e.g. one route group picked in a list); the camera frames them. */
    focusRoutes: List<List<TripPoint>> = emptyList(),
    /** Receives a function that renders the current map to a bitmap (for exporting reports). */
    onSnapshotProvider: ((suspend () -> Bitmap?) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedLayer by remember { mutableStateOf(MapLayer.load(context)) }

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
    fun getStyleUri(layer: MapLayer): String = layer.styleUri

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
    val heat = remember(heatRoutes) { RouteAnalysis.heatSegments(heatRoutes) }

    // Everything on the map, for the recenter button and the first framing
    fun contentBounds(): LatLngBounds? {
        val all = (focusRoutes.takeIf { it.isNotEmpty() } ?: (listOf(points) + multiRoutes + heatRoutes)).flatten()
        if (all.isEmpty()) return null
        val b = LatLngBounds.Builder()
        all.forEach { b.include(LatLng(it.lat, it.lon)) }
        if (all.size == 1) b.include(LatLng(all[0].lat + 0.002, all[0].lon + 0.002))
        return runCatching { b.build() }.getOrNull()
    }

    LaunchedEffect(mapLibreMapInstance, isMapStyleLoaded) {
        val map = mapLibreMapInstance ?: return@LaunchedEffect
        onSnapshotProvider?.invoke {
            kotlinx.coroutines.suspendCancellableCoroutine { cont -> map.snapshot { bmp -> if (cont.isActive) cont.resumeWith(Result.success(bmp)) } }
        }
    }

    LaunchedEffect(isMapStyleLoaded, points, multiRoutes, extraMarkers, pinnedLocation, currentLocation, heat, focusRoutes) {
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

        // 2b. Frequency map: one hue, stronger and wider where more trips share the road
        if (heat.isNotEmpty()) {
            heatRoutes.filter { it.size >= 2 }.forEach { route ->
                map.addPolyline(PolylineOptions().addAll(route.map { LatLng(it.lat, it.lon) }).color(AndroidColor.parseColor("#0E0D1A")).width(7f))
            }
            heat.sortedBy { it.trips }.forEach { seg ->
                val (alpha, width) = heatStyle(seg.trips)
                map.addPolyline(
                    PolylineOptions()
                        .addAll(seg.points.map { LatLng(it.lat, it.lon) })
                        .color(AndroidColor.argb((alpha * 255).toInt(), 0xE8, 0xFF, 0x3A))
                        .width(width)
                )
            }
        }

        // 2c. Focused routes on top
        focusRoutes.filter { it.size >= 2 }.forEach { route ->
            val latLngs = route.map { LatLng(it.lat, it.lon) }
            map.addPolyline(PolylineOptions().addAll(latLngs).color(AndroidColor.parseColor("#0E0D1A")).width(10f))
            map.addPolyline(PolylineOptions().addAll(latLngs).color(AndroidColor.parseColor("#F478D9")).width(5.5f))
        }
        if (focusRoutes.isNotEmpty() || (heat.isNotEmpty() && !userInteracted)) {
            contentBounds()?.let { runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(it, 120)) } }
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
            if (!userInteracted && points.size < 2) {
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
            if (!userInteracted && points.size < 2 && pinnedLocation == null) {
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
                                .clickable {
                                    selectedLayer = layer
                                    MapLayer.save(context, layer)
                                }
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

            if (heat.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .background(CyberInk.copy(alpha = 0.9f), RectangleShape)
                        .border(1.dp, CyberLine, RectangleShape)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text("VIAGENS NO TRECHO", style = EyebrowStyle.copy(fontSize = 9.sp), color = TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        listOf(1 to "1×", 2 to "2-3×", 4 to "4-6×", 7 to "7+×").forEach { (n, label) ->
                            val (a, w) = heatStyle(n)
                            Column(Modifier.padding(end = 4.dp)) {
                                Box(Modifier.size(width = 26.dp, height = (w / 2).dp).background(CyberAcid.copy(alpha = a)))
                                Text(label, style = monoStyle(9, FontWeight.Normal), color = TextPrimary)
                            }
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
                        } else {
                            contentBounds()?.let { b ->
                                runCatching { mapLibreMapInstance?.animateCamera(CameraUpdateFactory.newLatLngBounds(b, 130)) }
                            }
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

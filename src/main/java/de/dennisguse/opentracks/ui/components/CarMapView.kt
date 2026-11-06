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
import androidx.compose.ui.graphics.toArgb
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
import de.dennisguse.opentracks.util.HeatSegment
import de.dennisguse.opentracks.util.RouteAnalysis
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
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

    /** Small chevron pointing along +x; line-placed symbols turn it to the direction of travel. */
    fun getArrowBitmap(context: Context): Bitmap = cache.getOrPut("arrow") {
        val density = context.resources.displayMetrics.density
        val size = (13 * density).toInt().coerceAtLeast(20)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = 1.6f * density
        val path = android.graphics.Path().apply {
            moveTo(inset, inset)
            lineTo(size - inset, size / 2f)
            lineTo(inset, size - inset)
            lineTo(size * 0.32f, size / 2f)
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = AndroidColor.parseColor("#0E0D1A") }
        canvas.drawPath(path, paint)
        // light rim keeps the ink chevron visible on any route colour and on dark tiles
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.3f * density
        paint.color = AndroidColor.parseColor("#F3F5E6")
        canvas.drawPath(path, paint)
        bitmap
    }

    /** Destination badge: ink frame, magenta square, number of arrivals in the middle. */
    fun getHotspotBitmap(context: Context, count: Int): Bitmap = cache.getOrPut("hot$count") {
        val density = context.resources.displayMetrics.density
        val label = if (count > 99) "99+" else count.toString()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#FBEFF8")
            textSize = 12 * density
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val shadow = 3 * density
        val h = 26 * density
        val w = maxOf(h, text.measureText(label) + 12 * density)
        val bitmap = Bitmap.createBitmap((w + shadow).toInt(), (h + shadow).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = AndroidColor.parseColor("#990E0D1A")
        canvas.drawRect(shadow, shadow, w + shadow, h + shadow, paint)
        paint.color = AndroidColor.parseColor("#0E0D1A")
        canvas.drawRect(0f, 0f, w, h, paint)
        val b = 2.2f * density
        paint.color = AndroidColor.parseColor("#C5259C")
        canvas.drawRect(b, b, w - b, h - b, paint)
        canvas.drawText(label, w / 2f, h / 2f - (text.descent() + text.ascent()) / 2f, text)
        bitmap
    }

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

/** A place many trips end at, drawn as a numbered badge on the frequency map. */
data class MapHotspot(val lat: Double, val lon: Double, val count: Int, val title: String)

/** One step of the frequency scale: stretches crossed by [min]..[max] trips. */
data class HeatBin(val min: Int, val max: Int, val color: Color, val width: Float) {
    val label get() = if (min == max) "$min×" else "$min–$max×"
}

private val HEAT_RAMP = listOf(CyberBlue, CyberCyan, CyberAcid, CyberPink)
private val HEAT_WIDTHS = listOf(3.2f, 5f, 7f, 9.5f)

/**
 * Sequential scale relative to the busiest stretch of the period, so a month with three commutes
 * still spans the whole ramp. Colour and width both grow with the trip count.
 */
fun heatBins(maxTrips: Int): List<HeatBin> {
    val top = maxTrips.coerceAtLeast(1)
    val bounds = if (top <= 4) (1..top).map { it to it } else {
        val a = maxOf(2, Math.round(top * 0.25f))
        val b = maxOf(a + 1, Math.round(top * 0.6f))
        listOf(1 to 1, 2 to a, a + 1 to b, b + 1 to top)
    }
    val n = bounds.size
    return bounds.mapIndexed { i, (lo, hi) ->
        // the busiest bin is always the hottest colour; fewer bins skip the coldest steps
        val step = if (n == 1) 2 else HEAT_RAMP.lastIndex - (n - 1 - i)
        HeatBin(lo, hi, HEAT_RAMP[step], HEAT_WIDTHS[step])
    }
}

private fun heatBinOf(bins: List<HeatBin>, trips: Int) = bins.lastOrNull { trips >= it.min } ?: bins.first()

private const val ARROW_SOURCE = "cartracks-direction-source"
private const val ARROW_LAYER = "cartracks-direction-arrows"
private const val ARROW_IMAGE = "cartracks-direction-arrow"

/**
 * Chevrons along each route pointing the way it was driven. A style layer (not markers) so they
 * follow the line and turn with the map; added on top after every redraw, above the annotation
 * polylines, which MapLibre inserts just under its marker layer.
 */
private fun drawDirectionArrows(context: Context, style: Style, routes: List<List<TripPoint>>) {
    style.getLayer(ARROW_LAYER)?.let { style.removeLayer(it) }
    style.getSource(ARROW_SOURCE)?.let { style.removeSource(it) }
    val lines = routes.filter { it.size >= 2 }
    if (lines.isEmpty()) return
    if (style.getImage(ARROW_IMAGE) == null) style.addImage(ARROW_IMAGE, ModernMarkerFactory.getArrowBitmap(context))
    val features = lines.map { r -> Feature.fromGeometry(LineString.fromLngLats(r.map { Point.fromLngLat(it.lon, it.lat) })) }
    style.addSource(GeoJsonSource(ARROW_SOURCE, FeatureCollection.fromFeatures(features)))
    style.addLayer(
        SymbolLayer(ARROW_LAYER, ARROW_SOURCE).withProperties(
            PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
            PropertyFactory.symbolSpacing(90f),
            PropertyFactory.iconImage(ARROW_IMAGE),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconKeepUpright(false),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP)
        )
    )
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
    /** Numbered badges for frequent destinations on the frequency map. */
    hotspots: List<MapHotspot> = emptyList(),
    /** Small chevrons showing the direction of travel on single, individual and focused routes. */
    showDirectionArrows: Boolean = true,
    /** Receives a function that renders the current map to a bitmap (for exporting reports). */
    onSnapshotProvider: ((suspend () -> Bitmap?) -> Unit)? = null,
    /** Live trip: camera stays on the car, tilted and turned to [vehicleBearing], until the user drags the map. */
    followVehicle: Boolean = false,
    vehicleBearing: Float? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedLayer by remember { mutableStateOf(MapLayer.load(context)) }

    var mapLibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var isMapStyleLoaded by remember { mutableStateOf(false) }
    var userInteracted by remember { mutableStateOf(false) }
    // moved in place on every GPS fix instead of clearing and redrawing the whole map
    val vehicleMarker = remember { arrayOfNulls<Marker>(1) }

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
    // densifying a long period is heavy; compute it off the UI thread
    val heat by produceState(emptyList<HeatSegment>(), heatRoutes) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { RouteAnalysis.heatSegments(heatRoutes) }
    }
    val bins = remember(heat) { heatBins(heat.maxOfOrNull { it.trips } ?: 1) }

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

    LaunchedEffect(isMapStyleLoaded, points, multiRoutes, extraMarkers, pinnedLocation, heat, focusRoutes, hotspots) {
        val map = mapLibreMapInstance ?: return@LaunchedEffect
        if (!isMapStyleLoaded) return@LaunchedEffect

        map.clear()
        vehicleMarker[0] = null
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

            // Auto-frame route camera (a followed car keeps the camera on itself)
            if (!userInteracted && !followVehicle) {
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

        // 2b. Frequency map: colour and width climb with the number of trips sharing the road and
        // the busiest bin glows. Faded while a route group is focused on top of it.
        if (heat.isNotEmpty()) {
            val fade = if (focusRoutes.isNotEmpty()) 0.35f else 1f
            heatRoutes.filter { it.size >= 2 }.forEach { route ->
                map.addPolyline(PolylineOptions().addAll(route.map { LatLng(it.lat, it.lon) }).color(AndroidColor.parseColor("#0E0D1A")).width(6f).alpha(fade))
            }
            val top = bins.last()
            if (bins.size > 1) {
                heat.filter { it.trips >= top.min }.forEach { seg ->
                    map.addPolyline(
                        PolylineOptions().addAll(seg.points.map { LatLng(it.lat, it.lon) })
                            .color(top.color.toArgb()).width(top.width + 9f).alpha(0.22f * fade)
                    )
                }
            }
            heat.sortedBy { it.trips }.forEach { seg ->
                val bin = heatBinOf(bins, seg.trips)
                map.addPolyline(
                    PolylineOptions()
                        .addAll(seg.points.map { LatLng(it.lat, it.lon) })
                        .color(bin.color.toArgb())
                        .width(bin.width)
                        .alpha((if (bin == bins.first() && bins.size > 1) 0.7f else 1f) * fade)
                )
            }
            if (focusRoutes.isEmpty()) {
                hotspots.forEach { h ->
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(h.lat, h.lon))
                            .icon(iconFactory.fromBitmap(ModernMarkerFactory.getHotspotBitmap(context, h.count)))
                            .title(h.title)
                            .snippet("${h.count} chegadas no período")
                    )
                }
            }
        }

        // 2c. Focused routes on top
        focusRoutes.filter { it.size >= 2 }.forEach { route ->
            val latLngs = route.map { LatLng(it.lat, it.lon) }
            map.addPolyline(PolylineOptions().addAll(latLngs).color(AndroidColor.parseColor("#0E0D1A")).width(10f))
            map.addPolyline(PolylineOptions().addAll(latLngs).color(AndroidColor.parseColor("#F478D9")).width(5.5f))
        }
        focusRoutes.firstOrNull { it.size >= 2 }?.let { r ->
            map.addMarker(MarkerOptions().position(LatLng(r.first().lat, r.first().lon)).icon(iconFactory.fromBitmap(ModernMarkerFactory.getStartMarkerBitmap(context))).title("Partida"))
            map.addMarker(MarkerOptions().position(LatLng(r.last().lat, r.last().lon)).icon(iconFactory.fromBitmap(ModernMarkerFactory.getEndMarkerBitmap(context))).title("Chegada"))
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

        // 5. Current vehicle marker: drawn by its own effect below
        currentLocation?.let { (lat, lon) ->
            vehicleMarker[0] = map.addMarker(
                MarkerOptions()
                    .position(LatLng(lat, lon))
                    .icon(iconFactory.fromBitmap(ModernMarkerFactory.getGpsVehicleBitmap(context)))
                    .title("Veículo")
            )
        }

        // 6. Direction of travel (the frequency map mixes both ways, so only its focused group)
        map.style?.let { style ->
            val directed = if (showDirectionArrows) focusRoutes + multiRoutes + listOf(points) else emptyList()
            runCatching { drawDirectionArrows(context, style, directed) }
        }
    }

    // Vehicle position and follow camera, without touching the routes drawn above
    LaunchedEffect(currentLocation, vehicleBearing, followVehicle, userInteracted, isMapStyleLoaded) {
        val map = mapLibreMapInstance ?: return@LaunchedEffect
        if (!isMapStyleLoaded) return@LaunchedEffect
        val loc = currentLocation
        if (loc == null) {
            vehicleMarker[0]?.let { runCatching { map.removeMarker(it) } }
            vehicleMarker[0] = null
            return@LaunchedEffect
        }
        val gpsLatLng = LatLng(loc.first, loc.second)
        val marker = vehicleMarker[0]
        if (marker != null) {
            marker.position = gpsLatLng
        } else {
            vehicleMarker[0] = map.addMarker(
                MarkerOptions()
                    .position(gpsLatLng)
                    .icon(IconFactory.getInstance(context).fromBitmap(ModernMarkerFactory.getGpsVehicleBitmap(context)))
                    .title("Veículo")
            )
        }
        if (userInteracted) return@LaunchedEffect
        if (followVehicle) {
            map.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(gpsLatLng).zoom(16.5).tilt(35.0)
                    .apply { vehicleBearing?.let { bearing(it.toDouble()) } }.build()), 800)
        } else if (points.size < 2 && pinnedLocation == null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(gpsLatLng, 16.5))
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
                    Row(verticalAlignment = Alignment.Bottom) {
                        bins.forEach { bin ->
                            Column(Modifier.padding(end = 4.dp)) {
                                Box(Modifier.size(width = 30.dp, height = (bin.width / 1.6f).dp).background(bin.color))
                                Text(bin.label, style = monoStyle(9, FontWeight.Normal), color = TextPrimary)
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
                            val gpsLatLng = LatLng(currentLocation.first, currentLocation.second)
                            mapLibreMapInstance?.animateCamera(
                                if (followVehicle) CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(gpsLatLng).zoom(16.5).tilt(35.0)
                    .apply { vehicleBearing?.let { bearing(it.toDouble()) } }.build())
                                else CameraUpdateFactory.newLatLngZoom(gpsLatLng, 16.5)
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

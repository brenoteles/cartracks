package de.dennisguse.opentracks.ui.components

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
import de.dennisguse.opentracks.ui.model.TripPoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun CarMapView(
    modifier: Modifier = Modifier,
    points: List<TripPoint> = emptyList(),
    multiRoutes: List<List<TripPoint>> = emptyList(),
    currentLocation: Pair<Double, Double>? = null,
    onPointClicked: ((TripPoint) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(-23.5874, -46.6576)) // Default São Paulo / Brazil
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { mapView },
        update = { map ->
            map.overlays.clear()

            // 1. Single Route Polyline
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
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                map.overlays.add(startMarker)

                // End Marker
                if (points.size > 1) {
                    val endPt = points.last()
                    val endMarker = Marker(map).apply {
                        position = GeoPoint(endPt.lat, endPt.lon)
                        title = "Chegada"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    map.overlays.add(endMarker)
                }

                try {
                    val box = BoundingBox.fromGeoPoints(geoPoints)
                    map.post {
                        map.zoomToBoundingBox(box.increaseByScale(1.3f), true)
                    }
                } catch (e: Exception) {
                    map.controller.setCenter(geoPoints.first())
                }
            }

            // 2. Multi-Routes (for Period Overview)
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

            // 3. Current Location Marker (Live GPS tracking)
            currentLocation?.let { (lat, lon) ->
                val curPoint = GeoPoint(lat, lon)
                val curMarker = Marker(map).apply {
                    position = curPoint
                    title = "Posição Atual"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                map.overlays.add(curMarker)
                if (points.isEmpty() && multiRoutes.isEmpty()) {
                    map.controller.animateTo(curPoint)
                }
            }

            map.invalidate()
        }
    )
}

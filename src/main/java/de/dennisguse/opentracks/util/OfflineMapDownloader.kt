package de.dennisguse.opentracks.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/**
 * Downloads the Grande São Paulo area for offline use with MapLibre's OfflineManager.
 * Both vector styles (Noite and Ruas) are stored; they share the OpenFreeMap tiles, so the
 * second pass mostly fetches style, sprites and fonts. Vector tiles end at z14 and are
 * overzoomed by the renderer, so z10-14 covers street-level detail.
 */
object OfflineMapDownloader {

    // Grande SP: Caieiras/Mairiporã (N) to Parelheiros (S), Cotia (W) to Mogi das Cruzes (E)
    private val GRANDE_SP = LatLngBounds.from(-23.25, -46.15, -24.0, -47.1)
    private const val MIN_ZOOM = 10.0
    private const val MAX_ZOOM = 14.0
    private val STYLES = listOf("asset://maplibre/style_night.json", "asset://maplibre/style_streets.json")

    fun download(
        context: Context,
        onProgress: (percent: Int, megabytes: Double) -> Unit,
        onFinished: (success: Boolean, message: String) -> Unit
    ) {
        MapLibre.getInstance(context)
        val manager = OfflineManager.getInstance(context)
        manager.setOfflineMapboxTileCountLimit(20_000)
        val main = Handler(Looper.getMainLooper())
        val pixelRatio = context.resources.displayMetrics.density
        var bytesBefore = 0L

        fun downloadStyle(index: Int) {
            if (index >= STYLES.size) {
                onFinished(true, String.format("Grande SP offline: %.0f MB baixados", bytesBefore / 1_048_576.0))
                return
            }
            val definition = OfflineTilePyramidRegionDefinition(STYLES[index], GRANDE_SP, MIN_ZOOM, MAX_ZOOM, pixelRatio)
            manager.createOfflineRegion(definition, "grande-sp-$index".toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val required = status.requiredResourceCount.coerceAtLeast(1)
                            val stylePercent = 100.0 * status.completedResourceCount / required
                            val overall = ((index * 100 + stylePercent) / STYLES.size).toInt()
                            main.post { onProgress(overall, (bytesBefore + status.completedResourceSize) / 1_048_576.0) }
                            if (status.isComplete) {
                                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                offlineRegion.setObserver(null)
                                bytesBefore += status.completedResourceSize
                                main.post { downloadStyle(index + 1) }
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            // Transient network errors are retried by MapLibre; only report.
                            main.post { onProgress(-1, bytesBefore / 1_048_576.0) }
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            main.post { onFinished(false, "Limite de $limit tiles excedido") }
                        }
                    })
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    main.post { onFinished(false, "Falha ao criar região offline: $error") }
                }
            })
        }

        // Replace previous downloads instead of stacking duplicate regions.
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions.orEmpty()
                var pending = regions.size
                if (pending == 0) {
                    downloadStyle(0)
                    return
                }
                regions.forEach { region ->
                    region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() {
                            if (--pending == 0) downloadStyle(0)
                        }

                        override fun onError(error: String) {
                            if (--pending == 0) downloadStyle(0)
                        }
                    })
                }
            }

            override fun onError(error: String) = downloadStyle(0)
        })
    }
}

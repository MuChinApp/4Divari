package ir.chardivari.feature.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Provider seam (DEPENDENCY_PLAN: "provider abstraction first").
 * Swapping osmdroid for Play Services Maps / MapLibre means replacing this
 * file only — [MapPin] stays the module contract. Keyless OSM tiles were
 * chosen deliberately: no API key exists for Play Maps in this project.
 */
@Composable
internal fun OsmMapView(
    pins: List<MapPin>,
    onPinClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnClick by rememberUpdatedState(onPinClick)
    val latestPins by rememberUpdatedState(pins)
    var mapView by remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        factory = { ctx ->
            Configuration.getInstance().userAgentValue =
                "${ctx.packageName}/0.1.0 (4Divari)"
            MapView(ctx).also { map ->
                map.setTileSource(TileSourceFactory.MAPNIK)
                map.setMultiTouchControls(true)
                map.controller.setZoom(MAP_DEFAULT_ZOOM)
                map.controller.setCenter(MAP_DEFAULT_CENTER)
                map.onResume()
                mapView = map
            }
        },
        update = { map ->
            syncPins(map = map, pins = latestPins, onClick = { id -> latestOnClick(id) })
        },
        onRelease = { map ->
            map.onPause()
            map.onDetach()
        },
        modifier = modifier.fillMaxSize(),
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

private fun syncPins(
    map: MapView,
    pins: List<MapPin>,
    onClick: (String) -> Unit,
) {
    map.overlays.clear()
    pins.forEach { pin ->
        Marker(map).also { marker ->
            marker.position = GeoPoint(pin.latitude, pin.longitude)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = pin.label
            marker.setOnMarkerClickListener { _, _ ->
                onClick(pin.listingId)
                true
            }
            map.overlays.add(marker)
        }
    }
    map.invalidate()
}

/** Tehran — first frame before camera moves to content. */
private val MAP_DEFAULT_CENTER = GeoPoint(35.6892, 51.3890)
private const val MAP_DEFAULT_ZOOM = 11.0

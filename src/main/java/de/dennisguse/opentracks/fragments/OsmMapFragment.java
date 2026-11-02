package de.dennisguse.opentracks.fragments;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.List;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.TrackPointIterator;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;

public class OsmMapFragment extends Fragment {

    private static final String EXTRA_TRACK_ID = "extra_track_id";
    private MapView mapView;
    private Track.Id trackId;

    public static OsmMapFragment newInstance(Track.Id trackId) {
        OsmMapFragment fragment = new OsmMapFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_TRACK_ID, trackId);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context ctx = requireContext().getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(ctx.getPackageName());

        if (getArguments() != null) {
            trackId = getArguments().getParcelable(EXTRA_TRACK_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_osm_map, container, false);
        mapView = root.findViewById(R.id.osm_mapview);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(13.0);

        loadTrackRoute();
        return root;
    }

    private void loadTrackRoute() {
        if (trackId == null) return;
        new Thread(() -> {
            ContentProviderUtils provider = new ContentProviderUtils(requireContext());
            List<GeoPoint> geoPoints = new ArrayList<>();
            try (TrackPointIterator it = provider.getTrackPointIterator(trackId, null)) {
                while (it.hasNext()) {
                    TrackPoint tp = it.next();
                    if (tp != null && tp.position().hasLocation()) {
                        geoPoints.add(new GeoPoint(tp.position().latitude(), tp.position().longitude()));
                    }
                }
            } catch (Exception ignored) {}

            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (mapView == null || geoPoints.isEmpty()) return;

                Polyline line = new Polyline(mapView);
                line.setPoints(geoPoints);
                line.setColor(Color.parseColor("#0288D1")); // Automotive blue
                line.setWidth(10.0f);
                mapView.getOverlays().add(line);

                // Start Marker
                Marker startMarker = new Marker(mapView);
                startMarker.setPosition(geoPoints.get(0));
                startMarker.setTitle("Início da Viagem");
                startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                mapView.getOverlays().add(startMarker);

                // End Marker
                Marker endMarker = new Marker(mapView);
                endMarker.setPosition(geoPoints.get(geoPoints.size() - 1));
                endMarker.setTitle("Destino / Término");
                endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                mapView.getOverlays().add(endMarker);

                mapView.getController().setCenter(geoPoints.get(geoPoints.size() / 2));
                mapView.invalidate();
            });
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }
}
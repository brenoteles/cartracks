package de.dennisguse.opentracks;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.FuelDatabaseHelper;
import de.dennisguse.opentracks.data.MockDataGenerator;
import de.dennisguse.opentracks.data.TrackListIterator;
import de.dennisguse.opentracks.data.TrackPointIterator;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.services.TrackRecordingService;
import de.dennisguse.opentracks.services.TrackRecordingServiceConnection;
import de.dennisguse.opentracks.settings.SettingsActivity;
import de.dennisguse.opentracks.util.IntentUtils;

public class CarMainActivity extends AppCompatActivity {

    private WebView webView;
    private ContentProviderUtils providerUtils;
    private FuelDatabaseHelper fuelDb;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean fine = Boolean.TRUE.equals(result.get(android.Manifest.permission.ACCESS_FINE_LOCATION));
                    boolean coarse = Boolean.TRUE.equals(result.get(android.Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (fine || coarse) {
                        doStartTrip();
                    } else {
                        Toast.makeText(this, "Permissão de GPS necessária para gravar viagens de carro.", Toast.LENGTH_LONG).show();
                    }
                }
        );

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(android.graphics.Color.parseColor("#000000"));
            getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#141416"));
        }

        MockDataGenerator.populateMockDataIfEmpty(this);

        providerUtils = new ContentProviderUtils(this);
        fuelDb = FuelDatabaseHelper.getInstance(this);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 CarTracksApp/1.0");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                android.util.Log.d("CarTracksJS", consoleMessage.message() + " -- From line "
                        + consoleMessage.lineNumber() + " of " + consoleMessage.sourceId());
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                webView.loadUrl("javascript:loadDataFromAndroid()");
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                android.util.Log.e("CarTracksWeb", "Error " + errorCode + ": " + description + " URL: " + failingUrl);
            }

            @Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                handler.proceed();
            }
        });

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.loadUrl("javascript:loadDataFromAndroid()");
        }
    }

    public class WebAppInterface {

        @JavascriptInterface
        public String getTripsJson() {
            JSONArray arr = new JSONArray();
            try {
                try (TrackListIterator it = providerUtils.searchTracks(null)) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                    while (it != null && it.hasNext()) {
                        TrackListIterator.Item item = it.next();
                        JSONObject obj = new JSONObject();
                        obj.put("id", item.id().id());
                        obj.put("name", item.name());
                        obj.put("desc", item.description());
                        double km = item.totalDistance().toKM();
                        obj.put("distance", String.format(java.util.Locale.getDefault(), "%.1f km", km));
                        obj.put("date", item.startTime().atOffset(item.zoneOffset()).format(formatter));
                        long minutes = item.totalTime().toMinutes();
                        obj.put("duration", minutes + " min");

                        Track track = providerUtils.getTrack(item.id());
                        double maxSpeedVal = 0;
                        double avgSpeedVal = 0;
                        if (track != null && track.statistics() != null) {
                            if (track.statistics().maxSpeed() != null) {
                                maxSpeedVal = track.statistics().maxSpeed().toKMH();
                            }
                            if (track.statistics().getAverageSpeed() != null) {
                                avgSpeedVal = track.statistics().getAverageSpeed().toKMH();
                            }
                        }
                        if (avgSpeedVal <= 0) {
                            double hours = item.totalTime().getSeconds() / 3600.0;
                            avgSpeedVal = hours > 0 ? (km / hours) : 45.0;
                        }
                        if (maxSpeedVal <= 0) {
                            maxSpeedVal = avgSpeedVal * 1.35;
                        }

                        obj.put("avgSpeed", String.format(java.util.Locale.getDefault(), "%.0f km/h", avgSpeedVal));
                        obj.put("maxSpeed", String.format(java.util.Locale.getDefault(), "%.0f km/h", maxSpeedVal));
                        double tripFuel = km * 0.08;
                        obj.put("estFuel", String.format(java.util.Locale.getDefault(), "%.1f L", tripFuel));
                        obj.put("estCost", String.format(java.util.Locale.getDefault(), "R$ %.2f", tripFuel * 5.89));
                        arr.put(obj);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return arr.toString();
        }

        @JavascriptInterface
        public String getTripPointsJson(long trackId) {
            JSONArray arr = new JSONArray();
            try {
                try (TrackPointIterator it = providerUtils.getTrackPointIterator(new Track.Id(trackId), null)) {
                    while (it.hasNext()) {
                        TrackPoint tp = it.next();
                        if (tp != null && tp.position().hasLocation()) {
                            JSONObject obj = new JSONObject();
                            obj.put("lat", tp.position().latitude());
                            obj.put("lon", tp.position().longitude());
                            double speedKmh = (tp.position().hasSpeed() && tp.position().speed() != null)
                                    ? tp.position().speed().toKMH() : 0.0;
                            obj.put("speed", speedKmh);
                            arr.put(obj);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return arr.toString();
        }

        @JavascriptInterface
        public String getFuelJson() {
            JSONArray arr = new JSONArray();
            try {
                List<FuelDatabaseHelper.FuelEntry> entries = fuelDb.getAllEntries();
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
                for (FuelDatabaseHelper.FuelEntry e : entries) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", e.id);
                    obj.put("station", e.stationName);
                    obj.put("liters", e.liters);
                    obj.put("price", e.pricePerLiter);
                    obj.put("total", e.totalCost);
                    obj.put("odometer", (int) e.odometerKm);
                    obj.put("date", sdf.format(new java.util.Date(e.dateTime)));
                    arr.put(obj);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return arr.toString();
        }

        @JavascriptInterface
        public String getGarageStatsJson() {
            JSONObject obj = new JSONObject();
            try {
                double totalKm = 0;
                try (TrackListIterator it = providerUtils.searchTracks(null)) {
                    while (it != null && it.hasNext()) {
                        totalKm += it.next().totalDistance().toKM();
                    }
                }
                double fuelEstLiters = totalKm * 0.08;
                double fuelEstCost = fuelEstLiters * 5.89;

                obj.put("totalKm", String.format(java.util.Locale.getDefault(), "%.1f", totalKm));
                obj.put("fuelLiters", String.format(java.util.Locale.getDefault(), "%.1f", fuelEstLiters));
                obj.put("fuelCost", String.format(java.util.Locale.getDefault(), "%.2f", fuelEstCost));
                obj.put("avgKmL", "12.5");
                obj.put("favDest", "Escritório Paulista (45%)");
                obj.put("favStation", "Shell Jardins (65%)");
            } catch (Exception e) {
                e.printStackTrace();
            }
            return obj.toString();
        }

        @JavascriptInterface
        public void saveFuel(String station, double liters, double price, double total, double odo, String notes) {
            fuelDb.addEntry(station, liters, price, total, odo, notes);
        }

        @JavascriptInterface
        public void startNewTrip() {
            runOnUiThread(() -> {
                boolean hasFine = ContextCompat.checkSelfPermission(CarMainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                boolean hasCoarse = ContextCompat.checkSelfPermission(CarMainActivity.this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                if (!hasFine && !hasCoarse) {
                    List<String> perms = new ArrayList<>();
                    perms.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
                    perms.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms.add(android.Manifest.permission.POST_NOTIFICATIONS);
                    }
                    locationPermissionLauncher.launch(perms.toArray(new String[0]));
                } else {
                    doStartTrip();
                }
            });
        }

        @JavascriptInterface
        public void openSettings() {
            startActivity(new Intent(CarMainActivity.this, SettingsActivity.class));
        }
    }

    private void doStartTrip() {
        try {
            TrackRecordingServiceConnection.executeForeground(CarMainActivity.this, (service, connection) -> {
                if (!service.isRecording()) {
                    Track.Id trackId = service.startNewTrack();
                    Toast.makeText(CarMainActivity.this, "Viagem iniciada! Gravando trajeto no GPS...", Toast.LENGTH_SHORT).show();
                    Intent intent = IntentUtils.newIntent(CarMainActivity.this, TrackRecordingActivity.class)
                            .putExtra(TrackRecordingActivity.EXTRA_TRACK_ID, trackId);
                    startActivity(intent);
                } else {
                    Toast.makeText(CarMainActivity.this, "Já existe uma viagem em gravação.", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(CarMainActivity.this, "Erro ao iniciar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
}
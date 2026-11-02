package de.dennisguse.opentracks.sensors.driver;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.location.LocationListenerCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.location.LocationRequestCompat;

import de.dennisguse.opentracks.settings.PreferencesUtils;
import de.dennisguse.opentracks.util.PermissionRequester;

public class GpsInternal implements Driver {

    private static final String TAG = GpsInternal.class.getSimpleName();

    public static final String LOCATION_PROVIDER = LocationManager.GPS_PROVIDER;

    @VisibleForTesting
    public final LocationListenerCompat locationListenerCompat = new LocationListenerCompat() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            driverObserver.onDataReceived(location);
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
            driverObserver.onConnected(null, provider);
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            driverObserver.onSensorDeactivated();
        }
    };

    private final DriverObserver<Location> driverObserver;

    @VisibleForTesting
    public LocationManager locationManager;

    public GpsInternal(DriverObserver<Location> driverObserver) {
        this.driverObserver = driverObserver;
    }

    @Override
    public void connect(Context context, Handler handler, String address) {
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        if (!LocationManagerCompat.hasProvider(locationManager, LOCATION_PROVIDER)) {
            Log.e(TAG, "Device doesn't have GPS.");
            return;
        }

        if (PermissionRequester.GPS.hasPermission(context)) {
            try {
                Log.i(TAG, "Register for location updates");
                driverObserver.onConnected(null, null);
                LocationManagerCompat.requestLocationUpdates(locationManager, LOCATION_PROVIDER, createLocationRequest(), handler::post, locationListenerCompat);
                return;
            } catch (SecurityException e) {
                Log.e(TAG, "Could not register location listener; permissions not granted.", e);
            }
        }

        driverObserver.onDisconnected();
    }

    @SuppressWarnings({"MissingPermission"})
    @Override
    public void updateDriverParameters(Context context, Handler handler) {
        if (!isConnected()) return;

        Log.i(TAG, "Update for location updates");
        driverObserver.onConnected(null, null);
        LocationManagerCompat.requestLocationUpdates(locationManager, LOCATION_PROVIDER, createLocationRequest(), handler::post, locationListenerCompat);
    }

    @SuppressWarnings({"MissingPermission"})
    @Override
    public void disconnect() {
        if (!isConnected()) return;

        LocationManagerCompat.removeUpdates(locationManager, locationListenerCompat);

        locationManager = null;
        driverObserver.onDisconnected();
    }

    @Override
    public boolean isConnected() {
        return locationManager != null;
    }

    private LocationRequestCompat createLocationRequest() {
        return new LocationRequestCompat.Builder(PreferencesUtils.getMinSamplingInterval().toMillis())
                .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
                .setMaxUpdateDelayMillis(0)
                .build();
    }
}

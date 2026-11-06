package de.dennisguse.opentracks.util;

import android.location.Location;

import androidx.annotation.NonNull;

import java.time.Instant;

import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.sensors.SensorManager;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorBarometer;
import de.dennisguse.opentracks.services.handlers.TrackPointCreator;

/**
 * Feeds fake GPS fixes (and optional barometric altitude change) into the recording pipeline.
 * Extracted from OpenTracks' ExportImportTest, which was removed with the GPX/KML importers.
 */
public final class MockLocations {

    private MockLocations() {
    }

    public static Position sendLocation(TrackPointCreator trackPointCreator, String time, double latitude, double longitude, float accuracy, float verticalAccuracy, float speed, double altitude, Float altitudeGain) {
        Location location = new Location("mock");
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setAccuracy(accuracy);
        location.setVerticalAccuracyMeters(verticalAccuracy);
        location.setSpeed(speed);
        location.setAltitude(altitude);

        mockAltitudeChange(trackPointCreator, altitudeGain);

        trackPointCreator.setClock(time);
        trackPointCreator.getSensorManager().getGpsHandler().onDataReceived(location);

        return Position.of(location, Instant.parse(time));
    }

    private static void mockAltitudeChange(TrackPointCreator trackPointCreator, Float altitudeGain) {
        SensorManager sensorManager = trackPointCreator.getSensorManager();

        if (altitudeGain == null) {
            sensorManager.getAltitudeChangeHandler().setAggregator(new AggregatorBarometer("test", null));
            return;
        }

        AggregatorBarometer aggregatorBarometer = new AggregatorBarometer("", "") {
            @NonNull
            @Override
            public AltitudeGainLoss getAggregatedValue(Instant now) {
                return new AltitudeGainLoss(altitudeGain, altitudeGain);
            }

            @Override
            public boolean hasReceivedData() {
                return true;
            }
        };
        sensorManager.getAltitudeChangeHandler().setAggregator(aggregatorBarometer);
    }
}

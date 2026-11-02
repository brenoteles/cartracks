package de.dennisguse.opentracks.services;

import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.sensors.sensorData.SensorDataSet;

/**
 * {@link TrackPoint} must be immutable (i.e., their content does not change).
 */
public record RecordingData(
        Track track,
        /*
         * The current view on the SensorData.
         * May contain data from previous measurements that are not present in SensorDataSet.
         */
        TrackPoint latestTrackPoint,
        SensorDataSet sensorDataSet,
        Statistics currentSegment) {

    public static final RecordingData NOT_RECORDING = new RecordingData(null, null, null, null);

    public Statistics trackStatistics() {
        return track.statistics();
    }
}

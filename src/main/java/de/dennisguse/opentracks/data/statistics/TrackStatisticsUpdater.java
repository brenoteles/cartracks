/*
 * Copyright 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package de.dennisguse.opentracks.data.statistics;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.util.List;

import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.settings.PreferencesUtils;

/**
 * Updater for {@link SegmentStatisticUpdater}.
 * For updating track {@link SegmentStatisticUpdater} as new {@link TrackPoint}s are added.
 * NOTE: {@link TrackPoint} represent pause/resume separator.
 *
 * @author Sandor Dornbush
 * @author Rodrigo Damazio
 */
public class TrackStatisticsUpdater {

    private static final String TAG = TrackStatisticsUpdater.class.getSimpleName();

    private Statistics statisticsWithoutCurrentSegment = null;

    private float averageHeartRateBPM;
    private Duration totalHeartRateDuration = Duration.ZERO;
    private float averagePowerW;
    private Duration totalPowerDuration = Duration.ZERO;

    // The current segment's statistics
    private SegmentStatisticUpdater currentSegment;
    // Current segment's last trackPoint
    private TrackPoint lastTrackPoint;

    private boolean isIdle;

    public TrackStatisticsUpdater(@NonNull TrackPoint trackPoint) {
        addTrackPoint(trackPoint);
    }

    public TrackStatisticsUpdater(List<TrackPoint> trackPoints) {
        assert !trackPoints.isEmpty(); //TODO Enforce that this is always true (e.g., import)

        trackPoints.forEach(this::addTrackPoint);
    }

    public TrackStatisticsUpdater(@NonNull Statistics statistics) {
        this.statisticsWithoutCurrentSegment = statistics;
        this.currentSegment = null;

        resetAverageHeartRate();
    }

    public TrackStatisticsUpdater(@NonNull TrackStatisticsUpdater toCopy, TrackPoint tmp) {
        this.statisticsWithoutCurrentSegment = toCopy.statisticsWithoutCurrentSegment;
        this.currentSegment = new SegmentStatisticUpdater(toCopy.currentSegment);

        this.lastTrackPoint = toCopy.lastTrackPoint;
        this.isIdle = toCopy.isIdle;
        resetAverageHeartRate();

        addTrackPoint(tmp);
    }

    /**
     * Compute TrackStatistics.
     */
    public Statistics getTrackStatistics() {
        return currentSegment.merge(statisticsWithoutCurrentSegment);
    }

    public boolean isIdle() {
        return isIdle;
    }

    public Statistics getCurrentSegment() {
        return currentSegment.getStatistics();
    }

    public void addTrackPoint(TrackPoint trackPoint) {
        if (trackPoint.isSegmentManualStart()) {
            reset(trackPoint);
        }
        if (currentSegment == null) {
            currentSegment = new SegmentStatisticUpdater(trackPoint.getTime());
        }

        // Always update time
        currentSegment.setStopTime(trackPoint.getTime());
        currentSegment.updateTotalTime(trackPoint.getTime());

        // Process sensor data: barometer
        if (trackPoint.altitudeGainLoss() != null) {
            currentSegment.addTotalAltitudeGainLoss(trackPoint.altitudeGainLoss());
        }

        // Update absolute (GPS-based) altitude
        if (trackPoint.position().hasAltitude()) {
            currentSegment.updateAltitudeExtremities(trackPoint.position().altitude());
        }

        // Update heart rate
        if (trackPoint.heartRate() != null && lastTrackPoint != null) {
            Duration trackPointDuration = Duration.between(lastTrackPoint.getTime(), trackPoint.getTime());
            Duration newTotalDuration = totalHeartRateDuration.plus(trackPointDuration);

            averageHeartRateBPM = (totalHeartRateDuration.toMillis() * averageHeartRateBPM + trackPointDuration.toMillis() * trackPoint.heartRate().getBPM()) / newTotalDuration.toMillis();
            totalHeartRateDuration = newTotalDuration;

            currentSegment.setAverageHeartRate(HeartRate.of(averageHeartRateBPM));
        }

        // Update power
        if (trackPoint.power() != null && lastTrackPoint != null) {
            Duration trackPointDuration = Duration.between(lastTrackPoint.getTime(), trackPoint.getTime());
            Duration newTotalDuration = totalPowerDuration.plus(trackPointDuration);

            averagePowerW = (totalPowerDuration.toMillis() * averagePowerW + trackPointDuration.toMillis() * trackPoint.power().getW()) / newTotalDuration.toMillis();
            totalPowerDuration = newTotalDuration;

            currentSegment.setAveragePower(Power.of(averagePowerW));
        }

        {
            // Update total distance
            Distance movingDistance = null;
            if (trackPoint.sensorDistance() != null) {
                movingDistance = trackPoint.sensorDistance();
            } else if (lastTrackPoint != null
                    && lastTrackPoint.position().hasLocation()
                    && trackPoint.position().hasLocation()) {
                // GPS-based distance/speed
                movingDistance = trackPoint.distanceToPrevious(lastTrackPoint);
            }
            if (movingDistance != null) {
                currentSegment.addTotalDistance(movingDistance);
            }

            if (!isIdle
                    && !trackPoint.isSegmentManualStart()
                    && lastTrackPoint != null) {
                currentSegment.addMovingTime(trackPoint, lastTrackPoint);
            }

            if (trackPoint.isIdleTriggered()) {
                isIdle = true;
            } else if (isIdle) {
                // Shall we switch to non-idle?
                if (movingDistance != null
                        && movingDistance.greaterOrEqualThan(PreferencesUtils.getRecordingDistanceInterval())) {
                    isIdle = false;
                }
            }

            if (trackPoint.position().hasSpeed()) {
                updateSpeed(trackPoint);
            }
        }

        if (trackPoint.isSegmentManualEnd()) {
            reset(trackPoint);
            return;
        }

        lastTrackPoint = trackPoint;
    }

    private void reset(TrackPoint trackPoint) {
        if (currentSegment != null) {
            statisticsWithoutCurrentSegment = currentSegment.merge(statisticsWithoutCurrentSegment);
        }
        currentSegment = new SegmentStatisticUpdater(trackPoint.getTime());

        lastTrackPoint = null;
        resetAverageHeartRate();
    }

    private void resetAverageHeartRate() {
        averageHeartRateBPM = 0.0f;
        totalHeartRateDuration = Duration.ZERO;
    }

    /**
     * Updates a speed reading while assuming the user is moving.
     */
    private void updateSpeed(@NonNull TrackPoint trackPoint) {
        Speed currentSpeed = trackPoint.position().speed();
        if (currentSpeed.greaterThan(currentSegment.getMaxSpeed())) {
            currentSegment.setMaxSpeed(currentSpeed);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "TrackStatisticsUpdater{" +
                "currentSegment=" + currentSegment +
                ", segmentStatisticUpdater=" + statisticsWithoutCurrentSegment +
                ", lastTrackPoint=" + lastTrackPoint +
                '}';
    }
}

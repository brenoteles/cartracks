/*
 * Copyright 2010 Google Inc.
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
import androidx.annotation.VisibleForTesting;

import java.time.Duration;
import java.time.Instant;

import de.dennisguse.opentracks.data.models.Altitude;
import de.dennisguse.opentracks.data.models.AltitudeExtremities;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.TrackBuilder;
import de.dennisguse.opentracks.data.models.TrackPoint;

/**
 * Statistical data about a {@link TrackBuilder}.
 * The data in this class should be filled out by {@link TrackStatisticsUpdater}.
 *
 * @author Rodrigo Damazio
 */
//TODO Check that data ranges are valid (not less than zero etc.)
public class SegmentStatisticUpdater {

    // The min and max altitude (meters) seen on this track.
    private final ExtremityMonitor altitudeExtremities = new ExtremityMonitor();

    // The track start time.
    @NonNull
    private final Instant startTime;
    // The track stop time.
    @NonNull
    private Instant stopTime;

    private Distance totalDistance;
    /**
     * Updated when new points are received, may be stale.
     * This statistic is only updated when a new point is added to the statistics, so it may be off.
     * If you need to calculate the proper totalDuration, use startTime with the current time.
     */
    private Duration totalDuration;
    // Based on when we believe the user is traveling.
    private Duration movingDuration;
    // The maximum speed (meters/second) that we believe is valid.
    private Speed maxSpeed;
    private Float totalAltitudeGain_m = null;
    private Float totalAltitudeLoss_m = null;
    // The average heart rate seen on this track
    private HeartRate avgHeartRate = null;
    private Power avgPower = null;

    public SegmentStatisticUpdater(@NonNull Instant startTime) {
        this.startTime = this.stopTime = startTime;
        totalDuration = Duration.ZERO;
        movingDuration = Duration.ZERO;
        totalDistance = Distance.ZERO;
        maxSpeed = Speed.ZERO;
        totalAltitudeGain_m = null;
        totalAltitudeLoss_m = null;
    }

    /**
     * Copy constructor.
     *
     * @param other another statistics data object to copy from
     */
    public SegmentStatisticUpdater(SegmentStatisticUpdater other) {
        this(other.getStatistics());
    }

    public SegmentStatisticUpdater(Statistics statistics) {
        startTime = statistics.startTime();
        stopTime = statistics.stopTime();
        totalDistance = statistics.totalDistance();
        totalDuration = statistics.totalDuration();
        movingDuration = statistics.movingDuration();
        maxSpeed = statistics.maxSpeed();
        if (statistics.altitudeExtremities() != null) {
            altitudeExtremities.set(statistics.altitudeExtremities().min_m(), statistics.altitudeExtremities().max_m());
        }
        if (statistics.altitudeGainLoss() != null) {
            totalAltitudeGain_m = statistics.altitudeGainLoss().gain_m();
            totalAltitudeLoss_m = statistics.altitudeGainLoss().loss_m();
        }
        avgHeartRate = statistics.avgHeartRate();
        avgPower = statistics.avgPower();
    }

    public Statistics merge(Statistics statistics) {
        return getStatistics().merge(statistics);
    }

    public Statistics getStatistics() {
        // Times may not be live (i.e., updated automatically).
        return new Statistics(
                startTime,
                stopTime,
                totalDuration,
                movingDuration,

                totalDistance,
                getMaxSpeed(),

                // This is calculated from the smoothed altitude, so this can actually be less than the current altitude.
                altitudeExtremities.hasData() ? new AltitudeExtremities(altitudeExtremities.getMin(), altitudeExtremities.getMax()) : null,
                totalAltitudeGain_m != null && totalAltitudeLoss_m != null ? new AltitudeGainLoss(totalAltitudeGain_m, totalAltitudeLoss_m) : null,
                avgHeartRate,
                avgPower
        );
    }

    public void setStopTime(Instant stopTime) {
        if (stopTime.isBefore(startTime)) {
            // Time must be monotonically increasing, but we might have events at the same point in time (BLE and GPS)
            throw new RuntimeException("stopTime cannot be less than startTime: " + startTime + " " + stopTime);
        }
        this.stopTime = stopTime;
    }

    @VisibleForTesting
    public void setTotalDistance(Distance totalDistance_m) {
        this.totalDistance = totalDistance_m;
    }

    public void addTotalDistance(Distance distance_m) {
        totalDistance = totalDistance.plus(distance_m);
    }

    public void updateTotalTime(Instant now) {
        this.totalDuration = Duration.between(startTime, now);
    }

    public void addMovingTime(TrackPoint trackPoint, TrackPoint lastTrackPoint) {
        Duration movingDuration = Duration.between(lastTrackPoint.getTime(), trackPoint.getTime());

        if (movingDuration.isNegative()) {
            throw new RuntimeException("Moving time cannot be negative: " + lastTrackPoint.getTime() + " is after " + trackPoint.getTime());
        }
        this.movingDuration = this.movingDuration.plus(movingDuration);
    }

    private Speed getAverageMovingSpeed() {
        return Speed.of(totalDistance, movingDuration);
    }

    public Speed getMaxSpeed() {
        return Speed.max(maxSpeed, getAverageMovingSpeed());
    }

    public void setMaxSpeed(Speed maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public void updateAltitudeExtremities(Altitude altitude) {
        if (altitude != null) {
            altitudeExtremities.update(altitude.toM());
        }
    }

    public void setAverageHeartRate(HeartRate heartRate) {
        if (heartRate != null) {
            avgHeartRate = heartRate;
        }
    }

    public void setAveragePower(Power power) {
        if (power != null) {
            avgPower = power;
        }
    }

    @VisibleForTesting
    @Deprecated
    public void setTotalAltitudeGain(Float totalAltitudeGain_m) {
        this.totalAltitudeGain_m = totalAltitudeGain_m;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public void addTotalAltitudeGainLoss(AltitudeGainLoss altitudeGainLoss) {
        if (totalAltitudeGain_m == null) {
            totalAltitudeGain_m = 0f;
        }
        totalAltitudeGain_m += altitudeGainLoss.gain_m();

        if (totalAltitudeLoss_m == null) {
            totalAltitudeLoss_m = 0f;
        }
        totalAltitudeLoss_m += altitudeGainLoss.loss_m();
    }

    @NonNull
    @Override
    public String toString() {
        return "SegmentStatisticUpdater{" + getStatistics() + "}";
    }
}
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
package de.dennisguse.opentracks.data.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.Instant;

/**
 * Sensor and/or location information for a specific point in time.
 * <p>
 * Time is created using the {@link de.dennisguse.opentracks.services.handlers.MonotonicClock}, because system time jump backwards.
 * GPS time is ignored as for non-GPS events, we cannot create GPS-based timestamps.
 */
public record TrackPoint(

        @Nullable
        TrackPoint.Id id,

        @NonNull
        TrackPoint.Type type,

        //Requires: position.time must be non-null
        @NonNull
        Position position,

        Distance sensorDistance,
        HeartRate heartRate,
        Temperature temperature,
        Cadence cadence,
        Power power,
        AltitudeGainLoss altitudeGainLoss
) {

    public TrackPoint(Type type, Position position) {
        this(
                null,
                type,
                position,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @VisibleForTesting
    public TrackPoint(Type type, Instant now) {
        this(
                null,
                type,
                Position.of(now),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static TrackPoint createSegmentStartManualWithTime(Instant time) {
        return new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, time);
    }

    public static TrackPoint with(TrackPoint trackPoint, Altitude.EGM2008 correctAltitude) {
        return new TrackPoint(
                trackPoint.id,
                trackPoint.type,
                new Position(
                        trackPoint.position.time(),
                        trackPoint.position.latitude(),
                        trackPoint.position.longitude(),
                        trackPoint.position.horizontalAccuracy(),
                        correctAltitude,
                        trackPoint.position.verticalAccuracy(),
                        trackPoint.position.bearing(),
                        trackPoint.position.speed()
                ),
                trackPoint.sensorDistance,
                trackPoint.heartRate,
                null,
                trackPoint.cadence,
                trackPoint.power,
                trackPoint.altitudeGainLoss
        );
    }

    public TrackPoint with(Type newType) {
        return new TrackPoint(
                id,
                newType,
                position,
                sensorDistance,
                heartRate,
                null,
                cadence,
                power,
                altitudeGainLoss
        );
    }

    public TrackPoint with(Position newPosition) {
        return new TrackPoint(
                id,
                type,
                newPosition,
                sensorDistance,
                heartRate,
                null,
                cadence,
                power,
                altitudeGainLoss
        );
    }

    @NonNull
    public Instant getTime() {
        return position.time();
    }


    public TrackPoint minusCumulativeSensorData(@NonNull TrackPoint lastTrackPoint) {
        Distance newSensorDistance = sensorDistance;
        if (sensorDistance != null && lastTrackPoint.sensorDistance != null) {
            newSensorDistance = sensorDistance.minus(lastTrackPoint.sensorDistance);
        }
        AltitudeGainLoss newAltitudeGainLoss = altitudeGainLoss;
        if (altitudeGainLoss != null && lastTrackPoint.altitudeGainLoss() != null) {
            newAltitudeGainLoss = new AltitudeGainLoss(
                    altitudeGainLoss.gain_m() - lastTrackPoint.altitudeGainLoss.gain_m(),
                    altitudeGainLoss.loss_m() - lastTrackPoint.altitudeGainLoss.loss_m());
        }
        return new TrackPoint(
                id,
                type,
                position,
                newSensorDistance,
                heartRate,
                null,
                cadence,
                power,
                newAltitudeGainLoss
        );
    }

    @NonNull
    public Distance distanceToPrevious(@NonNull TrackPoint previous) {
        if (sensorDistance() != null) {
            return sensorDistance();
        }
        return distanceToPreviousFromLocation(previous);
    }

    @NonNull
    public Distance distanceToPreviousFromLocation(@NonNull TrackPoint previous) {
        if (!position.hasLocation() || !previous.position().hasLocation()) {
            throw new RuntimeException("Cannot compute distance.");
        }

        return Distance.of(position().toLocation().distanceTo(previous.position().toLocation()));
    }

    public boolean isIdleTriggered() {
        return type == TrackPoint.Type.IDLE;
    }

    public boolean isSegmentManualStart() {
        return type == TrackPoint.Type.SEGMENT_START_MANUAL;
    }

    public boolean isSegmentManualEnd() {
        return type == TrackPoint.Type.SEGMENT_END_MANUAL;
    }

    public enum Type {
        SEGMENT_START_MANUAL(-2), //Start of a segment due to user interaction (start, resume)

        SEGMENT_START_AUTOMATIC(-1), //Start of a segment due to too much distance from previous TrackPoint
        TRACKPOINT(0), //Was created due to sensor data (may contain GPS or other BLE data)

        // Was used to distinguish the source (i.e., GPS vs BLE sensor), but this was too complicated. Everything is now a TRACKPOINT again.
        @Deprecated
        SENSORPOINT(2),
        IDLE(3), //Device became idle

        SEGMENT_END_MANUAL(1); //End of a segment

        public final int type_db;


        Type(int type_db) {
            this.type_db = type_db;
        }

        @NonNull
        @Override
        public String toString() {
            return name() + "(" + type_db + ")";
        }

        public static Type getById(int id) {
            for (Type e : values()) {
                if (e.type_db == id) return e;
            }

            throw new RuntimeException("unknown id: " + id);
        }
    }

    public record Id(long id) implements Parcelable {

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            parcel.writeLong(id);
        }

        public static final Creator<Id> CREATOR = new Creator<>() {
            public Id createFromParcel(Parcel in) {
                return new Id(in.readLong());
            }

            public Id[] newArray(int size) {
                return new Id[size];
            }
        };
    }
}

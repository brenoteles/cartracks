package de.dennisguse.opentracks.data.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public record Track(
        // May be null if the track was not loaded from the database.
        @NonNull
        Id id,
        // May be null if the track was not loaded from the database.
        @NonNull
        UUID uuid,
        @NonNull
        String name,
        @NonNull
        String description,

        @NonNull
        String activityTypeLocalized,
        @NonNull
        ActivityType activityType,
        @NonNull
        ZoneOffset zoneOffset,
        @NonNull
        Statistics statistics
) {

    public OffsetDateTime startTime() {
        return statistics
                .startTime().atOffset(zoneOffset);
    }

    public OffsetDateTime stopTime() {
        return statistics
                .stopTime().atOffset(zoneOffset);
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

        public static final Creator<Track.Id> CREATOR = new Creator<>() {
            public Track.Id createFromParcel(Parcel in) {
                return new Track.Id(in.readLong());
            }

            public Track.Id[] newArray(int size) {
                return new Track.Id[size];
            }
        };
    }
}

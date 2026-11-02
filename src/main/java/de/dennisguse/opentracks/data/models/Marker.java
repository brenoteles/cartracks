package de.dennisguse.opentracks.data.models;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 *  * NOTE: A marker is indirectly (via it's {@link Position}) assigned to one {@link TrackPoint} via position.time.
 */
public record Marker(

        @NonNull
        Id id,

        @NonNull
        Track.Id trackId,

        @NonNull
        String name,
        @NonNull
        String description,
        @NonNull
        String typeLocalized,

        //Some data might not be used.
        Position position,

        @Nullable
        Uri photoUrl
) {
    public record Id(long id) implements Parcelable {

        @NonNull
        @Override
        public String toString() {
            throw new RuntimeException("Not supported");
        }

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

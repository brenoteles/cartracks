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

package de.dennisguse.opentracks.data.models;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * NOTE: A marker is indirectly (via it's {@link Position}) assigned to one {@link TrackPoint} via position.time.
 *
 * @author Leif Hendrik Wilden
 * @author Rodrigo Damazio
 */
public final class MarkerBuilder {

    private Marker.Id id;
    private String name;
    private String description;
    private String typeLocalized;
    private final Track.Id trackId;

    //Some data might not be used.
    private final Position position;

    private Uri photoUrl;

    @VisibleForTesting
    public MarkerBuilder(@Nullable Track.Id trackId, @NonNull TrackPoint trackPoint) {
        this.id = null;
        this.trackId = trackId;
        this.name = "";
        this.description = "";
        this.typeLocalized = "";
        this.photoUrl = null;

        if (!trackPoint.position().hasLocation())
            throw new RuntimeException("Marker requires a trackpoint with a location.");

        this.position = trackPoint.position();
    }

    public MarkerBuilder(Marker marker) {
        this.id = marker.id();
        this.trackId = marker.trackId();
        this.name = marker.name();
        this.description = marker.description();
        this.typeLocalized = marker.typeLocalized();
        this.position = marker.position();
        this.photoUrl = marker.photoUrl();
    }

    /**
     * May be null if the Marker was not loaded from the database.
     */
    @Nullable
    public Marker.Id getId() {
        return id;
    }

    //TODO Remove
    @VisibleForTesting
    @Deprecated
    public void setId(Marker.Id id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setTypeLocalized(String typeLocalized) {
        this.typeLocalized = typeLocalized;
    }

    public Track.Id getTrackId() {
        return trackId;
    }

    public Uri getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(Uri photoUrl) {
        this.photoUrl = photoUrl;
    }

    public boolean hasPhoto() {
        return photoUrl != null;
    }

    public Marker getMarker() {
        return new Marker(
                id,
                trackId,
                name,
                description,
                typeLocalized,
                position,
                photoUrl
        );
    }
}

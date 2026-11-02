/*
 * Copyright 2008 Google Inc.
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

import android.content.Context;

import androidx.annotation.NonNull;

import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A track.
 *
 * @author Leif Hendrik Wilden
 * @author Rodrigo Damazio
 */
public class TrackBuilder {

    private final Track.Id id;
    private final UUID uuid;

    private String name;
    private String description;
    private String activityTypeLocalized;

    private ActivityType activityType;

    @NonNull
    private ZoneOffset zoneOffset;

    @NonNull
    private Statistics trackStatistics;

    public TrackBuilder(Track track) {
        this.id = track.id();
        this.uuid = track.uuid();
        this.name = track.name();
        this.description = track.description();
        this.activityTypeLocalized = track.activityTypeLocalized();
        this.activityType = track.activityType();
        this.zoneOffset = track.zoneOffset();
        this.trackStatistics = track.statistics();
    }

    public TrackBuilder(Track track, UUID uuid) {
        this.id = track.id();
        this.uuid = uuid;
        this.name = track.name();
        this.description = track.description();
        this.activityTypeLocalized = track.activityTypeLocalized();
        this.activityType = track.activityType();
        this.zoneOffset = track.zoneOffset();
        this.trackStatistics = track.statistics();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setActivityTypeLocalized(String activityTypeLocalized) {
        this.activityTypeLocalized = activityTypeLocalized;
    }

    public void setActivityType(ActivityType activityType) {
        this.activityType = activityType;
    }

    public void setActivityTypeLocalizedAndUpdateActivityType(Context context, String activityTypeLocalized) {
        setActivityTypeLocalized(activityTypeLocalized);
        setActivityType(ActivityType.findByLocalizedString(context, activityTypeLocalized));
    }

    public void setZoneOffset(@NonNull ZoneOffset zoneOffset) {
        this.zoneOffset = zoneOffset;
    }

    @Deprecated
    public Statistics getStatistics() {
        return trackStatistics;
    }

    public void setStatistics(Statistics trackStatistics) {
        this.trackStatistics = trackStatistics;
    }

    public Track getTrack() {
        return new Track(
                id,
                uuid,
                name,
                description,
                activityTypeLocalized,
                activityType,
                zoneOffset,
                trackStatistics
        );
    }
}

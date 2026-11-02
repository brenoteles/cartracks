package de.dennisguse.opentracks.data;

import android.text.TextUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.tables.TracksColumns;

public class TrackSelection {
    private final List<Track.Id> trackIds = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private Instant from;
    private Instant to;

    public TrackSelection addDateRange(Instant from, Instant to) {
        this.from = from;
        this.to = to;
        return this;
    }

    public TrackSelection addTrackId(Track.Id trackId) {
        if (!this.trackIds.contains(trackId)) {
            this.trackIds.add(trackId);
        }
        return this;
    }

    public TrackSelection addActivityType(String activityType) {
        if (!this.categories.contains(activityType)) {
            this.categories.add(activityType);
        }
        return this;
    }

    public boolean isEmpty() {
        return trackIds.isEmpty() && categories.isEmpty() && from == null && to == null;
    }

    public ContentProviderUtils.SelectionData buildSelection() {
        String selection = "";
        List<String> selectionArgs = new ArrayList<>();

        // Builds selection.
        if (!trackIds.isEmpty()) {
            selection = String.format(TracksColumns._ID + " IN (%s)", TextUtils.join(",", Collections.nCopies(trackIds.size(), "?")));
            selectionArgs.addAll(trackIds.stream().map(id -> Long.toString(id.id())).toList());
        }
        if (!categories.isEmpty()) {
            selection += selection.isEmpty() ? "" : " AND ";
            selection += String.format(TracksColumns.ACTIVITY_TYPE_LOCALIZED + " IN (%s)", TextUtils.join(",", Collections.nCopies(categories.size(), "?")));
            selectionArgs.addAll(categories);
        }

        if (from != null && to != null) {
            selection += selection.isEmpty() ? "" : " AND ";
            selection += TracksColumns.STARTTIME + " BETWEEN ? AND ?";
            selectionArgs.add(Long.toString(from.toEpochMilli()));
            selectionArgs.add(Long.toString(to.toEpochMilli()));
        }

        if (selection.isEmpty()) {
            return new ContentProviderUtils.SelectionData(null, null);
        }

        return new ContentProviderUtils.SelectionData(selection, selectionArgs);
    }
}

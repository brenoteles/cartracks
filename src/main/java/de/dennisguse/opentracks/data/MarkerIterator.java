package de.dennisguse.opentracks.data;

import android.database.Cursor;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.data.models.Marker;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.util.AutoCloseableIterator;

public class MarkerIterator extends AutoCloseableIterator<Marker> {

    private final ContentProviderUtils contentProviderUtils;

    MarkerIterator(ContentProviderUtils contentProviderUtils, Cursor cursor) {
        super(cursor);
        this.contentProviderUtils = contentProviderUtils;
    }

    MarkerIterator(ContentProviderUtils contentProviderUtils, Track.Id trackId, int maxCount) {
        super(contentProviderUtils.getMarkerCursor(trackId, maxCount));
        this.contentProviderUtils = contentProviderUtils;
    }

    @NonNull
    @Override
    public Marker get() {
        return contentProviderUtils.createMarker(cursor);
    }
}
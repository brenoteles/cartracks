package de.dennisguse.opentracks.data;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.util.AutoCloseableIterator;

public class TrackPointIterator extends AutoCloseableIterator<TrackPoint> {

    TrackPointIterator(ContentProviderUtils contentProviderUtils, Track.Id trackId, TrackPoint.Id startTrackPointId) {
        super(contentProviderUtils.getTrackPointCursor(trackId, startTrackPointId));
    }

    @Override
    @NonNull
    public TrackPoint get() {
        return ContentProviderUtils.fillTrackPoint(cursor);
    }
}
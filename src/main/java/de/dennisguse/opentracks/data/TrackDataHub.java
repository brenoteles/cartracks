/*
 * Copyright 2011 Google Inc.
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

package de.dennisguse.opentracks.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.data.statistics.TrackStatisticsUpdater;
import de.dennisguse.opentracks.services.RecordingStatus;
import de.dennisguse.opentracks.services.TrackRecordingService;
import de.dennisguse.opentracks.services.handlers.AltitudeCorrectionManager;

/**
 * Track data hub.
 * Receives data from {@link CustomContentProvider} and distributes it to {@link Listener} after some processing.
 * <p>
 * {@link TrackPoint}s are filtered/downsampled with a dynamic sampling frequency.
 *
 * @author Rodrigo Damazio
 */
//TODO register contentobserver only for exact URL (incl. trackId) to not filter here.
public class TrackDataHub {

    /**
     * Target number of track points displayed by the diagrams (recommended).
     * We may display more than this number of points.
     */
    private static final int TARGET_DISPLAYED_TRACKPOINTS = 5000;

    /**
     * Maximum number of markers to displayed in the diagrams.
     */
    @VisibleForTesting
    private static final int MAX_DISPLAYED_MARKERS = 128;

    private static final String TAG = TrackDataHub.class.getSimpleName();

    private final Context context;
    private final Set<Listener> listeners;
    private final ContentProviderUtils contentProviderUtils;
    private final int targetNumPoints;

    private final AltitudeCorrectionManager egm2008Correction = new AltitudeCorrectionManager();

    //TODO Check if this is needed.
    private HandlerThread handlerThread;
    private Handler handler;

    private Track.Id selectedTrackId;

    private RecordingStatus recordingStatus = TrackRecordingService.STATUS_DEFAULT;

    // Track points sampling state
    private int numLoadedPoints;
    private TrackPoint.Id firstSeenTrackPointId;
    private TrackPoint.Id lastSeenTrackPointId;
    private TrackStatisticsUpdater trackStatisticsUpdater;

    // Registered listeners
    private ContentObserver tracksTableObserver;
    private ContentObserver trackPointsTableObserver;

    public TrackDataHub(Context context) {
        this(context, new ContentProviderUtils(context), TARGET_DISPLAYED_TRACKPOINTS);
    }

    @VisibleForTesting
    private TrackDataHub(Context context, ContentProviderUtils contentProviderUtils, int targetNumPoints) {
        this.context = context;
        this.listeners = new HashSet<>();
        this.contentProviderUtils = contentProviderUtils;
        this.targetNumPoints = targetNumPoints;
        resetSamplingState();
    }

    public void start() {
        if (isStarted()) {
            Log.i(TAG, "TrackDataHub already started, ignoring start.");
            return;
        }
        handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        //register listeners
        ContentResolver contentResolver = context.getContentResolver();
        tracksTableObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                notifyTracksTableUpdate(listeners);
            }
        };
        contentResolver.registerContentObserver(CustomContentProvider.UrlType.TRACKS.getUri(), false, tracksTableObserver);

        trackPointsTableObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                notifyTrackPointsTableUpdate(true, listeners);
            }
        };
        contentResolver.registerContentObserver(CustomContentProvider.UrlType.TRACKPOINTS_BY_ID.getUri(), false, trackPointsTableObserver);
    }

    public void stop() {
        if (!isStarted()) {
            Log.i(TAG, "TrackDataHub not started, ignoring stop.");
            return;
        }

        //Unregister listeners
        ContentResolver contentResolver = context.getContentResolver();
        contentResolver.unregisterContentObserver(tracksTableObserver);
        contentResolver.unregisterContentObserver(trackPointsTableObserver);

        if (handlerThread != null) {
            handlerThread.getLooper().quit();
            handlerThread = null;
        }
        handler = null;
        trackStatisticsUpdater = null;
    }

    public void loadTrack(final @NonNull Track.Id trackId) {
        handler.post(() -> {
            if (trackId.equals(selectedTrackId)) {
                Log.i(TAG, "Not reloading track " + trackId.id());
                return;
            }
            selectedTrackId = trackId;
            loadDataForAll();
        });
    }

    /**
     * Registers a {@link Listener}.
     *
     * @param trackDataListener the track data listener
     */
    public void registerTrackDataListener(final Listener trackDataListener) {
        handler.post(() -> {
            listeners.add(trackDataListener);
            if (isStarted()) {
                loadDataForListener(trackDataListener);
            }
        });
    }

    /**
     * Unregisters a {@link Listener}.
     *
     * @param trackDataListener the track data listener
     */
    public void unregisterTrackDataListener(final Listener trackDataListener) {
        handler.post(() -> listeners.remove(trackDataListener));
    }

    /**
     * Returns true if the selected track is recording.
     */
    public boolean isSelectedTrackRecording() {
        return selectedTrackId != null && selectedTrackId.equals(recordingStatus.trackId());
    }

    /**
     * Loads data for all listeners. To be run in the {@link #handler} thread.
     */
    private void loadDataForAll() {
        resetSamplingState();
        if (listeners.isEmpty()) {
            return;
        }

        notifyTracksTableUpdate(listeners);

        for (Listener listener : listeners) {
            listener.clearTrackPoints();
        }
        notifyTrackPointsTableUpdate(true, listeners);
    }

    /**
     * Loads data for a listener; to be run in the {@link #handler} thread.
     *
     * @param trackDataListener the track data listener.
     */
    private void loadDataForListener(Listener trackDataListener) {
        Set<Listener> trackDataListeners = Collections.singleton(trackDataListener);

        //Track
        notifyTracksTableUpdate(trackDataListeners);

        //TrackPoints
        trackDataListener.clearTrackPoints();
        boolean isOnlyListener = listeners.size() == 1;
        if (isOnlyListener) {
            resetSamplingState();
        }
        notifyTrackPointsTableUpdate(isOnlyListener, trackDataListeners);
    }

    /**
     * Notifies track table update; to be run in the {@link #handler} thread.
     *
     * @param trackDataListeners the track data listeners to notify
     */
    private void notifyTracksTableUpdate(Set<Listener> trackDataListeners) {
        if (trackDataListeners.isEmpty()) {
            return;
        }
        Track track = contentProviderUtils.getTrack(selectedTrackId);
        for (Listener trackDataListener : trackDataListeners) {
            trackDataListener.onTrackUpdated(track);
        }
    }

    /**
     * Notifies track points table update; to be run in the {@link #handler} thread.
     *
     * @param updateSamplingState true to update the sampling state
     */
    private void notifyTrackPointsTableUpdate(boolean updateSamplingState, Set<Listener> listeners) {
        if (listeners.isEmpty()) {
            return;
        }

        if (updateSamplingState && numLoadedPoints >= targetNumPoints) {
            // Reload and resample the track at a lower frequency.
            Log.i(TAG, "Resampling track after " + numLoadedPoints + " points.");
            resetSamplingState();
            for (Listener listener : listeners) {
                listener.clearTrackPoints();
            }
        }

        int localNumLoadedTrackPoints = updateSamplingState ? numLoadedPoints : 0;
        TrackPoint.Id localFirstSeenTrackPointId = updateSamplingState ? firstSeenTrackPointId : null;
        TrackPoint.Id localLastSeenTrackPointIdId = updateSamplingState ? lastSeenTrackPointId : null;
        TrackPoint.Id maxPointId = updateSamplingState ? null : lastSeenTrackPointId;

        if (selectedTrackId == null) {
            Log.w(TAG, "This should not happen, but it does"); //TODO
            return;
        }

        TrackPoint.Id next = null;
        if (localLastSeenTrackPointIdId != null) {
            next = new TrackPoint.Id(localLastSeenTrackPointIdId.id() + 1); //TODO startTrackPointId + 1 is an assumption assumption; should be derived from the DB.
        }

        TrackPoint trackPoint = null;
        try (TrackPointIterator trackPointIterator = contentProviderUtils.getTrackPointIterator(selectedTrackId, next)) {
            long numTotalPoints = trackPointIterator.getCount();
            int samplingFrequency = 1 + (int) (numTotalPoints / targetNumPoints);

            while (trackPointIterator.hasNext()) {
                if (!isStarted()) {
                    return;
                }

                trackPoint = trackPointIterator.next();
                TrackPoint.Id trackPointId = trackPoint.id();

                // Stop if past the last wanted point
                if (maxPointId != null && trackPointId.id() > maxPointId.id()) {
                    break;
                }

                trackPoint = egm2008Correction.correctAltitude(context, trackPoint);

                if (localFirstSeenTrackPointId == null) {
                    localFirstSeenTrackPointId = trackPointId;
                }

                if (trackStatisticsUpdater == null) {
                    trackStatisticsUpdater = new TrackStatisticsUpdater(trackPoint);
                } else {
                    trackStatisticsUpdater.addTrackPoint(trackPoint);
                }
                Statistics statistics = trackStatisticsUpdater.getTrackStatistics();

                // Also include the last point if the selected track is not recording.
                if ((localNumLoadedTrackPoints % samplingFrequency == 0) || (!trackPointIterator.hasNext() && !isSelectedTrackRecording())) {
                    for (Listener trackDataListener : listeners)
                        trackDataListener.onSampledInTrackPoint(trackPoint, statistics);
                }

                localNumLoadedTrackPoints++;
            }
        }

        if (trackPoint != null) {
            localLastSeenTrackPointIdId = trackPoint.id();
        }

        if (updateSamplingState) {
            numLoadedPoints = localNumLoadedTrackPoints;
            firstSeenTrackPointId = localFirstSeenTrackPointId;
            lastSeenTrackPointId = localLastSeenTrackPointIdId;
        }

        listeners.forEach(Listener::onNewTrackPointsDone);
    }


    /**
     * Resets the track points sampling states.
     */
    private void resetSamplingState() {
        numLoadedPoints = 0;
        firstSeenTrackPointId = null;
        lastSeenTrackPointId = null;
        trackStatisticsUpdater = null;
    }

    private boolean isStarted() {
        return handlerThread != null;
    }

    public void setRecordingStatus(RecordingStatus recordingStatus) {
        this.recordingStatus = recordingStatus;
    }

    public interface Listener {

        /**
         * Called when the track or its statistics has been updated.
         *
         * @param track the track
         */
        void onTrackUpdated(@NonNull Track track);

        /**
         * Called to clear previously-sent track points.
         */
        void clearTrackPoints();

        /**
         * Called when a sampled in track point is read.
         *
         * @param trackPoint the trackPoint
         */
        default void onSampledInTrackPoint(@NonNull TrackPoint trackPoint, @NonNull Statistics trackStatistics) {
        }

        /**
         * Called when finish sending new track points.
         */
        default void onNewTrackPointsDone() {
        }
    }
}
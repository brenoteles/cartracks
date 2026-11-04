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

package de.dennisguse.opentracks.services;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.ServiceCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;

import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Marker;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.sensors.GpsStatusValue;
import de.dennisguse.opentracks.services.announcement.VoiceAnnouncementManager;
import de.dennisguse.opentracks.services.handlers.TrackPointCreator;
import de.dennisguse.opentracks.settings.PreferencesUtils;

public class TrackRecordingService extends Service implements TrackPointCreator.Callback, SharedPreferences.OnSharedPreferenceChangeListener, TrackRecordingManager.IdleObserver {

    private static final String TAG = TrackRecordingService.class.getSimpleName();

    @VisibleForTesting
    public static final Duration RECORDING_DATA_UPDATE_INTERVAL = Duration.ofSeconds(1);

    public static final RecordingStatus STATUS_DEFAULT = RecordingStatus.notRecording();
    public static final GpsStatusValue STATUS_GPS_DEFAULT = GpsStatusValue.GPS_NONE;

    public TrackPoint getLastStoredTrackPointWithLocation() {
        return trackRecordingManager.getLastStoredTrackPointWithLocation();
    }

    public class Binder extends android.os.Binder {

        private Binder() {
            super();
        }

        public TrackRecordingService getService() {
            return TrackRecordingService.this;
        }
    }

    private final Binder binder = new Binder();

    private final Runnable updateRecordingData = new Runnable() {
        @Override
        public void run() {
            updateRecordingDataWhileRecording();

            TrackRecordingService.this.handler.postDelayed(this, RECORDING_DATA_UPDATE_INTERVAL.toMillis());
        }
    };

    // The following variables are set in onCreate:
    private RecordingStatus recordingStatus;
    private MutableLiveData<RecordingStatus> recordingStatusObservable;
    private MutableLiveData<GpsStatusValue> gpsStatusObservable;
    private MutableLiveData<RecordingData> recordingDataObservable;

    private Handler handler;

    private TrackPointCreator trackPointCreator;
    private TrackRecordingManager trackRecordingManager;

    // Smart idle pause: stop storing points while the car is parked, never while it moves.
    private static final long SMART_PAUSE_AFTER_MS = 180_000L;
    private static final double STATIONARY_SPEED_MPS = 0.8;
    private static final double MOVING_SPEED_MPS = 2.5;
    private static final float MOVED_RADIUS_M = 40f;

    private long stationaryStartTime = 0;
    private TrackPoint stationaryAnchor = null;
    private boolean isSmartAutoPaused = false;

    public boolean isSmartPaused() {
        return isSmartAutoPaused;
    }

    private VoiceAnnouncementManager voiceAnnouncementManager;
    private TrackRecordingServiceNotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Create");

        handler = new Handler(Looper.getMainLooper());

        recordingStatusObservable = new MutableLiveData<>();
        updateRecordingStatus(STATUS_DEFAULT);
        gpsStatusObservable = new MutableLiveData<>(STATUS_GPS_DEFAULT);
        recordingDataObservable = new MutableLiveData<>(RecordingData.NOT_RECORDING);

        trackPointCreator = new TrackPointCreator(this);
        trackRecordingManager = new TrackRecordingManager(this, trackPointCreator, this, handler);

        voiceAnnouncementManager = new VoiceAnnouncementManager(this);
        notificationManager = new TrackRecordingServiceNotificationManager(this);

        PreferencesUtils.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroying");
        if (trackPointCreator != null && trackPointCreator.isStarted()) {
            stopSensors();
        }

        PreferencesUtils.unregisterOnSharedPreferenceChangeListener(this);

        trackPointCreator = null;

        handler.removeCallbacksAndMessages(null); //Some tests do not finish the recording completely
        handler = null;

        trackRecordingManager = null;

        // Reverse order from onCreate
        notificationManager = null;

        voiceAnnouncementManager = null;

        recordingStatusObservable = null;
        gpsStatusObservable = null;
        recordingDataObservable = null;

        Log.d(TAG, "Destroyed");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.i(TAG, "Service got killed and is restarting");

            //Best guess: do we need to resume a recording?
            Track.Id trackId = TrackIdStorage.get(this);
            if (trackId != null) {
                Log.i(TAG, "After restart: resuming recording for " + trackId);
                resumeTrack(trackId);
            }
        }
        return START_STICKY;
    }

    @Override
    public Binder onBind(Intent intent) {
        return binder;
    }

    public Track.Id startNewTrack() {
        if (isRecording()) {
            Log.w(TAG, "Ignore startNewTrack. Already recording.");
            return null;
        }
        Log.i(TAG, "startNewTrack");

        // Set recording status
        Track.Id trackId = trackRecordingManager.startNewTrack();
        startRecording(trackId);
        return trackId;
    }

    public void resumeTrack(Track.Id trackId) {
        if (!trackRecordingManager.resumeExistingTrack(trackId)) {
            Log.w(TAG, "Cannot resume a non-existing track.");
            return;
        }
        Log.i(TAG, "resumeTrack");

        startRecording(trackId);
    }

    private void startRecording(Track.Id trackId) {
        updateRecordingStatus(RecordingStatus.record(trackId));
        TrackIdStorage.set(this, trackId);

        startSensors();

        voiceAnnouncementManager.start(trackRecordingManager.getTrackStatistics());

        notificationManager.onRecordingStarted(this, recordingStatus.trackId());

        // Update instance variables
        updateRecordingData.run();
        handler.postDelayed(updateRecordingData, RECORDING_DATA_UPDATE_INTERVAL.toMillis());
    }

    public void tryStartSensors() {
        if (trackPointCreator.isStarted()) return;

        Log.i(TAG, "tryStartSensors");

        startSensors();
    }

    private synchronized void startSensors() {
        if (trackPointCreator.isStarted()) {
            Log.i(TAG, "sensors already started; skipping");
            return;
        }
        Log.i(TAG, "startSensors");
        trackPointCreator.start(this, handler);

        ServiceCompat.startForeground(this, TrackRecordingServiceNotificationManager.NOTIFICATION_ID, notificationManager.onSensorsStarted(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
    }

    public void endCurrentTrack() {
        if (!isRecording()) {
            Log.w(TAG, "Ignore endCurrentTrack. Not recording.");
            return;
        }

        // Set recording status
        updateRecordingStatus(STATUS_DEFAULT);
        isSmartAutoPaused = false;
        stationaryStartTime = 0;
        stationaryAnchor = null;

        trackRecordingManager.endCurrentTrack();
        TrackIdStorage.unset(this);

        stopUpdateRecordingData();

        voiceAnnouncementManager.stop();

        stopSensors();
    }

    void stopSensors() {
        trackPointCreator.stop();
        stopForeground(true);
        notificationManager.stop();
        gpsStatusObservable.postValue(STATUS_GPS_DEFAULT);
    }

    public Marker.Id createMarker() {
        if (!isRecording()) {
            return null;
        }

        //TODO This contains some duplication to TrackRecodingActivity's Marker creation
        TrackPoint trackPoint = trackRecordingManager.getLastStoredTrackPointWithLocation();
        if (trackPoint == null) {
            return null;
        }
        Marker marker = new Marker(
                null,
                recordingStatus.trackId(),
                "",
                "",
                "",
                trackPoint.position(),
                null
        );
        return new ContentProviderUtils(this).insertMarker(marker);
    }

    @Override
    public boolean newTrackPoint(TrackPoint trackPoint, Distance thresholdHorizontalAccuracy) {
        if (!isRecording()) {
            Log.w(TAG, "Ignore newTrackPoint. Not recording.");
            return false;
        }

        updateSmartPause(trackPoint);

        if (isSmartAutoPaused) {
            return false;
        }

        boolean stored = trackRecordingManager.onNewTrackPoint(trackPoint);
        //TODO Should be done during updateRecordingDataWhileRecording()
        notificationManager.updateTrackPoint(this, trackRecordingManager.getDataForUI(), thresholdHorizontalAccuracy);
        return stored;
    }

    /**
     * Decides whether the car is parked. Resumes as soon as the GPS reports speed OR the
     * position moved away from where the car stopped: some chipsets report speed 0 (or no
     * speed) while driving, and relying on speed alone left trips paused until the end,
     * storing only the start and end points.
     */
    private void updateSmartPause(TrackPoint trackPoint) {
        boolean enabled = getSharedPreferences("CarTracksPrefs", MODE_PRIVATE)
                .getBoolean("smart_idle_pause_enabled", true);
        if (!enabled || !trackPoint.position().hasLocation()) {
            if (!enabled) {
                resumeFromSmartPause();
            }
            return;
        }

        double speedMps = trackPoint.position().hasSpeed() ? trackPoint.position().speed().toMPS() : -1;
        boolean moved = false;
        if (stationaryAnchor != null) {
            float accuracy = trackPoint.position().hasHorizontalAccuracy()
                    ? (float) trackPoint.position().horizontalAccuracy().toM() : 0f;
            float radius = Math.max(MOVED_RADIUS_M, 2 * accuracy);
            moved = trackPoint.position().toLocation().distanceTo(stationaryAnchor.position().toLocation()) > radius;
        }

        if (speedMps > MOVING_SPEED_MPS || moved) {
            resumeFromSmartPause();
            return;
        }

        if (speedMps < STATIONARY_SPEED_MPS) {
            long now = System.currentTimeMillis();
            if (stationaryAnchor == null) {
                stationaryAnchor = trackPoint;
                stationaryStartTime = now;
            } else if (!isSmartAutoPaused && now - stationaryStartTime > SMART_PAUSE_AFTER_MS) {
                isSmartAutoPaused = true;
                notificationManager.updateGPSStatus("⏸️ Pausa Inteligente: veículo parado");
            }
        }
    }

    private void resumeFromSmartPause() {
        stationaryAnchor = null;
        stationaryStartTime = 0;
        if (isSmartAutoPaused) {
            isSmartAutoPaused = false;
            notificationManager.updateGPSStatus("🚗 Viagem retomada!");
        }
    }

    @Override
    public void newGpsStatus(GpsStatusValue gpsStatusValue) {
        Log.i(TAG, "newGpsStatus: " + gpsStatusValue.message);

        if (notificationManager == null) {

            StringWriter writer = new StringWriter();
            Exception e = new RuntimeException("TrackRecording.newGpsStatus() called after onDestroy(); objectID: " + this + " with thread: " + Thread.currentThread());
            e.printStackTrace(new PrintWriter(writer));

            Log.e(TAG, e.getMessage() + " " + writer);
            return;
        }
        notificationManager.updateGPSStatus(getString(gpsStatusValue.message));
        gpsStatusObservable.postValue(gpsStatusValue);
    }

    @Deprecated
    @VisibleForTesting
    public TrackPointCreator getTrackPointCreator() {
        return trackPointCreator;
    }

    @Deprecated
    @VisibleForTesting
    public TrackRecordingManager getTrackRecordingManager() {
        return trackRecordingManager;
    }

    public LiveData<GpsStatusValue> getGpsStatusObservable() {
        return gpsStatusObservable;
    }

    public LiveData<RecordingData> getRecordingDataObservable() {
        return recordingDataObservable;
    }

    private void updateRecordingDataWhileRecording() {
        if (!isRecording()) {
            Log.w(TAG, "Currently not recording; cannot update data.");
            return;
        }

        // Compute temporary track statistics using sensorData and update time.
        RecordingData data = trackRecordingManager.getDataForUI();

        voiceAnnouncementManager.announceStatisticsIfNeeded(data);

        recordingDataObservable.postValue(data);
    }

    public void onIdle() {
        voiceAnnouncementManager.announceIdle();
    }

    @VisibleForTesting
    public void stopUpdateRecordingData() {
        handler.removeCallbacks(updateRecordingData);
    }

    public LiveData<RecordingStatus> getRecordingStatusObservable() {
        return recordingStatusObservable;
    }

    private void updateRecordingStatus(RecordingStatus status) {
        Log.i(TAG, "new status " + recordingStatus + " -> " + status);
        recordingStatus = status;
        recordingStatusObservable.postValue(recordingStatus);
    }

    @Deprecated //TODO Should be @VisibleForTesting
    public boolean isRecording() {
        return recordingStatus.isRecording();
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        voiceAnnouncementManager.onSharedPreferenceChanged(sharedPreferences, key);
        trackRecordingManager.onSharedPreferenceChanged(sharedPreferences, key);
        trackPointCreator.onSharedPreferenceChanged(sharedPreferences, key);
        notificationManager.onSharedPreferenceChanged(sharedPreferences, key);
    }
}
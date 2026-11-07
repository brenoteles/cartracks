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

    // Auto-stop when parked: traffic jams keep recording, only a parked car ends the trip.
    private final ParkedDetector parkedDetector = new ParkedDetector();
    private long lastFixAt = 0;
    private boolean autoStopping = false;
    /** No fix at all for this long (underground parking) also ends the trip. */
    static final long NO_FIX_END_MS = 15 * 60_000L;
    private final Runnable noFixWatchdog = new Runnable() {
        @Override
        public void run() {
            if (!isRecording() || autoStopping) return;
            boolean enabled = getSharedPreferences("CarTracksPrefs", MODE_PRIVATE)
                    .getBoolean("auto_stop_parked_enabled", true);
            if (enabled && lastFixAt > 0 && System.currentTimeMillis() - lastFixAt > NO_FIX_END_MS) {
                Log.i(TAG, "No GPS fix for 15 min (garage?); ending the trip automatically.");
                autoStopping = true;
                autoEndParkedTrip();
                return;
            }
            handler.postDelayed(this, 60_000L);
        }
    };

    /** Kept for the UI; recording is never paused any more. */
    public boolean isSmartPaused() {
        return false;
    }

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
        lastFixAt = 0;
        parkedDetector.reset();
        autoStopping = false;
        handler.removeCallbacks(noFixWatchdog);
        handler.postDelayed(noFixWatchdog, 60_000L);
        QuickControls.setRecording(this, true);
        updateRecordingStatus(RecordingStatus.record(trackId));
        TrackIdStorage.set(this, trackId);

        startSensors();

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
        parkedDetector.reset();
        autoStopping = false;
        handler.removeCallbacks(noFixWatchdog);
        QuickControls.setRecording(this, false);

        trackRecordingManager.endCurrentTrack();
        TrackIdStorage.unset(this);

        stopUpdateRecordingData();

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

        checkParked(trackPoint);

        boolean stored = trackRecordingManager.onNewTrackPoint(trackPoint);
        //TODO Should be done during updateRecordingDataWhileRecording()
        notificationManager.updateTrackPoint(this, trackRecordingManager.getDataForUI(), thresholdHorizontalAccuracy);
        return stored;
    }

    /**
     * Ends the trip when every fix of the last 10 minutes stayed within 200 m of the current
     * position. Stop-and-go traffic moves more than that, so it keeps recording; a parked car
     * ends the trip without touching the phone.
     */
    private void checkParked(TrackPoint trackPoint) {
        boolean enabled = getSharedPreferences("CarTracksPrefs", MODE_PRIVATE)
                .getBoolean("auto_stop_parked_enabled", true);
        if (!enabled || autoStopping || !trackPoint.position().hasLocation()) {
            return;
        }
        long now = System.currentTimeMillis();
        lastFixAt = now;
        if (parkedDetector.onFix(now, trackPoint.position().latitude(), trackPoint.position().longitude())) {
            Log.i(TAG, "Parked for 10 min; ending the trip automatically.");
            autoStopping = true;
            handler.post(this::autoEndParkedTrip);
        }
    }

    private void autoEndParkedTrip() {
        endTripAndPark("O carro ficou parado por 10 minutos. Viagem e local de estacionamento salvos.");
    }

    /**
     * Ends the trip without the UI (parked timeout, car Bluetooth off), keeping the parking spot and a backup.
     * A null reason skips the "encerrada automaticamente" notification (the caller posts its own).
     */
    public void endTripAndPark(@Nullable String reason) {
        if (!isRecording()) {
            return;
        }
        TrackPoint last = trackRecordingManager.getLastStoredTrackPointWithLocation();
        if (last != null) {
            getSharedPreferences("CarTracksParking", MODE_PRIVATE).edit()
                    .putBoolean("parking_active", true)
                    .putString("parking_lat", String.valueOf(last.position().latitude()))
                    .putString("parking_lon", String.valueOf(last.position().longitude()))
                    .putLong("parking_time_ms", System.currentTimeMillis())
                    .putString("parking_address", "Local de parada do veículo")
                    .apply();
        }
        endCurrentTrack();
        try {
            de.dennisguse.opentracks.io.CarTracksBackupHelper.INSTANCE.saveAutoBackup(this);
        } catch (Exception e) {
            Log.w(TAG, "Auto backup after auto-stop failed", e);
        }
        new android.app.backup.BackupManager(this).dataChanged();
        if (reason != null) {
            de.dennisguse.opentracks.util.CarNotifications.autoStopped(this, reason);
        }
        stopSelf();
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

        recordingDataObservable.postValue(data);
    }

    public void onIdle() {
        // No voice announcement: the "becoming idle" speech was annoying while driving.
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
        trackRecordingManager.onSharedPreferenceChanged(sharedPreferences, key);
        trackPointCreator.onSharedPreferenceChanged(sharedPreferences, key);
        notificationManager.onSharedPreferenceChanged(sharedPreferences, key);
    }
}
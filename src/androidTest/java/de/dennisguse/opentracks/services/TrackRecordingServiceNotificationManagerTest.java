package de.dennisguse.opentracks.services;

import static org.junit.Assert.assertEquals;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.Altitude;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.settings.UnitSystem;

public class TrackRecordingServiceNotificationManagerTest {

    private static final String CHANNEL_ID = "TrackRecordingServiceNotificationManagerTest";

    private final Context context = ApplicationProvider.getApplicationContext();
    private final NotificationManager notificationManager = context.getSystemService(NotificationManager.class);

    @After
    public void tearDown() {
        notificationManager.cancelAll();
    }

    @Test
    public void updateLocation_triggersAlertOnlyOnFirstInaccurateLocation() {
        notificationManager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT));

        TrackingNotificationBuilder notificationBuilder = new TrackingNotificationBuilder(context, CHANNEL_ID);
        TrackRecordingServiceNotificationManager subject = new TrackRecordingServiceNotificationManager(notificationManager, notificationBuilder);
        subject.setUnitSystem(UnitSystem.METRIC);

        Track track = new Track(
                null,
                UUID.fromString("f81d4fae-7dec-11d0-a765-00a0c91e6bf6"),
                "Berlin",
                "",
                "",
                ActivityType.CYCLING,
                ZoneOffset.UTC,
                Statistics.DEFAULT
        );

        subject.updateTrackPoint(context, createRecordingData(track, Distance.of(999f)), Distance.of(100));
        subject.updateTrackPoint(context, createRecordingData(track, Distance.of(999f)), Distance.of(100));
        subject.updateTrackPoint(context, createRecordingData(track, Distance.of(999f)), Distance.of(1000));
        subject.updateTrackPoint(context, createRecordingData(track, Distance.of(999f)), Distance.of(100));
        assertEquals(java.util.Arrays.asList(false, true, true, true, true, true, false, true), notificationBuilder.onlyAlertOnceValues);
    }

    private RecordingData createRecordingData(Track track, Distance horizontalAccuracy) {
        TrackPoint trackPoint = new TrackPoint(
                TrackPoint.Type.TRACKPOINT,
                new Position(
                        null,
                        null,
                        null,
                        horizontalAccuracy,
                        Altitude.WGS84.of(10),
                        null,
                        null,
                        Speed.ZERO
                )
        );
        return new RecordingData(track, trackPoint, null, null);
    }

    private static final class TrackingNotificationBuilder extends NotificationCompat.Builder {
        private final List<Boolean> onlyAlertOnceValues = new ArrayList<>();

        private TrackingNotificationBuilder(Context context, String channelId) {
            super(context, channelId);
            setSmallIcon(R.drawable.ic_logo_color_24dp);
        }

        @NonNull
        @Override
        public NotificationCompat.Builder setOnlyAlertOnce(boolean onlyAlertOnce) {
            onlyAlertOnceValues.add(onlyAlertOnce);
            return super.setOnlyAlertOnce(onlyAlertOnce);
        }
    }
}

package de.dennisguse.opentracks.io.file.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.rule.ServiceTestRule;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.TestUtil;
import de.dennisguse.opentracks.TimezoneRule;
import de.dennisguse.opentracks.content.data.TestDataUtil;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.Altitude;
import de.dennisguse.opentracks.data.models.AltitudeExtremities;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Marker;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Temperature;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackBuilder;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.io.file.TrackFileFormat;
import de.dennisguse.opentracks.io.file.exporter.TrackExporter;
import de.dennisguse.opentracks.sensors.SensorManager;
import de.dennisguse.opentracks.sensors.driver.CyclingPowerBluetooth;
import de.dennisguse.opentracks.sensors.sensorData.Aggregator;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorBarometer;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingCadence;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingDistanceSpeed;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingPower;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorHeartRate;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorTemperature;
import de.dennisguse.opentracks.services.TrackRecordingService;
import de.dennisguse.opentracks.services.handlers.TrackPointCreator;
import de.dennisguse.opentracks.settings.PreferencesUtils;

/**
 * Export a track to {@link TrackFileFormat} and verify that the import is identical.
 * <p>
 * Note: those tests are affected by {@link Aggregator}.isOutdated().
 * If the test device is too slow (like in a CI) these are likely to fail as the sensor data will be omitted from actual.
 */
@RunWith(AndroidJUnit4.class)
public class ExportImportTest {

    @Rule
    public final ServiceTestRule mServiceRule = ServiceTestRule.withTimeout(5, TimeUnit.SECONDS);

    @Rule
    public GrantPermissionRule mGrantPermissionRule = TestUtil.createGrantPermissionRule();

    //For csv_export_only() as the timezone is hardcoded in the expectation.
    @Rule
    public TimezoneRule timezoneRule = new TimezoneRule(TimeZone.getTimeZone("Europe/Berlin"));

    private static final Context context = ApplicationProvider.getApplicationContext();

    @BeforeClass
    public static void preSetUp() {
        // Prepare looper for Android's message queue
        if (Looper.myLooper() == null) Looper.prepare();

        PreferencesUtils.resetPreferences(context, true);
    }

    private final ContentProviderUtils contentProviderUtils = new ContentProviderUtils(context);

    private static final ActivityType TRACK_ACTIVITY_TYPE = ActivityType.MOUNTAIN_BIKING;
    private static final String TRACK_ACTIVITY_TYPE_LOCALIZED = "the activity type";
    private static final String TRACK_DESCRIPTION = "the description";

    private File tmpFile;
    private Uri tmpFileUri;

    private List<Marker> markers = new ArrayList<>();
    private List<TrackPoint> trackPoints = new ArrayList<>();

    private Track.Id trackId;
    private Track.Id importTrackId;

    private TrackImporter trackImporter;

    @Before
    public void fileSetup() throws IOException, TimeoutException {
        //TODO Replace with a ByteArrayOutputStream instead of storing a file?
        tmpFile = File.createTempFile("test", "test", context.getFilesDir());
        tmpFileUri = Uri.fromFile(tmpFile);

        trackImporter = new TrackImporter(context, contentProviderUtils, Distance.of(200), true);

        setUp();
    }

    @After
    public void tearDown() {
        tmpFile.deleteOnExit();
        tmpFileUri = null;

        // Ensure that the database is empty after every test
        contentProviderUtils.deleteAllTracks(context);
    }

    public void setUp() throws TimeoutException {
        TrackRecordingService service = ((TrackRecordingService.Binder) mServiceRule.bindService(new Intent(context, TrackRecordingService.class)))
                .getService();

        TrackPointCreator trackPointCreator = service.getTrackPointCreator();

        trackPointCreator.setClock("2020-02-02T02:02:02Z");
        trackId = service.startNewTrack();

        Distance sensorDistance = Distance.of(10); // recording distance interval

        Position position = sendLocation(trackPointCreator, "2020-02-02T02:02:03Z", 3.1234567, 14.0014567, 10, 13, 15, 1020.25, 1f);
        contentProviderUtils.insertMarker(
                new Marker(
                        null,
                        trackId,
                        "Marker 1",
                        "Marker 1 desc",
                        "Marker 1 typeLocalized",
                        position,
                        null
                ));

        // A sensor-only TrackPoint
        trackPointCreator.setClock("2020-02-02T02:02:04Z");
        mockSensorData(trackPointCreator, 15f, sensorDistance, 66f, 3f, 50f, 1f, 27);

        trackPointCreator.setClock("2020-02-02T02:02:14Z"); //ignored
        mockSensorData(trackPointCreator, 15f, null, 67f, 3f, 50f, null, 28);
        trackPointCreator.setClock("2020-02-02T02:02:15Z");
        mockSensorData(trackPointCreator, null, null, 68f, 3f, 50f, null, 29);
        trackPointCreator.setClock("2020-02-02T02:02:16Z");
        mockSensorData(trackPointCreator, 5f, Distance.of(2), 69f, 3f, 50f, null, 30); //Distance will be added to next TrackPoint

        position = sendLocation(trackPointCreator, "2020-02-02T02:02:17Z", 3.1234567, 14.0014567, 10, 13, 15, 1020.25, 0f);
        contentProviderUtils.insertMarker(
                new Marker(
                        null,
                        trackId,
                        "Marker 2",
                        "Marker 2 desc",
                        "Marker 2 typeLocalized",
                        position,
                        null
                ));

        trackPointCreator.setClock("2020-02-02T02:02:18Z");
        service.endCurrentTrack();

        trackPointCreator.setClock("2020-02-02T02:03:20Z");
        service.resumeTrack(trackId);

        sendLocation(trackPointCreator, "2020-02-02T02:03:21Z", 3.1234567, 14.0024567, 10, 13, 15, 999.123, 0f);

        sendLocation(trackPointCreator, "2020-02-02T02:03:22Z", 3.1234567, 16, 10, 13, 15, 999.123, 0f);

        trackPointCreator.setClock("2020-02-02T02:03:30Z");
        service.getTrackRecordingManager().onIdle();

        sendLocation(trackPointCreator, "2020-02-02T02:03:50Z", 3.1234567, 16.001, 10, 27, 15, 999.123, 0f);

        trackPointCreator.setClock("2020-02-02T02:04:00Z");
        service.endCurrentTrack();

        TrackBuilder trackBuilder = new TrackBuilder(contentProviderUtils.getTrack(trackId));
        trackBuilder.setActivityType(TRACK_ACTIVITY_TYPE);
        trackBuilder.setActivityTypeLocalized(TRACK_ACTIVITY_TYPE_LOCALIZED);
        trackBuilder.setDescription(TRACK_DESCRIPTION);
        contentProviderUtils.updateTrack(trackBuilder.getTrack());

        trackPoints = TestDataUtil.getTrackPoints(contentProviderUtils, trackId);
        markers = TestDataUtil.getMarkers(contentProviderUtils, trackId);
    }

    @LargeTest
    @Test
    public void track() throws TimeoutException {
        Track track = contentProviderUtils.getTrack(trackId);
        Statistics trackStatistics = track.statistics();

        assertEquals(ZoneOffset.of("+01:00"), track.zoneOffset());

        List<TrackPoint> actual = TestDataUtil.getTrackPoints(contentProviderUtils, trackId);
        new TrackPointAssert().assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse("2020-02-02T02:02:02Z")),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse("2020-02-02T02:02:03Z"),
                                3.123456, 14.001456, Distance.of(10),
                                Altitude.WGS84.of(1020.25), null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(1, 1)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse("2020-02-02T02:02:04Z"),
                                null, null, null,
                                null, null,
                                null,
                                Speed.of(15)),
                        Distance.of(10),
                        HeartRate.of(66),
                        Temperature.of(27),
                        Cadence.of(3),
                        Power.of(50),
                        new AltitudeGainLoss(1, 1)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        Position.of(Instant.parse("2020-02-02T02:02:15Z")),
                        null,
                        HeartRate.of(68),
                        Temperature.of(29),
                        Cadence.of(3),
                        Power.of(50),
                        null
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse("2020-02-02T02:02:17Z"),
                                3.123456, 14.001456, Distance.of(10),
                                Altitude.WGS84.of(1020.25), null,
                                null,
                                Speed.of(5)),
                        Distance.of(2),
                        HeartRate.of(69),
                        Temperature.of(30),
                        Cadence.of(3),
                        Power.of(50),
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_END_MANUAL,
                        new Position(
                                Instant.parse("2020-02-02T02:02:18Z"),
                                3.123456, 14.001456, Distance.of(10),
                                Altitude.WGS84.of(1020.25), null,
                                null,
                                Speed.of(5)),
                        Distance.of(0),
                        HeartRate.of(69),
                        null,
                        Cadence.of(3),
                        Power.of(50),
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse("2020-02-02T02:03:20Z")),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse("2020-02-02T02:03:21Z"),
                                3.123456, 14.002456, Distance.of(10),
                                Altitude.WGS84.of(999.1229858398438), null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_START_AUTOMATIC,
                        new Position(Instant.parse("2020-02-02T02:03:22Z"),
                                3.123456, 16d, Distance.of(10),
                                Altitude.WGS84.of(999.1229858398438), null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.IDLE,
                        Position.of(Instant.parse("2020-02-02T02:03:30Z")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse("2020-02-02T02:03:50Z"),
                                3.123456, 16.001, Distance.of(10),
                                Altitude.WGS84.of(999.1229858398438), null,
                                null, Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_END_MANUAL,
                        Position.of(Instant.parse("2020-02-02T02:04:00Z")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0))
        ), actual);

        assertEquals(new Statistics(
                        Instant.parse("2020-02-02T02:02:02Z"),
                        Instant.parse("2020-02-02T02:04:00Z"),
                        Duration.ofSeconds(56),
                        Duration.ofSeconds(26),  //TODO Likely too low
                        Distance.of(222049.34375), //TODO Too low
                        Speed.of(22203.734375),
                        new AltitudeExtremities(999.1229858398438, 1020.25),
                        new AltitudeGainLoss(2, 2),
                        null,
                        null
                ),
                trackStatistics);
    }

    //TODO Does not test marker images
    @LargeTest
    @Test
    public void kmz_with_trackdetail_and_sensordata() throws IOException {
        // given
        Track track = contentProviderUtils.getTrack(trackId);

        TrackExporter trackExporter = TrackFileFormat.KMZ_WITH_TRACKDETAIL_AND_SENSORDATA_AND_PICTURES.createTrackExporter(context, contentProviderUtils);

        // when
        // 1. export
        trackExporter.writeTrack(List.of(track), context.getContentResolver().openOutputStream(tmpFileUri));
        contentProviderUtils.deleteTrack(context, trackId);

        // 2. import
        KMZTrackImporter importer = new KMZTrackImporter(context, trackImporter);
        importTrackId = importer.importFile(tmpFileUri).get(0);

        // then
        // 1. track
        Track importedTrack = contentProviderUtils.getTrack(importTrackId);
        assertNotNull(importedTrack);
        assertEquals(track.activityType(), importedTrack.activityType());
        assertEquals(track.activityTypeLocalized(), importedTrack.activityTypeLocalized());
        assertEquals(track.description(), importedTrack.description());
        assertEquals(track.name(), importedTrack.name());

        // 2. trackpoints
        List<TrackPoint> actual = TestDataUtil.getTrackPoints(contentProviderUtils, importTrackId);
        new TrackPointAssert().assertEquals(trackPoints, actual);

        // 3. trackstatistics
        Statistics importedTrackStatistics = importedTrack.statistics();

        assertEquals(track.zoneOffset(), importedTrack.zoneOffset());
        assertEquals(new Statistics(
                        Instant.parse("2020-02-02T02:02:02Z"),
                        Instant.parse("2020-02-02T02:04:00Z"),
                        Duration.ofSeconds(56),
                        Duration.ofSeconds(26),  //TODO Likely too low
                        Distance.of(222049.421875), //TODO Too low
                        Speed.of(22203.7421875),
                        new AltitudeExtremities(999.1229858398438, 1020.25),
                        new AltitudeGainLoss(2, 2),
                        null,
                        null
                ),
                importedTrackStatistics);

        // 4. markers
        assertMarkers();
    }

    @LargeTest
    @Test(expected = ImportAlreadyExistsException.class)
    public void kml_with_trackdetail_and_sensordata_duplicate_trackUUID() throws IOException {
        // given
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean(context.getString(R.string.import_prevent_reimport_key), true);
        editor.commit();
        Track track = contentProviderUtils.getTrack(trackId);

        TrackExporter trackExporter = TrackFileFormat.KML_WITH_TRACKDETAIL_AND_SENSORDATA.createTrackExporter(context, contentProviderUtils);

        // when
        trackExporter.writeTrack(List.of(track), context.getContentResolver().openOutputStream(tmpFileUri));

        // 2. import
        InputStream inputStream = context.getContentResolver().openInputStream(tmpFileUri);
        XMLImporter importer = new XMLImporter(new KMLTrackImporter(context, trackImporter));
        importTrackId = importer.importFile(inputStream).get(0);

        // then
        Track importedTrack = contentProviderUtils.getTrack(importTrackId);
        assertNull(importedTrack);
    }

    @LargeTest
    @Test
    public void gpx() throws TimeoutException, IOException {
        // given
        Track track = contentProviderUtils.getTrack(trackId);

        TrackExporter trackExporter = TrackFileFormat.GPX.createTrackExporter(context, contentProviderUtils);

        // when
        // 1. export
        trackExporter.writeTrack(List.of(track), context.getContentResolver().openOutputStream(tmpFileUri));
        contentProviderUtils.deleteTrack(context, trackId);

        // 2. import
        InputStream inputStream = context.getContentResolver().openInputStream(tmpFileUri);
        XMLImporter importer = new XMLImporter(new GPXTrackImporter(context, trackImporter));
        importTrackId = importer.importFile(inputStream).get(0);

        // then
        // 1. track
        Track importedTrack = contentProviderUtils.getTrack(importTrackId);
        assertNotNull(importedTrack);
        assertEquals(track.activityType(), importedTrack.activityType());
        assertEquals(track.activityTypeLocalized(), importedTrack.activityTypeLocalized());
        assertEquals(track.description(), importedTrack.description());
        assertEquals(track.name(), importedTrack.name());

        // 2. trackpoints
        // The GPX exporter does not support exporting TrackPoints without lat/lng.
        // Therefore, the track segmentation is changes.

        TrackPointAssert a = new TrackPointAssert()
                .setDelta(0.05); // speed is not fully
        List<TrackPoint> actual = TestDataUtil.getTrackPoints(contentProviderUtils, importTrackId);
        a.assertEquals(List.of(
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_START_AUTOMATIC,
                        new Position(
                                Instant.parse("2020-02-02T02:02:03Z"),
                                3.123456, 14.001456d, Distance.of(10),
                                Altitude.WGS84.of(1020.2), null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(1, 1)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse("2020-02-02T02:02:17Z"),
                                3.123456, 14.001456, Distance.of(10),
                                Altitude.WGS84.of(1020.2), null,
                                null,
                                Speed.of(5)),
                        Distance.of(12),
                        HeartRate.of(69),
                        Temperature.of(30),
                        Cadence.of(3),
                        Power.of(50),
                        new AltitudeGainLoss(1, 1)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_START_AUTOMATIC,
                        new Position(
                                Instant.parse("2020-02-02T02:03:21Z"),
                                3.123456, 14.002456, Distance.of(10),
                                Altitude.WGS84.of(999.0999755859375), null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_START_AUTOMATIC,
                        new Position(
                                Instant.parse("2020-02-02T02:03:22Z"),
                                3.123456, 16d, Distance.of(10),
                                Altitude.WGS84.of(999.0999755859375), null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse("2020-02-02T02:03:50Z"),
                                3.123456, 16.001, Distance.of(10),
                                Altitude.WGS84.of(999.0999755859375), null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                )
        ), actual);

        // 3. trackstatistics
        assertEquals(track.zoneOffset(), importedTrack.zoneOffset());

        Statistics importedTrackStatistics = importedTrack.statistics();
        assertEquals(new Statistics(
                        Instant.parse("2020-02-02T02:02:03Z"),
                        Instant.parse("2020-02-02T02:03:50Z"),
                        Duration.ofSeconds(107),
                        Duration.ofSeconds(107),
                        Distance.of(222271.734375),
                        Speed.of(2077.305908203125),
                        new AltitudeExtremities(999.0999755859375, 1020.2000122070312),
                        new AltitudeGainLoss(2, 2),
                        null,
                        null
                ),
                importedTrackStatistics);

        // 4. markers
        assertMarkers();
    }

    @LargeTest
    @Test(expected = ImportAlreadyExistsException.class)
    public void gpx_duplicate_trackUUID() throws IOException {
        // given
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean(context.getString(R.string.import_prevent_reimport_key), true);
        editor.commit();
        Track track = contentProviderUtils.getTrack(trackId);

        TrackExporter trackExporter = TrackFileFormat.GPX.createTrackExporter(context, contentProviderUtils);

        // when
        // 1. export
        trackExporter.writeTrack(List.of(track), context.getContentResolver().openOutputStream(tmpFileUri));

        // 2. import
        InputStream inputStream = context.getContentResolver().openInputStream(tmpFileUri);
        XMLImporter importer = new XMLImporter(new GPXTrackImporter(context, trackImporter));
        importTrackId = importer.importFile(inputStream).get(0);

        // then
        // 1. track
        Track trackImported = contentProviderUtils.getTrack(importTrackId);
        assertNull(trackImported);
    }

    @LargeTest
    @Test
    public void csv_export_only() throws IOException {
        // given
        Track track = contentProviderUtils.getTrack(trackId);

        TrackExporter trackExporter = TrackFileFormat.CSV.createTrackExporter(context, contentProviderUtils);

        // when
        // 1. export
        trackExporter.writeTrack(List.of(track), context.getContentResolver().openOutputStream(tmpFileUri));
        contentProviderUtils.deleteTrack(context, trackId);

        // then
        InputStream expected = InstrumentationRegistry.getInstrumentation().getContext().getResources().openRawResource(de.dennisguse.opentracks.test.R.raw.csv_export);
        String expectedText = new BufferedReader(new InputStreamReader(expected, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));


        InputStream actual = context.getContentResolver().openInputStream(tmpFileUri);
        String actualText = new BufferedReader(new InputStreamReader(actual, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));

        assertEquals(expectedText, actualText);
    }

    private void assertMarkers() {
        List<Marker> importedMarkers = TestDataUtil.getMarkers(contentProviderUtils, importTrackId);
        assertEquals(markers.size(), importedMarkers.size());

        for (int i = 0; i < markers.size(); i++) {
            Marker marker = markers.get(i);
            Marker importMarker = importedMarkers.get(i);
            assertEquals(marker.typeLocalized(), importMarker.typeLocalized());
            assertEquals(marker.description(), importMarker.description());
            assertEquals(marker.name(), importMarker.name());
            assertNull(importMarker.photoUrl());

            assertEquals(marker.position().latitude(), importMarker.position().latitude(), 0.001);
            assertEquals(marker.position().longitude(), importMarker.position().longitude(), 0.001);
            assertEquals(marker.position().altitude().toM(), importMarker.position().altitude().toM(), 0.1);
        }
    }

    private static void mockSensorData(TrackPointCreator trackPointCreator, Float speed, Distance distance, float heartRate, float cadence, Float power, Float altitudeGain, float temperature) {
        SensorManager sensorManager = trackPointCreator.getSensorManager();

        AggregatorCyclingPower cyclingPower = new AggregatorCyclingPower("", "");
        cyclingPower.add(trackPointCreator.getNow(), new CyclingPowerBluetooth.Data(Power.of(power), null));
        sensorManager.setAggregator(cyclingPower);


        AggregatorHeartRate avgHeartRate = new AggregatorHeartRate("", "");
        avgHeartRate.add(trackPointCreator.getNow(), HeartRate.of(heartRate));
        sensorManager.setAggregator(avgHeartRate);

        AggregatorCyclingCadence cyclingCadence = new AggregatorCyclingCadence("", "") {
            @NonNull
            @Override
            public Cadence getAggregatedValue(Instant now) {
                return Cadence.of(cadence);
            }

            @Override
            public boolean hasReceivedData() {
                return true;
            }
        };
        sensorManager.setAggregator(cyclingCadence);

        if (distance != null && speed != null) {
            AggregatorCyclingDistanceSpeed aggregatorCyclingDistanceSpeed = new AggregatorCyclingDistanceSpeed("", "") {
                @NonNull
                @Override
                public Data getAggregatedValue(Instant now) {
                    return new AggregatorCyclingDistanceSpeed.Data(null, distance, Speed.of(speed));
                }

                @Override
                public boolean hasReceivedData() {
                    return true;
                }
            };
            sensorManager.setAggregator(aggregatorCyclingDistanceSpeed);
        } else {
            sensorManager.setAggregator(new AggregatorCyclingDistanceSpeed("", ""));
        }

        mockAltitudeChange(trackPointCreator, altitudeGain);

        sensorManager.setAggregator(new AggregatorTemperature("", "'") {
            @NonNull
            @Override
            public Temperature getAggregatedValue(Instant now) {
                return Temperature.of(temperature);
            }

            @Override
            public boolean hasReceivedData() {
                return true;
            }
        });

        trackPointCreator.onChange();
    }

    private static void mockAltitudeChange(TrackPointCreator trackPointCreator, Float altitudeGain) {
        SensorManager sensorManager = trackPointCreator.getSensorManager();

        if (altitudeGain == null) {
            sensorManager.getAltitudeChangeHandler().setAggregator(new AggregatorBarometer("test", null));
            return;
        }

        AggregatorBarometer aggregatorBarometer = new AggregatorBarometer("", "") {
            @NonNull
            @Override
            public AltitudeGainLoss getAggregatedValue(Instant now) {
                return new AltitudeGainLoss(altitudeGain, altitudeGain);
            }

            @Override
            public boolean hasReceivedData() {
                return true;
            }
        };
        sensorManager.getAltitudeChangeHandler().setAggregator(aggregatorBarometer);
    }

    //TODO Use return value (for markers)
    public static Position sendLocation(TrackPointCreator trackPointCreator, String time, double latitude, double longitude, float accuracy, float verticalAccuracy, float speed, double altitude, Float altitudeGain) {
        Location location = new Location("mock");
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setAccuracy(accuracy);
        location.setVerticalAccuracyMeters(verticalAccuracy);
        location.setSpeed(speed);
        location.setAltitude(altitude);

        mockAltitudeChange(trackPointCreator, altitudeGain);

        trackPointCreator.setClock(time);
        trackPointCreator.getSensorManager().getGpsHandler().onDataReceived(location);

        return Position.of(location, Instant.parse(time));
    }
}
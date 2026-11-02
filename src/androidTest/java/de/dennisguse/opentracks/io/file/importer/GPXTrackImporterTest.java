package de.dennisguse.opentracks.io.file.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import de.dennisguse.opentracks.content.data.TestDataUtil;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.Altitude;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.io.file.TrackFileFormat;
import de.dennisguse.opentracks.io.file.exporter.TrackExporter;

/**
 * Test that legacy KML/GPX formats can still be imported.
 */
@RunWith(JUnit4.class)
public class GPXTrackImporterTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private final ContentProviderUtils contentProviderUtils = new ContentProviderUtils(context);

    private TrackImporter trackImporter;

    private Track.Id importTrackId;

    @Before
    public void setUp() {
        trackImporter = new TrackImporter(context, contentProviderUtils, Distance.of(200), true);
    }

    @After
    public void tearDown() {
        if (importTrackId != null) {
            contentProviderUtils.deleteTrack(context, importTrackId);
        }
    }

    @LargeTest
    @Test
    public void gpx_without_speed() throws IOException {
        // given
        XMLImporter importer = new XMLImporter(new GPXTrackImporter(context, trackImporter));
        InputStream inputStream = InstrumentationRegistry.getInstrumentation().getContext().getResources().openRawResource(de.dennisguse.opentracks.test.R.raw.gpx11_without_speed);

        // when
        // 1. import
        importTrackId = importer.importFile(inputStream).get(0);

        // then
        // 2. track
        Track importedTrack = contentProviderUtils.getTrack(importTrackId);
        assertNotNull(importedTrack);
        assertEquals("", importedTrack.activityTypeLocalized());
        assertEquals("", importedTrack.description());
        assertEquals("20210907_213924.gpx", importedTrack.name());
        assertEquals(ActivityType.UNKNOWN, importedTrack.activityType());

        // 3. trackstatistics
        Statistics trackStatistics = importedTrack.statistics();
        assertEquals(0.75, trackStatistics.maxSpeed().toMPS(), 0.01);
        assertEquals(Duration.ofSeconds(101), trackStatistics.movingDuration());

        // 4. trackpoints
        List<TrackPoint> importedTrackPoints = TestDataUtil.getTrackPoints(contentProviderUtils, importTrackId);
        assertEquals(3, importedTrackPoints.size());

        TrackPointAssert a = new TrackPointAssert();
        a.assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_AUTOMATIC,
                        new Position(
                                Instant.parse("2021-09-07T22:10:19Z"),
                                30.14185982, -40.3863038, null,
                                Altitude.WGS84.of(-5), null,
                                null,
                                null)),
                new TrackPoint(TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse("2021-09-07T22:11:07Z"),
                                30.14184657, -40.38670089, null,
                                Altitude.WGS84.of(-5), null,
                                null,
                                Speed.of(0.7976524233818054))),
                new TrackPoint(TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse("2021-09-07T22:12:00Z"),
                                30.14185982, -40.3863038, null,
                                Altitude.WGS84.of(-5), null,
                                null,
                                Speed.of(0.7224021553993225)))
        ), importedTrackPoints);
    }

    @LargeTest
    @Test
    public void gpx_missing_time() throws IOException {
        // given
        XMLImporter importer = new XMLImporter(new GPXTrackImporter(context, trackImporter));
        InputStream inputStream = InstrumentationRegistry.getInstrumentation().getContext().getResources().openRawResource(de.dennisguse.opentracks.test.R.raw.gpx11_missing_time);

        // when
        // 1. import
        importTrackId = importer.importFile(inputStream).get(0);

        // then
        // 2. track
        Track importedTrack = contentProviderUtils.getTrack(importTrackId);
        assertNotNull(importedTrack);
        assertEquals("", importedTrack.activityTypeLocalized());
        assertEquals("", importedTrack.description());
        assertEquals("20210907_213924.gpx", importedTrack.name());
        assertEquals(ActivityType.UNKNOWN, importedTrack.activityType());

        // 3. trackstatistics
        Statistics trackStatistics = importedTrack.statistics();
        assertEquals(0, trackStatistics.maxSpeed().toMPS(), 0.01);
        assertEquals(Duration.ofSeconds(0), trackStatistics.movingDuration());

        // 4. trackpoints
        List<TrackPoint> importedTrackPoints = TestDataUtil.getTrackPoints(contentProviderUtils, importTrackId);
        assertEquals(1, importedTrackPoints.size());

        TrackPointAssert a = new TrackPointAssert();
        a.assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_AUTOMATIC,
                        new Position(
                                Instant.parse("2021-09-07T22:12:00Z"),
                                30.14185982, -40.3863038, null,
                                Altitude.WGS84.of(-5), null,
                                null,
                                null))
        ), importedTrackPoints);
    }

    @LargeTest
    @Test
    public void gpx_with_power_pwd() throws IOException {
        // given
        XMLImporter importer = new XMLImporter(new GPXTrackImporter(context, trackImporter));
        InputStream inputStream = InstrumentationRegistry.getInstrumentation().getContext().getResources().openRawResource(de.dennisguse.opentracks.test.R.raw.gpx_with_power_pwr);

        // when
        // 1. import
        importTrackId = importer.importFile(inputStream).get(0);

        // then
        // 2. track
        Track importedTrack = contentProviderUtils.getTrack(importTrackId);
        assertNotNull(importedTrack);
        assertEquals("", importedTrack.activityTypeLocalized());
        assertEquals("", importedTrack.description());
        assertEquals("gpx with power", importedTrack.name());
        assertEquals(ActivityType.UNKNOWN, importedTrack.activityType());

        // 3. trackpoints
        List<TrackPoint> importedTrackPoints = TestDataUtil.getTrackPoints(contentProviderUtils, importTrackId);
        assertEquals(3, importedTrackPoints.size());

        TrackPointAssert a = new TrackPointAssert();
        a.assertEquals(List.of(
                new TrackPoint(null, TrackPoint.Type.SEGMENT_START_AUTOMATIC,
                        new Position(
                                Instant.parse("2021-09-07T22:10:19Z"),
                                30.14185982, -40.3863038, null,
                                Altitude.WGS84.of(-5), null,
                                null,
                                null),
                        null,
                        null,
                        null,
                        null,
                        new Power(100f),
                        null
                ),
                new TrackPoint(null, TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse("2021-09-07T22:11:07Z"),
                                30.14184657, -40.38670089, null,
                                Altitude.WGS84.of(-5), null,
                                null,
                                Speed.of(0.7976524233818054)),
                        null,
                        null,
                        null,
                        null,
                        new Power(110f),
                        null),
                new TrackPoint(null, TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse("2021-09-07T22:12:00Z"),
                                30.14185982, -40.3863038, null,
                                Altitude.WGS84.of(-5), null,
                                null,
                                Speed.of(0.7224021553993225)),
                        null,
                        null,
                        null,
                        null,
                        new Power(150f),
                        null)
        ), importedTrackPoints);
    }
    @LargeTest
    @Test
    public void gpx_with_power_gpxpx() throws IOException {
        // given
        XMLImporter importer = new XMLImporter(new GPXTrackImporter(context, trackImporter));
        InputStream inputStream = InstrumentationRegistry.getInstrumentation().getContext().getResources().openRawResource(de.dennisguse.opentracks.test.R.raw.gpx_with_power_gpxpx);

        // when
        // 1. import
        importTrackId = importer.importFile(inputStream).get(0);

        // then
        // 2. track
        Track importedTrack = contentProviderUtils.getTrack(importTrackId);
        assertNotNull(importedTrack);
        assertEquals("", importedTrack.activityTypeLocalized());
        assertEquals("", importedTrack.description());
        assertEquals("gpx with power", importedTrack.name());
        assertEquals(ActivityType.UNKNOWN, importedTrack.activityType());

        // 3. trackpoints
        List<TrackPoint> importedTrackPoints = TestDataUtil.getTrackPoints(contentProviderUtils, importTrackId);
        assertEquals(3, importedTrackPoints.size());

        TrackPointAssert a = new TrackPointAssert();
        a.assertEquals(List.of(
                new TrackPoint(null, TrackPoint.Type.SEGMENT_START_AUTOMATIC,
                        new Position(
                                Instant.parse("2021-09-07T22:10:19Z"),
                                30.14185982, -40.3863038, null,
                                Altitude.WGS84.of(-5), null,
                                null,
                                null),
                        null,
                        null,
                        null,
                        null,
                        new Power(100f),
                        null
                ),
                new TrackPoint(null, TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse("2021-09-07T22:11:07Z"),
                                30.14184657, -40.38670089, null,
                                Altitude.WGS84.of(-5), null,
                                null,
                                Speed.of(0.7976524233818054)),
                        null,
                        null,
                        null,
                        null,
                        new Power(110f),
                        null),
                new TrackPoint(null, TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse("2021-09-07T22:12:00Z"),
                                30.14185982, -40.3863038, null,
                                Altitude.WGS84.of(-5), null,
                                null,
                                Speed.of(0.7224021553993225)),
                        null,
                        null,
                        null,
                        null,
                        new Power(150f),
                        null)
        ), importedTrackPoints);
    }

    @LargeTest
    @Test
    public void gpx_speed_no_namespace() throws IOException {
        // given
        XMLImporter importer = new XMLImporter(new GPXTrackImporter(context, trackImporter));
        InputStream inputStream = InstrumentationRegistry.getInstrumentation().getContext().getResources().openRawResource(de.dennisguse.opentracks.test.R.raw.gpx11_with_speed_no_namespace);

        // when
        // 1. import
        importTrackId = importer.importFile(inputStream).get(0);

        // then
        // 2. track
        Track importedTrack = contentProviderUtils.getTrack(importTrackId);
        assertNotNull(importedTrack);
        assertEquals("", importedTrack.activityTypeLocalized());
        assertEquals("", importedTrack.description());
        assertEquals("20210907_213924.gpx", importedTrack.name());
        assertEquals(ActivityType.UNKNOWN, importedTrack.activityType());

        // 3. trackstatistics
        Statistics trackStatistics = importedTrack.statistics();
        assertEquals(5.0, trackStatistics.maxSpeed().toMPS(), 0.01);
        assertEquals(Duration.ofSeconds(101), trackStatistics.movingDuration());

        // 4. trackpoints
        List<TrackPoint> importedTrackPoints = TestDataUtil.getTrackPoints(contentProviderUtils, importTrackId);
        assertEquals(3, importedTrackPoints.size());

        TrackPointAssert a = new TrackPointAssert();
        a.assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_AUTOMATIC,
                        new Position(
                                Instant.parse("2021-09-07T22:10:19Z"),
                                30.14185982, -40.3863038, null,
                                Altitude.WGS84.of(-5), null,
                                null,
                                Speed.of(5))),
                new TrackPoint(TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse("2021-09-07T22:11:07Z"),
                                30.14184657, -40.38670089, null,
                                Altitude.WGS84.of(-5), null,
                                null,
                                Speed.of(4))),
                new TrackPoint(TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse("2021-09-07T22:12:00Z"),
                                30.14185982, -40.3863038, null,
                                Altitude.WGS84.of(-5), null,
                                null,
                                Speed.of(3)))
        ), importedTrackPoints);
    }

    /**
     * until v4.18.0: some extensions where incorrectly added to gpxtpx:TrackPointExtension
     * We only need to check the trackpoints.
     */
    @LargeTest
    @Test
    public void gpx_legacy_trackpointextension() throws IOException {
        // given
        XMLImporter importer = new XMLImporter(new GPXTrackImporter(context, trackImporter));
        InputStream inputStream = InstrumentationRegistry.getInstrumentation().getContext().getResources().openRawResource(de.dennisguse.opentracks.test.R.raw.legacy_gpx_trackpointextensions_incorrect);

        // when
        // 1. import
        importTrackId = importer.importFile(inputStream).get(0);

        // then: We only need to check the trackpoints.

        List<TrackPoint> actual = TestDataUtil.getTrackPoints(contentProviderUtils, importTrackId);

        TrackPointAssert a = new TrackPointAssert()
                .setDelta(0.05); // speed is not fully
        a.assertEquals(List.of(
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_START_AUTOMATIC,
                        new Position(
                                Instant.parse("2020-02-02T02:02:03Z"),
                                3d, 14d, Distance.of(10),
                                Altitude.WGS84.of(10), null,
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
                                3d, 14.001, Distance.of(10),
                                Altitude.WGS84.of(10), null,
                                null,
                                Speed.of(5)),
                        Distance.of(12),
                        HeartRate.of(69),
                        null,
                        Cadence.of(3),
                        Power.of(50),
                        new AltitudeGainLoss(1, 1)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_START_AUTOMATIC,
                        new Position(
                                Instant.parse("2020-02-02T02:03:21Z"),
                                3d, 14.002, Distance.of(10),
                                Altitude.WGS84.of(10), null,
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
                                3d, 16d, Distance.of(10),
                                Altitude.WGS84.of(10), null,
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
                                3d, 16.001, Distance.of(10),
                                Altitude.WGS84.of(10), null,
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
    }

    @LargeTest
    @Test
    public void importExportTest_timezone() throws IOException {
        // given
        XMLImporter importer = new XMLImporter(new GPXTrackImporter(context, trackImporter));
        InputStream inputStream = InstrumentationRegistry.getInstrumentation().getContext().getResources().openRawResource(de.dennisguse.opentracks.test.R.raw.gpx11_timezone);
        try (InputStream inputStreamExpected = InstrumentationRegistry.getInstrumentation().getContext().getResources().openRawResource(de.dennisguse.opentracks.test.R.raw.gpx11_timezone)) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            // when
            // 1. import
            importTrackId = importer.importFile(inputStream).get(0);
            Track importedTrack = contentProviderUtils.getTrack(importTrackId);

            TrackExporter trackExporter = TrackFileFormat.GPX.createTrackExporter(context, contentProviderUtils);
            trackExporter.writeTrack(List.of(importedTrack), outputStream);

            // then
            assertEquals(new String(inputStreamExpected.readAllBytes(), StandardCharsets.UTF_8), outputStream.toString());
        }
    }
}
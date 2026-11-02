package de.dennisguse.opentracks.ui.intervals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.util.Pair;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import de.dennisguse.opentracks.content.data.TestDataUtil;
import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.data.statistics.TrackStatisticsUpdater;

@RunWith(JUnit4.class)
public class IntervalStatisticsUpdaterTest {

    /**
     * Tests that build method compute the distance correctly comparing the result with TrackStatisticsUpdater result.
     */
    @Test
    public void testBuild_1() {
        // With 50 points and interval distance of 1000m.

        // given
        float distanceInterval = 1000f;

        // when and then
        IntervalStatisticsComputation computation = computeIntervalStatistics(50, distanceInterval);
        assertIntervalStatisticsComputation(computation, 50, distanceInterval);
        assertEquals((int) Math.ceil(computation.track.statistics().totalDistance().toM() / distanceInterval), computation.intervalList.size());
        assertEquals(computation.track.statistics().totalDistance().toM(), computation.totalDistance.toM(), 0.01);
    }

    /**
     * Tests that build method compute the distance correctly comparing the result with TrackStatisticsUpdater result.
     */
    @Test
    public void testBuild_2() {
        // With 200 points and interval distance of 1000m.

        // given
        float distanceInterval = 1000f;

        // when and then
        IntervalStatisticsComputation computation = computeIntervalStatistics(200, distanceInterval);
        assertIntervalStatisticsComputation(computation, 200, distanceInterval);
        assertEquals((int) Math.ceil(computation.track.statistics().totalDistance().toM() / distanceInterval), computation.intervalList.size());
        assertEquals(computation.track.statistics().totalDistance().toM(), computation.totalDistance.toM(), 0.01);
    }

    /**
     * Tests that build method compute the distance correctly comparing the result with TrackStatisticsUpdater result.
     */
    @Test
    public void testBuild_3() {
        // With 200 points and interval distance of 3000m.

        // given
        float distanceInterval = 3000f;

        // when and then
        IntervalStatisticsComputation computation = computeIntervalStatistics(3000, distanceInterval);
        assertIntervalStatisticsComputation(computation, 3000, distanceInterval);
        assertEquals((int) Math.ceil(computation.track.statistics().totalDistance().toM() / distanceInterval), computation.intervalList.size());
        assertEquals(computation.track.statistics().totalDistance().toM(), computation.totalDistance.toM(), 0.01);
    }

    /**
     * Tests that build method compute the distance correctly comparing the result with TrackStatisticsUpdater result.
     */
    @Test
    public void testBuild_4() {
        // With 1000 points and interval distance of 3000m.

        // given
        float distanceInterval = 3000f;

        // when and then
        IntervalStatisticsComputation computation = computeIntervalStatistics(1000, distanceInterval);
        assertIntervalStatisticsComputation(computation, 1000, distanceInterval);
        assertEquals((int) Math.ceil(computation.track.statistics().totalDistance().toM() / distanceInterval), computation.intervalList.size());
        assertEquals(computation.track.statistics().totalDistance().toM(), computation.totalDistance.toM(), 0.01);
    }

    /**
     * Tests that build method compute the distance correctly comparing the result with TrackStatisticsUpdater result.
     */
    @Test
    public void testBuild_5() {
        // With 10000 points and interval distance of 1000m.

        // given
        float distanceInterval = 1000f;

        // when and then
        IntervalStatisticsComputation computation = computeIntervalStatistics(10000, distanceInterval);
        assertIntervalStatisticsComputation(computation, 10000, distanceInterval);
        assertEquals((int) Math.ceil(computation.track.statistics().totalDistance().toM() / distanceInterval), computation.intervalList.size());
        assertEquals(computation.track.statistics().totalDistance().toM(), computation.totalDistance.toM(), 0.01);
    }

    @Test
    public void testWithNoLossTrackPoints() {
        // TrackPoints with elevation gain but without elevation loss.

        // given
        float distanceInterval = 1000f;
        int numberOfPoints = 10000;

        ArrayList<TrackPoint> trackPoints = new ArrayList<>();
        for (int i = 0; i < numberOfPoints; i++) {
            trackPoints.add(TestDataUtil.createTrackPoint(i, TrackPoint.Type.TRACKPOINT, null));
        }
        TrackStatisticsUpdater trackStatisticsUpdater = new TrackStatisticsUpdater(trackPoints);

        Track dummyTrack = new Track(
                new Track.Id(System.currentTimeMillis()),
                null,
                "Dummy Track Without Elevation Loss",
                null,
                "",
                null,
                ZoneOffset.UTC,
                trackStatisticsUpdater.getTrackStatistics()
        );

        Pair<Track, List<TrackPoint>> trackWithStats = new Pair<>(dummyTrack, trackPoints);

        // when and then
        IntervalStatisticsComputation computation = computeIntervalStatistics(trackWithStats.first, trackWithStats.second, distanceInterval);
        assertIntervalStatisticsComputation(computation, numberOfPoints, distanceInterval);
        assertEquals((int) Math.ceil(computation.track.statistics().totalDistance().toM() / distanceInterval), computation.intervalList.size());
        assertEquals(computation.track.statistics().totalDistance().toM(), computation.totalDistance.toM(), 0.01);
    }

    private IntervalStatisticsComputation computeIntervalStatistics(int numberOfPoints, float distanceInterval) {
        Pair<Track, List<TrackPoint>> trackWithStats = buildTrackWithTrackPoints(numberOfPoints);
        return computeIntervalStatistics(trackWithStats.first, trackWithStats.second, distanceInterval);
    }

    private IntervalStatisticsComputation computeIntervalStatistics(Track track, List<TrackPoint> trackPoints, float distanceInterval) {
        IntervalStatisticsUpdater intervalStatistics = new IntervalStatisticsUpdater(Distance.of(distanceInterval));

        intervalStatistics.addTrackPoints(trackPoints.iterator());

        List<Statistics> intervalList = intervalStatistics.getIntervalList();
        Distance totalDistance = Distance.ZERO;
        float totalTime = 0L;
        Float totalGain = null;
        Float totalLoss = null;
        for (Statistics i : intervalList) {
            totalDistance = totalDistance.plus(i.totalDistance());
            totalTime += i.totalDistance().toM() / i.getAverageSpeed().toMPS();

            if (totalGain == null) {
                totalGain = i.altitudeGainLoss() != null ? i.altitudeGainLoss().gain_m() : null;
            } else if (i.altitudeGainLoss() != null) {
                totalGain += i.altitudeGainLoss().gain_m();
            }

            if (totalLoss == null) {
                totalLoss = i.altitudeGainLoss() != null ? i.altitudeGainLoss().loss_m() : null;
            } else if (i.altitudeGainLoss() != null) {
                totalLoss += i.altitudeGainLoss().loss_m();
            }
        }

        return new IntervalStatisticsComputation(track, intervalList, totalDistance, totalTime, totalGain, totalLoss);
    }

    private void assertIntervalStatisticsComputation(IntervalStatisticsComputation computation, int numberOfPoints, float distanceInterval) {
        assertEquals(computation.track.statistics().totalDuration().toSeconds(), computation.totalTime, 0.01);
        assertEquals(computation.track.statistics().totalDistance().toM(), computation.totalDistance.toM(), 0.01);
        assertEquals(computation.intervalList.size(), (int) Math.ceil(computation.track.statistics().totalDistance().toM() / distanceInterval));
        if (computation.totalGain != null && computation.totalLoss != null) {
            assertEquals(computation.totalGain, numberOfPoints * TestDataUtil.ALTITUDE_GAIN, 0.1);
            assertEquals(computation.totalLoss, numberOfPoints * TestDataUtil.ALTITUDE_LOSS, 0.1);

        } else {
            assertTrue(computation.intervalList.stream().noneMatch(i -> i.altitudeGainLoss() != null));
        }

        Distance remainingDistance = computation.totalDistance;
        for (int i = 0; i < computation.intervalList.size() - 1; i++) {
            assertEquals(computation.intervalList.get(i).totalDistance().toM(), distanceInterval, 0.001);
            remainingDistance = remainingDistance.minus(computation.intervalList.get(i).totalDistance());
        }
        assertEquals(computation.intervalList.get(computation.intervalList.size() - 1).totalDistance().toM(), remainingDistance.toM(), 0.01);
    }

    private static Pair<Track, List<TrackPoint>> buildTrackWithTrackPoints(int numberOfPoints) {
        ArrayList<TrackPoint> trackPoints = new ArrayList<>();
        for (int i = 0; i < numberOfPoints; i++) {
            trackPoints.add(TestDataUtil.createTrackPoint(i));
        }
        TrackStatisticsUpdater trackStatisticsUpdater = new TrackStatisticsUpdater(trackPoints);

        Track track = new Track(
                null,
                null,
                "",
                "",
                "",
                ActivityType.UNKNOWN,
                ZoneOffset.UTC,
                trackStatisticsUpdater.getTrackStatistics());

        return new Pair<>(track, trackPoints);
    }

    private static final class IntervalStatisticsComputation {
        private final Track track;
        private final List<Statistics> intervalList;
        private final Distance totalDistance;
        private final float totalTime;
        private final Float totalGain;
        private final Float totalLoss;

        private IntervalStatisticsComputation(Track track,
                                              List<Statistics> intervalList,
                                              Distance totalDistance,
                                              float totalTime,
                                              Float totalGain,
                                              Float totalLoss) {
            this.track = track;
            this.intervalList = intervalList;
            this.totalDistance = totalDistance;
            this.totalTime = totalTime;
            this.totalGain = totalGain;
            this.totalLoss = totalLoss;
        }
    }
}

package de.dennisguse.opentracks.ui.intervals;

import androidx.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.data.statistics.TrackStatisticsUpdater;

public class IntervalStatisticsUpdater {
    private TrackStatisticsUpdater trackStatisticsUpdater;
    private final List<Interval> intervalList;
    private final Distance distanceInterval;
    private Interval interval, lastInterval;

    /**
     * @param distanceInterval distance of every interval.
     */
    public IntervalStatisticsUpdater(Distance distanceInterval) {
        this.distanceInterval = distanceInterval;

        interval = new Interval();
        lastInterval = new Interval();
        intervalList = new ArrayList<>();
        intervalList.add(lastInterval);
    }

    /**
     * Complete intervals with the tracks points from the iterator.
     *
     * @return the last track point's id used to compute the intervals.
     */
    public TrackPoint.Id addTrackPoints(Iterator<TrackPoint> trackPointIterator) {
        boolean newIntervalAdded = false;
        TrackPoint trackPoint = null;

        while (trackPointIterator.hasNext()) {
            trackPoint = trackPointIterator.next();
            if (trackStatisticsUpdater == null) {
                trackStatisticsUpdater = new TrackStatisticsUpdater(trackPoint);
            } else {
                trackStatisticsUpdater.addTrackPoint(trackPoint);
            }

            if (trackStatisticsUpdater.getTrackStatistics().totalDistance().plus(interval.distance).greaterOrEqualThan(distanceInterval)) {
                interval.add(trackStatisticsUpdater.getTrackStatistics(), trackPoint);

                double adjustFactor = distanceInterval.dividedBy(interval.distance);
                Interval adjustedInterval = new Interval(interval, adjustFactor);

                intervalList.set(intervalList.size() - 1, adjustedInterval);

                interval = new Interval(interval.distance.minus(adjustedInterval.distance), interval.time.minus(adjustedInterval.time));
                trackStatisticsUpdater = new TrackStatisticsUpdater(trackPoint);

                lastInterval = new Interval(interval);
                intervalList.add(lastInterval);

                newIntervalAdded = true;
            }
        }

        if (trackStatisticsUpdater != null) {
            if (newIntervalAdded) {
                lastInterval.add(trackStatisticsUpdater.getTrackStatistics(), null);
            } else {
                lastInterval.set(trackStatisticsUpdater.getTrackStatistics());
            }
        }

        return trackPoint != null ? trackPoint.id() : null;
    }

    public List<Statistics> getIntervalList() {
        return intervalList.stream().map(Interval::toStatistics).toList();
    }

    /**
     * Return the last completed interval.
     * An interval is complete if its distance is equal to distanceInterval_m.
     *
     * @return the interval object or null if any interval is completed.
     */
    @Nullable
    public Statistics getLastInterval() {
        if (intervalList.size() == 1 && intervalList.get(0).distance.lessThan(distanceInterval)) {
            return null;
        }

        for (int i = intervalList.size() - 1; i >= 0; i--) {
            if (intervalList.get(i).distance.greaterOrEqualThan(distanceInterval)) {
                return this.intervalList.get(i).toStatistics();
            }
        }

        return null;
    }

    //TODO Could be replaced with Statistics?
    @Deprecated
    private static class Interval {
        private Distance distance = Distance.ZERO;
        private Duration time = Duration.ZERO;
        private Float gain_m;
        private Float loss_m;
        private HeartRate avgHeartRate;
        private Power avgPower;

        public Interval() {
        }

        public Interval(Distance distance, Duration time) {
            this.distance = distance;
            this.time = time;
        }

        public Interval(Interval i, double adjustFactor) {
            distance = i.distance.multipliedBy(adjustFactor);
            time = Duration.ofMillis((long) (i.time.toMillis() * adjustFactor));
            gain_m = i.gain_m;
            loss_m = i.loss_m;
            avgHeartRate = i.avgHeartRate;
            avgPower = i.avgPower;
        }

        public Interval(Interval i) {
            distance = i.distance;
            time = i.time;
            gain_m = i.gain_m;
            loss_m = i.loss_m;
            avgHeartRate = i.avgHeartRate;
            avgPower = i.avgPower;
        }

        private void add(Statistics trackStatistics, @Nullable TrackPoint lastTrackPoint) {
            distance = distance.plus(trackStatistics.totalDistance());
            time = time.plus(trackStatistics.totalDuration());
            if (trackStatistics.altitudeGainLoss() != null) {
                gain_m = trackStatistics.altitudeGainLoss().gain_m();
                loss_m = trackStatistics.altitudeGainLoss().loss_m();
            }
            avgHeartRate = trackStatistics.avgHeartRate();
            avgPower = trackStatistics.avgPower();
            if (lastTrackPoint == null) {
                return;
            }
            if (gain_m != null && lastTrackPoint.altitudeGainLoss() != null) {
                gain_m = gain_m - lastTrackPoint.altitudeGainLoss().gain_m();
                loss_m = loss_m - lastTrackPoint.altitudeGainLoss().loss_m();
            }
        }

        private void set(Statistics trackStatistics) {
            distance = trackStatistics.totalDistance();
            time = trackStatistics.totalDuration();
            if (trackStatistics.altitudeGainLoss() != null) {
                gain_m = trackStatistics.altitudeGainLoss().gain_m();
                loss_m = trackStatistics.altitudeGainLoss().loss_m();
            }
            avgHeartRate = trackStatistics.avgHeartRate();
            avgPower = trackStatistics.avgPower();
        }

        private Statistics toStatistics() {
            return new Statistics(
                    null,
                    null,
                    time,
                    null,
                    distance,
                    null,
                    null,
                    gain_m != null ? new AltitudeGainLoss(gain_m, loss_m) : null,
                    avgHeartRate,
                    avgPower
            );
        }
    }
}

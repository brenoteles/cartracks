/*
 * Copyright 2012 Google Inc.
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

package de.dennisguse.opentracks.share;

import android.content.Context;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.VisibleForTesting;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.SpeedFormatter;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.settings.UnitSystem;
import de.dennisguse.opentracks.util.StringUtils;

/**
 * Generates descriptions for tracks.
 *
 * @author Jimmy Shih
 */
public class TrackShareSummaryGenerator {

    private static final String TEXT_LINE_BREAK = "\n";

    private final Context context;

    public TrackShareSummaryGenerator(Context context) {
        this.context = context;
    }

    /**
     * Generates a track description.
     *
     * @param track the track
     */
    public String generateTrackDescription(Track track) {
        StringBuilder builder = new StringBuilder();

        // Created by
        builder.append(context.getString(R.string.app_name));
        builder.append(TEXT_LINE_BREAK);
        builder.append(TEXT_LINE_BREAK);

        writeString(track.name(), builder, R.string.generic_name_line);
        writeString(track.activityTypeLocalized(), builder, R.string.description_activity_type);
        writeString(track.description(), builder, R.string.generic_description_line);
        builder.append(generateTrackStatisticsDescription(track.statistics()));

        return builder.toString();
    }

    private void writeString(String text, StringBuilder builder, int resId) {
        if (TextUtils.isEmpty(text)) {
            text = context.getString(R.string.value_unknown);
        }
        builder.append(context.getString(resId, text));
        builder.append(TrackShareSummaryGenerator.TEXT_LINE_BREAK);
    }

    private String generateTrackStatisticsDescription(Statistics stats) {
        StringBuilder builder = new StringBuilder();

        // Total distance
        writeDistance(stats.totalDistance(), builder, R.string.description_total_distance);

        // Total time
        writeTime(stats.totalDuration(), builder, R.string.description_total_time);

        // Moving time
        writeTime(stats.movingDuration(), builder, R.string.description_moving_time);

        // Average speed
        writeSpeed(stats.getAverageSpeed(), builder, R.string.description_average_speed);

        // Average moving speed
        writeSpeed(stats.getAverageMovingSpeed(), builder, R.string.description_average_moving_speed);

        // Max speed
        writeSpeed(stats.maxSpeed(), builder, R.string.description_max_speed);

        // Average pace
        writePace(stats.getAverageSpeed(), builder, R.string.description_average_pace_in_minute);

        // Average moving pace
        writePace(stats.getAverageMovingSpeed(), builder, R.string.description_average_moving_pace_in_minute);

        // Fastest pace
        writePace(stats.maxSpeed(), builder, R.string.description_fastest_pace_in_minute);

        // Min and Max altitude
        if (stats.altitudeExtremities() != null) {
            writeAltitude(stats.altitudeExtremities().max_m(), builder, R.string.description_max_altitude);
            writeAltitude(stats.altitudeExtremities().min_m(), builder, R.string.description_min_altitude);
        }

        // Altitude gain & loss
        if (stats.altitudeGainLoss() != null) {
            writeAltitude(stats.altitudeGainLoss().gain_m(), builder, R.string.description_altitude_gain);
            writeAltitude(stats.altitudeGainLoss().loss_m(), builder, R.string.description_altitude_loss);
        }

        // Recorded time
        builder.append(context.getString(R.string.description_recorded_time, StringUtils.formatDateTimeWithOffset(OffsetDateTime.ofInstant(stats.startTime(), ZoneId.systemDefault()))));
        builder.append(TEXT_LINE_BREAK);

        return builder.toString();
    }

    @VisibleForTesting
    void writeDistance(Distance distance, StringBuilder builder, int resId) {
        double distanceInKm = distance.toKM();
        double distanceInMi = distance.toMI();
        builder.append(context.getString(resId, distanceInKm, distanceInMi));
        builder.append(TEXT_LINE_BREAK);
    }

    @VisibleForTesting
    void writeTime(Duration time, StringBuilder builder, int resId) {
        builder.append(context.getString(resId, StringUtils.formatElapsedTime(time)));
        builder.append(TEXT_LINE_BREAK);
    }

    @VisibleForTesting
    void writeSpeed(Speed speed, StringBuilder builder, int resId) {
        builder.append(context.getString(resId, speed.toKMH(), speed.toMPH()));
        builder.append(TEXT_LINE_BREAK);
    }

    @VisibleForTesting
    void writePace(Speed speed, StringBuilder builder, int resId) {
        Pair<String, String> paceInMetrics = SpeedFormatter.Builder().setUnit(UnitSystem.METRIC).setReportSpeedOrPace(false).build(context).getSpeedParts(speed);
        Pair<String, String> paceInImperial = SpeedFormatter.Builder().setUnit(UnitSystem.IMPERIAL_FEET).setReportSpeedOrPace(false).build(context).getSpeedParts(speed);

        String formattedPaceMetrics = paceInMetrics.first != null ? paceInMetrics.first : context.getString(R.string.value_unknown);
        String formattedPaceImperial = paceInImperial.first != null ? paceInImperial.first : context.getString(R.string.value_unknown);

        builder.append(context.getString(resId, formattedPaceMetrics, formattedPaceImperial));
        builder.append(TEXT_LINE_BREAK);
    }

    @VisibleForTesting
    void writeAltitude(double altitude_m, StringBuilder builder, int resId) {
        long altitudeInM = Math.round(altitude_m);
        long altitudeInFt = Math.round(Distance.of(altitude_m).toFT());
        builder.append(context.getString(resId, altitudeInM, altitudeInFt));
        builder.append(TEXT_LINE_BREAK);
    }
}

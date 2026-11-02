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

package de.dennisguse.opentracks.ui.util;

import android.content.Context;
import android.widget.TextView;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.DistanceFormatter;
import de.dennisguse.opentracks.settings.UnitSystem;
import de.dennisguse.opentracks.util.StringUtils;

/**
 * Utilities to display a list item.
 *
 * @author Jimmy Shih
 */
public class ListItemUtils {

    private ListItemUtils() {
    }

    public static String getTimeText(Context context, boolean isRecording, Duration totalTime) {
        String timeText;
        if (isRecording) {
            timeText = context.getString(R.string.generic_recording);
        } else {
            timeText = StringUtils.formatElapsedTime(totalTime);
        }
        return timeText;
    }

    public static String getDistanceText(Context context, UnitSystem unitSystem, boolean isRecording, Distance totalDistance) {

        StringBuilder builder = new StringBuilder();

        if (!isRecording) {
            String distanceText = DistanceFormatter.Builder()
                    .setUnit(unitSystem)
                    .build(context)
                    .formatDistance(totalDistance);

            builder.append("(").append(distanceText).append(")");
        }

        return builder.toString();
    }

    public static void setDateAndTime(Context context, TextView dateView, TextView timeView, Instant time, ZoneOffset timeZone) {
        OffsetDateTime offsetDateTime = OffsetDateTime.ofInstant(time, timeZone);
        String dateContent = StringUtils.formatDateTodayRelative(context, offsetDateTime);
        String pattern = "HH:mm";
        if (!offsetDateTime.getOffset().equals(OffsetDateTime.now().getOffset())) {
            pattern = "HH:mm x";
        }
        String timeContent = offsetDateTime.format(DateTimeFormatter.ofPattern(pattern));

        dateView.setText(dateContent);
        timeView.setText(timeContent);
    }
}

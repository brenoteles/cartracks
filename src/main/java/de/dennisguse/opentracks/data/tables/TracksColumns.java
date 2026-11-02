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

package de.dennisguse.opentracks.data.tables;

import android.provider.BaseColumns;

/**
 * Constants for the tracks table.
 *
 * @author Leif Hendrik Wilden
 */
public interface TracksColumns extends BaseColumns {

    String TABLE_NAME = "tracks";

    // Columns
    String UUID = "uuid"; // identifier to make tracks globally unique (prevent re-import)
    String NAME = "name"; // track name
    String DESCRIPTION = "description"; // track description
    /** see {@link de.dennisguse.opentracks.data.models.ActivityType}.id */
    String ACTIVITY_TYPE = "activity_type";
    String ACTIVITY_TYPE_LOCALIZED = "activity_type_localized";

    String STARTTIME_OFFSET = "time_offset"; // in plus/minus in seconds
    String STARTTIME = "time_start"; // track start time
    String STOPTIME = "time_stop"; // track stop time

    String MARKER_COUNT = "markerCount"; // the numbers of markers (virtual column)
    String TRACK_COUNT = "trackCount"; // the numbers of markers (virtual column)

    String TOTALDISTANCE = "distance"; // total distance
    String TOTALTIME = "duration_total";
    String MOVINGTIME = "duration_moving";

    String MAXSPEED = "speed_max";
    String MIN_ALTITUDE = "altitude_min";
    String MAX_ALTITUDE = "altitude_max";
    String ALTITUDE_GAIN = "altitude_gain";
    String ALTITUDE_LOSS = "altitude_loss";

    String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " ("
            + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + UUID + " BLOB, "
            + NAME + " TEXT, "
            + DESCRIPTION + " TEXT, "
            + ACTIVITY_TYPE + " TEXT, "
            + ACTIVITY_TYPE_LOCALIZED + " TEXT, "
            + STARTTIME_OFFSET + " INTEGER, "
            + STARTTIME + " INTEGER, "
            + STOPTIME + " INTEGER, "
            + TOTALTIME + " INTEGER, "
            + MOVINGTIME + " INTEGER, "
            + TOTALDISTANCE + " FLOAT, "
            + MAXSPEED + " FLOAT, "
            + MIN_ALTITUDE + " FLOAT, "
            + MAX_ALTITUDE + " FLOAT, "
            + ALTITUDE_GAIN + " FLOAT, "
            + ALTITUDE_LOSS + " FLOAT)";

    String CREATE_TABLE_INDEX = "CREATE UNIQUE INDEX " + TABLE_NAME + "_" + UUID + "_index ON " + TABLE_NAME + "(" + UUID + ")";

}

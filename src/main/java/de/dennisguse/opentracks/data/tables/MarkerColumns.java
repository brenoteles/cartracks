/*
 * Copyright 2009 Google Inc.
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

import android.content.Context;
import android.provider.BaseColumns;
import android.widget.ArrayAdapter;

import de.dennisguse.opentracks.R;

/**
 * Constants for markers table.
 *
 * @author Leif Hendrik Wilden
 */
public interface MarkerColumns extends BaseColumns {

    String TABLE_NAME = "markers";

    // Columns
    String NAME = "name";
    String DESCRIPTION = "description";
    String TYPE_LOCALIZED = "marker_type_localized";
    String TRACKID = "trackid";
    String LONGITUDE = "longitude";
    String LATITUDE = "latitude";
    String TIME = "time";
    String ALTITUDE = "altitude";
    String HORIZONTAL_ACCURACY = "accuracy";
    String BEARING = "bearing";

    String PHOTOURL = "photoUrl"; // url for the photo

    String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " ("
            + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + TRACKID + " INTEGER NOT NULL, "
            + NAME + " TEXT, "
            + DESCRIPTION + " TEXT, "
            + TYPE_LOCALIZED + " TEXT, "
            + PHOTOURL + " TEXT, "
            + LONGITUDE + " INTEGER, "
            + LATITUDE + " INTEGER, "
            + TIME + " INTEGER, "
            + ALTITUDE + " FLOAT, "
            + HORIZONTAL_ACCURACY + " FLOAT, "
            + BEARING + " FLOAT, "
            + "FOREIGN KEY (" + TRACKID + ") REFERENCES " + TracksColumns.TABLE_NAME + "(" + TracksColumns._ID + ") ON UPDATE CASCADE ON DELETE CASCADE"
            + ")";

    String CREATE_TABLE_INDEX = "CREATE INDEX " + TABLE_NAME + "_" + TRACKID + "_index ON " + TABLE_NAME + "(" + TRACKID + ")";

    static ArrayAdapter<CharSequence> getTypes(Context context) {
        return ArrayAdapter.createFromResource(context, R.array.marker_types, android.R.layout.simple_dropdown_item_1line);
    }
}

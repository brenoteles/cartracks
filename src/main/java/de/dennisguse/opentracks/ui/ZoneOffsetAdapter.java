package de.dennisguse.opentracks.ui;


import android.content.Context;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import java.time.ZoneOffset;

/**
 * <a href="Data source for UTC Offsets">https://en.wikipedia.org/wiki/List_of_UTC_offsets</a>
 */
public class ZoneOffsetAdapter extends ArrayAdapter<ZoneOffset> {
    public ZoneOffsetAdapter(@NonNull Context context, int resource) {
        super(context, resource);

        addAll(
                ZoneOffset.ofHoursMinutes(-12, 0),
                ZoneOffset.ofHoursMinutes(-11, 0),
                ZoneOffset.ofHoursMinutes(-10, 0),
                ZoneOffset.ofHoursMinutes(-9, -30),
                ZoneOffset.ofHoursMinutes(-8, 0),
                ZoneOffset.ofHoursMinutes(-7, 0),
                ZoneOffset.ofHoursMinutes(-6, 0),
                ZoneOffset.ofHoursMinutes(-5, 0),
                ZoneOffset.ofHoursMinutes(-4, 0),
                ZoneOffset.ofHoursMinutes(-3, -30),
                ZoneOffset.ofHoursMinutes(-3, 0),
                ZoneOffset.ofHoursMinutes(-2, 0),
                ZoneOffset.ofHoursMinutes(-1, 0),
                ZoneOffset.ofHoursMinutes(0, 0),
                ZoneOffset.ofHoursMinutes(1, 0),
                ZoneOffset.ofHoursMinutes(2, 0),
                ZoneOffset.ofHoursMinutes(3, 0),
                ZoneOffset.ofHoursMinutes(3, 30),
                ZoneOffset.ofHoursMinutes(4, 0),
                ZoneOffset.ofHoursMinutes(4, 30),
                ZoneOffset.ofHoursMinutes(5, 30),
                ZoneOffset.ofHoursMinutes(5, 45),
                ZoneOffset.ofHoursMinutes(6, 0),
                ZoneOffset.ofHoursMinutes(6, 30),
                ZoneOffset.ofHoursMinutes(7, 0),
                ZoneOffset.ofHoursMinutes(8, 0),
                ZoneOffset.ofHoursMinutes(8, 45),
                ZoneOffset.ofHoursMinutes(9, 0),
                ZoneOffset.ofHoursMinutes(9, 30),
                ZoneOffset.ofHoursMinutes(10, 0),
                ZoneOffset.ofHoursMinutes(10, 30),
                ZoneOffset.ofHoursMinutes(11, 0),
                ZoneOffset.ofHoursMinutes(12, 0),
                ZoneOffset.ofHoursMinutes(12, 45),
                ZoneOffset.ofHoursMinutes(13, 0),
                ZoneOffset.ofHoursMinutes(14, 0)
        );
    }
}

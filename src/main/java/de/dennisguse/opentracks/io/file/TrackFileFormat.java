package de.dennisguse.opentracks.io.file;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.io.file.exporter.CSVTrackExporter;
import de.dennisguse.opentracks.io.file.exporter.GPXTrackExporter;
import de.dennisguse.opentracks.io.file.exporter.KMLTrackExporter;
import de.dennisguse.opentracks.io.file.exporter.KMZTrackExporter;
import de.dennisguse.opentracks.io.file.exporter.TrackExporter;

/**
 * Definition of all possible track formats.
 * <p>
 * NOTE: The names of the entries are used in the user's settings.
 */
public enum TrackFileFormat {

    KML_WITH_TRACKDETAIL_AND_SENSORDATA("KML_WITH_TRACKDETAIL_AND_SENSORDATA") {
        @Override
        public TrackExporter createTrackExporter(@NonNull Context context, @NonNull ContentProviderUtils contentProviderUtils) {
            return new KMLTrackExporter(context, contentProviderUtils, false);
        }

        @NonNull
        @Override
        public String getMimeType() {
            return "application/vnd.google-earth.kml+xml";
        }

        @NonNull
        public String getExtension() {
            return "kml";
        }
    },

    KMZ_WITH_TRACKDETAIL_AND_SENSORDATA_AND_PICTURES("KMZ_WITH_TRACKDETAIL_AND_SENSORDATA_AND_PICTURES") {

        private static final boolean exportPhotos = true;

        @Override
        public TrackExporter createTrackExporter(@NonNull Context context, @NonNull ContentProviderUtils contentProviderUtils) {
            return new KMZTrackExporter(context, contentProviderUtils, exportPhotos);
        }

        @NonNull
        @Override
        public String getMimeType() {
            return "application/vnd.google-earth.kmz";
        }

        @NonNull
        public String getExtension() {
            return "kmz";
        }

        @Override
        public boolean includesPhotos() {
            return exportPhotos;
        }

    },

    GPX("GPX") {
        @Override
        public TrackExporter createTrackExporter(@NonNull Context context, @NonNull ContentProviderUtils contentProviderUtils) {
            return new GPXTrackExporter(contentProviderUtils, context.getString(R.string.app_name));
        }

        @NonNull
        @Override
        public String getMimeType() {
            return "application/gpx+xml";
        }

        @NonNull
        public String getExtension() {
            return "gpx";
        }
    },

    CSV("CSV") {
        @Override
        public TrackExporter createTrackExporter(@NonNull Context context, @NonNull ContentProviderUtils contentProviderUtils) {
            return new CSVTrackExporter(contentProviderUtils);
        }

        @NonNull
        @Override
        public String getMimeType() {
            return "text/csv";
        }

        @NonNull
        @Override
        public String getExtension() {
            return "csv";
        }
    };

    private final String preferenceId;

    TrackFileFormat(String preferenceId) {
        this.preferenceId = preferenceId;
    }

    public static Map<String, String> toPreferenceIdLabelMap(final Resources resources, final TrackFileFormat... trackFileFormats) {
        Map<String, String> preferenceIdLabelMap = new LinkedHashMap<>(trackFileFormats.length);
        for (TrackFileFormat trackFileFormat : trackFileFormats) {
            String trackFileFormatUpperCase = trackFileFormat.getExtension().toUpperCase(Locale.US); //ASCII upper case
            int photoMessageId = trackFileFormat.includesPhotos() ? R.string.export_with_photos : R.string.export_without_photos;
            preferenceIdLabelMap.put(trackFileFormat.getPreferenceId(), String.format("%s (%s)", trackFileFormatUpperCase, resources.getString(photoMessageId)));
        }
        return preferenceIdLabelMap;
    }

    /**
     * Returns the mime type for each format.
     */
    @NonNull
    public abstract String getMimeType();

    /**
     * Creates a new track writer for the format.
     *
     * @param context the context
     */
    public abstract TrackExporter createTrackExporter(@NonNull Context context, @NonNull ContentProviderUtils contentProviderUtils);

    /**
     * Returns the file extension for each format.
     */
    @NonNull
    public abstract String getExtension();

    /**
     * Returns whether the format supports photos.
     */
    public boolean includesPhotos() {
        return false;
    }

    /**
     * The identifier to be stored in the preferences.
     */
    @NonNull
    public String getPreferenceId() {
        return preferenceId;
    }
}
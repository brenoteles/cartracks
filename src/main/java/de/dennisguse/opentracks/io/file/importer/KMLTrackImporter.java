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

package de.dennisguse.opentracks.io.file.importer;

import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.util.Log;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.dennisguse.opentracks.data.models.Altitude;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Marker;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Temperature;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.io.file.exporter.KMLTrackExporter;
import de.dennisguse.opentracks.util.StringUtils;

/**
 * Imports a KML file; preferred version: KML2.3, but also supports KML2.2.
 *
 * @author Jimmy Shih
 */
public class KMLTrackImporter extends DefaultHandler implements XMLImporter.TrackParser {

    private static final String TAG = KMLTrackImporter.class.getSimpleName();

    private static final String MARKER_STYLE = "#" + KMLTrackExporter.MARKER_STYLE;

    private static final String TAG_COORDINATES = "coordinates";
    private static final String TAG_DESCRIPTION = "description";

    private static final String TAG_COORD = "coord";
    private static final String TAG_KML22_COORD = "gx:coord";

    private static final String TAG_MULTI_TRACK = "MultiTrack";
    private static final String TAG_KML22_MULTI_TRACK = "gx:MultiTrack";

    private static final String TAG_EXTENDED_DATA = "Data";

    private static final String TAG_SIMPLE_ARRAY_DATA = "SimpleArrayData";
    private static final String TAG_KML22_SIMPLE_ARRAY_DATA = "gx:SimpleArrayData";

    private static final String TAG_TRACK = "Track";
    private static final String TAG_KML22_TRACK = "gx:Track";

    private static final String TAG_VALUE = "value";
    private static final String TAG_KML22_VALUE = "gx:value";

    private static final String TAG_HREF = "href";
    private static final String TAG_KML = "kml";
    private static final String TAG_NAME = "name";
    private static final String TAG_PHOTO_OVERLAY = "PhotoOverlay";
    private static final String TAG_PLACEMARK = "Placemark";
    private static final String TAG_STYLE_URL = "styleUrl";
    private static final String TAG_WHEN = "when";
    private static final String TAG_UUID = "opentracks:trackid";

    private static final String ATTRIBUTE_NAME = "name";
    // Until v4.13.0, was in contradiction with KML2.3 standard; keeping backward compatibility.
    public static final String EXTENDED_DATA_TYPE_HEART_RATE_LEGACY = "heart_rate";

    private Locator locator;

    private final Context context;

    // Belongs to the current track
    private ZoneOffset zoneOffset;

    private final ArrayList<Instant> whenList = new ArrayList<>();
    private final ArrayList<LatLngAltitude> positionList = new ArrayList<>();

    private String dataType;

    //TODO For ArrayList use specific data models; and why Float? Double would be easier.
    private final ArrayList<String> trackpointTypeList = new ArrayList<>();
    private final ArrayList<Float> sensorSpeedList = new ArrayList<>();
    private final ArrayList<Float> sensorDistanceList = new ArrayList<>();
    private final ArrayList<Float> sensorCadenceList = new ArrayList<>();
    private final ArrayList<Float> sensorHeartRateList = new ArrayList<>();
    private final ArrayList<Float> sensorTemperatureList = new ArrayList<>();
    private final ArrayList<Float> sensorPowerList = new ArrayList<>();
    private final ArrayList<Float> altitudeGainList = new ArrayList<>();
    private final ArrayList<Float> altitudeLossList = new ArrayList<>();
    private final ArrayList<Float> accuracyHorizontal = new ArrayList<>();
    private final ArrayList<Float> accuracyVertical = new ArrayList<>();

    private final ArrayList<Marker> markers = new ArrayList<>();

    // The current element content
    private String content = "";

    private String name;
    private String description;
    private String activityType;
    private String activityTypeLocalized;
    private String latitude;
    private String longitude;
    private String altitude;
    private String markerType;
    private Uri photoUrl;
    private String uuid;

    private final TrackImporter trackImporter;

    public KMLTrackImporter(Context context, TrackImporter trackImporter) {
        this.context = context;
        this.trackImporter = trackImporter;
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        switch (qName) {
            case TAG_PLACEMARK, TAG_PHOTO_OVERLAY ->
                // Note that a track is contained in a Placemark, calling onMarkerStart will clear various track variables like name, typeLocalized, and description.
                    onMarkerStart();
            case TAG_MULTI_TRACK, TAG_KML22_MULTI_TRACK -> trackImporter.newTrack();
            case TAG_TRACK, TAG_KML22_TRACK -> {
                if (trackImporter == null) {
                    throw new SAXException("Missing " + TAG_MULTI_TRACK);
                }
                onTrackSegmentStart();
            }
            case TAG_EXTENDED_DATA, TAG_SIMPLE_ARRAY_DATA, TAG_KML22_SIMPLE_ARRAY_DATA -> dataType = attributes.getValue(ATTRIBUTE_NAME);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        content += new String(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        switch (qName) {
            case TAG_KML -> onFileEnd();
            case TAG_PLACEMARK, TAG_PHOTO_OVERLAY ->
                // Note that a track is contained in a Placemark, calling onMarkerEnd is save since markerType is not set for a track.
                    onMarkerEnd();
            case TAG_COORDINATES -> onMarkerLocationEnd();
            case TAG_MULTI_TRACK, TAG_KML22_MULTI_TRACK -> {
                trackImporter.setTrackData(context, name, uuid, description, activityTypeLocalized, activityType, zoneOffset);
                zoneOffset = null;
            }
            case TAG_TRACK, TAG_KML22_TRACK -> onTrackSegmentEnd();
            case TAG_COORD, TAG_KML22_COORD -> onCoordEnded();
            case TAG_VALUE, TAG_KML22_VALUE -> {
                switch (dataType) {
                    case KMLTrackExporter.EXTENDED_DATA_ACTIVITY_TYPE -> {
                        if (content != null) {
                            activityType = content.trim();
                        }
                    }
                    case KMLTrackExporter.EXTENDED_DATA_TYPE_LOCALIZED -> {
                        if (content != null) {
                            activityTypeLocalized = content.trim();
                        }
                    }
                    default -> onExtendedDataValueEnd();
                }
            }
            case TAG_NAME -> {
                if (content != null) {
                    name = content.trim();
                }
            }
            case TAG_UUID -> {
                if (content != null) {
                    uuid = content.trim();
                }
            }
            case TAG_DESCRIPTION -> {
                if (content != null) {
                    description = content.trim();
                }
            }
            case TAG_WHEN -> {
                if (content != null) {
                    try {
                        OffsetDateTime time = StringUtils.parseTime(content.trim());
                        if (zoneOffset == null) {
                            zoneOffset = time.getOffset();
                        }
                        whenList.add(time.toInstant());
                    } catch (Exception e) {
                        throw new ParsingException(createErrorMessage(String.format(Locale.US, "Unable to parse time: %s", content.trim())), e);
                    }
                }
            }
            case TAG_STYLE_URL -> {
                if (content != null) {
                    markerType = content.trim();
                }
            }
            case TAG_HREF -> {
                if (content != null) {
                    photoUrl = Uri.parse(content.trim());
                }
            }
        }

        // Reset element content
        content = "";
    }

    private void onMarkerStart() {
        // Reset all Placemark variables
        name = null;
        description = null;
        activityTypeLocalized = null;
        photoUrl = null;
        latitude = null;
        longitude = null;
        altitude = null;
        markerType = null;
    }

    private void onMarkerEnd() {
        if (!MARKER_STYLE.equals(markerType)) {
            return;
        }

        if (whenList.size() != 1) {
            Log.w(TAG, "Marker without time ignored.");
            return;
        }

        LatLngAltitude latLngAltitude = createPosition(latitude, longitude, altitude);
        if (latLngAltitude == null) {
            Log.w(TAG, "Marker with invalid coordinates ignored: " + latitude + " " + longitude);
            return;
        }
        Position position = new Position(
                whenList.get(0),
                latLngAltitude.latitude,
                latLngAltitude.longitude,
                null,
                latLngAltitude.altitude,
                null,
                null,
                null
        );
        markers.add(new Marker(
                null,
                null,
                name != null ? name : "",
                description != null ? description : "",
                activityTypeLocalized != null ? activityTypeLocalized : "",
                position,
                photoUrl
        ));

        name = null;
        description = null;
        activityTypeLocalized = null;
        photoUrl = null;
        whenList.clear();
    }

    private void onMarkerLocationEnd() {
        if (content != null) {
            String[] parts = content.trim().split(",");
            if (parts.length != 2 && parts.length != 3) {
                return;
            }
            longitude = parts[0];
            latitude = parts[1];
            altitude = parts.length == 3 ? parts[2] : null;
        }
    }

    private void onTrackSegmentStart() {
        positionList.clear();
        whenList.clear();

        trackpointTypeList.clear();
        sensorSpeedList.clear();
        sensorDistanceList.clear();
        sensorHeartRateList.clear();
        sensorTemperatureList.clear();
        sensorCadenceList.clear();
        sensorPowerList.clear();
        altitudeGainList.clear();
        altitudeLossList.clear();
        accuracyHorizontal.clear();
        accuracyVertical.clear();
    }

    private void onTrackSegmentEnd() {
        if (positionList.size() != whenList.size()) {
            throw new ImportParserException("<coords> and <when> should have the same count.");
        }

        // Close a track segment by inserting the segment locations
        for (int i = 0; i < positionList.size(); i++) {
            Instant time = whenList.get(i);
            LatLngAltitude latLngAltitude = positionList.get(i);

            TrackPoint.Type type = null;
            Speed speed = null;
            Distance sensorDistance = null;
            HeartRate heartRate = null;
            Temperature temperature = null;
            Cadence cadence = null;
            Power power = null;
            AltitudeGainLoss altitudeGainLoss = null;
            Distance horizontalAccuracy = null;
            Distance verticalAccuracy = null;

            if (i < trackpointTypeList.size() && trackpointTypeList.get(i) != null) {
                type = TrackPoint.Type.valueOf(trackpointTypeList.get(i));
            }
            if (i < sensorSpeedList.size() && sensorSpeedList.get(i) != null) {
                speed = Speed.of(sensorSpeedList.get(i));
            }
            if (i < sensorDistanceList.size() && sensorDistanceList.get(i) != null) {
                sensorDistance = Distance.of(sensorDistanceList.get(i));
            }
            if (i < sensorHeartRateList.size()) {
                heartRate = HeartRate.ofOrNull(sensorHeartRateList.get(i));
            }
            if (i < sensorTemperatureList.size() && sensorTemperatureList.get(i) != null) {
                temperature = Temperature.of(sensorTemperatureList.get(i));
            }
            if (i < sensorCadenceList.size() && sensorCadenceList.get(i) != null) {
                cadence = Cadence.of(sensorCadenceList.get(i));
            }
            if (i < sensorPowerList.size() && sensorPowerList.get(i) != null) {
                power = Power.of(sensorPowerList.get(i));
            }
            if ((i < altitudeGainList.size() && altitudeGainList.get(i) != null)
                    && (i < altitudeLossList.size() && altitudeLossList.get(i) != null)) {
                altitudeGainLoss = new AltitudeGainLoss(altitudeGainList.get(i), altitudeLossList.get(i));
            }
            if (i < accuracyHorizontal.size() && accuracyHorizontal.get(i) != null) {
                horizontalAccuracy = Distance.of(accuracyHorizontal.get(i));
            }
            if (i < accuracyVertical.size() && accuracyVertical.get(i) != null) {
                verticalAccuracy = Distance.of(accuracyVertical.get(i));
            }

            Position position = new Position(
                    time,
                    latLngAltitude != null ? latLngAltitude.latitude : null,
                    latLngAltitude != null ? latLngAltitude.longitude : null,
                    horizontalAccuracy,
                    latLngAltitude != null ? latLngAltitude.altitude : null,
                    verticalAccuracy,
                    null,
                    speed
            );

            // Update TrackPoint type for START / STOP.
            if (i == 0) {
                //first
                if (wasCreatedAutomatically(position)) {
                    type = TrackPoint.Type.SEGMENT_START_AUTOMATIC;
                } else {
                    type = TrackPoint.Type.SEGMENT_START_MANUAL;
                }
            } else if (i == positionList.size() - 1 && !wasCreatedAutomatically(position)) {
                //last
                type = TrackPoint.Type.SEGMENT_END_MANUAL;
            }

            trackImporter.addTrackPoint(new TrackPoint(
                    null,
                    type != null ? type : TrackPoint.Type.TRACKPOINT,
                    position,
                    sensorDistance,
                    heartRate,
                    temperature,
                    cadence,
                    power,
                    altitudeGainLoss
            ));
        }
    }

    private void onCoordEnded() {
        String[] parts = content.trim().split(" ");
        if (parts.length == 2 || parts.length == 3) {
            longitude = parts[0];
            latitude = parts[1];
            altitude = parts.length == 3 ? parts[2] : null;
        }

        positionList.add(createPosition(latitude, longitude, altitude));

        longitude = null;
        latitude = null;
        altitude = null;
    }

    private LatLngAltitude createPosition(String latitude, String longitude, String altitude) {
        //TODO Do not use Location.
        if (longitude != null && latitude != null) {
            Location location = new Location("import");
            try {
                location.setLatitude(Double.parseDouble(latitude));
                location.setLongitude(Double.parseDouble(longitude));
            } catch (NumberFormatException e) {
                throw new ParsingException(createErrorMessage(String.format(Locale.US, "Unable to parse latitude longitude: %s %s", latitude, longitude)), e);
            }

            if (altitude != null) {
                try {
                    location.setAltitude(Double.parseDouble(altitude));
                } catch (NumberFormatException e) {
                    throw new ParsingException(createErrorMessage(String.format(Locale.US, "Unable to parse altitude: %s", altitude)), e);
                }
            }
            return new LatLngAltitude(location.getLatitude(), location.getLongitude(), Altitude.WGS84.of(location.getAltitude()));
        }

        return null;
    }

    private void onExtendedDataValueEnd() throws SAXException {
        if (dataType.equals(KMLTrackExporter.EXTENDED_DATA_TYPE_TRACKPOINT)) {
            trackpointTypeList.add(content != null ? content.trim() : null);
            return;
        }
        Float value = null;
        if (content != null) {
            content = content.trim();
            if (!content.isEmpty()) {
                try {
                    value = Float.parseFloat(content);
                } catch (NumberFormatException e) {
                    throw new SAXException(createErrorMessage("Unable to parse value:" + content), e);
                }
            }
        }
        switch (dataType) {
            case KMLTrackExporter.EXTENDED_DATA_TYPE_SPEED -> sensorSpeedList.add(value);
            case KMLTrackExporter.EXTENDED_DATA_TYPE_DISTANCE -> sensorDistanceList.add(value);
            case KMLTrackExporter.EXTENDED_DATA_TYPE_POWER -> sensorPowerList.add(value);
            case KMLTrackExporter.EXTENDED_DATA_TYPE_HEARTRATE,
                 EXTENDED_DATA_TYPE_HEART_RATE_LEGACY -> sensorHeartRateList.add(value);
            case KMLTrackExporter.EXTENDED_DATA_TYPE_TEMPERATURE -> sensorTemperatureList.add(value);
            case KMLTrackExporter.EXTENDED_DATA_TYPE_CADENCE -> sensorCadenceList.add(value);
            case KMLTrackExporter.EXTENDED_DATA_TYPE_ALTITUDE_GAIN -> altitudeGainList.add(value);
            case KMLTrackExporter.EXTENDED_DATA_TYPE_ALTITUDE_LOSS -> altitudeLossList.add(value);
            case KMLTrackExporter.EXTENDED_DATA_TYPE_ACCURACY_HORIZONTAL -> accuracyHorizontal.add(value);
            case KMLTrackExporter.EXTENDED_DATA_TYPE_ACCURACY_VERTICAL -> accuracyVertical.add(value);
            default -> Log.w(TAG, "Data from extended data " + dataType + " is not (yet) supported.");
        }
    }

    private String createErrorMessage(String message) {
        return String.format(Locale.US, "Parsing error at line: %d column: %d. %s", locator.getLineNumber(), locator.getColumnNumber(), message);
    }

    private void onFileEnd() {
        trackImporter.addMarkers(markers);
        trackImporter.finish();
    }

    public boolean wasCreatedAutomatically(Position position) {
        return position.hasLocation() || position.hasSpeed();
    }

    @Override
    public DefaultHandler getHandler() {
        return this;
    }

    @Override
    public List<Track.Id> getImportTrackIds() {
        return trackImporter.getTrackIds();
    }

    @Override
    public void cleanImport() {
        trackImporter.cleanImport();
    }

    private record LatLngAltitude(
            double latitude,
            double longitude,
            Altitude altitude
    ) {
    }
}

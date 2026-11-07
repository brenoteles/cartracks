package de.dennisguse.opentracks.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.Altitude;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.settings.PreferencesUtils;

public class MockDataGenerator {

    private static final String PREF_MOCK_DATA_INSERTED = "mock_car_trips_inserted_v8";

    public static void populateMockDataIfEmpty(Context context) {
        // Disabled permanently so real user trips are never mixed with fake demo data
    }

    public static void purgeAllMockTrips(Context context) {
        ContentProviderUtils provider = new ContentProviderUtils(context);
        try (TrackListIterator it = provider.searchTracks(null)) {
            while (it != null && it.hasNext()) {
                TrackListIterator.Item item = it.next();
                String desc = item.description() != null ? item.description() : "";
                String name = item.name() != null ? item.name() : "";
                if (desc.contains("Trecho rodoviário") || desc.contains("Ida diária") ||
                    desc.contains("Almoço e reunião") || desc.contains("Raposo Tavares") ||
                    desc.contains("Posto Shell") || desc.contains("Marginal & Corredor") ||
                    desc.contains("Volta noturna") || desc.contains("pista livre") ||
                    name.contains("Castelo Branco") || name.contains("JK Iguatemi") ||
                    name.contains("Raposo Tavares") || name.contains("Escritório Paulista") ||
                    name.contains("Deslocamento -> Posto") || name.contains("Retorno: Posto")) {
                    provider.deleteTrack(context, item.id());
                }
            }
        } catch (Exception ignored) {}
    }

    private static void createRealisticTrip(ContentProviderUtils provider,
                                           String name,
                                           String description,
                                           Instant startTime,
                                           double distanceKm,
                                           int durationMinutes,
                                           double[] wayLats,
                                           double[] wayLons,
                                           double[] speedsKmh) {
        Track.Id trackId = new Track.Id(startTime.toEpochMilli());
        Duration totalDuration = Duration.ofMinutes(durationMinutes);
        Instant stopTime = startTime.plus(totalDuration);

        double maxSpeed = 0;
        for (double s : speedsKmh) {
            if (s > maxSpeed) maxSpeed = s;
        }

        Statistics stats = new Statistics(
                startTime,
                stopTime,
                totalDuration,
                totalDuration.minusSeconds(90),
                Distance.of(distanceKm * 1000.0),
                Speed.of(maxSpeed / 3.6),
                null,
                new AltitudeGainLoss(30f, 25f),
                null,
                null
        );

        Track track = new Track(
                trackId,
                UUID.randomUUID(),
                name,
                description,
                "Condução",
                ActivityType.DRIVING,
                ZoneOffset.ofHours(-3),
                stats
        );

        provider.insertTrack(track);

        // Interpola 60 pontos GPS ao longo dos waypoints para uma curva suave e mapa perfeito
        int totalSegments = wayLats.length - 1;
        int pointsPerSegment = 15;
        int totalPoints = totalSegments * pointsPerSegment;
        long timeStepSec = totalDuration.getSeconds() / totalPoints;

        List<TrackPoint> points = new ArrayList<>();
        int ptIdx = 0;

        for (int s = 0; s < totalSegments; s++) {
            double startLat = wayLats[s];
            double endLat = wayLats[s + 1];
            double startLon = wayLons[s];
            double endLon = wayLons[s + 1];
            double baseSpeed = speedsKmh[s];
            double nextSpeed = speedsKmh[Math.min(s + 1, speedsKmh.length - 1)];

            for (int p = 0; p < pointsPerSegment; p++) {
                double progress = (double) p / pointsPerSegment;
                double curLat = startLat + (endLat - startLat) * progress;
                double curLon = startLon + (endLon - startLon) * progress;
                double curSpeed = baseSpeed + (nextSpeed - baseSpeed) * progress + (Math.sin(p) * 3.0);
                if (curSpeed < 10) curSpeed = 10;

                Instant ptTime = startTime.plusSeconds(ptIdx * timeStepSec);
                double curDist = (distanceKm * 1000.0 / totalPoints) * ptIdx;

                TrackPoint.Type type = (ptIdx == 0) ? TrackPoint.Type.SEGMENT_START_MANUAL :
                                       (ptIdx == totalPoints - 1) ? TrackPoint.Type.SEGMENT_END_MANUAL :
                                       TrackPoint.Type.TRACKPOINT;

                Position pos = new Position(
                        ptTime,
                        curLat,
                        curLon,
                        Distance.of(4.0),
                        Altitude.WGS84.of(760.0 + Math.sin(ptIdx * 0.2) * 15.0),
                        null,
                        0.0f,
                        Speed.of(curSpeed / 3.6)
                );

                points.add(new TrackPoint(type, pos));
                ptIdx++;
            }
        }

        provider.bulkInsertTrackPoint(points, trackId);
    }
}
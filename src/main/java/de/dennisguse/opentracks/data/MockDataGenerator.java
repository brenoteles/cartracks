package de.dennisguse.opentracks.data;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

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

public class MockDataGenerator {

    private static final String PREF_MOCK_DATA_INSERTED = "mock_car_trips_inserted_v8";

    public static void populateMockDataIfEmpty(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(PREF_MOCK_DATA_INSERTED, false)) {
            return;
        }

        ContentProviderUtils provider = new ContentProviderUtils(context);
        // Limpa tracks anteriores caso existam para recriar dados completos e detalhados
        try (TrackListIterator it = provider.searchTracks(null)) {
            while (it != null && it.hasNext()) {
                provider.deleteTrack(context, it.next().id());
            }
        } catch (Exception ignored) {}

        Instant now = Instant.now();

        // 1. Rodovia Castelo Branco - Trecho Rodoviário Rápido (Máx 124 km/h)
        createRealisticTrip(provider,
                "Rodovia Castelo Branco (Alphaville -> SP)",
                "Trecho rodoviário de alta fluidez. Pista livre na faixa da esquerda.",
                now.minus(Duration.ofHours(36)),
                62.5, 38,
                new double[]{-23.5010, -23.5180, -23.5350, -23.5480, -23.5615},
                new double[]{-46.8520, -46.8010, -46.7450, -46.7020, -46.6559},
                new double[]{70.0, 115.0, 124.0, 95.0, 45.0}
        );

        // 2. Viagem Urbana: Casa -> Av. Paulista (24.8 km, trechos lentos no semáforo e trechos rápidos na Marginal)
        createRealisticTrip(provider,
                "Casa -> Escritório Paulista",
                "Ida diária pelo corredor Rebouças e Marginal Pinheiros.",
                now.minus(Duration.ofHours(28)),
                24.8, 38,
                new double[]{-23.6015, -23.5930, -23.5810, -23.5700, -23.5615},
                new double[]{-46.6900, -46.6850, -46.6780, -46.6650, -46.6559},
                new double[]{35.0, 78.0, 85.0, 42.0, 25.0}
        );

        // 3. Viagem Rápida: Almoço JK Iguatemi
        createRealisticTrip(provider,
                "Escritório -> JK Iguatemi",
                "Deslocamento rápido para almoço e reunião.",
                now.minus(Duration.ofHours(20)),
                7.5, 16,
                new double[]{-23.5615, -23.5750, -23.5910},
                new double[]{-46.6559, -46.6780, -46.6910},
                new double[]{30.0, 68.0, 35.0}
        );

        // 4. Viagem Rodovia: Raposo Tavares (Velocidade Alta, trechos > 100 km/h)
        createRealisticTrip(provider,
                "Rodovia Raposo Tavares - Cotia",
                "Trecho rodoviário com pista livre e alta velocidade.",
                now.minus(Duration.ofHours(14)),
                48.5, 34,
                new double[]{-23.5724, -23.5850, -23.6020, -23.6250, -23.6480},
                new double[]{-46.6961, -46.7350, -46.7900, -46.8500, -46.9100},
                new double[]{55.0, 95.0, 115.0, 118.0, 80.0}
        );

        // 5. Abastecimento e Conveniência: Posto Shell Jardins
        createRealisticTrip(provider,
                "Deslocamento -> Posto Shell Jardins",
                "Abastecimento completo com Gasolina V-Power.",
                now.minus(Duration.ofHours(6)),
                4.2, 8,
                new double[]{-23.5615, -23.5645, -23.5668},
                new double[]{-46.6559, -46.6620, -46.6698},
                new double[]{25.0, 52.0, 30.0}
        );

        // 6. Retorno Noturno: Marginal & Corredor 23 de Maio
        createRealisticTrip(provider,
                "Retorno: Posto Shell -> Casa (Noturno)",
                "Volta noturna com fluxo leve e sem retenções.",
                now.minus(Duration.ofHours(2)),
                31.4, 28,
                new double[]{-23.5668, -23.5780, -23.5910, -23.6015},
                new double[]{-46.6698, -46.6790, -46.6860, -46.6900},
                new double[]{45.0, 82.0, 92.0, 40.0}
        );

        // Preenche histórico de abastecimento de exemplo se estiver vazio
        try {
            FuelDatabaseHelper fuelDb = FuelDatabaseHelper.getInstance(context);
            if (fuelDb.getAllEntries().isEmpty()) {
                fuelDb.addEntry("Posto Shell Jardins", 45.2, 5.89, 266.23, 45600, "Gasolina V-Power - Tanque Cheio");
                fuelDb.addEntry("Posto Ipiranga Faria Lima", 40.0, 5.99, 239.60, 45120, "Gasolina Aditivada Dtclean");
                fuelDb.addEntry("Posto BR Petrobras Pinheiros", 42.5, 5.79, 246.08, 44650, "Gasolina Grid");
            }
        } catch (Exception ignored) {}

        prefs.edit().putBoolean(PREF_MOCK_DATA_INSERTED, true).apply();
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
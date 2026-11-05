package de.dennisguse.opentracks.services;

import java.util.ArrayDeque;

/**
 * Decides when a recording car is parked: every fix of the last {@code windowMs} stayed within
 * {@code radiusM} of the current position. Stop-and-go traffic moves more than that, so it keeps
 * recording. The window must be covered by enough fixes, so a single fix after a tunnel or
 * garage blackout is never taken as "parked".
 */
public class ParkedDetector {

    public static final long WINDOW_MS = 10 * 60_000L;
    public static final double RADIUS_M = 200;
    public static final long MIN_COVERAGE_MS = 9 * 60_000L;
    public static final int MIN_FIXES = 20;
    /** A hole this long inside the window means the car may have left and come back unseen. */
    public static final long MAX_GAP_MS = 2 * 60_000L;

    private record Fix(long timeMs, double lat, double lon) {
    }

    private final ArrayDeque<Fix> fixes = new ArrayDeque<>();
    private long startedAt = -1;

    public void reset() {
        fixes.clear();
        startedAt = -1;
    }

    /** @return true when the car is considered parked after this fix. */
    public boolean onFix(long timeMs, double lat, double lon) {
        if (startedAt < 0) {
            startedAt = timeMs;
        }
        Fix current = new Fix(timeMs, lat, lon);
        fixes.addLast(current);
        while (!fixes.isEmpty() && timeMs - fixes.peekFirst().timeMs > WINDOW_MS) {
            fixes.pollFirst();
        }
        if (timeMs - startedAt < WINDOW_MS
                || fixes.size() < MIN_FIXES
                || timeMs - fixes.peekFirst().timeMs < MIN_COVERAGE_MS) {
            return false;
        }
        long previous = fixes.peekFirst().timeMs;
        for (Fix f : fixes) {
            if (f.timeMs - previous > MAX_GAP_MS || distanceM(f.lat, f.lon, lat, lon) > RADIUS_M) {
                return false;
            }
            previous = f.timeMs;
        }
        return true;
    }

    static double distanceM(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}

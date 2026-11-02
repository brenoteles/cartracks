package de.dennisguse.opentracks.io.file.importer;

import org.junit.Assert;

import java.util.List;

import de.dennisguse.opentracks.data.models.TrackPoint;

public class TrackPointAssert {

    private double delta = 0.001;

    public TrackPointAssert() {
    }

    public void assertEquals(TrackPoint expected, TrackPoint actual) {
        Assert.assertEquals("time", expected.getTime(), actual.getTime());

        Assert.assertEquals("type", expected.type(), actual.type());

        Assert.assertEquals("has location,", expected.position().hasLocation(), actual.position().hasLocation());
        if (expected.position().hasLocation()) {
            Assert.assertEquals("latitude", expected.position().latitude(), actual.position().latitude(), 0.001);
            Assert.assertEquals("longitude", expected.position().longitude(), actual.position().longitude(), 0.001);
        }

        Assert.assertEquals("has altitude", expected.position().hasAltitude(), actual.position().hasAltitude());
        if (expected.position().hasAltitude()) {
            Assert.assertEquals("altitude", expected.position().altitude().getClass(), actual.position().altitude().getClass());
            Assert.assertEquals("altitude", expected.position().altitude().toM(), actual.position().altitude().toM(), delta);
        }

        Assert.assertEquals("altitudeGainLoss", expected.altitudeGainLoss(), actual.altitudeGainLoss());

        Assert.assertEquals("has speed", expected.position().hasSpeed(), actual.position().hasSpeed());
        if (expected.position().hasSpeed()) {
            Assert.assertEquals("speed", expected.position().speed().toMPS(), actual.position().speed().toMPS(), delta);
        }

        Assert.assertEquals("has horizontalAccuracy", expected.position().hasHorizontalAccuracy(), actual.position().hasHorizontalAccuracy());
        if (expected.position().hasHorizontalAccuracy()) {
            Assert.assertEquals("horizontalAccuracy", expected.position().horizontalAccuracy().toM(), actual.position().horizontalAccuracy().toM(), delta);
        }
        Assert.assertEquals("has verticalAccuracy", expected.position().hasVerticalAccuracy(), actual.position().hasVerticalAccuracy());
        if (expected.position().hasVerticalAccuracy()) {
            Assert.assertEquals("verticalAccuracy", expected.position().verticalAccuracy().toM(), actual.position().verticalAccuracy().toM(), delta);
        }

        Assert.assertEquals("has sensorDistance", expected.sensorDistance() != null, actual.sensorDistance() != null);
        if (expected.sensorDistance() != null) {
            Assert.assertEquals("sensorDistance", expected.sensorDistance().toM(), actual.sensorDistance().toM(), delta);
        }

        Assert.assertEquals("heartRate", expected.heartRate(), actual.heartRate());

        Assert.assertEquals("temperature", expected.temperature(), actual.temperature());

        Assert.assertEquals("power", expected.power(), actual.power());

        Assert.assertEquals("cadence", expected.cadence(), actual.cadence());
    }

    public void assertEquals(List<TrackPoint> expected, List<TrackPoint> actual) {
        try {
            Assert.assertEquals(expected.size(), actual.size());
        } catch (AssertionError e) {
            throw new AssertionError("Size difference; expected: " + expected.size() + "; actual: " + actual.size() + "\nExpected: " + expected + "\n actual: " + actual);
        }

        for (int i = 0; i < expected.size(); i++) {
            try {
                assertEquals(expected.get(i), actual.get(i));
            } catch (AssertionError e) {
                throw new AssertionError("Expected: " + i + " to be " + expected.get(i) + "\n actual: " + actual.get(i), e);
            }
        }
        Assert.assertEquals(expected.size(), actual.size());

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), actual.get(i));
        }
    }

    public TrackPointAssert setDelta(double delta) {
        this.delta = delta;
        return this;
    }
}

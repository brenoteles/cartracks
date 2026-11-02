package de.dennisguse.opentracks.data.models;

public record Temperature(
        float value_celsius
) {

    public static Temperature of(float value_celsius) {
        return new Temperature(value_celsius);
    }

    public static Temperature ofCentiCelsius(int value_centi_celsius) {
        return Temperature.of((float) (value_centi_celsius / 100.0));
    }

    public float getCelsius() {
        return value_celsius;
    }
}

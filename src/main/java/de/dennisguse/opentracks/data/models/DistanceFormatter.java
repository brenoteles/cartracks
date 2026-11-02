package de.dennisguse.opentracks.data.models;

import android.content.Context;
import android.content.res.Resources;
import android.util.Pair;

import androidx.annotation.Nullable;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.settings.UnitSystem;
import de.dennisguse.opentracks.util.StringUtils;

public class DistanceFormatter {

    private final Resources resources;

    private final int decimalPlaces;

    private final double threshold;

    private final UnitSystem unitSystem;

    private DistanceFormatter(Resources resources, int decimalPlaces, double threshold, UnitSystem unitSystem) {
        this.resources = resources;
        this.decimalPlaces = decimalPlaces;
        this.threshold = threshold;
        this.unitSystem = unitSystem;
        assert unitSystem != null;
    }

    public String formatDistance(Distance distance) {
        if (distance.isInvalid()) {
            return resources.getString(R.string.value_unknown);
        }

        Pair<String, String> distanceParts = getDistanceParts(distance);

        return resources.getString(R.string.distance_with_unit, distanceParts.first, distanceParts.second);
    }

    /**
     * Get the formatted distance with unit.
     *
     * @return the formatted distance (or null) and it's unit as {@link Pair}
     */
    public Pair<String, String> getDistanceParts(Distance distance) {
        if (distance.isInvalid()) {
            String valueUnknown = resources.getString(R.string.value_unknown);
            return switch (unitSystem) {
                case METRIC, IMPERIAL_METER ->
                        new Pair<>(valueUnknown, resources.getString(R.string.unit_meter));
                case IMPERIAL_FEET, NAUTICAL_IMPERIAL ->
                        new Pair<>(valueUnknown, resources.getString(R.string.unit_feet));
            };
        }

        switch (unitSystem) {
            case METRIC -> {
                if (distance.greaterThan(Distance.ofKilometer(threshold))) {
                    return new Pair<>(StringUtils.formatDecimal(distance.toKM(), decimalPlaces), resources.getString(R.string.unit_kilometer));
                } else {
                    return new Pair<>(StringUtils.formatDecimal(distance.toM(), decimalPlaces), resources.getString(R.string.unit_meter));
                }
            }
            case IMPERIAL_FEET -> {
                if (distance.greaterThan(Distance.ofMile(threshold))) {
                    return new Pair<>(StringUtils.formatDecimal(distance.toMI(), decimalPlaces), resources.getString(R.string.unit_mile));
                } else {
                    return new Pair<>(StringUtils.formatDecimal(distance.toFT(), decimalPlaces), resources.getString(R.string.unit_feet));
                }
            }
            case IMPERIAL_METER -> {
                if (distance.greaterThan(Distance.ofMile(threshold))) {
                    return new Pair<>(StringUtils.formatDecimal(distance.toMI(), decimalPlaces), resources.getString(R.string.unit_mile));
                } else {
                    return new Pair<>(StringUtils.formatDecimal(distance.toM(), decimalPlaces), resources.getString(R.string.unit_meter));
                }
            }
            case NAUTICAL_IMPERIAL -> {
                if (distance.greaterThan(Distance.ofNauticalMile(threshold))) {
                    return new Pair<>(StringUtils.formatDecimal(distance.toNauticalMiles(), decimalPlaces), resources.getString(R.string.unit_nautical_mile));
                } else {
                    return new Pair<>(StringUtils.formatDecimal(distance.toFT(), decimalPlaces), resources.getString(R.string.unit_feet));
                }
            }
            default -> throw new RuntimeException("Not implemented");
        }
    }

    public static Builder Builder() {
        return new Builder();
    }

    public static class Builder {

        private int decimalPlaces;

        private UnitSystem unitSystem;

        private double threshold;

        public Builder() {
            decimalPlaces = 2;
            threshold = 0.5;
        }

        public Builder setDecimalPlaces(int decimalPlaces) {
            this.decimalPlaces = decimalPlaces;
            return this;
        }

        public Builder setUnit(@Nullable UnitSystem unitSystem) {
            this.unitSystem = unitSystem;
            return this;
        }

        public Builder setThreshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        public DistanceFormatter build(Resources resource) {
            return new DistanceFormatter(resource, decimalPlaces, threshold, unitSystem);
        }

        public DistanceFormatter build(Context context) {
            return build(context.getResources());
        }
    }
}

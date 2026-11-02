package de.dennisguse.opentracks.viewmodels;

import android.util.Pair;
import android.view.LayoutInflater;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.HeartRateZones;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.databinding.StatsSensorItemBinding;
import de.dennisguse.opentracks.sensors.sensorData.SensorDataSet;
import de.dennisguse.opentracks.services.RecordingData;
import de.dennisguse.opentracks.settings.PreferencesUtils;
import de.dennisguse.opentracks.settings.UnitSystem;
import de.dennisguse.opentracks.ui.customRecordingLayout.DataField;
import de.dennisguse.opentracks.util.StringUtils;

public abstract class SensorStatisticsViewHolder extends StatisticViewHolder<StatsSensorItemBinding> {

    @Override
    protected StatsSensorItemBinding createViewBinding(LayoutInflater inflater) {
        return StatsSensorItemBinding.inflate(inflater);
    }

    @Override
    public void configureUI(DataField dataField) {
        getBinding().statsValue.setTextAppearance(dataField.isPrimary() ? R.style.TextAppearance_OpenTracks_PrimaryValue : R.style.TextAppearance_OpenTracks_SecondaryValue);
        getBinding().statsDescriptionMain.setTextAppearance(dataField.isPrimary() ? R.style.TextAppearance_OpenTracks_PrimaryHeader : R.style.TextAppearance_OpenTracks_SecondaryHeader);
    }

    public static class SensorHeartRate extends SensorStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            SensorDataSet sensorDataSet = data.sensorDataSet();
            String sensorName = getContext().getString(R.string.value_unknown);

            Pair<String, String> valueAndUnit;
            if (sensorDataSet != null && sensorDataSet.heartRate() != null) {
                valueAndUnit = StringUtils.getHeartRateParts(getContext(), sensorDataSet.heartRate().data());
                sensorName = sensorDataSet.heartRate().sensorNameOrAddress();
            } else {
                valueAndUnit = StringUtils.getHeartRateParts(getContext(), null);
            }

            //TODO Loads preference every time
            HeartRateZones zones = PreferencesUtils.getHeartRateZones();
            int textColor;
            if (sensorDataSet != null && sensorDataSet.heartRate() != null) {
                textColor = zones.getTextColorForZone(getContext(), sensorDataSet.heartRate().data());
            } else {
                textColor = zones.getTextColorForZone(getContext(), null);
            }

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
            getBinding().statsDescriptionMain.setText(R.string.stats_sensors_heart_rate);

            getBinding().statsDescriptionSecondary.setText(sensorName);

            getBinding().statsValue.setTextColor(textColor);
        }
    }

    public static class SensorTemperature extends SensorStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            SensorDataSet sensorDataSet = data.sensorDataSet();
            String sensorName = getContext().getString(R.string.value_unknown);

            Pair<String, String> valueAndUnit;
            if (sensorDataSet != null && sensorDataSet.temperature() != null) {
                valueAndUnit = StringUtils.getTemperatureParts(getContext(), sensorDataSet.temperature().data());
                sensorName = sensorDataSet.temperature().sensorNameOrAddress();
            } else {
                valueAndUnit = StringUtils.getTemperatureParts(getContext(), null);
            }

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
            getBinding().statsDescriptionMain.setText(R.string.stats_sensors_temperature);

            getBinding().statsDescriptionSecondary.setText(sensorName);
        }
    }

    public static class SensorCadence extends SensorStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            SensorDataSet sensorDataSet = data.sensorDataSet();
            String sensorName = getContext().getString(R.string.value_unknown);

            Pair<String, String> valueAndUnit;
            if (sensorDataSet != null && sensorDataSet.cadence() != null) {
                valueAndUnit = StringUtils.getCadenceParts(getContext(), sensorDataSet.cadence().data());
                sensorName = sensorDataSet.cadence().sensorNameOrAddress();
            } else {
                valueAndUnit = StringUtils.getCadenceParts(getContext(), null);
            }

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
            getBinding().statsDescriptionMain.setText(R.string.stats_sensors_cadence);

            getBinding().statsDescriptionSecondary.setText(sensorName);
        }
    }

    public static class SensorPower extends SensorStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            SensorDataSet sensorDataSet = data.sensorDataSet();
            String sensorName = getContext().getString(R.string.value_unknown);

            Pair<String, String> valueAndUnit;
            if (sensorDataSet != null && sensorDataSet.power() != null) {
                valueAndUnit = StringUtils.getPowerParts(getContext(), sensorDataSet.power().data());
                sensorName = sensorDataSet.power().sensorNameOrAddress();
            } else {
                valueAndUnit = StringUtils.getCadenceParts(getContext(), null);
            }

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
            getBinding().statsDescriptionMain.setText(R.string.stats_sensors_power);

            getBinding().statsDescriptionSecondary.setText(sensorName);
        }
    }

    public static class Altitude extends SensorStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            TrackPoint latestTrackPoint = data.latestTrackPoint();
            Float altitude = latestTrackPoint != null && latestTrackPoint.position().hasAltitude() ? (float) latestTrackPoint.position().altitude().toM() : null;
            String altitudeReference = latestTrackPoint != null && latestTrackPoint.position().hasAltitude() ? getContext().getString(latestTrackPoint.position().altitude().getLabelId()) : null;
            Pair<String, String> valueAndUnit = StringUtils.getAltitudeParts(getContext(), altitude, unitSystem);

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
            getBinding().statsDescriptionMain.setText(R.string.stats_altitude);
            getBinding().statsDescriptionSecondary.setText(altitudeReference);
        }
    }
}

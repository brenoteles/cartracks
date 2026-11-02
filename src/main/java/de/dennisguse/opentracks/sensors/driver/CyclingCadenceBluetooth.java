package de.dennisguse.opentracks.sensors.driver;

import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;
import android.util.Pair;

import java.util.List;

import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.sensors.ServiceMeasurementUUID;

public class CyclingCadenceBluetooth implements BluetoothDriver.BluetoothParser<CyclingCadenceBluetooth.CyclingCadenceMeasurement> {
    private static final String TAG = CyclingCadenceBluetooth.class.getSimpleName();

    public static final List<ServiceMeasurementUUID> SUPPORTED_SERVICES = List.of(
            CyclingPowerBluetooth.CYCLING_POWER,
            CyclingDistanceSpeedBluetooth.CYCLING_SPEED_CADENCE,
            BoschEbikeParser.BOSCH_EBIKE
    );

    @Override
    public List<ServiceMeasurementUUID> getServices() {
        return SUPPORTED_SERVICES;
    }

    @Override
    public CyclingCadenceMeasurement parsePayload(ServiceMeasurementUUID serviceMeasurementUUID, String sensorName, BluetoothGattCharacteristic characteristic) {
        if (serviceMeasurementUUID.equals(CyclingPowerBluetooth.CYCLING_POWER)) {
            CyclingPowerBluetooth.Data data = CyclingPowerBluetooth.parseCyclingPower(characteristic);
            if (data != null && data.crank() != null) {
                return data.crank();
            }
        }

        if (serviceMeasurementUUID.equals(CyclingDistanceSpeedBluetooth.CYCLING_SPEED_CADENCE)) {
            Pair<CyclingDistanceSpeedBluetooth.WheelData, CrankData> data = CyclingDistanceSpeedBluetooth.parseCyclingCrankAndWheel(characteristic);

            if (data != null && data.second != null) {
                return data.second;
            }
        }

        if (serviceMeasurementUUID.equals(BoschEbikeParser.BOSCH_EBIKE)) {
            BoschEbikeParser.Data boschData = BoschEbikeParser.parse(characteristic.getValue());
            if (boschData != null && boschData.cadence() != null) {
                return new DirectCadenceData(boschData.cadence());
            }
        }

        Log.e(TAG, "Don't know how to decode this payload.");
        return null;
    }

    public sealed interface CyclingCadenceMeasurement permits CrankData, DirectCadenceData {
    }

    public record CrankData(
            long crankRevolutionsCount, // UINT32
            int crankRevolutionsTime // UINT16; 1/1024s
    ) implements CyclingCadenceMeasurement {
    }

    public record DirectCadenceData(
            Cadence cadence
    ) implements CyclingCadenceMeasurement {
    }
}

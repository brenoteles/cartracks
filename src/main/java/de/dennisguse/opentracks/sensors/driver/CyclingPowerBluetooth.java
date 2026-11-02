package de.dennisguse.opentracks.sensors.driver;

import android.bluetooth.BluetoothGattCharacteristic;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.List;
import java.util.UUID;

import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.sensors.ServiceMeasurementUUID;

public class CyclingPowerBluetooth implements BluetoothDriver.BluetoothParser<CyclingPowerBluetooth.Data> {

    public static final ServiceMeasurementUUID CYCLING_POWER = new ServiceMeasurementUUID(
            new UUID(0x181800001000L, 0x800000805f9b34fbL),
            new UUID(0x2A6300001000L, 0x800000805f9b34fbL)
    );

    public static final List<ServiceMeasurementUUID> SUPPORTED_SERVICES = List.of(
            CYCLING_POWER,
            BoschEbikeParser.BOSCH_EBIKE
    );

    @Override
    public List<ServiceMeasurementUUID> getServices() {
        return SUPPORTED_SERVICES;
    }

    @Override
    public CyclingPowerBluetooth.Data parsePayload(@NonNull ServiceMeasurementUUID serviceMeasurementUUID, String sensorName, BluetoothGattCharacteristic characteristic) {
        if (serviceMeasurementUUID.equals(CYCLING_POWER)) {
            return parseCyclingPower(characteristic);
        }
        if (serviceMeasurementUUID.equals(BoschEbikeParser.BOSCH_EBIKE)) {
            BoschEbikeParser.Data boschData = BoschEbikeParser.parse(characteristic.getValue());
            if (boschData != null && boschData.humanPower() != null) {
                return new Data(boschData.humanPower(), null);
            }
        }
        return null;
    }


    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public static Data parseCyclingPower(BluetoothGattCharacteristic characteristic) {
        // DOCUMENTATION https://www.bluetooth.com/wp-content/uploads/Sitecore-Media-Library/Gatt/Xml/Characteristics/org.bluetooth.characteristic.cycling_power_measurement.xml
        int valueLength = characteristic.getValue().length;
        if (valueLength == 0) {
            return null;
        }

        int index = 0;
        int flags1 = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, index++);
        int flags2 = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, index++);
        boolean hasPedalPowerBalance = (flags1 & 0x01) > 0;
        boolean hasAccumulatedTorque = (flags1 & 0x04) > 0;
        boolean hasWheel = (flags1 & 16) > 0;
        boolean hasCrank = (flags1 & 32) > 0;

        Integer instantaneousPower = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, index);
        index += 2;

        if (hasPedalPowerBalance) {
            index += 1;
        }
        if (hasAccumulatedTorque) {
            index += 2;
        }
        if (hasWheel) {
            index += 2 + 2;
        }

        CyclingCadenceBluetooth.CrankData crankData = null;
        if (hasCrank && valueLength - index >= 4) {
            long crankCount = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, index);
            index += 2;

            int crankTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, index); // 1/1024s

            crankData = new CyclingCadenceBluetooth.CrankData(crankCount, crankTime);
        }

        return new Data(Power.of(instantaneousPower), crankData);
    }

    public record Data(
            Power power,
            CyclingCadenceBluetooth.CrankData crank
    ) {
    }
}

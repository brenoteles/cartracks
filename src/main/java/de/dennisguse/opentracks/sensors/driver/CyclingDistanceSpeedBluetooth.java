package de.dennisguse.opentracks.sensors.driver;

import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.List;
import java.util.UUID;

import de.dennisguse.opentracks.sensors.ServiceMeasurementUUID;

public class CyclingDistanceSpeedBluetooth implements BluetoothDriver.BluetoothParser<CyclingDistanceSpeedBluetooth.WheelData> {

    public static final ServiceMeasurementUUID CYCLING_SPEED_CADENCE = new ServiceMeasurementUUID(
            new UUID(0x181600001000L, 0x800000805f9b34fbL),
            new UUID(0x2A5B00001000L, 0x800000805f9b34fbL)
    );

    @Override
    public List<ServiceMeasurementUUID> getServices() {
        return List.of(CYCLING_SPEED_CADENCE);
    }

    @Override
    public WheelData parsePayload(ServiceMeasurementUUID serviceMeasurementUUID, String sensorName, BluetoothGattCharacteristic characteristic) {
        Pair<WheelData, CyclingCadenceBluetooth.CrankData> data = parseCyclingCrankAndWheel(characteristic);
        if (data != null && data.first != null) {
            return data.first;
        }

        return null;
    }


    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public static Pair<WheelData, CyclingCadenceBluetooth.CrankData> parseCyclingCrankAndWheel(@NonNull BluetoothGattCharacteristic characteristic) {
        // DOCUMENTATION https://www.bluetooth.com/wp-content/uploads/Sitecore-Media-Library/Gatt/Xml/Characteristics/org.bluetooth.characteristic.csc_measurement.xml
        int valueLength = characteristic.getValue().length;
        if (valueLength == 0) {
            return null;
        }

        int flags = characteristic.getValue()[0];
        boolean hasWheel = (flags & 0x01) > 0;
        boolean hasCrank = (flags & 0x02) > 0;

        int index = 1;
        WheelData wheelData = null;
        if (hasWheel && valueLength - index >= 6) {
            long wheelTotalRevolutionCount = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, index);
            index += 4;
            int wheelTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, index); // 1/1024s
            wheelData = new WheelData(wheelTotalRevolutionCount, wheelTime);
            index += 2;
        }

        CyclingCadenceBluetooth.CrankData crankData = null;
        if (hasCrank && valueLength - index >= 4) {
            long crankCount = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, index);
            index += 2;

            int crankTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, index); // 1/1024s
            crankData = new CyclingCadenceBluetooth.CrankData(crankCount, crankTime);
        }

        return new Pair<>(wheelData, crankData);
    }

    public record WheelData(

            long wheelRevolutionsCount, // UINT32

            int wheelRevolutionsTime // UINT16; 1/1024s
    ) {
    }
}

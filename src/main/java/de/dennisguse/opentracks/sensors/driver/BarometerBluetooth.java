package de.dennisguse.opentracks.sensors.driver;

import android.bluetooth.BluetoothGattCharacteristic;

import java.util.List;
import java.util.UUID;

import de.dennisguse.opentracks.data.models.AtmosphericPressure;
import de.dennisguse.opentracks.sensors.ServiceMeasurementUUID;

public class BarometerBluetooth implements BluetoothDriver.BluetoothParser<AtmosphericPressure> {
    static final UUID ENVIRONMENTAL_SENSING_SERVICE = new UUID(0x181A00001000L, 0x800000805f9b34fbL);
    public static final ServiceMeasurementUUID BAROMETRIC_PRESSURE = new ServiceMeasurementUUID(
            ENVIRONMENTAL_SENSING_SERVICE,
            new UUID(0x2A6D00001000L, 0x800000805f9b34fbL) //TODO extract leastSigBits
    );

    @Override
    public List<ServiceMeasurementUUID> getServices() {
        return List.of(BAROMETRIC_PRESSURE);
    }

    @Override
    public AtmosphericPressure parsePayload(ServiceMeasurementUUID serviceMeasurementUUID, String sensorName, BluetoothGattCharacteristic characteristic) {
        return parseEnvironmentalSensing(characteristic);
    }

    /**
     * Decoding: org.bluetooth.characteristic.pressure.xml
     */
    public static AtmosphericPressure parseEnvironmentalSensing(BluetoothGattCharacteristic characteristic) {
        byte[] raw = characteristic.getValue();

        if (raw.length < 4) {
            return null;
        }

        Integer pressure = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);
        return AtmosphericPressure.ofPA(pressure / 10f);
    }

}

package de.dennisguse.opentracks.sensors.driver;

import static de.dennisguse.opentracks.sensors.driver.BarometerBluetooth.ENVIRONMENTAL_SENSING_SERVICE;

import android.bluetooth.BluetoothGattCharacteristic;

import java.util.List;
import java.util.UUID;

import de.dennisguse.opentracks.data.models.Temperature;
import de.dennisguse.opentracks.sensors.ServiceMeasurementUUID;

public class TemperatureBluetooth implements BluetoothDriver.BluetoothParser<Temperature> {
    public static final ServiceMeasurementUUID TEMPERATURE = new ServiceMeasurementUUID(
            ENVIRONMENTAL_SENSING_SERVICE,
            new UUID(0x2A6E00001000L, 0x800000805f9b34fbL)
    );

    @Override
    public List<ServiceMeasurementUUID> getServices() {
        return List.of(TEMPERATURE);
    }

    @Override
    public Temperature parsePayload(ServiceMeasurementUUID serviceMeasurementUUID, String sensorName, BluetoothGattCharacteristic characteristic) {
        return parseEnvironmentalSensing(characteristic);
    }

    /**
     * Decoding: org.bluetooth.characteristic.temperature.xml
     */
    public static Temperature parseEnvironmentalSensing(BluetoothGattCharacteristic characteristic) {
        byte[] raw = characteristic.getValue();

        if (raw.length < 2) {
            return null;
        }

        Integer pressure = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0);
        return Temperature.ofCentiCelsius(pressure);
    }
}

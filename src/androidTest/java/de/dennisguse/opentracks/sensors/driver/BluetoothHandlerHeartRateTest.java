package de.dennisguse.opentracks.sensors.driver;

import static org.junit.Assert.assertEquals;

import android.bluetooth.BluetoothGattCharacteristic;

import org.junit.Test;

import de.dennisguse.opentracks.data.models.HeartRate;

public class BluetoothHandlerHeartRateTest {

    @Test
    public void parseHeartRate_uint8() {
        // given
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(HeartRateBluetooth.HEARTRATE.serviceUUID(), 0, 0);
        characteristic.setValue(new byte[]{0x02, 0x3C});

        // when
        HeartRate heartRate = HeartRateBluetooth.parseHeartRate(characteristic);

        // then
        assertEquals(HeartRate.of(60), heartRate);
    }

    @Test
    public void parseHeartRate_uint16() {
        // given
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(HeartRateBluetooth.HEARTRATE.serviceUUID(), 0, 0);
        characteristic.setValue(new byte[]{0x01, 0x01, 0x01});

        // when
        HeartRate heartRate = HeartRateBluetooth.parseHeartRate(characteristic);

        // then
        assertEquals(HeartRate.of(257), heartRate);
    }
}
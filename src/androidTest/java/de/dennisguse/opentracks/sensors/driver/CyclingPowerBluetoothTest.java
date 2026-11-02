package de.dennisguse.opentracks.sensors.driver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.bluetooth.BluetoothGattCharacteristic;

import org.junit.Test;

public class CyclingPowerBluetoothTest {

    @Test
    public void parseCyclingPower_power() {
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(CyclingPowerBluetooth.CYCLING_POWER.serviceUUID(), 0, 0);
        characteristic.setValue(new byte[]{0, 0, 40, 0});

        // when
        CyclingPowerBluetooth.Data powerCadence = CyclingPowerBluetooth.parseCyclingPower(characteristic);

        // then
        assertEquals(40, powerCadence.power().getW(), 0.01);
    }

    @Test
    public void parseCyclingPower_power_with_cadence() {
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(CyclingPowerBluetooth.CYCLING_POWER.serviceUUID(), 0, 0);
        characteristic.setValue(new byte[]{0x2C, 0x00, 0x00, 0x00, (byte) 0x9F, 0x00, 0x0C, 0x00, (byte) 0xE5, 0x42});

        // when
        CyclingPowerBluetooth.Data powerCadence = CyclingPowerBluetooth.parseCyclingPower(characteristic);

        // then
        assertEquals(0, powerCadence.power().getW(), 0.01);

        assertEquals(12, powerCadence.crank().crankRevolutionsCount());
        assertEquals(17125, powerCadence.crank().crankRevolutionsTime());
    }

    @Test
    public void parsePayload_boschPower() {
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(BoschEbikeParser.BOSCH_EBIKE.measurementUUID(), 0, 0);
        // Bosch human power frame: message 0x985B with value 0x7B => 123 W.
        characteristic.setValue(new byte[] { 0x30, 0x04, (byte) 0x98, 0x5B, 0x08, 0x7B });

        CyclingPowerBluetooth.Data power = new CyclingPowerBluetooth().parsePayload(BoschEbikeParser.BOSCH_EBIKE, null, characteristic);

        assertEquals(123, power.power().getW(), 0.01);
        assertNull(power.crank());
    }

    @Test
    public void parsePayload_boschCadenceOnly_returnsNull() {
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(BoschEbikeParser.BOSCH_EBIKE.measurementUUID(), 0, 0);
        // Bosch cadence-only frame: message 0x985A with value 0x01B8 => 220 / 2 = 110 rpm, so power parsing should ignore it.
        characteristic.setValue(new byte[] { 0x30, 0x05, (byte) 0x98, 0x5A, 0x08, (byte) 0xB8, 0x01 });

        CyclingPowerBluetooth.Data power = new CyclingPowerBluetooth().parsePayload(BoschEbikeParser.BOSCH_EBIKE, null, characteristic);

        assertNull(power);
    }

}

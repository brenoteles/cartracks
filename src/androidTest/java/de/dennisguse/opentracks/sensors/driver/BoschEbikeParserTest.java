package de.dennisguse.opentracks.sensors.driver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class BoschEbikeParserTest {

    @Test
    public void parse_multipleFrames_returnsCombinedMeasurements() {
        byte[] raw = new byte[] {
                // Bosch cadence frame
                0x30, 0x04, (byte) 0x98, 0x5A, 0x08, 0x76,
                // Bosch human power frame
                0x30, 0x05, (byte) 0x98, 0x5B, 0x08, (byte) 0x83, 0x01,
                // Bosch speed frame
                0x30, 0x07, (byte) 0x98, 0x2D, 0x08, (byte) 0x94, 0x0B, 0x10, 0x01
        };

        BoschEbikeParser.Data data = BoschEbikeParser.parse(raw);

        assertNotNull(data);
        assertEquals(59.0f, data.cadence().getRPM(), 0.01f);
        assertEquals(131.0f, data.humanPower().getW(), 0.01f);
        assertEquals(14.28 / 3.6, data.speed().toMPS(), 0.0001);
    }

    @Test
    public void parse_multiByteVarint_returnsScaledSpeed() {
        // Bosch speed frame using a multi-byte varint
        BoschEbikeParser.Data data = BoschEbikeParser.parse(new byte[] {
                0x30, 0x07, (byte) 0x98, 0x2D, 0x08, (byte) 0xE1, 0x0E, 0x10, 0x01
        });

        assertNotNull(data);
        assertEquals(18.89 / 3.6, data.speed().toMPS(), 0.0001);
    }

    @Test
    public void parse_zeroValueFrame_returnsZeroCadence() {
        // Bosch zero-value cadence frame
        BoschEbikeParser.Data data = BoschEbikeParser.parse(new byte[] {
                0x30, 0x02, (byte) 0x98, 0x5A
        });

        assertNotNull(data);
        assertEquals(0.0f, data.cadence().getRPM(), 0.01f);
        assertNull(data.speed());
        assertNull(data.humanPower());
    }

    @Test
    public void parse_unsupportedMeasurementsOnly_returnsNull() {
        // Bosch frame for message 0x9865, which the parser does not map to a measurement.
        BoschEbikeParser.Data data = BoschEbikeParser.parse(new byte[] {
                0x30, 0x02, (byte) 0x98, 0x65
        });

        assertNull(data);
    }

    @Test
    public void parse_unimplementedMeasurementsOnly_returnsNull() {
        // Bosch motor power frame: message 0x985D with value 0x42, currently ignored.
        BoschEbikeParser.Data data = BoschEbikeParser.parse(new byte[] {
                0x30, 0x04, (byte) 0x98, 0x5D, 0x08, 0x42
        });

        assertNull(data);
    }

    @Test
    public void parse_malformedVarint_returnsNull() {
        // Truncated Bosch speed frame: starts like a real 0x982D frame but the varint is incomplete.
        BoschEbikeParser.Data data = BoschEbikeParser.parse(new byte[] {
                0x30, 0x04, (byte) 0x98, 0x2D, 0x08, (byte) 0x94
        });

        assertNull(data);
    }

    @Test
    public void parse_varintLargerThan32Bit_returnsNull() {
        BoschEbikeParser.Data data = BoschEbikeParser.parse(new byte[] {
                0x30, 0x08, (byte) 0x98, 0x2D, 0x08,
                (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01
        });

        assertNull(data);
    }

    @Test
    public void parse_validMeasurementBeforeMalformedFrame_returnsValidMeasurement() {
        BoschEbikeParser.Data data = BoschEbikeParser.parse(new byte[] {
                // Bosch cadence frame: message 0x985A with value 0x76
                0x30, 0x04, (byte) 0x98, 0x5A, 0x08, 0x76,
                // Truncated Bosch speed frame: malformed trailing frame should not discard the valid cadence above.
                0x30, 0x04, (byte) 0x98, 0x2D, 0x08, (byte) 0x94
        });

        assertNotNull(data);
        assertEquals(59.0f, data.cadence().getRPM(), 0.01f);
        assertNull(data.speed());
    }

    @Test
    public void parse_malformedVarintDoesNotConsumeNextFrameStartByte() {
        BoschEbikeParser.Data data = BoschEbikeParser.parse(new byte[] {
                // Truncated Bosch speed frame: the varint continuation bit is set, but the frame ends
                // before the next varint byte. The following frame start byte must not be consumed.
                0x30, 0x04, (byte) 0x98, 0x2D, 0x08, (byte) 0x94,
                // Bosch cadence frame: message 0x985A with value 0x76
                0x30, 0x04, (byte) 0x98, 0x5A, 0x08, 0x76
        });

        assertNotNull(data);
        assertNull(data.speed());
        assertEquals(59.0f, data.cadence().getRPM(), 0.01f);
    }

    @Test
    public void parse_leadingNoise_ignored() {
        byte[] raw = new byte[] {
                // Noise bytes to verify the parser resynchronizes on the Bosch frame start byte 0x30.
                0x01, 0x02, 0x03,
                // Bosch cadence frame: message 0x985A with value 0x76
                0x30, 0x04, (byte) 0x98, 0x5A, 0x08, 0x76
        };

        BoschEbikeParser.Data data = BoschEbikeParser.parse(raw);

        assertNotNull(data);
        assertEquals(59.0f, data.cadence().getRPM(), 0.01f);
    }
}

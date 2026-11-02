package de.dennisguse.opentracks.sensors.driver;

import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.sensors.ServiceMeasurementUUID;

// Implementation of metrics from a Bosch Smart System eBike via BLE.
// as described and implemented here: https://github.com/RobbyPee/Bosch-Smart-System-Ebike-Garmin-Android
//
// The bike exposes a single custom BLE service with one notification characteristic onto
// which all measurements are multiplexed as concatenated frames.
//
// ```
// 30  <length>  <id-hi>  <id-lo>  [08  <varint...>]
// ```
//
// Frame format
//
// - `30` : start byte
// - `<length>` - number of payload bytes following (min 2, which means value = 0)
// - two-byte big-endian message ID
// - `08` : varint type tag (absent when value is 0)
// - varint : strip MSB from each byte, 7-bit groups, little-endian concatenation
//
// Multiple frames may be packed into a single characteristic notification.
//

public final class BoschEbikeParser {

    private static final String TAG = BoschEbikeParser.class.getSimpleName();

    private static final int MESSAGE_ID_CADENCE = 0x985A;
    private static final int MESSAGE_ID_HUMAN_POWER = 0x985B;
    private static final int MESSAGE_ID_SPEED = 0x982D;
    private static final int MESSAGE_ID_MOTOR_POWER = 0x985D;
    private static final int MESSAGE_ID_BATTERY = 0x8088;
    private static final int MESSAGE_ID_ASSIST_MODE = 0x9809;

    private static final int START_BYTE = 0x30;
    private static final int VARINT_TAG = 0x08;
    private static final int MAX_VARINT_32_BIT_BYTES = 5;

    public static final ServiceMeasurementUUID BOSCH_EBIKE = new ServiceMeasurementUUID(
            UUID.fromString("00000010-eaa2-11e9-81b4-2a2ae2dbcce4"),
            UUID.fromString("00000011-eaa2-11e9-81b4-2a2ae2dbcce4"));

    private BoschEbikeParser() {
    }

    /**
     * Decodes a single varint from {@code data}. Uses little-endian 7-bit groups; MSB=1 means another byte follows.
     */
    private static Integer decodeVarint(byte[] data) {
        if (data == null || data.length == 0) {
            Log.d(TAG, "Malformed varint: empty payload");
            return null;
        }

        int result = 0;

        for (int i = 0; i < data.length; i++) {
            if (i >= MAX_VARINT_32_BIT_BYTES) {
                Log.d(TAG, "Malformed varint: exceeds 32-bit limit after " + i + " bytes");
                return null;
            }

            int byteValue = data[i] & 0x7F;
            result |= byteValue << (7 * i);

            if ((data[i] & 0x80) == 0) {
                return result;
            }
        }

        Log.d(TAG, "Malformed varint: truncated after " + data.length + " bytes");
        return null;
    }

    /**
     * Parses one Bosch notification payload, which may contain multiple concatenated frames, and returns the measurements recognized in that payload.
     */
    static Data parse(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return null;
        }

        Data data = splitFrames(raw).stream()
                .filter(BoschEbikeParser::isValidFrame)
                .filter(BoschEbikeParser::isSupportedFrame)
                .map(BoschEbikeParser::decodeFrame)
                .filter(Objects::nonNull)
                .reduce(null,
                        BoschEbikeParser::processMessage,
                        (left, right) -> right);
        Log.d(TAG, "Parsed Bosch data: " + data);
        return data;
    }

    private static List<byte[]> splitFrames(byte[] raw) {
        List<byte[]> frames = new ArrayList<>();

        int i = 0;
        while (i < raw.length) {
            if (raw[i] != START_BYTE) {
                i++;
                continue;
            }

            if (i + 1 >= raw.length) {
                Log.d(TAG, "Malformed frame at offset " + i + ": missing length byte (raw length=" + raw.length + ")");
                i++;
                continue;
            }

            int length = raw[i + 1] & 0xFF;
            int frameEnd = i + length + 2;
            frames.add(Arrays.copyOfRange(raw, i, Math.min(frameEnd, raw.length)));
            i = frameEnd;
        }

        return frames;
    }

    private static boolean isValidFrame(byte[] frame) {
        int length = getFrameLength(frame);
        if (length < 2) {
            Log.d(TAG, "Malformed frame: payload length " + length + " is shorter than minimum 2 bytes");
            return false;
        }

        if (frame.length != length + 2) {
            Log.d(TAG, "Malformed frame: expected " + (length + 2) + " bytes, got " + frame.length);
            return false;
        }

        return true;
    }

    private static boolean isSupportedFrame(byte[] frame) {
        boolean supported = getFrameLength(frame) == 2 || (frame[4] & 0xFF) == VARINT_TAG;
        if (!supported) {
            Log.d(TAG, "Unsupported frame: tag 0x" + String.format("%02X", frame[4] & 0xFF));
        }
        return supported;
    }

    @Nullable
    private static DecodedFrame decodeFrame(byte[] frame) {
        if (getFrameLength(frame) == 2) {
            return new DecodedFrame(getMessageId(frame), 0);
        }

        Integer varintResult = decodeVarint(Arrays.copyOfRange(frame, 5, frame.length));
        if (varintResult == null) {
            return null;
        }

        return new DecodedFrame(getMessageId(frame), varintResult);
    }

    private static int getFrameLength(byte[] frame) {
        return frame[1] & 0xFF;
    }

    private static int getMessageId(byte[] frame) {
        return (frame[2] & 0xFF) << 8 | (frame[3] & 0xFF);
    }

    /**
     * Processes a message ID and value, updating the data with any measurements.
     */
    @Nullable
    private static Data processMessage(@Nullable Data data, DecodedFrame decodedFrame) {
        switch (decodedFrame.messageId()) {
            case MESSAGE_ID_CADENCE:
                return new Data(null, Cadence.of(decodedFrame.value() / 2.0f), null, data);
            case MESSAGE_ID_HUMAN_POWER:
                return new Data(null, null, Power.of((float) decodedFrame.value()), data);
            case MESSAGE_ID_SPEED:
                double speed_kmh = decodedFrame.value() / 100.0;
                return new Data(Speed.ofKMH(speed_kmh), null, null, data);
            case MESSAGE_ID_MOTOR_POWER:
            case MESSAGE_ID_BATTERY:
            case MESSAGE_ID_ASSIST_MODE:
                Log.d(TAG, "Ignoring unimplemented message: raw=" + decodedFrame.value());
                return data;
        }
        Log.d(TAG, "Ignoring unknown message: raw=" + decodedFrame.value());
        return data;
    }

    public record Data(
            @Nullable Speed speed,
            @Nullable Cadence cadence,
            @Nullable Power humanPower) {

        public Data(@Nullable Speed speed, @Nullable Cadence cadence, @Nullable Power humanPower, @Nullable Data oldData) {
            this(
                    speed != null ? speed : oldData != null ? oldData.speed() : null,
                    cadence != null ? cadence : oldData != null ? oldData.cadence() : null,
                    humanPower != null ? humanPower : oldData != null ? oldData.humanPower() : null);
        }
    }

    private record DecodedFrame(int messageId, int value) {
    }
}

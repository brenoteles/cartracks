package de.dennisguse.opentracks.util;

import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import de.dennisguse.opentracks.services.TrackRecordingService;
import de.dennisguse.opentracks.services.TrackRecordingServiceConnection;

public final class TrackRecordingServiceTestUtils {

    private TrackRecordingServiceTestUtils() {
    }

    public static void waitForRecordingState(boolean isRecording, Duration timeout) {
        Context context = ApplicationProvider.getApplicationContext();
        CountDownLatch connectedLatch = new CountDownLatch(1);
        AtomicReference<TrackRecordingService> serviceRef = new AtomicReference<>();
        TrackRecordingServiceConnection connection = new TrackRecordingServiceConnection((service, self) -> {
            serviceRef.set(service);
            connectedLatch.countDown();
        });

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> connection.bind(context));

        try {
            if (!connectedLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                fail("Timed out waiting to bind TrackRecordingService");
            }

            LiveDataTestUtils.waitForValue(serviceRef.get().getRecordingStatusObservable(),
                    status -> status != null && status.isRecording() == isRecording,
                    timeout,
                    "recording state to become " + isRecording);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for recording state " + isRecording);
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> connection.unbind(context));
        }
    }
}

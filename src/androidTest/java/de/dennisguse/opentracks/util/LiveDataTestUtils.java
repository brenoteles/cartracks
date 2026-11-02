package de.dennisguse.opentracks.util;

import static org.junit.Assert.fail;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.test.platform.app.InstrumentationRegistry;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public final class LiveDataTestUtils {

    private LiveDataTestUtils() {
    }

    public static <T> T waitForValue(LiveData<T> liveData,
                                     Predicate<T> predicate,
                                     Duration timeout,
                                     String description) {
        AtomicReference<T> lastValue = new AtomicReference<>(liveData.getValue());
        CountDownLatch latch = new CountDownLatch(1);

        Observer<T> observer = value -> {
            lastValue.set(value);
            if (predicate.test(value)) {
                latch.countDown();
            }
        };

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            liveData.observeForever(observer);

            T currentValue = liveData.getValue();
            lastValue.set(currentValue);
            if (predicate.test(currentValue)) {
                latch.countDown();
            }
        });

        try {
            if (!latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                fail("Timed out waiting for " + description + ". Last value: " + lastValue.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for " + description);
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> liveData.removeObserver(observer));
        }

        return lastValue.get();
    }
}

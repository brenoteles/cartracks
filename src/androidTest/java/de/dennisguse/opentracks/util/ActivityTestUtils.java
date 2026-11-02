package de.dennisguse.opentracks.util;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ActivityTestUtils {

    private ActivityTestUtils() {
    }

    public static <T extends Activity> T waitForResumedActivity(Class<T> activityClass, Duration timeout) {
        T resumedActivity = getResumedActivity(activityClass);
        if (resumedActivity != null) {
            return resumedActivity;
        }

        Application application = ApplicationProvider.getApplicationContext();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> resumedActivityRef = new AtomicReference<>();

        Application.ActivityLifecycleCallbacks callbacks = new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if (activityClass.isInstance(activity)) {
                    resumedActivityRef.set(activityClass.cast(activity));
                    latch.countDown();
                }
            }

            @Override
            public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        };

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            application.registerActivityLifecycleCallbacks(callbacks);

            T currentResumedActivity = findResumedActivity(activityClass);
            if (currentResumedActivity != null) {
                resumedActivityRef.set(currentResumedActivity);
                latch.countDown();
            }
        });

        try {
            if (!latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                fail("Timed out waiting for resumed activity " + activityClass.getSimpleName());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for resumed activity " + activityClass.getSimpleName());
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> application.unregisterActivityLifecycleCallbacks(callbacks));
        }

        return resumedActivityRef.get();
    }

    private static <T extends Activity> T getResumedActivity(Class<T> activityClass) {
        AtomicReference<T> resumedActivity = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> resumedActivity.set(findResumedActivity(activityClass)));
        return resumedActivity.get();
    }

    private static <T extends Activity> T findResumedActivity(Class<T> activityClass) {
        for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
            if (activityClass.isInstance(activity)) {
                return activityClass.cast(activity);
            }
        }
        return null;
    }
}

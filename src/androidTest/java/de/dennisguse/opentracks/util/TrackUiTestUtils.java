package de.dennisguse.opentracks.util;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.isClickable;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static de.dennisguse.opentracks.util.ActivityTestUtils.waitForResumedActivity;
import static de.dennisguse.opentracks.util.EspressoUtils.performClick;
import static de.dennisguse.opentracks.util.EspressoUtils.performLongClick;
import static de.dennisguse.opentracks.util.EspressoUtils.waitUntilDisplayed;
import static de.dennisguse.opentracks.util.TrackRecordingServiceTestUtils.waitForRecordingState;
import static org.hamcrest.Matchers.allOf;

import androidx.annotation.IdRes;

import java.time.Duration;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.TrackListActivity;
import de.dennisguse.opentracks.TrackRecordingActivity;
import de.dennisguse.opentracks.TrackStoppedActivity;

public final class TrackUiTestUtils {

    private TrackUiTestUtils() {
    }

    public static void startRecordingFromTrackList(Duration timeout) {
        onView(withId(R.id.track_list_fab_action))
                .perform(performClick());
        waitForResumedActivity(TrackRecordingActivity.class, timeout);
        waitForRecordingState(true, timeout);
        waitUntilDisplayed(withId(R.id.track_recording_fab_action), timeout);
    }

    public static void stopRecordingToTrackStopped(Duration timeout, @IdRes int... expectedVisibleViewIds) {
        onView(withId(R.id.track_recording_fab_action))
                .perform(performLongClick());
        waitForResumedActivity(TrackStoppedActivity.class, timeout);
        waitUntilDisplayed(withId(R.id.track_edit_name), timeout);
        for (int viewId : expectedVisibleViewIds) {
            waitUntilDisplayed(withId(viewId), timeout);
        }
        onView(withId(R.id.track_edit_name))
                .perform(closeSoftKeyboard());
    }

    public static void resumeStoppedTrack(Duration timeout) {
        onView(allOf(withId(R.id.resume_button), isClickable()))
                .perform(performClick());
        waitForResumedActivity(TrackRecordingActivity.class, timeout);
        waitForRecordingState(true, timeout);
        waitUntilDisplayed(withId(R.id.track_recording_fab_action), timeout);
    }

    public static void finishStoppedTrack(Duration timeout) {
        onView(withId(R.id.finish_button))
                .perform(performClick());
        waitForResumedActivity(TrackListActivity.class, timeout);
        waitUntilDisplayed(withId(R.id.track_list), timeout);
    }

    public static void renameStoppedTrack(String name, Duration timeout) {
        waitUntilDisplayed(withId(R.id.track_edit_name), timeout);
        onView(withId(R.id.track_edit_name))
                .perform(scrollTo(), replaceText(name), closeSoftKeyboard());
    }
}

package de.dennisguse.opentracks;


import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static de.dennisguse.opentracks.util.EspressoUtils.performClick;
import static de.dennisguse.opentracks.util.EspressoUtils.performClickOnAncestor;
import static de.dennisguse.opentracks.util.EspressoUtils.performLongClickOnAncestor;
import static de.dennisguse.opentracks.util.EspressoUtils.waitUntilDisplayed;
import static de.dennisguse.opentracks.util.EspressoUtils.waitUntilRecyclerViewItemCount;
import static de.dennisguse.opentracks.util.EspressoUtils.withRecyclerViewItemCount;
import static de.dennisguse.opentracks.util.TestEnvironmentUtils.resetTrackRecordingServiceAndDeleteTracks;
import static de.dennisguse.opentracks.util.TrackUiTestUtils.renameStoppedTrack;
import static de.dennisguse.opentracks.util.TrackUiTestUtils.startRecordingFromTrackList;
import static de.dennisguse.opentracks.util.TrackUiTestUtils.stopRecordingToTrackStopped;
import static org.hamcrest.Matchers.allOf;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class EspressoDeleteTrackTest {

    private static final Duration UI_TIMEOUT = Duration.ofSeconds(10);

    @Rule
    public ActivityScenarioRule<TrackListActivity> mActivityTestRule = new ActivityScenarioRule<>(TrackListActivity.class);

    @Rule
    public GrantPermissionRule mGrantPermissionRule = TestUtil.createGrantPermissionRule();

    @Before
    public void setUp() {
        resetTrackRecordingServiceAndDeleteTracks();
    }

    @Test
    public void deleteTrackFromTrackStoppedActivity() {
        recordTrackAndOpenTrackStoppedActivity();

        // TrackStoppedActivity: discard the just-recorded track directly
        onView(withId(R.id.discard_button))
                .perform(performClick());
        onView(withText(android.R.string.ok))
                .inRoot(isDialog())
                .perform(performClick());
        waitUntilDisplayed(withId(R.id.track_list), UI_TIMEOUT);
        waitUntilRecyclerViewItemCount(withId(R.id.track_list), 0, UI_TIMEOUT);

        // the recycler is empty again after discarding the only recorded track
        onView(withId(R.id.track_list))
                .check(matches(withRecyclerViewItemCount(0)));
    }

    @Test
    public void deleteTrackFromSelectedTrackInTrackList() {
        String uniqueTrackName = "DeleteFromTrackList-" + System.currentTimeMillis();

        recordTrackAndOpenTrackStoppedActivity();
        renameStoppedTrack(uniqueTrackName, UI_TIMEOUT);

        onView(withId(R.id.finish_button))
                .perform(performClick());
        waitUntilDisplayed(withId(R.id.track_list), UI_TIMEOUT);
        waitUntilDisplayed(allOf(withId(R.id.track_list_item_name), withText(uniqueTrackName)), UI_TIMEOUT);
        waitUntilRecyclerViewItemCount(withId(R.id.track_list), 1, UI_TIMEOUT);

        onView(allOf(withId(R.id.track_list_item_name), withText(uniqueTrackName)))
                .perform(performLongClickOnAncestor());

        clickDeleteSelectedTrackAction();
        onView(withText(android.R.string.ok))
                .inRoot(isDialog())
                .perform(performClick());
        waitUntilRecyclerViewItemCount(withId(R.id.track_list), 0, UI_TIMEOUT);

        onView(withId(R.id.track_list))
                .check(matches(withRecyclerViewItemCount(0)));
    }

    private void recordTrackAndOpenTrackStoppedActivity() {
        startRecordingFromTrackList(UI_TIMEOUT);
        stopRecordingToTrackStopped(UI_TIMEOUT, R.id.discard_button, R.id.finish_button);
    }

    private void clickDeleteSelectedTrackAction() {
        try {
            waitUntilDisplayed(withId(R.id.list_context_menu_delete), UI_TIMEOUT);
            onView(withId(R.id.list_context_menu_delete))
                    .perform(performClick());
        } catch (AssertionError ignored) {
            onView(allOf(
                    withContentDescription(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                    isDescendantOfA(withId(androidx.appcompat.R.id.action_mode_bar))))
                    .perform(performClick());
            onView(withText(R.string.menu_delete))
                    .inRoot(isPlatformPopup())
                    .perform(performClickOnAncestor());
        }
    }

}

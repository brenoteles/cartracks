package de.dennisguse.opentracks;


import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.dennisguse.opentracks.util.EspressoUtils.performClick;
import static de.dennisguse.opentracks.util.EspressoUtils.performClickOnAncestor;
import static de.dennisguse.opentracks.util.EspressoUtils.waitUntilDisplayed;
import static de.dennisguse.opentracks.util.TestEnvironmentUtils.resetTrackRecordingServiceAndDeleteTracks;
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
import java.util.Locale;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class EspressoEditTrackRecordingTest {

    private static final Duration UI_TIMEOUT = Duration.ofSeconds(10);

    @Rule
    public final LocaleRule mLocaleRule = new LocaleRule(Locale.ENGLISH);

    @Rule
    public ActivityScenarioRule<TrackListActivity> mActivityTestRule = new ActivityScenarioRule<>(TrackListActivity.class);

    @Rule
    public GrantPermissionRule mGrantPermissionRule = TestUtil.createGrantPermissionRule();

    @Before
    public void setUp() {
        resetTrackRecordingServiceAndDeleteTracks();
    }

    @LargeTest
    @Test
    public void espressoEditTrackRecordingTest() {
        startRecordingFromTrackList(UI_TIMEOUT);
        {
            // TrackRecordingActivity

            // open menu
            onView(allOf(
                    withContentDescription(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                    isDescendantOfA(withId(R.id.bottom_app_bar))))
                    .perform(performClick());
            onView(withText(R.string.menu_edit))
                    .inRoot(isPlatformPopup())
                    .perform(performClickOnAncestor());
            waitUntilDisplayed(withId(R.id.track_edit_name), UI_TIMEOUT);

            // change name for "New Name"
            onView(withId(R.id.track_edit_name))
                    .perform(scrollTo(), replaceText("New Name"));

            onView(allOf(withId(R.id.track_edit_name), withText("New Name"), isDisplayed()))
                    .perform(closeSoftKeyboard());

            // save edition
            onView(withId(R.id.track_edit_save))
                    .perform(performClick());
            waitUntilDisplayed(withId(R.id.track_recording_fab_action), UI_TIMEOUT);

            // stop;
            stopRecordingToTrackStopped(UI_TIMEOUT, R.id.resume_button, R.id.finish_button);

            // it's on track stopped activity and there are two buttons
            onView(withId(R.id.resume_button))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.finish_button))
                    .check(matches(isDisplayed()));
        }
    }
}

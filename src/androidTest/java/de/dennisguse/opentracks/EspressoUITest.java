package de.dennisguse.opentracks;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static de.dennisguse.opentracks.util.EspressoUtils.performLongClickOnAncestor;
import static de.dennisguse.opentracks.util.EspressoUtils.selectTabAtIndex;
import static de.dennisguse.opentracks.util.EspressoUtils.waitForResumedViewCondition;
import static de.dennisguse.opentracks.util.EspressoUtils.waitUntilDisplayed;
import static de.dennisguse.opentracks.util.EspressoUtils.withSelectedTab;
import static de.dennisguse.opentracks.util.EspressoUtils.withViewPagerCurrentItem;
import static de.dennisguse.opentracks.util.TestEnvironmentUtils.resetTrackRecordingServiceAndDeleteTracks;
import static de.dennisguse.opentracks.util.TrackUiTestUtils.finishStoppedTrack;
import static de.dennisguse.opentracks.util.TrackUiTestUtils.resumeStoppedTrack;
import static de.dennisguse.opentracks.util.TrackUiTestUtils.startRecordingFromTrackList;
import static de.dennisguse.opentracks.util.TrackUiTestUtils.stopRecordingToTrackStopped;

import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.ViewInteraction;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.List;

import de.dennisguse.opentracks.content.data.TestDataUtil;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackBuilder;
import de.dennisguse.opentracks.data.models.TrackPoint;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class EspressoUITest {

    private static final Duration UI_TIMEOUT = Duration.ofSeconds(10);

    private final ActivityScenarioRule<TrackListActivity> activityRule = new ActivityScenarioRule<>(TrackListActivity.class);
    private final GrantPermissionRule grantPermissionRule = TestUtil.createGrantPermissionRule();

    @Rule
    public RuleChain rules = RuleChain.outerRule(grantPermissionRule)
            .around(activityRule);

    @Before
    public void setUp() {
        resetTrackRecordingServiceAndDeleteTracks();
    }

    @LargeTest
    @Test
    public void record_stop_resume_stop_finish() {
        startRecordingFromTrackList(UI_TIMEOUT);
        {
            stopRecordingToTrackStopped(UI_TIMEOUT, R.id.resume_button);
            resumeStoppedTrack(UI_TIMEOUT);
            stopRecordingToTrackStopped(UI_TIMEOUT, R.id.finish_button);
            finishStoppedTrack(UI_TIMEOUT);

            onView(withId(R.id.track_list))
                    .check(matches(isDisplayed()));
        }
    }

    @LargeTest
    @Test
    public void record_move_through_tabs() {
        startRecordingFromTrackList(UI_TIMEOUT);
        waitUntilDisplayed(withId(R.id.track_detail_activity_tablayout), UI_TIMEOUT);
        waitUntilDisplayed(withId(R.id.track_detail_activity_view_pager), UI_TIMEOUT);
        {
            // TrackRecordingActivity
            ViewInteraction tabLayout = onView(withId(R.id.track_detail_activity_tablayout));

            tabLayout.perform(selectTabAtIndex(1));
            waitUntilTabSelectionSettlesAt(1);

            tabLayout.perform(selectTabAtIndex(2));
            waitUntilTabSelectionSettlesAt(2);

            tabLayout.perform(selectTabAtIndex(3));
            waitUntilTabSelectionSettlesAt(3);

            tabLayout.perform(selectTabAtIndex(0));
            waitUntilTabSelectionSettlesAt(0);

            // stop
            stopRecordingToTrackStopped(UI_TIMEOUT, R.id.resume_button, R.id.finish_button);

            onView(withId(R.id.resume_button))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.finish_button))
                    .check(matches(isDisplayed()));
        }
    }

    @LargeTest
    @Test
    public void selectAndDeleteTrack() {
        Pair<Track, List<TrackPoint>> pair = TestDataUtil.createTrack(new Track.Id(System.currentTimeMillis()), 5);
        String uniqueTrackName = "SelectAndDeleteTrack-" + System.currentTimeMillis();
        TrackBuilder trackBuilder = new TrackBuilder(pair.first);
        trackBuilder.setName(uniqueTrackName);
        TestDataUtil.insertTrackWithLocations(new ContentProviderUtils(ApplicationProvider.getApplicationContext()), trackBuilder.getTrack(), pair.second);
        activityRule.getScenario().recreate();
        waitUntilDisplayed(allOf(withId(R.id.track_list_item_name), withText(uniqueTrackName)), UI_TIMEOUT);

        onView(withId(R.id.track_list)).check(matches(isDisplayed()));
        onView(allOf(withId(R.id.track_list_item_name), withText(uniqueTrackName)))
                .perform(performLongClickOnAncestor());

        onView(withId(androidx.appcompat.R.id.action_mode_bar))
                .check(matches(isDisplayed()));
    }

    private void waitUntilTabSelectionSettlesAt(int index) {
        waitForResumedViewCondition(
                withId(R.id.track_detail_activity_tablayout),
                view -> withSelectedTab(index).matches(view),
                "selected tab to be " + index,
                UI_TIMEOUT);
        waitForResumedViewCondition(
                withId(R.id.track_detail_activity_view_pager),
                view -> withViewPagerCurrentItem(index).matches(view),
                "ViewPager current item to be " + index,
                UI_TIMEOUT);
    }
}

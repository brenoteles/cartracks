package de.dennisguse.opentracks.util;

import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static org.hamcrest.Matchers.allOf;

import android.app.Activity;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewParent;
import android.widget.AdapterView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.espresso.PerformException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.util.HumanReadables;
import androidx.test.espresso.util.TreeIterables;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public class EspressoUtils {

    public static Matcher<View> withRecyclerViewItemCount(final int count) {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(View view) {
                if (!(view instanceof RecyclerView recyclerView)) {
                    return false;
                }

                RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
                return adapter != null && adapter.getItemCount() == count;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("RecyclerView should have " + count + " items");
            }
        };
    }

    public static Matcher<View> withSelectedTab(final int index) {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(View view) {
                return view instanceof TabLayout tabLayout
                        && tabLayout.getSelectedTabPosition() == index;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("TabLayout should have selected tab " + index);
            }
        };
    }

    public static Matcher<View> withViewPagerCurrentItem(final int index) {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(View view) {
                return view instanceof ViewPager2 viewPager2
                        && viewPager2.getCurrentItem() == index
                        && viewPager2.getScrollState() == ViewPager2.SCROLL_STATE_IDLE;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("ViewPager2 should be idle on item " + index);
            }
        };
    }

    public static void waitForResumedView(final Matcher<View> viewMatcher, final Duration timeout) {
        waitForResumedViewCondition(viewMatcher, ignored -> true, "view to match " + viewMatcher, timeout);
    }

    public static void waitUntilDisplayed(final Matcher<View> viewMatcher, final Duration timeout) {
        waitForResumedView(allOf(viewMatcher, isDisplayed()), timeout);
    }

    public static void waitUntilRecyclerViewItemCount(final Matcher<View> viewMatcher, final int expectedCount, final Duration timeout) {
        waitForResumedViewCondition(
                allOf(viewMatcher, isDisplayed()),
                view -> withRecyclerViewItemCount(expectedCount).matches(view),
                "RecyclerView item count to be " + expectedCount,
                timeout);
    }

    public static void waitForResumedViewCondition(final Matcher<View> viewMatcher,
                                                   final Predicate<View> condition,
                                                   final String conditionDescription,
                                                   final Duration timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout.toMillis();

        do {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            AtomicBoolean matched = new AtomicBoolean(false);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    View decorView = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
                    if (decorView == null) {
                        continue;
                    }

                    for (View child : TreeIterables.breadthFirstViewTraversal(decorView)) {
                        if (viewMatcher.matches(child) && condition.test(child)) {
                            matched.set(true);
                            return;
                        }
                    }
                }
            });

            if (matched.get()) {
                return;
            }
        } while (SystemClock.uptimeMillis() < deadline);

        throw new AssertionError("Timed out waiting for " + conditionDescription);
    }

    public static ViewAction selectTabAtIndex(final int index) {
        return new ViewAction() {
            @Override
            public String getDescription() {
                return "Selecting tab.";
            }

            @Override
            public Matcher<View> getConstraints() {
                return isDisplayed();
            }

            @Override
            public void perform(UiController uiController, View view) {
                TabLayout tabLayout = (TabLayout) view;
                tabLayout.getTabAt(index).select();
            }
        };
    }

    public static ViewAction performClick() {
        return new ViewAction() {
            @Override
            public String getDescription() {
                return "invoke View.performClick()";
            }

            @Override
            public Matcher<View> getConstraints() {
                return isDisplayed();
            }

            @Override
            public void perform(UiController uiController, View view) {
                if (!view.performClick()) {
                    throw new PerformException.Builder()
                            .withActionDescription(getDescription())
                            .withViewDescription(HumanReadables.describe(view))
                            .build();
                }
                uiController.loopMainThreadUntilIdle();
            }
        };
    }

    public static ViewAction performClickOnAncestor() {
        return new ViewAction() {
            @Override
            public String getDescription() {
                return "invoke performClick() on the matched view or its ancestor";
            }

            @Override
            public Matcher<View> getConstraints() {
                return isAssignableFrom(View.class);
            }

            @Override
            public void perform(UiController uiController, View view) {
                View current = view;
                while (current != null) {
                    if (current.performClick()) {
                        uiController.loopMainThreadUntilIdle();
                        return;
                    }

                    ViewParent parent = current.getParent();
                    current = parent instanceof View ? (View) parent : null;
                }

                current = view;
                while (current != null) {
                    ViewParent parent = current.getParent();
                    if (parent instanceof AdapterView<?> adapterView) {
                        int position = adapterView.getPositionForView(current);
                        if (position != AdapterView.INVALID_POSITION
                                && adapterView.performItemClick(current, position, adapterView.getAdapter().getItemId(position))) {
                            uiController.loopMainThreadUntilIdle();
                            return;
                        }
                    }

                    current = parent instanceof View ? (View) parent : null;
                }

                throw new PerformException.Builder()
                        .withActionDescription(getDescription())
                        .withViewDescription(HumanReadables.describe(view))
                        .build();
            }
        };
    }

    public static ViewAction performLongClick() {
        return new ViewAction() {
            @Override
            public String getDescription() {
                return "invoke View.performLongClick()";
            }

            @Override
            public Matcher<View> getConstraints() {
                return isDisplayed();
            }

            @Override
            public void perform(UiController uiController, View view) {
                if (!view.performLongClick()) {
                    throw new PerformException.Builder()
                            .withActionDescription(getDescription())
                            .withViewDescription(HumanReadables.describe(view))
                            .build();
                }
                uiController.loopMainThreadUntilIdle();
            }
        };
    }

    public static ViewAction performLongClickOnAncestor() {
        return new ViewAction() {
            @Override
            public String getDescription() {
                return "invoke performLongClick() on the matched view or its ancestor";
            }

            @Override
            public Matcher<View> getConstraints() {
                return isDisplayed();
            }

            @Override
            public void perform(UiController uiController, View view) {
                View current = view;
                while (current != null) {
                    if (current.performLongClick()) {
                        uiController.loopMainThreadUntilIdle();
                        return;
                    }

                    ViewParent parent = current.getParent();
                    current = parent instanceof View ? (View) parent : null;
                }

                throw new PerformException.Builder()
                        .withActionDescription(getDescription())
                        .withViewDescription(HumanReadables.describe(view))
                        .build();
            }
        };
    }

}

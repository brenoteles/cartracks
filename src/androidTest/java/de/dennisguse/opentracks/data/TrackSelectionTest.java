package de.dennisguse.opentracks.data;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import junit.framework.TestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import de.dennisguse.opentracks.data.models.Track;

@RunWith(AndroidJUnit4.class)
public class TrackSelectionTest extends TestCase {
    @Test
    public void testFilterBuildSelection_empty() {
        // given
        TrackSelection filter = new TrackSelection();

        // when
        ContentProviderUtils.SelectionData selection = filter.buildSelection();

        // Then
        assertNull(selection.selection());
        assertNull(selection.selectionArgs());
    }

    @Test
    public void testFilterBuildSelection_onlyOneTrackId() {
        // given
        Track.Id trackId = new Track.Id(1);
        TrackSelection filter = new TrackSelection().addTrackId(trackId);

        // when
        ContentProviderUtils.SelectionData selection = filter.buildSelection();

        // Then
        assertEquals("_id IN (?)", selection.selection());
        assertEquals(List.of("1"), selection.selectionArgs());
    }

    @Test
    public void testFilterBuildSelection_severalTracksId() {
        // given
        Track.Id trackId1 = new Track.Id(1);
        Track.Id trackId2 = new Track.Id(2);
        Track.Id trackId3 = new Track.Id(3);
        TrackSelection filter = new TrackSelection()
                .addTrackId(trackId1)
                .addTrackId(trackId2)
                .addTrackId(trackId3);

        // when
        ContentProviderUtils.SelectionData selection = filter.buildSelection();

        // Then
        assertEquals("_id IN (?,?,?)", selection.selection());
        assertEquals(List.of("1", "2", "3"), selection.selectionArgs());
    }

    @Test
    public void testFilterBuildSelection_onlyOneactivity_type_localized() {
        // given
        TrackSelection filter = new TrackSelection().addActivityType("running");

        // when
        ContentProviderUtils.SelectionData selection = filter.buildSelection();

        // Then
        assertEquals("activity_type_localized IN (?)", selection.selection());
        assertEquals(List.of("running"), selection.selectionArgs());
    }

    @Test
    public void testFilterBuildSelection_severalCategories() {
        // given
        TrackSelection filter = new TrackSelection()
                .addActivityType("running")
                .addActivityType("road biking")
                .addActivityType("mountain biking")
                .addActivityType("trail walking");

        // when
        ContentProviderUtils.SelectionData selection = filter.buildSelection();

        // Then
        assertEquals("activity_type_localized IN (?,?,?,?)", selection.selection());
        assertEquals(List.of("running", "road biking", "mountain biking", "trail walking"), selection.selectionArgs());
    }

    @Test
    public void testFilterBuildSelection_onlyDateRange() {
        // given
        Instant instant = Instant.now();
        long oneDay = 24 * 60 * 60 * 1000;
        TrackSelection filterWrong1 = new TrackSelection().addDateRange(instant, null);
        TrackSelection filterWrong2 = new TrackSelection().addDateRange(null, instant);
        TrackSelection filterOk = new TrackSelection().addDateRange(instant, instant.plus(Duration.ofDays(1)));

        // when
        ContentProviderUtils.SelectionData selectionWrong1 = filterWrong1.buildSelection();
        ContentProviderUtils.SelectionData selectionWrong2 = filterWrong2.buildSelection();
        ContentProviderUtils.SelectionData selectionOk = filterOk.buildSelection();

        // Then
        assertNull(selectionWrong1.selection());
        assertNull(selectionWrong1.selectionArgs());

        assertNull(selectionWrong2.selection());
        assertNull(selectionWrong2.selectionArgs());

        assertEquals("time_start BETWEEN ? AND ?", selectionOk.selection());
        assertEquals(List.of(Long.toString(instant.toEpochMilli()), Long.toString(instant.toEpochMilli() + oneDay)) , selectionOk.selectionArgs());
    }

    @Test
    public void testFilterBuildSelection_tracksId_and_categories() {
        // given
        Track.Id trackId1 = new Track.Id(1);
        Track.Id trackId2 = new Track.Id(2);
        Track.Id trackId3 = new Track.Id(3);
        TrackSelection filter = new TrackSelection()
                .addTrackId(trackId1)
                .addTrackId(trackId2)
                .addTrackId(trackId3)
                .addActivityType("running")
                .addActivityType("road biking");

        // when
        ContentProviderUtils.SelectionData selection = filter.buildSelection();

        // Then
        assertEquals("_id IN (?,?,?) AND activity_type_localized IN (?,?)", selection.selection());
        assertEquals(List.of("1", "2", "3", "running", "road biking"), selection.selectionArgs());
    }

    @Test
    public void testFilterBuildSelection_tracksId_and_dateRange() {
        // given
        Instant instant = Instant.now();
        long oneDay = 24 * 60 * 60 * 1000;

        Track.Id trackId1 = new Track.Id(1);
        Track.Id trackId2 = new Track.Id(2);
        Track.Id trackId3 = new Track.Id(3);

        TrackSelection filter = new TrackSelection()
                .addTrackId(trackId1)
                .addTrackId(trackId2)
                .addTrackId(trackId3)
                .addDateRange(instant, instant.plusMillis(oneDay));

        // when
        ContentProviderUtils.SelectionData selection = filter.buildSelection();

        // Then
        assertEquals("_id IN (?,?,?) AND time_start BETWEEN ? AND ?", selection.selection());
        assertEquals(List.of("1", "2", "3", Long.toString(instant.toEpochMilli()), Long.toString(instant.toEpochMilli() + oneDay)), selection.selectionArgs());
    }

    @Test
    public void testFilterBuildSelection_categories_and_dateRange() {
        // given
        Instant instant = Instant.now();
        long oneDay = 24 * 60 * 60 * 1000;

        TrackSelection filter = new TrackSelection()
                .addActivityType("running")
                .addActivityType("road biking")
                .addDateRange(instant, instant.plusMillis(oneDay));

        // when
        ContentProviderUtils.SelectionData selection = filter.buildSelection();

        // Then
        assertEquals("activity_type_localized IN (?,?) AND time_start BETWEEN ? AND ?", selection.selection());
        assertEquals(List.of("running", "road biking", Long.toString(instant.toEpochMilli()), Long.toString(instant.toEpochMilli() + oneDay)), selection.selectionArgs());
    }
}

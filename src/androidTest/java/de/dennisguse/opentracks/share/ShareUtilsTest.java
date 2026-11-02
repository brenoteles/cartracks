package de.dennisguse.opentracks.share;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Objects;

import de.dennisguse.opentracks.content.data.TestDataUtil;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.Track;

public class ShareUtilsTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private ContentProviderUtils contentProviderUtils;

    @Before
    public void setUp() {
        contentProviderUtils = new ContentProviderUtils(context);
        contentProviderUtils.deleteAllTracks(context);
    }

    @Test
    public void testSingleTrack() {
        // given
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        contentProviderUtils.insertTrack(TestDataUtil.createTrack(trackId));

        // when
        Intent intent = ShareUtils.newShareFileIntent(context, trackId);

        // then
        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertTrue(intent.getParcelableExtra(Intent.EXTRA_STREAM) instanceof Uri);
    }

    @Test
    public void testMultipleTracks() {
        // given
        long random = System.currentTimeMillis();
        Track.Id trackId1 = new Track.Id(random);
        Track.Id trackId2 = new Track.Id(random + 1);
        contentProviderUtils.insertTrack(TestDataUtil.createTrack(trackId1));
        contentProviderUtils.insertTrack(TestDataUtil.createTrack(trackId2));

        // when
        Intent intent = ShareUtils.newShareFileIntent(context, trackId1, trackId2);

        // then
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.getAction());
        ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        assertEquals(2, Objects.requireNonNull(uris).size());
    }
}

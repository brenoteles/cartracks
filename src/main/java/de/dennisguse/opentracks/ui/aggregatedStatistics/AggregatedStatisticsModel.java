package de.dennisguse.opentracks.ui.aggregatedStatistics;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import de.dennisguse.opentracks.data.AggregatedStatistic;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.TrackSelection;

public class AggregatedStatisticsModel extends AndroidViewModel {

    private MutableLiveData<List<AggregatedStatistic>> aggregatedStats;

    public AggregatedStatisticsModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<AggregatedStatistic>> getAggregatedStats(@Nullable TrackSelection selection) {
        if (aggregatedStats == null) {
            aggregatedStats = new MutableLiveData<>();
            loadAggregatedStats(selection);
        }
        return aggregatedStats;
    }

    public void updateSelection(TrackSelection selection) {
        loadAggregatedStats(selection);
    }

    public void clearSelection() {
        loadAggregatedStats(new TrackSelection());
    }

    private void loadAggregatedStats(TrackSelection selection) {
        new Thread(() -> {
            ContentProviderUtils contentProviderUtils = new ContentProviderUtils(getApplication().getApplicationContext());
            aggregatedStats.postValue(contentProviderUtils.getAggregatedStatisticsForTracks(selection));
        }).start();
    }
}

package de.dennisguse.opentracks.ui.aggregatedStatistics;

import android.content.Context;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.AggregatedStatistic;
import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.DistanceFormatter;
import de.dennisguse.opentracks.data.models.SpeedFormatter;
import de.dennisguse.opentracks.databinding.AggregatedStatsListItemBinding;
import de.dennisguse.opentracks.settings.PreferencesUtils;
import de.dennisguse.opentracks.settings.UnitSystem;
import de.dennisguse.opentracks.util.StringUtils;

public class AggregatedStatisticsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<AggregatedStatistic> aggregatedStatistics;
    private final Context context;

    public AggregatedStatisticsAdapter(Context context, @NonNull List<AggregatedStatistic> aggregatedStatistics) {
        this.context = context;
        this.aggregatedStatistics = aggregatedStatistics;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AggregatedStatsListItemBinding.inflate(LayoutInflater.from(parent.getContext())));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ViewHolder viewHolder = (ViewHolder) holder;

        AggregatedStatistic aggregatedStatistic = aggregatedStatistics.get(position);

        String type = aggregatedStatistic.activityTypeLocalized();
        if (ActivityType.findByLocalizedString(context, type).isShowSpeedPreferred()) {
            viewHolder.setSpeed(aggregatedStatistic);
        } else {
            viewHolder.setPace(aggregatedStatistic);
        }
    }

    @Override
    public int getItemCount() {
        return aggregatedStatistics.size();
    }

    public void swapData(List<AggregatedStatistic> aggregatedStatistics) {
        this.aggregatedStatistics = aggregatedStatistics;
        this.notifyDataSetChanged();
    }

    public List<String> getCategories() {
        return aggregatedStatistics.stream()
                .map(AggregatedStatistic::activityTypeLocalized)
                .toList();
    }

    private class ViewHolder extends RecyclerView.ViewHolder {
        private final AggregatedStatsListItemBinding viewBinding;
        private UnitSystem unitSystem = UnitSystem.defaultUnitSystem();
        private boolean reportSpeed;

        public ViewHolder(AggregatedStatsListItemBinding viewBinding) {
            super(viewBinding.getRoot());
            this.viewBinding = viewBinding;
        }

        public void setSpeed(AggregatedStatistic aggregatedStatistic) {
            setCommonValues(aggregatedStatistic);

            SpeedFormatter formatter = SpeedFormatter.Builder().setUnit(unitSystem).setReportSpeedOrPace(reportSpeed).build(context);
            {
                Pair<String, String> parts = formatter.getSpeedParts(aggregatedStatistic.getAverageMovingSpeed());
                viewBinding.aggregatedStatsAvgRate.setText(parts.first);
                viewBinding.aggregatedStatsAvgRateUnit.setText(parts.second);
                viewBinding.aggregatedStatsAvgRateLabel.setText(context.getString(R.string.stats_average_moving_speed));
            }

            {
                Pair<String, String> parts = formatter.getSpeedParts(aggregatedStatistic.maxSpeed());
                viewBinding.aggregatedStatsMaxRate.setText(parts.first);
                viewBinding.aggregatedStatsMaxRateUnit.setText(parts.second);
                viewBinding.aggregatedStatsMaxRateLabel.setText(context.getString(R.string.stats_max_speed));
            }
        }

        public void setPace(AggregatedStatistic aggregatedStatistic) {
            setCommonValues(aggregatedStatistic);

            SpeedFormatter formatter = SpeedFormatter.Builder().setUnit(unitSystem).setReportSpeedOrPace(reportSpeed).build(context);
            {
                Pair<String, String> parts = formatter.getSpeedParts(aggregatedStatistic.getAverageMovingSpeed());
                viewBinding.aggregatedStatsAvgRate.setText(parts.first);
                viewBinding.aggregatedStatsAvgRateUnit.setText(parts.second);
                viewBinding.aggregatedStatsAvgRateLabel.setText(context.getString(R.string.stats_average_moving_pace));
            }

            {
                Pair<String, String> parts = formatter.getSpeedParts(aggregatedStatistic.maxSpeed());
                viewBinding.aggregatedStatsMaxRate.setText(parts.first);
                viewBinding.aggregatedStatsMaxRateUnit.setText(parts.second);
                viewBinding.aggregatedStatsMaxRateLabel.setText(R.string.stats_fastest_pace);
            }
        }

        //TODO Check preference handling.
        private void setCommonValues(AggregatedStatistic aggregatedStatistic) {
            String activityType = aggregatedStatistic.activityTypeLocalized();

            reportSpeed = PreferencesUtils.isReportSpeed(activityType);
            unitSystem = PreferencesUtils.getUnitSystem();

            viewBinding.activityIcon.setImageResource(getIcon(aggregatedStatistic));
            viewBinding.aggregatedStatsTypeLabel.setText(activityType);
            viewBinding.aggregatedStatsNumTracks.setText(StringUtils.valueInParentheses(String.valueOf(aggregatedStatistic.countTracks())));

            Pair<String, String> parts = DistanceFormatter.Builder()
                    .setUnit(unitSystem)
                    .build(context).getDistanceParts(aggregatedStatistic.totalDistance());
            viewBinding.aggregatedStatsDistance.setText(parts.first);
            viewBinding.aggregatedStatsDistanceUnit.setText(parts.second);

            viewBinding.aggregatedStatsTime.setText(StringUtils.formatElapsedTime(aggregatedStatistic.totalMovingTime()));
        }

        private int getIcon(AggregatedStatistic aggregatedStatistic) {
            String localizedActivityType = aggregatedStatistic.activityTypeLocalized();
            return ActivityType.findByLocalizedString(context, localizedActivityType)
                    .getIconDrawableId();
        }
    }
}

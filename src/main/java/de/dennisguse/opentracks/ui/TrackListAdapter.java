package de.dennisguse.opentracks.ui;

import android.content.Intent;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.TrackRecordedActivity;
import de.dennisguse.opentracks.TrackRecordingActivity;
import de.dennisguse.opentracks.data.TrackListIterator;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.databinding.TrackListItemBinding;
import de.dennisguse.opentracks.services.RecordingStatus;
import de.dennisguse.opentracks.settings.UnitSystem;
import de.dennisguse.opentracks.ui.util.ActivityUtils;
import de.dennisguse.opentracks.ui.util.ListItemUtils;
import de.dennisguse.opentracks.util.IntentUtils;

public class TrackListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements ActionMode.Callback {


    private final AppCompatActivity context;
    private final RecyclerView recyclerView;
    private final SparseBooleanArray selection = new SparseBooleanArray();
    private RecordingStatus recordingStatus;
    private UnitSystem unitSystem;
    private TrackListIterator trackIterator;
    private boolean selectionMode = false;
    private ActivityUtils.ContextualActionModeCallback actionModeCallback;
    private ActionMode actionMode;

    public TrackListAdapter(AppCompatActivity context, RecyclerView recyclerView, RecordingStatus recordingStatus, UnitSystem unitSystem) {
        this.context = context;
        this.recyclerView = recyclerView;
        this.recordingStatus = recordingStatus;
        this.unitSystem = unitSystem;
        setHasStableIds(true);
    }

    public void setActionModeCallback(ActivityUtils.ContextualActionModeCallback actionModeCallback) {
        this.actionModeCallback = actionModeCallback;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.track_list_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ViewHolder viewHolder = (ViewHolder) holder;

        trackIterator.moveToPosition(position);
        viewHolder.bind(trackIterator);
    }

    @Override
    public long getItemId(int position) {
        trackIterator.moveToPosition(position);
        return trackIterator.get().id().id();
    }

    @Override
    public int getItemCount() {
        if (trackIterator == null) {
            return 0;
        }
        return trackIterator.getCount();
    }

    public void swapData(TrackListIterator cursor) {
        this.trackIterator = cursor;
        this.notifyDataSetChanged();
    }

    public void updateRecordingStatus(RecordingStatus recordingStatus) {
        this.recordingStatus = recordingStatus;
        this.notifyDataSetChanged();
    }

    public void updateUnitSystem(UnitSystem unitSystem) {
        this.unitSystem = unitSystem;
        this.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.list_context_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        selectionMode = true;

        actionModeCallback.onPrepare(menu, null, getCheckedIds(), true);
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (actionModeCallback.onClick(item.getItemId(), null, getCheckedIds())) {
            mode.finish();
        }
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        selectionMode = false;

        setAllSelected(false);

        actionModeCallback.onDestroy();
    }

    public void setAllSelected(boolean isSelected) {
        if (isSelected) {
            trackIterator.moveToFirst();
            while (trackIterator.hasNext()) {
                selection.put((int) trackIterator.get().id().id(), true);
                trackIterator.next();
            }
        } else {
            selection.clear();
        }

        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            ViewHolder holder = (ViewHolder) recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
            holder.setSelected(isSelected);
        }
    }

    private long[] getCheckedIds() {
        List<Long> ids = new ArrayList<>();

        for (int i = 0; i < selection.size(); i++) {
            if (selection.valueAt(i)) {
                ids.add((long) selection.keyAt(i));
            }
        }

        return ids.stream().mapToLong(i -> i).toArray();
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        private final TrackListItemBinding viewBinding;

        private Track.Id trackId;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            viewBinding = TrackListItemBinding.bind(itemView);

            viewBinding.getRoot().setOnClickListener(this);
            viewBinding.getRoot().setOnLongClickListener(this);
        }


        public void bind(TrackListIterator cursor) {
            TrackListIterator.Item tracksListItem = cursor.get();
            trackId = tracksListItem.id();

            int iconId = tracksListItem.activityType().getIconDrawableId();
            int iconDesc = R.string.image_track;

            boolean isRecordingThisTrackRecording = trackId.equals(recordingStatus.trackId());
            if (isRecordingThisTrackRecording) {
                iconId = R.drawable.ic_track_recording;
                iconDesc = R.string.image_record;
            }

            viewBinding.trackListItemIcon.setImageResource(iconId);
            viewBinding.trackListItemIcon.setContentDescription(context.getString(iconDesc));

            viewBinding.trackListItemName.setText(tracksListItem.name());

            String timeText = ListItemUtils.getTimeText(context, isRecordingThisTrackRecording, tracksListItem.totalTime());
            String distanceText = ListItemUtils.getDistanceText(context, unitSystem, isRecordingThisTrackRecording, tracksListItem.totalDistance());

            viewBinding.trackListItemDistance.setText(distanceText);
            viewBinding.trackListItemDuration.setText(timeText);

            if (tracksListItem.markerCount() > 0) {
                viewBinding.trackListItemDistanceMarkerSeparator.setVisibility(View.VISIBLE);
                viewBinding.trackListItemMarkerCountIcon.setVisibility(View.VISIBLE);
                viewBinding.trackListItemMarkerCount.setText(Integer.toString(tracksListItem.markerCount()));
            } else {
                viewBinding.trackListItemDistanceMarkerSeparator.setVisibility(View.GONE);
                viewBinding.trackListItemMarkerCountIcon.setVisibility(View.GONE);
                viewBinding.trackListItemMarkerCount.setText(null);
            }


            ListItemUtils.setDateAndTime(context, viewBinding.trackListItemDate, viewBinding.trackListItemTime, tracksListItem.startTime(), tracksListItem.zoneOffset());

            viewBinding.trackListItemActivityTypeLocalized.setText(tracksListItem.activityTypeLocalized());
            viewBinding.trackListItemActivityTypeLocalized.setVisibility(TextUtils.isEmpty(tracksListItem.activityTypeLocalized()) ? View.GONE : View.VISIBLE);

            viewBinding.trackListItemDescription.setText(tracksListItem.description());
            viewBinding.trackListItemDescription.setVisibility(TextUtils.isEmpty(tracksListItem.description()) ? View.GONE : View.VISIBLE);

            setSelected(selection.get((int) trackId.id()));
        }

        public void setSelected(boolean isSelected) {
            selection.put((int) trackId.id(), isSelected);
            viewBinding.getRoot().setActivated(isSelected);
        }

        @Override
        public void onClick(View v) {
            if (selectionMode) {
                setSelected(!viewBinding.getRoot().isActivated());
                actionMode.invalidate();
                return;
            }

            if (recordingStatus.isRecording() && trackId.equals(recordingStatus.trackId())) {
                // Is recording -> open record activity.
                Intent newIntent = IntentUtils.newIntent(context, TrackRecordingActivity.class)
                        .putExtra(TrackRecordedActivity.EXTRA_TRACK_ID, trackId);
                context.startActivity(newIntent);
            } else {
                // Not recording -> open detail activity.
                Intent newIntent = IntentUtils.newIntent(context, TrackRecordedActivity.class)
                        .putExtra(TrackRecordedActivity.EXTRA_TRACK_ID, trackId);
                context.startActivity(newIntent);
            }
        }

        @Override
        public boolean onLongClick(View v) {
            setSelected(!viewBinding.getRoot().isActivated());
            if (!selectionMode) {
                actionMode = context.startSupportActionMode(TrackListAdapter.this);
            } else {
                actionMode.invalidate();
            }
            return true;
        }
    }
}

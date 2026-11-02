package de.dennisguse.opentracks.io.healthconnect.exporter;

import android.content.Context;

import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.PermissionController;
import androidx.health.connect.client.permission.HealthPermission;
import androidx.health.connect.client.records.DistanceRecord;
import androidx.health.connect.client.records.ElevationGainedRecord;
import androidx.health.connect.client.records.ExerciseSessionRecord;
import androidx.health.connect.client.records.SpeedRecord;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import java.util.Set;

import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.settings.PreferencesUtils;
import kotlin.coroutines.Continuation;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.BuildersKt;

/**
 * Exports a single track as an ExerciseSession to Health Connect.
 * Uses the Track.uuid as clientRecordId to prevent duplicates on re-export.
 */
public class HealthConnectUtils {

    public static final Set<String> PERMISSIONS = Set.of(
            HealthPermission.getWritePermission(JvmClassMappingKt.getKotlinClass(ExerciseSessionRecord.class)),
            HealthPermission.getWritePermission(JvmClassMappingKt.getKotlinClass(DistanceRecord.class)),
            HealthPermission.getWritePermission(JvmClassMappingKt.getKotlinClass(ElevationGainedRecord.class)),
            HealthPermission.getWritePermission(JvmClassMappingKt.getKotlinClass(SpeedRecord.class)),
            HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE
    );

    private HealthConnectUtils() {
    }

    public static boolean isAvailable(Context context) {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE;
    }

    public static Set<String> getGrantedPermissions(Context context) {
        if (!isAvailable(context))
            return Set.of();

        try {
            HealthConnectClient client = HealthConnectClient.getOrCreate(context);
            PermissionController controller = client.getPermissionController();

            return (Set<String>) BuildersKt.runBlocking(
                    kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                    (Function2) (scope, cont) -> controller.getGrantedPermissions((Continuation) cont));
        } catch (InterruptedException e) {
            return Set.of();
        }
    }

    static boolean hasRequiredPermissions(Context context) {
        return getGrantedPermissions(context)
                .containsAll(PERMISSIONS);
    }

    public static void postExportTrack(Context context, Track.Id trackId) {
        if (!PreferencesUtils.shouldInstantExportToHealthConnect()) {
            return;
        }

        WorkRequest request = new OneTimeWorkRequest.Builder(HealthConnectWorker.class)
                .addTag(HealthConnectWorker.WORK_TAG)
                .setInputData(new Data.Builder()
                        .putLong(HealthConnectWorker.TRACK_ID_KEY, trackId.id())
                        .build())
                .build();
        WorkManager.getInstance(context).enqueue(request);
    }

    public static void postExportAll(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(HealthConnectWorker.class)
                .addTag(HealthConnectWorker.WORK_TAG)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                HealthConnectWorker.WORK_TAG,
                ExistingWorkPolicy.REPLACE,
                request
        );
    }

    public static void cancelHealthConnectExport(Context context) {
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.cancelAllWorkByTag(HealthConnectWorker.WORK_TAG);
    }
}

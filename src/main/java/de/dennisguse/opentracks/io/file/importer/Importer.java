package de.dennisguse.opentracks.io.file.importer;

import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.util.FileUtils;

//TODO Cancel does not abort current import
//TODO Provide some intermediate progress (not only when a file got imported)
public class Importer {

    private final MutableLiveData<Summary> liveData = new MutableLiveData<>();
    private final Summary summary = new Summary();
    private final List<DocumentFile> filesToImport = new ArrayList<>();

    private boolean cancel = false;

    private final ImportActivity context;
    private final WorkManager workManager;

    //TODO Add summary callback
    Importer(ImportActivity context, List<DocumentFile> documentFiles) {
        this.context = context;
        this.workManager = WorkManager.getInstance(context);

        List<ArrayList<DocumentFile>> nestedFileList = documentFiles.stream()
                .map(FileUtils::getFiles)
                .toList();

        List<DocumentFile> fileList = new ArrayList<>();
        nestedFileList.forEach(fileList::addAll);

        summary.totalCount = fileList.size();
        filesToImport.addAll(fileList);
    }

    LiveData<Summary> getLiveData() {
        return liveData;
    }

    void startImport() {
        liveData.postValue(summary);
        importNextFile();
    }

    void cancel() {
        cancel = true;
    }

    private void importNextFile() {
        if (cancel || filesToImport.isEmpty()) {
            return;
        }

        final DocumentFile documentFile = filesToImport.get(0);

        WorkRequest importRequest = new OneTimeWorkRequest.Builder(ImportWorker.class)
                .setInputData(new Data.Builder()
                        .putString(ImportWorker.URI_KEY, documentFile.getUri().toString())
                        .putString(ImportWorker.FILENAME_KEY, documentFile.getName())
                        .build())
                .build();

        workManager
                .getWorkInfoByIdLiveData(importRequest.getId())
                .observe(context, workInfo -> {
                    if (workInfo != null) {
                        WorkInfo.State state = workInfo.getState();
                        if (state.isFinished()) {
                            switch (state) {
                                case SUCCEEDED -> {
                                    summary.importedTrackIds.addAll(
                                            Arrays.stream(workInfo.getOutputData().getLongArray(ImportWorker.RESULT_SUCCESS_LIST_TRACKIDS_KEY))
                                                    .mapToObj(Track.Id::new)
                                                    .toList());

                                    summary.successCount++;
                                }
                                case FAILED -> {
                                    if (workInfo.getOutputData().getBoolean(ImportWorker.RESULT_FAILURE_IS_DUPLICATE, false)) {
                                        summary.existsCount++;
                                    } else {
                                        // Some error happened
                                        String errorMessage = workInfo.getOutputData().getString(ImportWorker.RESULT_MESSAGE_KEY);
                                        summary.fileErrors.add(context.getString(R.string.import_error_info, documentFile.getName(), errorMessage));
                                    }
                                }
                            }

                            liveData.postValue(summary);
                            importNextFile();
                        }
                    }
                });

        workManager.enqueue(importRequest);
        filesToImport.remove(0);
    }

    static class Summary {
        private int totalCount;
        private int successCount;
        private int existsCount;
        private final ArrayList<Track.Id> importedTrackIds = new ArrayList<>();
        private final ArrayList<String> fileErrors = new ArrayList<>();

        public int getTotalCount() {
            return totalCount;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getExistsCount() {
            return existsCount;
        }

        public int getErrorCount() {
            return fileErrors.size();
        }

        public ArrayList<Track.Id> getImportedTrackIds() {
            return importedTrackIds;
        }

        public ArrayList<String> getFileErrors() {
            return fileErrors;
        }

        public int getCount() {
            return getSuccessCount() + getExistsCount() + getErrorCount();
        }

        public boolean isDone() {
            return getTotalCount() == getCount();
        }
    }
}

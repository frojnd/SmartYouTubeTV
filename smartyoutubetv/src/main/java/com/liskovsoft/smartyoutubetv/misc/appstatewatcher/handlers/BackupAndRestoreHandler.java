package com.liskovsoft.smartyoutubetv.misc.appstatewatcher.handlers;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Environment;
import android.os.Handler;
import com.liskovsoft.sharedutils.dialogs.YesNoDialog;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv.R;
import com.liskovsoft.smartyoutubetv.misc.SmartUtils;
import com.liskovsoft.smartyoutubetv.misc.appstatewatcher.AppStateWatcherBase.StateHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BackupAndRestoreHandler extends StateHandler implements DialogInterface.OnClickListener {
    private static final String TAG = BackupAndRestoreHandler.class.getSimpleName();
    private final Context mContext;
    private final List<File> mDataDirs;
    private static final String WEBVIEW_SUBDIR = "app_webview";
    private static final String XWALK_SUBDIR = "app_xwalkcore";
    private static final String SHARED_PREFS_SUBDIR = "shared_prefs";
    private final List<File> mBackupDirs;
    private boolean mIsFirstRun;
    private boolean mIsUpdate;

    public BackupAndRestoreHandler(Context context) {
        mContext = context;
        mDataDirs = new ArrayList<>();
        mDataDirs.add(new File(mContext.getApplicationInfo().dataDir, WEBVIEW_SUBDIR));
        mDataDirs.add(new File(mContext.getApplicationInfo().dataDir, XWALK_SUBDIR));
        mDataDirs.add(new File(mContext.getApplicationInfo().dataDir, SHARED_PREFS_SUBDIR));

        mBackupDirs = new ArrayList<>();
        mBackupDirs.add(new File(Environment.getExternalStorageDirectory(), String.format("data/%s/Backup", mContext.getPackageName())));
        mBackupDirs.add(new File(Environment.getExternalStorageDirectory(), String.format("data/%s/Backup", "com.liskovsoft.videomanager")));
    }

    @Override
    public void onUpdate() {
        // do backup later, after full load
        mIsUpdate = true;
    }

    @Override
    public void onFirstRun() {
        // run restore later, after permissions dialog
        mIsFirstRun = true;
    }

    @Override
    public void onLoad() {
        // permissions dialog should be closed at this point
        if (mIsFirstRun) {
            boolean backupFound = false;

            for (File backupDir : mBackupDirs) {
                if (backupDir.isDirectory()) {
                    backupFound = true;
                    checkPermAndProposeRestore();
                    break;
                }
            }

            if (!backupFound) {
                // app might be upgraded from older version
                checkPermAndBackup();
            }
        } else if (mIsUpdate) {
            checkPermAndBackup();
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                //Yes button clicked
                restoreData();
                break;

            case DialogInterface.BUTTON_NEGATIVE:
                //No button clicked
                break;
        }
    }

    private void checkPermAndProposeRestore() {
        if (FileHelpers.isExternalStorageReadable()) {
            askUserPermission();
        }
    }

    private void checkPermAndBackup() {
        if (FileHelpers.isExternalStorageWritable()) {
            backupData();
        }
    }

    private void backupData() {
        Log.d(TAG, "App has been updated or installed. Doing data backup...");

        File currentBackup = mBackupDirs.get(0); // backup to first dir from list

        // remove old backup
        if (currentBackup.isDirectory()) {
            FileHelpers.delete(currentBackup);
        }

        for (File dataDir : mDataDirs) {
            if (dataDir.isDirectory()) {
                FileHelpers.copy(dataDir, new File(currentBackup, dataDir.getName()));
            }
        }
    }

    private void restoreData() {
        Log.d(TAG, "App just updated. Restoring data...");

        File currentBackup = null;

        for (File backupDir : mBackupDirs) {
            if (backupDir.isDirectory()) {
                currentBackup = backupDir;
                break;
            }
        }

        if (currentBackup == null) {
            Log.d(TAG, "Oops. Backup not exists.");
            return;
        }

        for (File dataDir : mDataDirs) {
            if (dataDir.isDirectory()) {
                // remove old data
                FileHelpers.delete(dataDir);
            }

            FileHelpers.copy(new File(currentBackup, dataDir.getName()), dataDir);
        }

        // to apply settings we need to kill the app
        new Handler(mContext.getMainLooper()).postDelayed(() -> SmartUtils.restartApp(mContext), 1_000);
    }

    private void askUserPermission() {
        YesNoDialog.create(mContext, R.string.do_restore_data_msg, this, R.style.AppDialog);
    }
}

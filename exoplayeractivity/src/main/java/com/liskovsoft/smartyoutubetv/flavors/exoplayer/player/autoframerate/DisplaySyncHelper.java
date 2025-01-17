package com.liskovsoft.smartyoutubetv.flavors.exoplayer.player.autoframerate;

import android.app.Activity;
import android.os.Build;
import android.view.Window;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv.CommonApplication;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.player.autoframerate.DisplayHolder.Mode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

// Source: https://developer.amazon.com/docs/fire-tv/4k-apis-for-hdmi-mode-switch.html#amazonextension

class DisplaySyncHelper implements UhdHelperListener {
    private static final String TAG = DisplaySyncHelper.class.getSimpleName();
    protected final Activity mContext;
    private boolean mDisplaySyncInProgress = false;
    private UhdHelper mUhdHelper;
    protected DisplayHolder.Mode mOriginalMode;
    private DisplayHolder.Mode mLastMode;
    protected DisplayHolder.Mode mNewMode;
    // switch not only framerate but resolution too
    private static final boolean SWITCH_TO_UHD = true;

    public DisplaySyncHelper(Activity context) {
        mContext = context;
    }

    private List<DisplayHolder.Mode> filterSameResolutionModes(DisplayHolder.Mode[] oldModes, DisplayHolder.Mode currentMode) {
        if (currentMode == null) {
            return Collections.emptyList();
        }

        ArrayList<DisplayHolder.Mode> newModes = new ArrayList<>();
        int oldModesLen = oldModes.length;

        for (int i = 0; i < oldModesLen; ++i) {
            DisplayHolder.Mode mode = oldModes[i];
            if (mode == null) {
                continue;
            }

            if (mode.getPhysicalHeight() == currentMode.getPhysicalHeight() && mode.getPhysicalWidth() == currentMode.getPhysicalWidth()) {
                newModes.add(mode);
            }
        }

        return newModes;
    }

    private ArrayList<DisplayHolder.Mode> filterUHDModes(DisplayHolder.Mode[] oldModes) {
        ArrayList<DisplayHolder.Mode> newModes = new ArrayList<>();
        int modesNum = oldModes.length;

        for (int i = 0; i < modesNum; ++i) {
            DisplayHolder.Mode mode = oldModes[i];
            Log.i(TAG, "Check fo UHD: " + mode.getPhysicalWidth() + "x" + mode.getPhysicalHeight() + "@" + mode.getRefreshRate());
            if (mode.getPhysicalHeight() >= 2160) {
                Log.i(TAG, "Found! UHD");
                newModes.add(mode);
            }
        }

        if (newModes.isEmpty()) {
            Log.i(TAG, "NO UHD MODES FOUND!!");
        }

        return newModes;
    }

    private DisplayHolder.Mode findCloserMode(List<Mode> modes, int videoWidth, float videoFramerate) {
        HashMap<Integer, int[]> relatedRates;

        if (SWITCH_TO_UHD && videoWidth > 1920) {
            relatedRates = getUHDRateMapping();
        } else {
            relatedRates = getRateMapping();
        }

        int myRate = (int) (videoFramerate * 100.0F);
        if (myRate >= 2300 && myRate <= 2399) {
            myRate = 2397;
        }

        if (relatedRates.containsKey(myRate)) {
            HashMap<Integer, DisplayHolder.Mode> rateAndMode = new HashMap<>();
            Iterator modeIterator = modes.iterator();

            while (modeIterator.hasNext()) {
                DisplayHolder.Mode mode = (DisplayHolder.Mode) modeIterator.next();
                rateAndMode.put((int) (mode.getRefreshRate() * 100.0F), mode);
            }

            int[] rates = relatedRates.get(myRate);
            int ratesLen = rates.length;

            for (int i = 0; i < ratesLen; ++i) {
                int newRate = rates[i];
                if (rateAndMode.containsKey(newRate)) {
                    return rateAndMode.get(newRate);
                }
            }
        }

        return null;
    }

    protected HashMap<Integer, int[]> getUHDRateMapping() {
        return getRateMapping();
    }

    protected HashMap<Integer, int[]> getRateMapping() {
        HashMap<Integer, int[]> relatedRates = new HashMap<>();
        relatedRates.put(1500, new int[]{3000, 6000});
        relatedRates.put(2397, new int[]{2397, 2400, 3000, 6000});
        relatedRates.put(2400, new int[]{2400, 3000, 6000});
        relatedRates.put(2500, new int[]{2500, 5000});
        relatedRates.put(2997, new int[]{2997, 3000, 6000});
        relatedRates.put(3000, new int[]{3000, 6000});
        relatedRates.put(5000, new int[]{5000, 2500});
        relatedRates.put(5994, new int[]{5994, 6000, 3000});
        relatedRates.put(6000, new int[]{6000, 3000});
        return relatedRates;
    }

    /**
     * Utility method to check if device is Amazon Fire TV device
     * @return {@code true} true if device is Amazon Fire TV device.
     */
    public static boolean isAmazonFireTVDevice(){
        String deviceName = Build.MODEL;
        String manufacturerName = Build.MANUFACTURER;
        return (deviceName.startsWith("AFT")
                && "Amazon".equalsIgnoreCase(manufacturerName));
    }

    /**
     * Check whether device supports mode change. Also shows toast if no
     * @return mode change supported
     */
    public static boolean supportsDisplayModeChange() {
        boolean supportedDevice = true;

        //We fail for following conditions
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            supportedDevice = false;
        } else {
            switch (Build.VERSION.SDK_INT) {
                case Build.VERSION_CODES.LOLLIPOP:
                case Build.VERSION_CODES.LOLLIPOP_MR1:
                    if (!isAmazonFireTVDevice()) {
                        supportedDevice = false;
                    }
                    break;
            }
        }

        if (!supportedDevice) {
            Log.i(TAG, "Device doesn't support display mode change");
        }

        return supportedDevice;
    }

    public boolean displayModeSyncInProgress() {
        return mDisplaySyncInProgress;
    }

    @Override
    public void onModeChanged(DisplayHolder.Mode mode) {
        mDisplaySyncInProgress = false;

        if (mode == null && mUhdHelper.getMode() == null) {
            String msg = "Mode changed Failure, Internal error occurred.";
            Log.w(TAG, msg);
        } else {
            int modeId = mNewMode != null ? mNewMode.getModeId() : -1;
            if (mUhdHelper.getMode().getModeId() != modeId) {
                // Once onDisplayChangedListener sends proper callback, the above if condition
                // need to changed to mode.getModeId() != modeId
                String msg = String.format("Mode changed Failure, Current mode id is %s, Expected mode id is %s", mUhdHelper.getMode().getModeId(), modeId);
                Log.w(TAG, msg);
            }
            else {
                Log.i(TAG, "Mode changed successfully");
                CommonApplication.getPreferences().setCurrentDisplayMode(UhdHelper.formatMode(mode));
            }
        }
    }

    // switch frame rate only
    private boolean getNeedDisplaySync() {
        return true;
    }

    /**
     * Tries to find best suited display params for the video
     * @param window window object
     * @param videoWidth width of the video material
     * @param videoFramerate framerate of the video
     * @return
     */
    public boolean syncDisplayMode(Window window, int videoWidth, float videoFramerate) {
        if (supportsDisplayModeChange() && videoWidth >= 10) {
            if (mUhdHelper == null) {
                mUhdHelper = new UhdHelper(mContext);
            }

            DisplayHolder.Mode[] modes = mUhdHelper.getSupportedModes();

            Log.d(TAG, "Modes supported by device:");
            Log.d(TAG, Arrays.asList(modes));

            boolean isUHD = false;
            List<DisplayHolder.Mode> resultModes = new ArrayList<>();

            if (SWITCH_TO_UHD) { // switch not only framerate but resolution too
                if (videoWidth > 1920) {
                    resultModes = filterUHDModes(modes);
                    if (!resultModes.isEmpty()) {
                        isUHD = true;
                    }
                }
            }

            Log.i(TAG, "Need resolution switch: " + isUHD);

            DisplayHolder.Mode mode = mUhdHelper.getMode();
            if (!isUHD) {
                resultModes = filterSameResolutionModes(modes, mode);
            }

            DisplayHolder.Mode closerMode = findCloserMode(resultModes, videoWidth, videoFramerate);

            if (closerMode == null) {
                Log.i(TAG, "Could not find closer refresh rate for " + videoFramerate + "fps");
                return false;
            }

            Log.i(TAG, "Found closer framerate: " + closerMode.getRefreshRate() + " for fps " + videoFramerate);
            Log.i(TAG, "Current mode: " + mode);

            if (closerMode.equals(mode)) {
                Log.i(TAG, "Do not need to change mode.");
                return false;
            }
            
            mNewMode = closerMode;
            mUhdHelper.registerModeChangeListener(this);
            mUhdHelper.setPreferredDisplayModeId(window, mNewMode.getModeId(), true);
            mDisplaySyncInProgress = true;
            return true;
        }

        return false;
    }

    /**
     * Lazy init of uhd helper.<br/>
     * Convenient when user doesn't use a afr at all.
     * @return helper
     */
    private UhdHelper getUhdHelper() {
        if (mUhdHelper == null) {
            mUhdHelper = new UhdHelper(mContext);
        }

        return mUhdHelper;
    }

    public void saveOriginalState() {
        DisplayHolder.Mode mode = getUhdHelper().getMode();

        if (mode != null) {
            mOriginalMode = mode;
        }
    }

    public void restoreOriginalState() {
        if (mOriginalMode == null) {
            return;
        }

        getUhdHelper().setPreferredDisplayModeId(
                        mContext.getWindow(),
                        mOriginalMode.getModeId(),
                        true);
    }

    public void saveLastState() {
        DisplayHolder.Mode mode = getUhdHelper().getMode();

        if (mode != null) {
            mLastMode = mode;
        }
    }

    public void restoreLastState() {
        if (mLastMode == null) {
            return;
        }

        getUhdHelper().setPreferredDisplayModeId(
                mContext.getWindow(),
                mLastMode.getModeId(),
                true);
    }
}

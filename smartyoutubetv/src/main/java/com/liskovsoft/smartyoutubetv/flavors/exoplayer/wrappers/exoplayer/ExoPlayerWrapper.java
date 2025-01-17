package com.liskovsoft.smartyoutubetv.flavors.exoplayer.wrappers.exoplayer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.browser.Browser;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.interceptors.ActionsSender;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.interceptors.BackgroundActionManager;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.interceptors.ExoInterceptor;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.interceptors.HistoryInterceptor;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.player.ExoPlayerFragment;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.player.support.SampleHelpers;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.player.support.SampleHelpers.Sample;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.injectors.GenericEventResourceInjector.GenericStringResultEvent;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.parsers.JsonNextParser.VideoMetadata;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.parsers.OnMediaFoundCallback;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.parsers.YouTubeMediaParser.GenericInfo;
import com.liskovsoft.smartyoutubetv.fragments.PlayerListener;
import com.liskovsoft.smartyoutubetv.fragments.TwoFragmentManager;
import com.liskovsoft.smartyoutubetv.misc.myquerystring.MyUrlEncodedQueryString;
import com.squareup.otto.Subscribe;

import java.io.InputStream;
import java.util.List;

public class ExoPlayerWrapper extends OnMediaFoundCallback implements PlayerListener {
    private static final String TAG = ExoPlayerWrapper.class.getSimpleName();
    private final SuggestionsWatcher mReceiver; // don't delete, its system bus receiver
    private final ActionsSender mActionSender;
    private final ExoInterceptor mInterceptor;
    private GenericInfo mInfo;
    private String mSpec;
    private Sample mSample;
    private Uri mTrackingUrl;
    private Uri mRealTrackingUrl;
    private final Context mContext;
    private final TwoFragmentManager mFragmentsManager;
    private final BackgroundActionManager mManager;
    private Intent mCachedIntent;
    private static final String PARAM_VIDEO_ID = "video_id";
    private static final String ACTION_CLOSE_SUGGESTIONS = "action_close_suggestions";
    private static final String ACTION_DISABLE_KEY_EVENTS = "action_disable_key_events";
    private static final long BROWSER_INIT_TIME_MS = 10_000;
    private final Runnable mPauseBrowser;
    private final Handler mHandler;
    private boolean mBlockHandlers;
    private VideoMetadata mMetadata;

    private class SuggestionsWatcher {
        SuggestionsWatcher() {
            Browser.getBus().register(this);
        }

        @Subscribe
        public void onGenericStringResult(GenericStringResultEvent event) {
            String action = event.getResult();

            switch (action) {
                case ACTION_CLOSE_SUGGESTIONS:
                    returnToPlayer();
                case ACTION_DISABLE_KEY_EVENTS:
                    mFragmentsManager.disableKeyEvents();
            }
        }

        private void returnToPlayer() {
            mHandler.post(() -> {
                if (mCachedIntent != null) {
                    Log.d(TAG, "Switching to the running player from suggestions or user's page");
                    prepareAndOpenExoPlayer(null); // player should already be running so pass null
                }
            });
        }
    }

    public ExoPlayerWrapper(Context context, ExoInterceptor interceptor) {
        mContext = context;
        mInterceptor = interceptor;

        mFragmentsManager = interceptor.getFragmentsManager();
        mManager = interceptor.getBackgroundActionManager();

        mReceiver = new SuggestionsWatcher();
        mActionSender = new ActionsSender(context, interceptor);

        // bind onPlayerAction callback
        mFragmentsManager.setPlayerListener(this);

        mPauseBrowser = () -> {
            if (mBlockHandlers) {
                Log.d(TAG, "Browser pause has been canceled");
                return;
            }

            boolean pauseBrowser = !mManager.isMirroring();
            mFragmentsManager.openExoPlayer(null, pauseBrowser);
        };

        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onStart() {
        mBlockHandlers = true;
        clearPendingEvents();
        mFragmentsManager.openExoPlayer(null, false);
    }

    @Override
    public void onDashUrlFound(Uri dashUrl) {
        mSample = SampleHelpers.buildFromMpdUri(dashUrl);
    }

    @Override
    public void onHLSFound(final Uri hlsUrl) {
        mSample = SampleHelpers.buildFromHlsUri(hlsUrl);
    }

    @Override
    public void onDashMPDFound(final InputStream mpdContent) {
        mSample = SampleHelpers.buildFromMPDPlaylist(mpdContent);
    }

    @Override
    public void onUrlListFound(final List<String> urlList) {
        mSample = SampleHelpers.buildFromList(urlList);
    }

    @Override
    public void onInfoFound(GenericInfo info) {
        mInfo = info;
    }

    @Override
    public void onTrackingUrlFound(Uri trackingUrl) {
        mTrackingUrl = trackingUrl;
    }

    @Override
    public void onRealTrackingUrlFound(Uri trackingUrl) {
        mRealTrackingUrl = trackingUrl;
    }

    @Override
    public void onStorySpecFound(String spec) {
        mSpec = spec;
    }

    @Override
    public void onMetadata(VideoMetadata metadata) {
        mMetadata = metadata;
    }

    @Override
    public void onDone() {
        if (mSample == null || mInfo == null) {
            mManager.onCancel();
            mFragmentsManager.openBrowser(true);
            return;
        }

        Log.d(TAG, "Video info has been parsed... opening exoplayer...");

        Intent exoIntent = createExoIntent(mSample, mInfo);
        prepareAndOpenExoPlayer(exoIntent);
        mSample = null;
        mSpec = null;
    }

    private Intent createExoIntent(Sample sample, GenericInfo info) {
        final Intent playerIntent = sample.buildIntent(mContext);
        playerIntent.putExtra(ExoPlayerFragment.VIDEO_TITLE, info.getTitle());
        playerIntent.putExtra(ExoPlayerFragment.VIDEO_AUTHOR, info.getAuthor());
        playerIntent.putExtra(ExoPlayerFragment.VIDEO_VIEW_COUNT, info.getViewCount());
        playerIntent.putExtra(ExoPlayerFragment.VIDEO_ID, extractVideoId());
        playerIntent.putExtra(ExoPlayerFragment.STORYBOARD_SPEC, mSpec);
        mCachedIntent = playerIntent;
        return playerIntent;
    }

    private String extractVideoId() {
        MyUrlEncodedQueryString query = MyUrlEncodedQueryString.parse(mInterceptor.getCurrentUrl());
        return query.get(PARAM_VIDEO_ID);
    }

    private void prepareAndOpenExoPlayer(final Intent playerIntent) {
        mBlockHandlers = false;
        clearPendingEvents();

        if (playerIntent != null) {
            if (mMetadata != null) {
                Helpers.mergeIntents(playerIntent, mMetadata.toIntent());
            }

            mManager.onOpen(); // about to open new video
        }

        mFragmentsManager.openExoPlayer(playerIntent, false); // pause every time, except when mirroring

        // give the browser time to initialization
        mHandler.postDelayed(mPauseBrowser, BROWSER_INIT_TIME_MS);
    }

    @Override
    public void onPlayerAction(Intent intent) {
        mBlockHandlers = true;
        clearPendingEvents();

        boolean showOverlay =
                intent.getBooleanExtra(ExoPlayerFragment.BUTTON_USER_PAGE, false) ||
                intent.getBooleanExtra(ExoPlayerFragment.BUTTON_SUGGESTIONS, false) ||
                intent.getBooleanExtra(ExoPlayerFragment.BUTTON_FAVORITES, false);

        if (!showOverlay) {
            mManager.onClose();
        }

        HistoryInterceptor history = mInterceptor.getHistoryInterceptor();

        if (history != null) {
            history.setPosition(intent.getFloatExtra(ExoPlayerFragment.VIDEO_POSITION, 0));
        }

        mActionSender.bindActions(intent, mMetadata);
    }

    private void clearPendingEvents() {
        mHandler.removeCallbacks(mPauseBrowser);
    }
}

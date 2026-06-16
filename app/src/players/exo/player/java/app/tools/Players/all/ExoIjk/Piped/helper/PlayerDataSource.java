package app.tools.Players.all.ExoIjk.Piped.helper;

import android.content.Context;
import android.util.Log;

import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubePostLiveStreamDvrDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.DefaultDashChunkSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistTracker;
import androidx.media3.exoplayer.smoothstreaming.DefaultSsChunkSource;
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.source.SingleSampleMediaSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import app.tools.Generators.Requirements.Piped.OkHttpDownloader;
import app.tools.Players.all.ExoIjk.Piped.datasource.NonUriHlsDataSourceFactory;
import app.tools.Players.all.ExoIjk.Piped.datasource.YoutubeHttpDataSource;

import java.io.File;

import androidx.annotation.Nullable;


@UnstableApi public class PlayerDataSource {
    public static final boolean DEBUG = true;
    public static final String TAG = PlayerDataSource.class.getSimpleName();

    public static final int LIVE_STREAM_EDGE_GAP_MILLIS = 10000;

    /**
     * An approximately 4.3 times greater value than the
     * {@link DefaultHlsPlaylistTracker#DEFAULT_PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT default}
     * to ensure that (very) low latency livestreams which got stuck for a moment don't crash too
     * early.
     */
    private static final double PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT = 15;

    /**
     * The maximum number of generated manifests per cache, in
     * {@link YoutubeProgressiveDashManifestCreator}, {@link YoutubeOtfDashManifestCreator} and
     * {@link YoutubePostLiveStreamDvrDashManifestCreator}.
     */
    private static final int MAX_MANIFEST_CACHE_SIZE = 500;

    /**
     * The folder name in which the ExoPlayer cache will be written.
     */
    private static final String CACHE_FOLDER_NAME = "exoplayer";

    /**
     * The {@link SimpleCache} instance which will be used to build
     * {@link com.google.android.exoplayer2.upstream.cache.CacheDataSource}s instances (with
     * {@link CacheFactory}).
     */
    private static SimpleCache cache;


    private final int progressiveLoadIntervalBytes;

    // Generic Data Source Factories (without or with cache)
    private final DataSource.Factory cachelessDataSourceFactory;
    private final CacheFactory cacheDataSourceFactory;

    // YouTube-specific Data Source Factories (with cache)
    // They use YoutubeHttpDataSource.Factory, with different parameters each
    private final CacheFactory ytHlsCacheDataSourceFactory;
    private final CacheFactory ytDashCacheDataSourceFactory;
    private final CacheFactory ytProgressiveDashCacheDataSourceFactory;


    public PlayerDataSource(final Context context,
                            final TransferListener transferListener) {

        progressiveLoadIntervalBytes = PlayerHelper.getProgressiveLoadIntervalBytes(context);

        // make sure the static cache was created: needed by CacheFactories below
        instantiateCacheIfNeeded(context);

        // generic data source factories use DefaultHttpDataSource.Factory
        cachelessDataSourceFactory = new DefaultDataSource.Factory(context,
                new DefaultHttpDataSource.Factory().setUserAgent(OkHttpDownloader.USER_AGENT))
                .setTransferListener(transferListener);
        cacheDataSourceFactory = new CacheFactory(context, transferListener, cache,
                new DefaultHttpDataSource.Factory().setUserAgent(OkHttpDownloader.USER_AGENT));

        // YouTube-specific data source factories use getYoutubeHttpDataSourceFactory()
        ytHlsCacheDataSourceFactory = new CacheFactory(context, transferListener, cache,
                getYoutubeHttpDataSourceFactory(false, false));
        ytDashCacheDataSourceFactory = new CacheFactory(context, transferListener, cache,
                getYoutubeHttpDataSourceFactory(true, true));
        ytProgressiveDashCacheDataSourceFactory = new CacheFactory(context, transferListener, cache,
                getYoutubeHttpDataSourceFactory(false, true));

        // set the maximum size to manifest creators
        YoutubeProgressiveDashManifestCreator.getCache().setMaximumSize(MAX_MANIFEST_CACHE_SIZE);
        YoutubeOtfDashManifestCreator.getCache().setMaximumSize(MAX_MANIFEST_CACHE_SIZE);
        YoutubePostLiveStreamDvrDashManifestCreator.getCache().setMaximumSize(
                MAX_MANIFEST_CACHE_SIZE);
    }


    //region Live media source factories
    public SsMediaSource.Factory getLiveSsMediaSourceFactory() {
        return getSSMediaSourceFactory().setLivePresentationDelayMs(LIVE_STREAM_EDGE_GAP_MILLIS);
    }

    /*public HlsMediaSource.Factory getLiveHlsMediaSourceFactory() {
        ErrorCodeApp.Log("HLS-ONN");

        HlsMediaSource.Factory factory = new HlsMediaSource.Factory(
                new DefaultDataSource.Factory(
                        Main.getContext(),
                        new DefaultHttpDataSource.Factory()
                                .setUserAgent(OkHttpDownloader.USER_AGENT)
                                .setAllowCrossProtocolRedirects(true)
                )
        );

        // Configure for real-time live edge playback
        factory.setAllowChunklessPreparation(true);

        return factory;
    }*/

    /*public HlsMediaSource.Factory getLiveHlsMediaSourceFactory() {
        return new HlsMediaSource.Factory(cachelessDataSourceFactory)
                .setAllowChunklessPreparation(true)
                .setPlaylistTrackerFactory((dataSourceFactory, loadErrorHandlingPolicy,
                                            playlistParserFactory) ->
                        new DefaultHlsPlaylistTracker(dataSourceFactory, loadErrorHandlingPolicy,
                                playlistParserFactory,
                                PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT));
    }*/

    public HlsMediaSource.Factory getLiveHlsMediaSourceFactory() {

        // Custom error handling policy - retry more aggressively
        DefaultLoadErrorHandlingPolicy errorPolicy = new DefaultLoadErrorHandlingPolicy() {
            @Override
            public long getRetryDelayMsFor(LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo) {
                // Retry after 1 second instead of giving up
                return 1000;
            }

            @Override
            public int getMinimumLoadableRetryCount(int dataType) {
                // Retry 5 times before failing
                return 5;
            }
        };

        return new HlsMediaSource.Factory(cachelessDataSourceFactory)
                .setAllowChunklessPreparation(true)
                .setLoadErrorHandlingPolicy(errorPolicy)  // Add custom error handling
                .setPlaylistTrackerFactory((dataSourceFactory, loadErrorHandlingPolicy,
                                            playlistParserFactory) ->
                        new DefaultHlsPlaylistTracker(dataSourceFactory, errorPolicy,
                                playlistParserFactory,
                                PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT));
    }

    public DashMediaSource.Factory getLiveDashMediaSourceFactory() {
        return new DashMediaSource.Factory(
                getDefaultDashChunkSourceFactory(cachelessDataSourceFactory),
                cachelessDataSourceFactory);
    }
    //endregion


    //region Generic media source factories
    public HlsMediaSource.Factory getHlsMediaSourceFactory(
            @Nullable final NonUriHlsDataSourceFactory.Builder hlsDataSourceFactoryBuilder) {
        if (hlsDataSourceFactoryBuilder != null) {
            hlsDataSourceFactoryBuilder.setDataSourceFactory(cacheDataSourceFactory);
            return new HlsMediaSource.Factory(hlsDataSourceFactoryBuilder.build());
        }

        return new HlsMediaSource.Factory(cacheDataSourceFactory);
    }

    public DashMediaSource.Factory getDashMediaSourceFactory() {
        return new DashMediaSource.Factory(
                getDefaultDashChunkSourceFactory(cacheDataSourceFactory),
                cacheDataSourceFactory);
    }

    public ProgressiveMediaSource.Factory getProgressiveMediaSourceFactory() {
        return new ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                .setContinueLoadingCheckIntervalBytes(progressiveLoadIntervalBytes);
    }

    public SsMediaSource.Factory getSSMediaSourceFactory() {
        return new SsMediaSource.Factory(
                new DefaultSsChunkSource.Factory(cachelessDataSourceFactory),
                cachelessDataSourceFactory);
    }

    public SingleSampleMediaSource.Factory getSingleSampleMediaSourceFactory() {
        return new SingleSampleMediaSource.Factory(cacheDataSourceFactory);
    }
    //endregion


    //region YouTube media source factories
    public HlsMediaSource.Factory getYoutubeHlsMediaSourceFactory() {
        return new HlsMediaSource.Factory(ytHlsCacheDataSourceFactory);
    }

    public DashMediaSource.Factory getYoutubeDashMediaSourceFactory() {
        return new DashMediaSource.Factory(
                getDefaultDashChunkSourceFactory(ytDashCacheDataSourceFactory),
                ytDashCacheDataSourceFactory);
    }

    public ProgressiveMediaSource.Factory getYoutubeProgressiveMediaSourceFactory() {
        return new ProgressiveMediaSource.Factory(ytProgressiveDashCacheDataSourceFactory)
                .setContinueLoadingCheckIntervalBytes(progressiveLoadIntervalBytes);
    }
    //endregion


    //region Static methods
    private static DefaultDashChunkSource.Factory getDefaultDashChunkSourceFactory(
            final DataSource.Factory dataSourceFactory) {
        return new DefaultDashChunkSource.Factory(dataSourceFactory);
    }

    private static YoutubeHttpDataSource.Factory getYoutubeHttpDataSourceFactory(
            final boolean rangeParameterEnabled,
            final boolean rnParameterEnabled) {
        return new YoutubeHttpDataSource.Factory()
                .setRangeParameterEnabled(rangeParameterEnabled)
                .setRnParameterEnabled(rnParameterEnabled);
    }

    private static void instantiateCacheIfNeeded(final Context context) {
        if (cache == null) {
            final File cacheDir = new File(context.getExternalCacheDir(), CACHE_FOLDER_NAME);
            if (DEBUG) {
                Log.d(TAG, "instantiateCacheIfNeeded: cacheDir = " + cacheDir.getAbsolutePath());
            }
            if (!cacheDir.exists() && !cacheDir.mkdir()) {
                Log.w(TAG, "instantiateCacheIfNeeded: could not create cache dir");
            }

            final LeastRecentlyUsedCacheEvictor evictor =
                    new LeastRecentlyUsedCacheEvictor(PlayerHelper.getPreferredCacheSize());
            cache = new SimpleCache(cacheDir, evictor, new StandaloneDatabaseProvider(context));
        }
    }
    //endregion
}

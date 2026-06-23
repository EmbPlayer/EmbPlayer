package app.tools.Players.all.ExoIjk.Piped.mediasourcebuilders;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.Collections;

import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import app.tools.Players.all.ExoIjk.Piped.ContentTypeDetector;
import app.tools.Players.all.ExoIjk.Piped.helper.PlayerDataSource;
import app.tools.Players.all.ExoIjk.Piped.mediaitem.StreamInfoTag;
import app.tools.Players.all.ExoIjk.Piped.resolver.PlaybackResolver;
import server.web.ErrorCodeApp;

import static app.tools.StaticFunctions.onErrorSave;

/**
 * Abstract base class for MediaSource Builders - No duration required
 */
@UnstableApi public abstract class BaseMediaSourceBuilder {
    protected static final String TAG = "BaseMSB";

    protected PlayerDataSource playerDataSource;
    protected final Context context;

    public BaseMediaSourceBuilder(Context context) {
        this.context = context;
    }

    public void initialize() {
        if (playerDataSource == null) {
            playerDataSource = new PlayerDataSource(context, null);
        }
    }

    // ==================== CORE METHODS ====================

    /**
     * Build MediaSource from given info
     */
    public MediaSource buildMediaSource(String url, boolean isLive) {
        return buildMediaSource(url, isLive, false);
    }

    /**
     * Build MediaSource with video-only flag
     */
    public MediaSource buildMediaSource(String url, boolean isLive, boolean isVideoOnly) {

        try {
            initialize();

            // Create metadata
            StreamInfoTag metadata = createMetadataFromInfo(url, isLive);
            if(isLive)
            {
                return PlaybackResolver.buildLiveMediaSource(playerDataSource,url, /*C.CONTENT_TYPE_HLS*/ ContentTypeDetector.detectContentType(url),metadata);
            }

            // Create Stream object from given info
            Stream stream = createStreamFromInfo(url, isLive, isVideoOnly);


            // Generate cache key from URL
            String cacheKey = String.valueOf(url.hashCode());

            // Use appropriate resolver
            return resolveMediaSource(stream, metadata, cacheKey);

        } catch (Exception e) {
            onErrorSave("BaseMediaSourceBuilder",e);

            return buildFallback(url, isLive);
        }
    }

    /**
     * Merge video and audio streams from given info
     */
    public MediaSource mergeStreams(String videoUrl, String audioUrl, boolean isLive) {
        try {
            initialize();

            // Build video-only source
            MediaSource videoSource = buildMediaSource(videoUrl, isLive, true);

            // Build audio source
            MediaSource audioSource = buildMediaSource(audioUrl, isLive, false);

            // Merge them
            return new MergingMediaSource(videoSource, audioSource);

        } catch (Exception e) {
            onErrorSave("BaseMediaSourceBuilder-Streams merge failed",e);
            //Log.e(TAG, "Streams merge failed", e);
            return buildFallback(videoUrl, isLive);
        }
    }

    // ==================== ABSTRACT METHODS ====================

    /**
     * Create Stream object from given info
     */
    protected abstract Stream createStreamFromInfo(String url, boolean isLive, boolean isVideoOnly);

    /**
     * Create metadata from given info
     */
    protected abstract StreamInfoTag createMetadataFromInfo(String url, boolean isLive);

    /**
     * Detect delivery method from URL
     */
    protected abstract DeliveryMethod detectDeliveryMethod(String url, boolean isLive);

    /**
     * Detect media format from URL
     */
    protected abstract MediaFormat detectMediaFormat(String url, boolean isVideoOnly);

    /**
     * Resolve the media source using appropriate resolver
     */
    protected abstract MediaSource resolveMediaSource(Stream stream, StreamInfoTag metadata, String cacheKey);

    /**
     * Build fallback media source when primary method fails
     */
    protected abstract MediaSource buildFallback(String url, boolean isLive);

    // ==================== COMMON HELPER METHODS ====================

    /**
     * Common video format detection
     */
    protected MediaFormat detectVideoFormat(String url) {
        String lowerUrl = url.toLowerCase();

        if (lowerUrl.contains("mime=video/mp4") || lowerUrl.contains(".mp4") ||
                lowerUrl.contains("/mp4/") || lowerUrl.contains("type=video/mp4")) {
            return MediaFormat.MPEG_4;
        } else if (lowerUrl.contains("mime=video/webm") || lowerUrl.contains(".webm") ||
                lowerUrl.contains("/webm/") || lowerUrl.contains("type=video/webm")) {
            return MediaFormat.WEBM;
        } else if (lowerUrl.contains("mime=video/3gpp") || lowerUrl.contains(".3gp") ||
                lowerUrl.contains(".3gpp") || lowerUrl.contains("type=video/3gpp")) {
            return MediaFormat.v3GPP;
        } else {
            return MediaFormat.MPEG_4;
        }
    }

    /**
     * Common audio format detection
     */
    protected MediaFormat detectAudioFormat(String url) {
        String lowerUrl = url.toLowerCase();

        if (lowerUrl.contains("mime=audio/mp4") || lowerUrl.contains(".m4a") ||
                lowerUrl.contains("type=audio/mp4") || lowerUrl.contains("/m4a/")) {
            return MediaFormat.M4A;
        } else if (lowerUrl.contains("mime=audio/webm") || lowerUrl.contains(".webm") ||
                lowerUrl.contains("type=audio/webm") || lowerUrl.contains("/webma/")) {
            if (lowerUrl.contains("opus") || lowerUrl.contains("codecs=opus")) {
                return MediaFormat.WEBMA_OPUS;
            } else {
                return MediaFormat.WEBMA;
            }
        } else if (lowerUrl.contains("mime=audio/mpeg") || lowerUrl.contains(".mp3") ||
                lowerUrl.contains("type=audio/mpeg")) {
            return MediaFormat.MP3;
        } else if (lowerUrl.contains("mime=audio/ogg") || lowerUrl.contains(".ogg") ||
                lowerUrl.contains("type=audio/ogg")) {
            return MediaFormat.OGG;
        } else if (lowerUrl.contains("mime=audio/opus") || lowerUrl.contains(".opus") ||
                lowerUrl.contains("type=audio/opus")) {
            return MediaFormat.OPUS;
        } else if (lowerUrl.contains("mime=audio/x-flac") || lowerUrl.contains(".flac") ||
                lowerUrl.contains("type=audio/flac")) {
            return MediaFormat.FLAC;
        } else if (lowerUrl.contains("mime=audio/wav") || lowerUrl.contains(".wav") ||
                lowerUrl.contains("type=audio/wav")) {
            return MediaFormat.WAV;
        } else if (lowerUrl.contains("mime=audio/aiff") || lowerUrl.contains(".aiff") ||
                lowerUrl.contains(".aif") || lowerUrl.contains("type=audio/aiff")) {
            return MediaFormat.AIFF;
        } else if (lowerUrl.contains("mime=audio/alac") || lowerUrl.contains(".alac") ||
                lowerUrl.contains("type=audio/alac")) {
            return MediaFormat.ALAC;
        } else if (lowerUrl.contains("mime=audio/mpeg") || lowerUrl.contains(".mp2") ||
                lowerUrl.contains("mpeg layer 2")) {
            return MediaFormat.MP2;
        } else {
            return MediaFormat.M4A;
        }
    }

    /**
     * Common fallback implementation
     */
    protected MediaSource buildCommonFallback(String url, boolean isLive, String cacheKeyPrefix) {
        try {
            initialize();

            MediaItem.Builder builder = new MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .setCustomCacheKey(cacheKeyPrefix + url.hashCode());

            if (isLive) {
                builder.setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(PlayerDataSource.LIVE_STREAM_EDGE_GAP_MILLIS)
                                .build());
            }

            // Use appropriate factory based on URL
            String lowerUrl = url.toLowerCase();
            if (lowerUrl.contains(".m3u8") || lowerUrl.contains("/hls/")) {

                return playerDataSource.getHlsMediaSourceFactory(null)
                        .createMediaSource(builder.build());
            } else if (lowerUrl.contains(".mpd") || lowerUrl.contains("/dash/")) {
                return playerDataSource.getDashMediaSourceFactory()
                        .createMediaSource(builder.build());
            } else {
                return playerDataSource.getProgressiveMediaSourceFactory()
                        .createMediaSource(builder.build());
            }

        } catch (Exception e) {
            onErrorSave("BaseMediaSourceBuilder-Common fallback failed",e);
            //Log.e(TAG, "Common fallback failed", e);
            // Create a simple media source
            MediaItem mediaItem = new MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .build();
            return playerDataSource.getProgressiveMediaSourceFactory()
                    .createMediaSource(mediaItem);
        }
    }

    // ==================== COMMON HELPER CLASSES ====================

    /**
     * Simple StreamInfo implementation (no duration)
     */
    protected static class SimpleStreamInfo extends org.schabi.newpipe.extractor.stream.StreamInfo {
        public SimpleStreamInfo(
                int serviceId,
                String url,
                String originalUrl,
                StreamType streamType,
                String id,
                String name,
                int ageLimit,
                String uploaderName
        ) {
            super(serviceId, url, originalUrl, streamType, id, name, ageLimit);

            // Initialize empty lists
            this.setAudioStreams(Collections.emptyList());
            this.setVideoStreams(Collections.emptyList());
            this.setVideoOnlyStreams(Collections.emptyList());
            this.setSubtitles(Collections.emptyList());
            this.setRelatedItems(Collections.emptyList());
            this.setUploaderName(uploaderName);
            this.setThumbnails(Collections.emptyList());
        }

        @Override
        public long getDuration() {
            return 0; // Duration not required
        }
    }

    /**
     * Base Stream wrapper
     */
    protected static abstract class BaseStreamWrapper extends Stream {
        protected final String url;
        protected final DeliveryMethod deliveryMethod;
        protected final boolean isVideoOnly;
        protected final MediaFormat mediaFormat;

        public BaseStreamWrapper(String url, DeliveryMethod deliveryMethod,
                                 boolean isVideoOnly, MediaFormat mediaFormat) {
            super(
                    String.valueOf(url.hashCode()), // id
                    url, // content
                    true, // isUrl
                    mediaFormat != null ? mediaFormat : (isVideoOnly ? MediaFormat.MPEG_4 : MediaFormat.M4A),
                    deliveryMethod,
                    url // manifestUrl
            );
            this.url = url;
            this.deliveryMethod = deliveryMethod;
            this.isVideoOnly = isVideoOnly;
            this.mediaFormat = mediaFormat;
        }

        // Additional helper methods
        public String getUrl() {
            return url;
        }

        public boolean isVideoOnly() {
            return isVideoOnly;
        }

        public boolean isAudio() {
            return url.toLowerCase().contains("mime=audio");
        }
    }

    public PlayerDataSource getPlayerDataSource() {
        initialize();
        return playerDataSource;
    }

    public void release() {
        playerDataSource = null;
    }
}
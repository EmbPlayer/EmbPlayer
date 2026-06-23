package app.tools.Players.all.ExoIjk.Piped.mediasourcebuilders;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamType;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.MediaSource;
import app.tools.Players.all.ExoIjk.Piped.helper.PlayerDataSource;
import app.tools.Players.all.ExoIjk.Piped.mediaitem.StreamInfoTag;
import app.tools.Players.all.ExoIjk.Piped.resolver.PlaybackResolver;

import static app.tools.StaticFunctions.onErrorSave;


/**
 * Optimized YouTube MediaSource Builder - No duration required
 */
@UnstableApi public class YouTubeMediaSourceBuilder extends BaseMediaSourceBuilder {
    private static final String TAG = "YouTubeMSB";

    public YouTubeMediaSourceBuilder(Context context) {
        super(context);
    }

    // ==================== ABSTRACT METHOD IMPLEMENTATIONS ====================

    @Override
    protected Stream createStreamFromInfo(String url, boolean isLive, boolean isVideoOnly) {
        // Determine delivery method from URL
        DeliveryMethod deliveryMethod = detectDeliveryMethod(url, isLive);

        // Extract itag from URL if present
        ItagItem itagItem = extractItagFromUrl(url);

        // Try to detect media format from URL
        MediaFormat mediaFormat = detectMediaFormat(url, itagItem, isVideoOnly);

        return new YouTubeStreamWrapper(url, deliveryMethod, itagItem, isVideoOnly, mediaFormat);
    }

    @Override
    protected StreamInfoTag createMetadataFromInfo(String url, boolean isLive) {
        // Create a custom StreamInfo implementation
        SimpleStreamInfo streamInfo = new SimpleStreamInfo(
                ServiceList.YouTube.getServiceId(),
                url,
                url,
                isLive ? StreamType.LIVE_STREAM : StreamType.VIDEO_STREAM,
                String.valueOf(url.hashCode()),
                "YouTube Stream",
                18, // age limit
                "YouTube"
        );

        return StreamInfoTag.of(streamInfo);
    }

    @Override
    protected DeliveryMethod detectDeliveryMethod(String url, boolean isLive) {
        String lowerUrl = url.toLowerCase();

        if (lowerUrl.contains(".m3u8") || lowerUrl.contains("/hls/")) {
            return DeliveryMethod.HLS;
        } else if (lowerUrl.contains(".mpd") || lowerUrl.contains("/dash/")) {
            return DeliveryMethod.DASH;
        } else if (lowerUrl.contains("otf=") || lowerUrl.contains("sq=")) {
            return DeliveryMethod.DASH; // OTF
        } else {
            return DeliveryMethod.PROGRESSIVE_HTTP;
        }
    }

    @Override
    protected MediaFormat detectMediaFormat(String url, boolean isVideoOnly) {
        // YouTube-specific detection without ItagItem
        return detectMediaFormat(url, null, isVideoOnly);
    }

    @Override
    protected MediaSource resolveMediaSource(Stream stream, StreamInfoTag metadata, String cacheKey) {
        try {
            // Direct implementation using YouTube-specific factories
            return buildYoutubeMediaSourceDirect(stream, metadata, cacheKey);
        } catch (Exception e) {
            onErrorSave("YouTubeMediaSourceBuilder-buildYoutubeMediaSourceDirect",e);
            // Fallback to PlaybackResolver
            //Log.w(TAG, "Direct YouTube media source build failed, falling back to PlaybackResolver", e);
            try {
                return PlaybackResolver.createYoutubeMediaSource(
                        stream, metadata.getMaybeStreamInfo().get(), playerDataSource,
                        cacheKey, metadata);
            } catch (Exception e2) {
                throw new RuntimeException("Failed to resolve YouTube media source", e2);
            }
        }
    }

    @Override
    protected MediaSource buildFallback(String url, boolean isLive) {
        // Use YouTube-specific fallback
        return buildYoutubeFallback(url, isLive);
    }

    // ==================== DIRECT YOUTUBE MEDIA SOURCE BUILDING ====================

    @OptIn(markerClass = UnstableApi.class) private MediaSource buildYoutubeMediaSourceDirect(Stream stream, StreamInfoTag metadata, String cacheKey) {
        initialize();

        DeliveryMethod deliveryMethod = stream.getDeliveryMethod();
        MediaItem mediaItem = new MediaItem.Builder()
                .setTag(metadata)
                .setUri(Uri.parse(stream.getContent()))
                .setCustomCacheKey(cacheKey)
                .build();

        switch (deliveryMethod) {
            case HLS:
                // Use YouTube HLS factory
                return playerDataSource.getYoutubeHlsMediaSourceFactory()
                        .createMediaSource(mediaItem);

            case DASH:
                // Use YouTube DASH factory
                return playerDataSource.getYoutubeDashMediaSourceFactory()
                        .createMediaSource(mediaItem);

            case PROGRESSIVE_HTTP:
                // Use YouTube Progressive factory
                if (stream instanceof YouTubeStreamWrapper) {
                    YouTubeStreamWrapper youtubeStream = (YouTubeStreamWrapper) stream;
                    if (youtubeStream.isVideoOnly() || youtubeStream.isAudio()) {
                        // For video-only or audio streams, use YouTube progressive factory
                        return playerDataSource.getYoutubeProgressiveMediaSourceFactory()
                                .createMediaSource(mediaItem);
                    }
                }
                // Fallback to regular progressive
                return playerDataSource.getYoutubeProgressiveMediaSourceFactory()
                        .createMediaSource(mediaItem);

            default:
                throw new RuntimeException("Unsupported delivery method for YouTube: " + deliveryMethod);
        }
    }

    // ==================== YOUTUBE-SPECIFIC FALLBACK ====================

    @OptIn(markerClass = UnstableApi.class) private MediaSource buildYoutubeFallback(String url, boolean isLive) {
        try {
            initialize();

            MediaItem.Builder builder = new MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .setCustomCacheKey("yt_fb_" + url.hashCode());

            if (isLive) {
                builder.setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(PlayerDataSource.LIVE_STREAM_EDGE_GAP_MILLIS)
                                .build());
            }

            MediaItem mediaItem = builder.build();

            // Use YouTube-specific factories for fallback
            String lowerUrl = url.toLowerCase();
            if (lowerUrl.contains(".m3u8") || lowerUrl.contains("/hls/")) {
                return playerDataSource.getYoutubeHlsMediaSourceFactory()
                        .createMediaSource(mediaItem);
            } else if (lowerUrl.contains(".mpd") || lowerUrl.contains("/dash/")) {
                return playerDataSource.getYoutubeDashMediaSourceFactory()
                        .createMediaSource(mediaItem);
            } else {
                return playerDataSource.getYoutubeProgressiveMediaSourceFactory()
                        .createMediaSource(mediaItem);
            }

        } catch (Exception e) {
            Log.e(TAG, "YouTube fallback failed", e);
            // Ultimate fallback
            MediaItem mediaItem = new MediaItem.Builder()
                    .setUri(Uri.parse(url))
                    .build();
            return playerDataSource.getYoutubeProgressiveMediaSourceFactory()
                    .createMediaSource(mediaItem);
        }
    }

    // ==================== YOUTUBE-SPECIFIC METHODS ====================

    private ItagItem extractItagFromUrl(String url) {
        try {
            int start = url.indexOf("itag=");
            if (start != -1) {
                start += 5;
                int end = url.indexOf('&', start);
                String itagStr = end == -1 ? url.substring(start) : url.substring(start, end);
                return ItagItem.getItag(Integer.parseInt(itagStr));
            }
        } catch (ParsingException | NumberFormatException e) {
            // Ignore, return null
        }
        return null;
    }

    private MediaFormat detectMediaFormat(String url, ItagItem itagItem, boolean isVideoOnly) {
        // First try to get format from itag item (for YouTube)
        if (itagItem != null && itagItem.getMediaFormat() != null) {
            return itagItem.getMediaFormat();
        }

        // Fall back to common detection
        return isVideoOnly ? detectVideoFormat(url) : detectAudioFormat(url);
    }

    // ==================== YOUTUBE-SPECIFIC HELPER CLASS ====================

    /**
     * YouTube-specific Stream wrapper
     */
    private static class YouTubeStreamWrapper extends BaseStreamWrapper {
        private final ItagItem itagItem;

        public YouTubeStreamWrapper(String url, DeliveryMethod deliveryMethod,
                                    ItagItem itagItem, boolean isVideoOnly, MediaFormat mediaFormat) {
            super(url, deliveryMethod, isVideoOnly, mediaFormat);
            this.itagItem = itagItem;
        }

        @Override
        public ItagItem getItagItem() {
            return itagItem;
        }
    }
}
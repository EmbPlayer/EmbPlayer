package app.tools.Players.all.ExoIjk.Piped.mediasourcebuilders;

import android.content.Context;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamType;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.MediaSource;
import app.tools.Players.all.ExoIjk.Piped.mediaitem.StreamInfoTag;
import app.tools.Players.all.ExoIjk.Piped.resolver.PlaybackResolver;

/**
 * Optimized Generic MediaSource Builder - No duration required
 */
@UnstableApi public class GenericMediaSourceBuilder extends BaseMediaSourceBuilder {
    private static final String TAG = "GenericMSB";

    public GenericMediaSourceBuilder(Context context) {
        super(context);
    }

    // ==================== ABSTRACT METHOD IMPLEMENTATIONS ====================

    @Override
    protected Stream createStreamFromInfo(String url, boolean isLive, boolean isVideoOnly) {
        // Determine delivery method from URL
        DeliveryMethod deliveryMethod = detectDeliveryMethod(url, isLive);

        // Try to detect media format from URL
        MediaFormat mediaFormat = detectMediaFormat(url, isVideoOnly);

        return new GenericStreamWrapper(url, deliveryMethod, isVideoOnly, mediaFormat);
    }

    @Override
    protected StreamInfoTag createMetadataFromInfo(String url, boolean isLive) {
        // Create a simple StreamInfo with custom service ID
        SimpleStreamInfo streamInfo = new SimpleStreamInfo(
                1, // Custom service ID for generic streams
                url,
                url,
                isLive ? StreamType.LIVE_STREAM : StreamType.VIDEO_STREAM,
                String.valueOf(url.hashCode()),
                "Stream",
                18, // age limit
                "Stream Provider"
        );

        return StreamInfoTag.of(streamInfo);
    }

    @Override
    protected DeliveryMethod detectDeliveryMethod(String url, boolean isLive) {
        String lowerUrl = url.toLowerCase();

        if (lowerUrl.contains(".m3u8") || lowerUrl.contains("/hls/"))
            return DeliveryMethod.HLS;
        else if (lowerUrl.contains(".mpd") || lowerUrl.contains("/dash/"))
            return DeliveryMethod.DASH;
        else if (lowerUrl.contains(".ism") || lowerUrl.contains("/ss/"))
            return DeliveryMethod.SS;
        else if(isLive)
            return DeliveryMethod.HLS;
        else
            return DeliveryMethod.PROGRESSIVE_HTTP;
    }

    @Override
    protected MediaFormat detectMediaFormat(String url, boolean isVideoOnly) {
        return isVideoOnly ? detectVideoFormat(url) : detectAudioFormat(url);
    }

    @Override
    protected MediaSource resolveMediaSource(Stream stream, StreamInfoTag metadata, String cacheKey) {
        try {
            return PlaybackResolver.buildMediaSource(
                    playerDataSource, stream, metadata.getMaybeStreamInfo().get(),
                    cacheKey, metadata);
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve generic media source", e);
        }
    }

    @Override
    protected MediaSource buildFallback(String url, boolean isLive) {
        // Use generic fallback
        return buildCommonFallback(url, isLive, "gen_fb_");
    }

    // ==================== GENERIC-SPECIFIC HELPER CLASS ====================

    /**
     * Generic Stream wrapper
     */
    private static class GenericStreamWrapper extends BaseStreamWrapper {
        public GenericStreamWrapper(String url, DeliveryMethod deliveryMethod,
                                    boolean isVideoOnly, MediaFormat mediaFormat) {
            super(url, deliveryMethod, isVideoOnly, mediaFormat);
        }

        @Override
        public org.schabi.newpipe.extractor.services.youtube.ItagItem getItagItem() {
            return null; // Not applicable for generic streams
        }
    }
}
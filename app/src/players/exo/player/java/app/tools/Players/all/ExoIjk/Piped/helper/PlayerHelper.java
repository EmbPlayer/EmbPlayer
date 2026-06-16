package app.tools.Players.all.ExoIjk.Piped.helper;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

public final class PlayerHelper {

    public static long getPreferredCacheSize() {
        return 64 * 1024 * 1024L;
    }

    public static long getPreferredFileSize() {
        return 2 * 1024 * 1024L; // ExoPlayer CacheDataSink.MIN_RECOMMENDED_FRAGMENT_SIZE
    }
    @OptIn(markerClass = UnstableApi.class) public static int getProgressiveLoadIntervalBytes(@NonNull final Context context) {

        return ProgressiveMediaSource.DEFAULT_LOADING_CHECK_INTERVAL_BYTES;
        /*


        <item>1</item>
        <item>16</item>
        <item>@string/progressive_load_interval_default_value</item>
        <item>256</item>
        <item>@string/progressive_load_interval_exoplayer_default_value</item>

        * */

        // Keeping the same KiB unit used by ProgressiveMediaSource
        //return Integer.parseInt(preferredIntervalBytes) * 1024;
    }
}

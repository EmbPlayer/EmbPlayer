/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright 2026-present Emre Hyuseinov (plaxir) <plaxirstudio@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.tools.Players.all.Ijk;


import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.net.Uri;
import android.view.SurfaceHolder;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;

import androidx.annotation.CallSuper;
import app.EmptyActivity;
import app.tools.Generators.Requirements.Piped.OkHttpDownloader;
import app.tools.Players.all.Player;
import server.tools.MediaProxyServlet;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.misc.IMediaDataSource;

import static app.tools.DisposableTools.addTask;
import static app.tools.DisposableTools.ioThreadPoolScheduler;
import static app.tools.DisposableTools.waitMS;
import static app.tools.StaticFunctions.onErrorSave;

public abstract class Ijk extends Player
{
    protected IjkMediaPlayer media;

    public Ijk(boolean videoOnly, int colorFormatIndex) {
        super(videoOnly);
        newMedia();
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", colorFormatIndex);
    }

    public Ijk(boolean videoOnly)
    {
        super(videoOnly);
        newMedia();
    }

   /* public void VAudio()
    {
        preset = () -> {

            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0);

            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 0);

            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 0);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "max-buffer-size", 2 * 1024 * 1024);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 15000L);

            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 0);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 0);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 1);
        };
    }

    public void VLive()
    {
        preset = () -> {

            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", 0);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 0);

            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 0);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1);

            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "max-buffer-size", 4 * 1024 * 1024);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 20000L);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 30000000);

            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-fps", 25);

            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 0);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 0);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 1);
        };
    }

    void V7()
    {
        preset = () -> {
            // 🚀 Increase buffering for smoother playback
            //media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "max-buffer-size", 10 * 1024 * 1024); // 10MB buffer
            //media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 5000000); // 5 seconds for stream analysis
            //media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 50000); // Increase probing size

// 🚀 Ensure buffering before playback starts
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1);
            // Wait until buffered
            //media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 10); // Pre-buffer 10 frames before starting
            //media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "buffering-time", 3000); // Buffer at least 3s before playing

// 🚀 Frame skipping (adjustable)
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 2); // Drop frames if decoding is too slow
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_frame", 8); // Skip non-reference frames

// 🚀 Use all CPU cores for fast decoding
            int availableCores = Runtime.getRuntime().availableProcessors();
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "threads", availableCores);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "fast", 1);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1);
        };
    }

    void V5()
    {
        preset = () -> {
            // Basic setup
//media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", "crypto,file,http,https,tcp,tls,udp,rtmp,rtsp,hls");
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0);

// Audio-only mode (disable video processing completely)
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "vn", 1);  // Disable video decoding
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "sn", 1);  // Disable subtitles

// Performance (audio-focused)
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 0);  // Disabled for audio-only
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-fps", 0);    // Disabled for audio-only

// Disable hardware acceleration (not needed for audio)
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 0);

// Network
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 30*1000*1000);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);
        };
    }

    void V4()
    {
        preset = () -> {
            // Basic setup
            //media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", "crypto,file,http,https,tcp,tls,udp,rtmp,rtsp,hls");
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0);

// Performance
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 5);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-fps", 30);

// Hardware acceleration
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1);

// Network
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 30*1000*1000);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);
        };
    }

    void V2M(Context context)
    {
        preset = () -> {
            // Apply optimized preset based on device capabilities
            AdaptiveIjkPresetManager.DeviceCapabilities deviceCapabilities = AdaptiveIjkPresetManager.analyzeDeviceCapabilities(context);
            AdaptiveIjkPresetManager.applyOptimizedPreset(media, context);
        };
    }

    void VClock()
    {
        preset = () ->
        {
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "sync-max-gap", "0.1");

            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "sync", "ext");

            // --- NO "sync: ext" HERE! Audio must use its own reliable clock. ---
*//*
// --- FAST STARTUP (Matching Video) ---
                media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 1L);
                media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 32L);

// --- STABLE MINIMAL BUFFER ---
                media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 128L);*//*
        };
    }

    void V2M()
    {
        preset = () -> {
            // Apply optimized preset based on device capabilities
                *//*AdaptiveIjkPresetManager.DeviceCapabilities deviceCapabilities = AdaptiveIjkPresetManager.analyzeDeviceCapabilities(context);
                AdaptiveIjkPresetManager.applyOptimizedPreset(media, context);*//*

            // Conservative but capable settings for 1080p
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 12); // Moderate frame dropping
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-fps", 30);   // 30FPS is smooth for 1080p
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fps-probe-size", 8);

            // Optimize for single-core rendering (Mali-400 is single cluster)
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "threads", 1);

            // Audio - use basic audio driver
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0);

            // Network settings optimized for 1080p streaming - BUFFERING VALUES x4
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 400000L); // 100000L * 4
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 20L); // 5L * 4
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "max-buffer-size", 32 * 1024 * 1024); // 8MB * 4 = 32MB

            // Buffer optimization for stable 1080p - BUFFERING VALUES x4
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 12); // 3 * 4
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 200); // 50 * 4

            // Reduce CPU load for software fallback
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "skip_loop_filter", 16);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "skip_frame", 0);

            // Enable 1080p support
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-resolution", "1920x1080");

            // Fast seek for better UX
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 0);
        };
    }

    void V2()
    {
        preset = () -> {
            // 🚀 Increase buffering for smoother playback
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "max-buffer-size", 10 * 1024 * 1024); // 10MB buffer
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 5000000); // 5 seconds for stream analysis
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 50000); // Increase probing size

// 🚀 Ensure buffering before playback starts
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1);
            // Wait until buffered
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 10); // Pre-buffer 10 frames before starting
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "buffering-time", 3000); // Buffer at least 3s before playing

// 🚀 Frame skipping (adjustable)
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 2); // Drop frames if decoding is too slow
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_frame", 8); // Skip non-reference frames

// 🚀 Use all CPU cores for fast decoding
            int availableCores = Runtime.getRuntime().availableProcessors();
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "threads", availableCores);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "fast", 1);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1);
        };
    }*/

    /*
    private void V1()
    {
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1);
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 5);
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "max-buffer-size", 2 * 1024 * 1024);
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 1000000);
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 25000);

// 🚀 Enable frame skipping (no real-time delay)
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 8); // Drop frames if decoding is too slow
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_frame", 8); // Skip non-reference frames

// 🚀 Use all CPU cores for fast decoding
        int availableCores = Runtime.getRuntime().availableProcessors();
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "threads", availableCores);
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "fast", 1);
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);
    }

    private void V3()
    {
        //media.native_setLogLevel(IjkMediaPlayer.IJK_LOG_DEBUG);

        // Reduce analysis duration (similar to -analyzeduration 0)
        media.setOption(media.OPT_CATEGORY_FORMAT, "analyzeduration", 0);

// Reduce probe size (similar to -probesize 32)
        media.setOption(media.OPT_CATEGORY_FORMAT, "probesize", 32);

// Disable packet buffering (like -fflags nobuffer)
        media.setOption(media.OPT_CATEGORY_PLAYER, "packet-buffering", 0);

// Enable frame dropping (like -framedrop)
        media.setOption(media.OPT_CATEGORY_PLAYER, "framedrop", 1);

// Low delay optimization (like -flags low_delay)
        media.setOption(media.OPT_CATEGORY_PLAYER, "fast", 1);

// Skip loop filter for faster decode (optional for performance)
        media.setOption(media.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);

    }*/

    @Override
    public void basePreset()
    {
        preset = () ->
        {/*
                media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "sync", "ext");

// --- EXTREME SYNCHRONIZATION ---
                media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "sync-max-gap", "0.02"); // 20ms tolerance
                media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 50L);      // Aggressive frame dropping
                media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 1L);       // Minimal frames needed
                media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1L);  // Minimal buffer size

// --- EXTREME DECODING SPEED ---
                media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fast", 1L);
                media.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48L); // Fastest decoding, some quality loss
                media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "video-wait", 0L);      // Don't wait for video frames

// --- EXTREME NETWORK STARTUP ---
                media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 1L); // 1 microsecond analysis
                media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 32L);      // Minimal probe*/
/*
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "sync-max-gap", "0.1");

            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "sync", "ext");*/

            // Frame rate control
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 25L);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fps", 25L);

            // Buffer control (minimal)
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 100L); // 100KB
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 0L);
/*
            // Don't wait for sync
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "sync-av-start", 0L);*/

            // Decoder optimizations
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0L);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fast", 1L);

            // Network optimizations
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek");
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 100000L);
        };
    }
    public void livePreset()
    {
        //preset = () -> media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "live_start_index", -1);
        //VLive();
        //V3M();
        /*preset = () -> {
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);

            // Basic buffer settings
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 400L);

            // Fast probe
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 100L);
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 512L);

            // That's it - minimal settings that should work
        };*/

        /*int tryc = SData.GetInt(SData.Data.SevedCorrect)+1;
        SData.SetInt(SData.Data.SevedCorrect,tryc);
        switch (tryc)
        {
            case 1:
                preset = () -> {
                    // Essential for HLS streams to start
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);

                    // Buffer settings (balanced for live streams)
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 512L); // 512KB
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1); // Infinite buffer for live streams

                    // Frame dropping for smooth playback
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 5);

                    // Fast startup for HLS
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 100L);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 1024L);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek");

                    // HLS specific optimizations
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "multiple_requests", 1);

                    // Network resilience
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_at_eof", 1);

                    // Hardware acceleration
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1);

                    // Audio specific
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0);
                };
                break;
            case 2:
                preset = () -> {
                    // CRITICAL: This must be set for live streams to start
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);

                    // Balanced buffer settings
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 300L); // 300KB
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1); // Infinite buffer

                    // Moderate frame dropping
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 10);

                    // Fast analysis
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 50000L); // 50ms
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 512L);

                    // HLS specific
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "multiple_requests", 1);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0);

                    // Enable fast decoding but not too aggressive
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fast", 1);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 8); // Moderate skipping

                    // Auto reconnect
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);
                };
                break;
            case 3:
                preset = () -> {
                    // ABSOLUTELY ESSENTIAL - without this, live streams won't start
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);

                    // Basic buffer settings
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 400L);

                    // Fast probe
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 100L);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 512L);

                    // That's it - minimal settings that should work
                };
                break;
            case 4:
                preset = () -> {
                    // Critical for immediate start
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);

                    // Minimal buffering for low latency
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 200L); // 200KB
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1);

                    // Fast analysis
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 50000L); // 50ms
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 256L);

                    // Aggressive frame dropping
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 15);

                    // Fast decoding
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fast", 1);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);

                    // HLS optimizations
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "multiple_requests", 1);
                };
                break;
            case 5:
                preset = () -> {
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);

                    // Larger buffer for stability
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 800L); // 800KB
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1);

                    // Conservative frame dropping
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 3);

                    // Network resilience
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_at_eof", 1);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_on_network_error", 1);

                    // Standard analysis
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 100000L); // 100ms
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 1024L);

                    // Hardware decoding
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", 1);
                };
                break;
            case 6:
                preset = () -> {
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);

                    // Balanced buffer
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 400L);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1);

                    // Moderate frame control
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 8);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0);

                    // Fast analysis
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 75000L);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 512L);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek");

                    // Decoder optimizations
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fast", 1);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 16);

                    // HLS specific
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "multiple_requests", 1);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0);
                };
                break;
            case 7:
                preset = () -> {
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);

                    // Large buffer for smooth playback
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 1200L); // 1.2MB
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1);

                    // Minimal frame dropping
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1);

                    // Pre-buffer more frames
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 25);

                    // Network optimizations
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_delay_max", 60);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 60000000); // 60 seconds

                    // Standard analysis
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 200000L);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 2048L);
                };
                break;
            case 8:
                preset = () -> {
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);

                    // Adaptive buffer for mobile networks
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 300L);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1);

                    // Aggressive network recovery
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_on_network_error", 1);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_delay_max", 30);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 30000000); // 30 seconds

                    // Fast resume
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek");
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "seekable", 0);

                    // Balanced frame dropping
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 10);

                    // Fast analysis
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 50000L);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 256L);
                };
                break;
            case 9:
                preset = () -> {
                    // Only the absolute essentials
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 500L);
                    media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 100000L);
                };
                break;
        }*/
    }

    public void startOnPrepared()
    {
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);
    }

    @Override
    public long getDuration()
    {
        if(isNull())
            return 0;

        return media.getDuration();
    }

    @Override
    public void seekTo(long seek)
    {
        media.seekTo(seek);
    }

    @Override
    public void audioNormal()
    {
        media.setAudioStreamType(AudioManager.STREAM_MUSIC);
    }

    @Override
    @CallSuper
    public void start()
    {
        media.start();
    }

    public final void start(long seek)
    {
        if(onEndTriggered(seek))
            return;
        afterCheckingStart(seek);
    }

    @CallSuper
    protected void afterCheckingStart(long seek){
        seekTo(seek);
        media.start();
    }

    @Override
    public boolean isPlaying()
    {
        return media.isPlaying();
    }

    @Override
    public boolean isNull()
    {
        return media==null;
    }

    @Override
    public void dontSleep(boolean on)
    {
        media.setScreenOnWhilePlaying(on);
    }

    @CallSuper
    public void modifySetDisplaySurface(SurfaceHolder holder)
    {
        media.setDisplay(holder);
    }

    @Override
    @CallSuper
    public void modifyNullDisplay() {
        media.setDisplay(null);
    }

    @Override
    public void pause()
    {
        media.pause();
    }

    @Override
    public long getCurrentPosition()
    {
        if(isNull())
            return 0;

        return media.getCurrentPosition();
    }

    @Override
    protected void pauseBeforeRelease() {
        if(media==null)
            return;

        media.pause();
    }

    @Override
    public void newMedia()
    {
        media = new IjkMediaPlayer();

        listenersUpdate();
    }

    @Override
    public void setOptionsAfterPlayerCreated(boolean hardwareDecoding)
    {
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0);
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0);
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1);
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fast-seek", 1);
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-fps", 30);
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "bit_rate", 800000); // 800 kbps (for 360p-480p)
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "buffer_size", 1024000); // 1MB buffer for smooth playback
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48); // Improve performance on weak CPUs
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "render-wait-start", 1);
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "threads", 2); // Use 2 threads for decoding
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", "fcc-_es2"); // Use OpenGL ES2 overlay
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "buffer_size", 2048000); // 2MB buffer size
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0); // Enable packet buffering
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "fast", 1); // Enable fast decoding
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48); // Reduce CPU usage
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "render-wait-start", 1); // Start rendering ASAP
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 12);
        //data.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_frame", 48);
        //V2();
        //V4();

        super.setOptionsAfterPlayerCreated(hardwareDecoding);

    }

    @Override
    public void hardwareDecoding()
    {
        // Enable hardware decoding
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1); // Enable MediaCodec
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1);
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1);

        // Optional: If you want to use MediaCodec for all videos (including non-secure ones)
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-all-videos", 1);

        // Enable fast seeking
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 0); // 0 for fast seeking
        media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fast", 1);
    }

    @Override
    public WeakReference<IjkMediaPlayer> resetGC()
    {
        super.resetGC();
        WeakReference<IjkMediaPlayer> oldData =  new WeakReference<>(media);

        media = null;
        media = new IjkMediaPlayer();

        return oldData;
    }

    @Override
    public void reset()
    {
        super.reset();
        media.reset();
    }

    @Override
    public void setVolume(float volume) {
        if(isNull())
            return;

        media.setVolume(volume,volume);
    }

    @Override
    public void release()
    {
        super.release();

        disposeReleaser();
        releaser = addTask(() -> {

            try {

                if (!secondPlayer)
                    EmptyActivity.EmptyIJK.finishMake();

                WeakReference<IjkMediaPlayer> selected = new WeakReference<>(media);

                media = null;

                cleaned = true;

                if (selected.get() != null)
                    selected.get().release();
            } catch (Exception e) {
                onErrorSave("IJKPlayer-Release-Error", e);
            }

            return true;
        }, () -> "IJKPlayer-ReleaseError",ioThreadPoolScheduler);
        while (!cleaned)
        {
            waitMS(250);
        }
        waitMS(250);
    }

    @Override
    public void load(boolean HardwareDecoding) throws IOException {
        super.load(HardwareDecoding);
        setOptionsAfterPlayerCreated(HardwareDecoding);
        startOnPrepared();

        media.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", OkHttpDownloader.USER_AGENT);

        media.setDataSource(MediaProxyServlet.getPure(link,videoOnly));

        media.setVolume(0.0f,0.0f);
        media.prepareAsync();
    }

    // Helper method to get what error as string
    protected String getWhatErrorString(int what) {
        switch (what) {
            case IMediaPlayer.MEDIA_ERROR_UNKNOWN:
                return "MEDIA_ERROR_UNKNOWN";
            case IMediaPlayer.MEDIA_ERROR_SERVER_DIED:
                return "MEDIA_ERROR_SERVER_DIED";
            case IMediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                return "MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK";
            case IMediaPlayer.MEDIA_ERROR_IO:
                return "MEDIA_ERROR_IO";
            case IMediaPlayer.MEDIA_ERROR_MALFORMED:
                return "MEDIA_ERROR_MALFORMED";
            case IMediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                return "MEDIA_ERROR_UNSUPPORTED";
            case IMediaPlayer.MEDIA_ERROR_TIMED_OUT:
                return "MEDIA_ERROR_TIMED_OUT";
            default:
                return "UNKNOWN_ERROR_CODE";
        }
    }

    public static class RawDataSourceProvider implements IMediaDataSource {
        private AssetFileDescriptor mDescriptor;

        private byte[] mMediaBytes;

        public RawDataSourceProvider(AssetFileDescriptor descriptor) {
            this.mDescriptor = descriptor;
        }

        @Override
        public int readAt(long position, byte[] buffer, int offset, int size) {
            if (position + 1 >= mMediaBytes.length) {
                return -1;
            }

            int length;
            if (position + size < mMediaBytes.length) {
                length = size;
            } else {
                length = (int) (mMediaBytes.length - position);
                if (length > buffer.length)
                    length = buffer.length;

                length--;
            }
            System.arraycopy(mMediaBytes, (int) position, buffer, offset, length);

            return length;
        }

        @Override
        public long getSize() throws IOException {
            long length = mDescriptor.getLength();
            if (mMediaBytes == null) {
                InputStream inputStream = mDescriptor.createInputStream();
                mMediaBytes = readBytes(inputStream);
            }


            return length;
        }

        @Override
        public void close() throws IOException {
            if (mDescriptor != null)
                mDescriptor.close();

            mDescriptor = null;
            mMediaBytes = null;
        }

        private byte[] readBytes(InputStream inputStream) throws IOException {
            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];

            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }

            return byteBuffer.toByteArray();
        }

        public static RawDataSourceProvider create(Context context, Uri uri) {
            try {
                AssetFileDescriptor fileDescriptor = context.getContentResolver().openAssetFileDescriptor(uri, "r");
                return new RawDataSourceProvider(fileDescriptor);

            } catch (FileNotFoundException e) {
                onErrorSave("IJK-RawDataSourceProvider",e);
                //e.printStackTrace();
            }
            return null;
        }
    }

/*
        public long UpdateSeekDynamic()
        {
            AtomicLong seek = new AtomicLong(-1);
            mediaGetSeek.started = true;
            mediaGetSeek.Start(() ->
            {
                while (seek.get()==-1)
                {
                    seek.set(media.getCurrentPosition());
                    WaitS(200);
                }
                mediaGetSeek.started = false;
                return true;
            }, error -> mediaGetSeek.disposable.dispose());

            int timeOut = 0;
            while (mediaGetSeek.started)
            {
                WaitS(1000);
                if(timeOut == mediaGetSeek.getSecond())
                {
                    return 0;
                }
                timeOut++;
            }

            return seek.get();
        }*/
}

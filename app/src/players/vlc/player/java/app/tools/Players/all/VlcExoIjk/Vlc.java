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

package app.tools.Players.all.VlcExoIjk;

import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.interfaces.IVLCVout;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import app.App.AppBack;
import app.Main;
import app.tools.Players.all.Player;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import server.tools.MediaProxyServlet;
import server.web.ErrorCodeApp;

import static app.tools.DisposableTools.addTask;
import static app.tools.DisposableTools.addTaskUI;
import static app.tools.DisposableTools.forSecondMedia;
import static app.tools.DisposableTools.ioThreadPoolScheduler;
import static app.tools.DisposableTools.waitMS;
import static app.tools.StaticFunctions.onErrorSave;

public abstract class Vlc extends Player
{
    protected MediaPlayer media;

    private Disposable voutDisposable;
    private LibVLC libVLC;
    private ArrayList<String> vlcOptions;
    private Runnable displayFixSecond = ()->{};
    private Disposable displayFix;
    private IVLCVout holderOfVLC;
    private SurfaceHolder holder;
    private int maxVolumeLevel = 100;
    private boolean sleep;
    private Runnable loader;
    private String audioLink;

    public Vlc(boolean videoOnly) {
        super(videoOnly);
        newMedia();
    }

    // Same preset methods as IJK but with VLC options
    /*public void VAudio()
    {
        preset = () -> {
            
            vlcOptions.add(":no-video");
            vlcOptions.add(":audio-resampler=soxr");
            vlcOptions.add(":network-caching=500");
            vlcOptions.add(":file-caching=200");
            vlcOptions.add(":avcodec-skiploopfilter=4");
        };
    }

    public void VLive()
    {
        preset = () -> {
            
            vlcOptions.add(":network-caching=1500");
            vlcOptions.add(":live-caching=1500");
            vlcOptions.add(":clock-jitter=0");
            vlcOptions.add(":clock-synchro=0");
            vlcOptions.add(":avcodec-fast");
        };
    }

    public void VOnPrepareDontStart()
    {
        preset = () -> {
            
            vlcOptions.add(":start-paused=1");
        };
    }

    public void VOnPrepareStart()
    {
        preset = () -> {
            
            vlcOptions.add(":start-paused=0");
        };
    }

    public void VClock()
    {
        preset = () -> {
            
            vlcOptions.add(":network-caching=100");
            vlcOptions.add(":clock-synchro=0");
            vlcOptions.add(":avcodec-skiploopfilter=8");
        };
    }

    public void V2M()
    {
        preset = () -> {

            vlcOptions.add(":network-caching=2000");
            vlcOptions.add(":file-caching=500");
            vlcOptions.add(":avcodec-skiploopfilter=4");
            vlcOptions.add(":avcodec-skip-frame=2");
        };
    }

    public void V2()
    {
        preset = () -> {

            vlcOptions.add(":network-caching=3000");
            vlcOptions.add(":file-caching=1000");
            vlcOptions.add(":avcodec-skiploopfilter=1");
            vlcOptions.add(":avcodec-skip-frame=0");
        };
    }

    public void VHighQuality()
    {
        preset = () -> {

            vlcOptions.add(":network-caching=2000");
            vlcOptions.add(":file-caching=1000");
            vlcOptions.add(":avcodec-skiploopfilter=0");
            vlcOptions.add(":avcodec-skip-frame=0");
            vlcOptions.add(":avcodec-fast=0");
        };
    }

    public void VLowLatency()
    {
        preset = () -> {

            vlcOptions.add(":network-caching=100");
            vlcOptions.add(":live-caching=100");
            vlcOptions.add(":avcodec-fast");
            vlcOptions.add(":avcodec-skiploopfilter=8");
        };
    }*/

    @Override
    public void basePreset()
    {
        preset = () -> {
            
            vlcOptions.add(":network-caching=1000");
            vlcOptions.add(":file-caching=300");
            vlcOptions.add(":avcodec-fast");
            vlcOptions.add(":avcodec-skiploopfilter=12");
        };
    }

    public void addAudio(String url)
    {
        audioLink = url;
    }

    @Override
    public long getDuration()
    {
        if(isNull())
            return 0;

        return media.getLength();
    }

    @Override
    public void seekTo(long seek)
    {
        media.setTime(seek);
    }

    @Override
    public void audioNormal()
    {
    }

    @Override
    public void start()
    {
        media.play();
    }

    @Override
    public void start(long seek)
    {
        if(onEndTriggered(seek))
            return;

        start();
        seekTo(seek);
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
        sleep = on;
    }

    @Override
    public void modifySetDisplaySurface(SurfaceHolder Holder)
    {
        holder = Holder;

        displayUpdate(()->{
            holderOfVLC.setVideoSurface(holder.getSurface(),holder);
            holderOfVLC.attachViews();
        });
    }

    @Override
    public void modifyNullDisplay() {
        displayUpdate(()-> holderOfVLC.detachViews());

        holder = null;
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

        return media.getTime();
    }

    @Override
    protected void pauseBeforeRelease() {
        if(media==null)
            return;

        media.pause();
    }

        /*@Override
        public void OnScreenChangeListenerSetup()
        {
        }

        @Override
        public void SetOnPreparedListener() {
            // Handled in event listener
        }*/

    @Override
    public void newMedia()
    {
        loader = () -> {
            libVLC = new LibVLC(Main.getContext(),vlcOptions);

            media = new MediaPlayer(libVLC);
            if(holder!=null)
            {
                //Pair<Integer, Integer> dimensions = getScreenDimensions();
                holderOfVLC = media.getVLCVout();
                holderOfVLC.setVideoSurface(holder.getSurface(),holder);
                holderOfVLC.attachViews();

                holder.setKeepScreenOn(sleep);
            }
/*
                SetOnPreparedListener();*/

            //Loop(loop);

            listenersUpdate();
            loader = null;
        };
    }

    @Override
    public void setOptionsBeforePlayerCreated(boolean hardwareDecoding)
    {
        vlcOptions = new ArrayList<>();
        //vlcOptions.add("--input-repeat=65535");
        super.setOptionsBeforePlayerCreated(hardwareDecoding);
    }



    @Override
    public void hardwareDecoding()
    {
        vlcOptions.add(":avcodec-hw=any");
        vlcOptions.add(":ffmpeg-hw");
    }

    @Override
    public WeakReference<MediaPlayer> resetGC()
    {
        super.resetGC();
        WeakReference<MediaPlayer> oldData =  new WeakReference<>(media);

        cleanDisplay();
        media = null;
        media = new MediaPlayer(libVLC);

        return oldData;
    }

    @Override
    public void reset()
    {
        super.reset();
        media.stop();
    }

    @Override
    public void release()
    {
        super.release();
        audioLink = null;

        disposeReleaser();
        releaser = addTask(() -> {

            try
            {
                WeakReference<MediaPlayer> selectedMedia = new WeakReference<>(media);
                WeakReference<LibVLC> selv = new WeakReference<>(libVLC);

                cleanDisplay();
                media = null;
                libVLC = null;

                cleaned = true;

                if(selectedMedia.get()!=null)
                {
                    // --- Stop playback ---
                    try { selectedMedia.get().stop(); } catch (Exception ignored) {
                        onErrorSave("vlc-stop",ignored);
                    }

                    // --- Detach any views (safe even if none attached) ---
                    try { selectedMedia.get().detachViews(); } catch (Exception ignored) {
                        onErrorSave("vlc-get-detachViews",ignored);
                    }

                    // --- Release media player ---
                    try { selectedMedia.get().release(); } catch (Exception ignored) {
                        onErrorSave("vlc-get-release",ignored);
                    }
                }

                if(selv.get()!=null)
                {
                    // --- Release VLC core ---
                    try { selv.get().release(); } catch (Exception ignored) {
                        onErrorSave("vlc-get-release",ignored);
                    }
                }

            } catch (Exception ignored) {
                onErrorSave("vlc-get-release",ignored);
            }
            return true;
        },() -> "VLCPlayer-ReleaseError",ioThreadPoolScheduler);
        while (!cleaned)
        {
            waitMS(250);
        }

        waitMS(250);
    }

    @Override
    public void setVolume(float volume) {
        if(!isNull())
            media.setVolume((int)(volume*maxVolumeLevel));
    }

    @Override
    public void load(boolean HardwareDecoding) throws IOException {
        super.load(HardwareDecoding);
        setOptionsBeforePlayerCreated(HardwareDecoding);

        if(loader!=null)
            loader.run();

        media.setVolume(0);

        media.setMedia(new Media(libVLC, Uri.parse(MediaProxyServlet.getPure(link,videoOnly))));

        if(audioLink!=null)
            media.addSlave(
                    Media.Slave.Type.Audio, // Or the correct integer value, likely 1
                    Uri.parse(MediaProxyServlet.getPure(audioLink,false)),
                    true // Select this track immediately
            );
    }

    /**
     * Handles Vout event - handles screen rotation properly
     */
    protected void handleVoutReady() {
        if (voutDisposable != null && !voutDisposable.isDisposed()) {
            voutDisposable.dispose();
        }

        voutDisposable = Observable.fromCallable(() -> {
                    int retryCount = 0;
                    while (retryCount < 15) {
                        try {
                            Thread.sleep(150);

                            IMedia media = this.media.getMedia();
                            if (media != null && !media.isReleased()) {
                                media.parse();

                                int trackCount = media.getTrackCount();
                                for (int i = 0; i < trackCount; i++) {
                                    IMedia.Track track = media.getTrack(i);
                                    if (track instanceof IMedia.VideoTrack) {
                                        IMedia.VideoTrack videoTrack = (IMedia.VideoTrack) track;
                                        if (videoTrack.width > 0 && videoTrack.height > 0) {
                                            return new int[]{videoTrack.width, videoTrack.height};
                                        }
                                    }
                                }
                            }
                            retryCount++;
                        } catch (Exception e) {
                            onErrorSave("voutDisposable",e);
                            retryCount++;
                        }
                    }
                    throw new Exception("Could not get video dimensions");
                })
                .subscribeOn(forSecondMedia)
                .observeOn(forSecondMedia)
                .subscribe(
                        dimensions -> {
                            applyVideoDimensionsWithRotation(dimensions[0], dimensions[1]);
                            AppBack.Panel.setOnRotate(() -> applyVideoDimensionsWithRotation(dimensions[0], dimensions[1]));
                        },
                        error -> {
                            // Fallback to screen size
                            applyVideoDimensionsWithRotation(0, 0);
                        }
                );
    }

    private void displayUpdate(Runnable make)
    {
        if(holderOfVLC == null)
            return;

        if(displayFix==null||displayFix.isDisposed())
            displayFix = addTaskUI(()->{
                make.run();
                displayFixSecond.run();

                displayFixSecond = ()->{};
                displayFix.dispose();
                return true;
            },()->"DisplayFix");
        else
            displayFixSecond = make;
    }

    /**
     * Applies video dimensions considering screen rotation
     */
    private int[] applyVideoDimensionsWithRotation(int videoWidth, int videoHeight) {
        IVLCVout vout = media.getVLCVout();
        DisplayMetrics metrics = Main.getContext().getResources().getDisplayMetrics();

        // Get current screen dimensions (already rotated by system)
        int currentScreenWidth = metrics.widthPixels;
        int currentScreenHeight = metrics.heightPixels;

        // Get natural screen dimensions (without rotation)
        int naturalScreenWidth = Math.min(metrics.widthPixels, metrics.heightPixels);
        int naturalScreenHeight = Math.max(metrics.widthPixels, metrics.heightPixels);

        int displayWidth, displayHeight;

        if (videoWidth <= 0 || videoHeight <= 0) {
            // Use full screen if video dimensions not available
            displayWidth = currentScreenWidth;
            displayHeight = currentScreenHeight;
        } else {
            float videoAspectRatio = (float) videoWidth / videoHeight;
            float currentScreenAspectRatio = (float) currentScreenWidth / currentScreenHeight;

            // Check if screen is in portrait or landscape
            boolean isPortrait = currentScreenHeight > currentScreenWidth;

            if (isPortrait) {
                // Portrait mode - adjust calculation
                if (videoAspectRatio > currentScreenAspectRatio) {
                    // Video is wider relative to portrait screen
                    displayWidth = currentScreenWidth;
                    displayHeight = (int) (currentScreenWidth / videoAspectRatio);
                } else {
                    // Video is taller relative to portrait screen
                    displayHeight = currentScreenHeight;
                    displayWidth = (int) (currentScreenHeight * videoAspectRatio);
                }
            } else {
                // Landscape mode - your original logic
                if (videoAspectRatio > currentScreenAspectRatio) {
                    displayWidth = currentScreenWidth;
                    displayHeight = (int) (currentScreenWidth / videoAspectRatio);
                } else {
                    displayHeight = currentScreenHeight;
                    displayWidth = (int) (currentScreenHeight * videoAspectRatio);
                }
            }
        }

        vout.setWindowSize(displayWidth, displayHeight);
        return new int[]{displayWidth,displayHeight};
    }

    private void cleanDisplay()
    {
        if(displayFix!=null&&!displayFix.isDisposed())
            displayFix.dispose();

        displayFixSecond = ()->{};

        if(holderOfVLC ==null)
            return;

        holderOfVLC.detachViews();
        holderOfVLC = null;
    }
}
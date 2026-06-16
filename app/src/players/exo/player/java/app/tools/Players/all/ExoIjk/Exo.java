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

package app.tools.Players.all.ExoIjk;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.SurfaceHolder;


import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import app.Main;
import app.tools.Players.all.ExoIjk.tools.ExceptionOriginUtil;
import app.tools.Players.all.Player;

import static app.tools.DisposableTools.addTask;
import static app.tools.DisposableTools.ioThreadPoolScheduler;
import static app.tools.DisposableTools.waitMS;
import static app.tools.StaticFunctions.onErrorSave;

public abstract class Exo extends Player {
    protected ExoPlayer media;
    protected SurfaceHolder holder;

    private HandlerCustom playerHandler;
    private boolean holderDontSleep;

    public Exo(boolean videoOnly) {
        super(videoOnly);
        playerHandler = new HandlerCustom();
        newMedia();
    }

    public static boolean isNotHaveError(Throwable throwable)
    {
        // Exact match: matches only the class named androidx.media3.common.util.ListenerSet and method flushEvents
        if(ExceptionOriginUtil.originatesFrom(
                throwable,
                "androidx.media3.common.util.ListenerSet",
                "flushEvents",
                java.util.ConcurrentModificationException.class,
                false
        ))
            return true;

        return false;
    }

    @Override
    public long getDuration()
    {
        return makeTry(()->{
            if(!isNull())
                return media.getDuration();
            return 0L;
        },0L,3);
    }

    //currently is not using in this class
    @Override
    public void seekTo(long seek)
    {
        makeTry(() -> media.seekTo(seek),1);
    }

    @Override
    public void audioNormal()
    {/*
            // ExoPlayer handles audio focus automatically
            // Can be configured via AudioAttributes if needed
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build();
            media.setAudioAttributes(audioAttributes, true);*/
    }

    @Override
    public void start()
    {
        makeTry(() -> media.play(),1);
    }

    @Override
    public void start(long seek)
    {
        if(onEndTriggered(seek))
            return;

        makeTry(() -> {
            if(!media.isPlaying())
                media.play();

            media.seekTo(seek);
        },1);
    }

    @Override
    public boolean isPlaying()
    {
        return makeTry(()-> media != null && media.isPlaying(),false,-1);
    }

    @Override
    public boolean isNull()
    {
        return media == null;
    }

    @Override
    public void dontSleep(boolean on)
    {
        holderDontSleep = on;
/*
            AppBack.NewDisposableForUI(() -> {
                // ExoPlayer manages wake lock via PlayerView or manually:
                media.setWakeMode(on ? C.WAKE_MODE_NETWORK : C.WAKE_MODE_NONE);
                return true;
            }, () -> true);*/
    }

    @Override
    public void modifySetDisplaySurface(SurfaceHolder holder)
    {
        this.holder = holder;
        makeTry(() -> media.setVideoSurfaceHolder(holder));
        // Video size change is handled in the listener
    }

    public void modifyNullDisplay() {
        holder = null;
        makeTry(() -> media.clearVideoSurface());
    }

    @Override
    public void pause()
    {
        makeTry(() -> media.pause(),1);
    }

    @Override
    public long getCurrentPosition()
    {
        return makeTry(()->{
            if(!isNull())
                return media.getCurrentPosition();
            return 0L;
        },0L,3);
    }

    @OptIn(markerClass = UnstableApi.class) @Override
    public void newMedia()
    {
        // Create player on that thread
        makeTry(() -> {
            ExoPlayer.Builder.verifyApplicationThreadDisable(true);

            media = new ExoPlayer.Builder(Main.getContext())
                    .setLooper(playerHandler.getLooper())
                    .build();

            listenersUpdate();
        });
    }

    @Override
    public void setOptionsAfterPlayerCreated(boolean hardwareDecoding)
    {
        super.setOptionsAfterPlayerCreated(hardwareDecoding);

        // ExoPlayer configuration
        // Most options are set via Builder or LoadControl
        // Hardware decoding is handled automatically by default

        if (hardwareDecoding) {
            hardwareDecoding();
        }
    }

    @Override
    public void hardwareDecoding()
    {
        // ExoPlayer uses hardware decoding by default
        // You can force it via DefaultRenderersFactory
        // This would need to be set during ExoPlayer.Builder() construction
        // For runtime: ExoPlayer handles it automatically

        // Note: If you need explicit control, recreate player with:
        // DefaultRenderersFactory factory = new DefaultRenderersFactory(context)
        //     .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
        // new ExoPlayer.Builder(context).setRenderersFactory(factory).build();
    }

    @Override
    public WeakReference<ExoPlayer> resetGC()
    {
        super.resetGC();

        AtomicReference<WeakReference<ExoPlayer>> oldData = new AtomicReference<>();

        makeTry(() -> {
            beforeClean();
            oldData.set(new WeakReference<>(media));

            media = null;
            media = new ExoPlayer.Builder(Main.getContext()).build();
        });

        return oldData.get();
    }

    @Override
    public void reset()
    {

        super.reset();

        makeTry(() -> beforeClean());
    }

    @Override
    public void setVolume(float volume) {
        makeTry(() -> {
            if(!isNull()){
                media.setVolume(volume);
            }
        });
    }

    @Override
    protected void pauseBeforeRelease(){}

    @Override
    public void release()
    {
        super.release();

        if(playerHandler==null){
            onReleased();
            return;
        }

        try {

            makeTry(() -> {
                if(media==null)
                    return;

                beforeClean();
                WeakReference<ExoPlayer> selected = new WeakReference<>(media);
                media = null;

                if (selected.get() != null)
                    selected.get().release();
            });
            playerHandler.clean();
        }
        catch (Exception e) {
            onErrorSave("ExPlayer-Release-Error",e);
        }

        onReleased();
    }

    @Override
    public void load(boolean HardwareDecoding) throws IOException {
        super.load(HardwareDecoding);
        makeTry(() -> {
            setOptionsAfterPlayerCreated(HardwareDecoding);
            setData();
            // Load media
            media.setPlayWhenReady(true);
            media.setVolume(0);

            media.prepare();

            onPrepareMaking();
        });
    }

    protected void setData()
    {
        //MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MediaProxyServlet.getPure(link,videoOnly)));
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(link));
        media.setMediaItem(mediaItem);
    }

    private void onReleased(){
        cleaned = true;
        playerHandler = null;
        waitMS(250);
    }

    private void makeTry(Runnable tryMake,int maxWaitSeconds){
        makeTry(()->{
            tryMake.run();
            return true;
        },true,maxWaitSeconds);
    }

    private void makeTry(Runnable tryMake){
        makeTry(tryMake,-1);
    }

    private <T> T makeTry(Callable<T> tryGetFromExoThread,T onFalse, int maxWaitSeconds) {
        AtomicReference<T> wait = new AtomicReference<>(null);

        playerHandler.post(()->{
            try{
                wait.set(tryGetFromExoThread.call());
            }
            catch (Exception e){
                wait.set(onFalse);
            }
        });

        try {
            if(maxWaitSeconds==-1)
                while (true){
                    waitMS(100);
                    T curr = wait.get();
                    if(curr != null)
                        return curr;
                }

            maxWaitSeconds = maxWaitSeconds*10;

            for(int p = 0; p<maxWaitSeconds; p++)
            {
                waitMS(100);
                T curr = wait.get();
                if(curr != null)
                    return curr;
            }
        }
        catch (Exception e)
        {
            onErrorSave("ExPlayer-Try",e);
            return onFalse;
        }

        return onFalse;
    }

    private void beforeClean()
    {
        media.pause();
        // Stop playback and clear the playlist
        media.stop();
        media.clearMediaItems();

        // Reset prepared state
        prepared = false;
    }

    private static String createThreadName(String player){
        return "ExoPlayerThread_"+player+"_CreatedTime:"+System.currentTimeMillis();
    }

    public class HandlerCustom
    {
        private Looper looper;
        private Handler playerHandlerr;

        public HandlerCustom()
        {
            String newName;
            if(secondPlayer)
                newName = createThreadName("SecondPlayer");
            else
                newName= createThreadName("MainPlayer");

            HandlerThread threadHand = new HandlerThread(newName);
            threadHand.start();
            looper = threadHand.getLooper();
            playerHandlerr = new Handler(looper);
        }

        public void post(Runnable run)
        {
            try{
                playerHandlerr.post(() -> {
                    try {
                        run.run();
                    } catch (Exception e) {
                        onErrorSave("Exo-Error",e);
                    }
                });
            }
            catch (Exception e)
            {
                waitMS(30);
                post(run);
            }
        }

        public Looper getLooper()
        {
            return looper;
        }

        public void clean()
        {
            playerHandlerr = null;
            looper = null;
        }
    }
}
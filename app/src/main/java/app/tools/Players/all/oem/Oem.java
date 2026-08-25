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

package app.tools.Players.all.oem;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.lang.ref.WeakReference;

import androidx.annotation.CallSuper;
import app.App.AppBack;
import app.EmptyActivity;
import app.tools.Players.all.Player;
import app.tools.StaticFunctions;
import server.tools.MediaProxyServlet;
import server.web.ErrorCodeApp;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

import static app.tools.DisposableTools.addTask;
import static app.tools.DisposableTools.ioThreadPoolScheduler;
import static app.tools.DisposableTools.waitMS;
import static app.tools.StaticFunctions.makeTry;
import static app.tools.StaticFunctions.onErrorSave;

public abstract class Oem extends Player {
    protected MediaPlayer media;

    public Oem(boolean videoOnly) {
        super(videoOnly);

        if(!secondPlayer)
            AppBack.Panel.blackPanelFix = true;

        newMedia();
    }

    @Override
    public void setOptionsAfterPlayerCreated(boolean hardwareDecoding)
    {
        super.setOptionsAfterPlayerCreated(hardwareDecoding);
    }
    
    @Override
    public void hardwareDecoding()
    {}

    public void basePreset()
    {
        preset = () -> {};
    }

    public void startOnPrepared()
    {
        media.start();
    }

    public void livePreset()
    {
        preset = () -> {};
    }


    @Override
    public long modifyGetDuration()
    {
        return makeTry(()->{
            if(isNull())
                return 0L;

            return (long)media.getDuration();
        },0L,3);
    }

    @Override
    public void seekTo(long seek)
    {
        makeTry(()->media.seekTo((int)seek), StaticFunctions.Empty.r,1);
    }

    @Override
    public void audioNormal()
    {
        makeTry(()->media.setAudioStreamType(AudioManager.STREAM_MUSIC));
    }

    @Override
    public void modifyStart()
    {
        makeTry(() -> media.start(),StaticFunctions.Empty.r,1);
    }

    @Override
    public void modifyStart(long seek)
    {
        if(onEndTriggered(seek))
            return;

        makeTry(() -> {
            seekTo(seek);
            start();
        },StaticFunctions.Empty.r,1);
    }

    @Override
    public void emptyPanelOpen(){
        EmptyActivity.EmptyOem.load();
    }

    @Override
    public void emptyPanelClose(){
        EmptyActivity.EmptyOem.finishMake();
    }

    @Override
    public boolean modifyIsPlaying()
    {
        return makeTry(()-> media != null && media.isPlaying(),false,-1);
    }

    @Override
    public boolean modifyIsNull()
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
        makeTry(() -> media.setDisplay(holder));
    }

    @Override
    public void modifyNullDisplay() {
        makeTry(() -> media.setDisplay(null));
    }

    @Override
    public void modifyPause()
    {
        makeTry(() -> media.pause(),StaticFunctions.Empty.r,1);
    }

    @Override
    public long modifyGetCurrentPosition()
    {
        return makeTry(()->{
            if(isNull())
                return 0L;

            return (long)media.getCurrentPosition();
        },0L,3);
    }

    @Override
    protected void modifyPauseBeforeRelease() {
        if(media==null)
            return;

        media.pause();
    }

    @Override
    public void newMedia()
    {
        makeTry(()->{
            media = new MediaPlayer();

            listenersUpdate();
        });
    }

    @Override
    public WeakReference<MediaPlayer> resetGC()
    {
        super.resetGC();
        WeakReference<MediaPlayer> oldData =  new WeakReference<>(media);

        media = null;
        media = new MediaPlayer();

        return oldData;
    }

    @Override
    public void reset()
    {
        super.reset();
        makeTry(()->{
            media.pause();
            media.stop();
            media.reset();
        });
    }

    @Override
    public void modifySetVolume(float volume) {
        makeTry(()->{
            if(isNull())
                return;

            media.setVolume(volume,volume);
        });
    }

    @Override
    public void release()
    {
        super.release();

        disposeReleaser();

        releaser = addTask(() -> {

            try {
                WeakReference<MediaPlayer> selected = new WeakReference<>(media);

                media = null;

                cleaned = true;

                if(selected.get()!=null)
                    selected.get().release();
            }
            catch (Exception e){
                onErrorSave("OemPlayer-Release-Error",e);
            }

            return true;
        },() -> "OemPlayer-ReleaseError",ioThreadPoolScheduler);
        while (!cleaned)
        {
            waitMS(250);
        }
        waitMS(250);
    }

    @Override
    public void load(boolean HardwareDecoding) throws IOException {
        super.load(HardwareDecoding);
        makeTry(()->{
            setOptionsAfterPlayerCreated(HardwareDecoding);
            try {
                media.setDataSource(MediaProxyServlet.getPure(link,videoOnly));
                media.setVolume(0.0f,0.0f);
                media.prepareAsync();
            } catch (IOException e) {}
        });
    }

    // Helper method to get what error as string
    protected String getWhatErrorString(int what) {
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                return "MEDIA_ERROR_UNKNOWN";
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                return "MEDIA_ERROR_SERVER_DIED";
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                return "MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK";
            case MediaPlayer.MEDIA_ERROR_IO:
                return "MEDIA_ERROR_IO";
            case MediaPlayer.MEDIA_ERROR_MALFORMED:
                return "MEDIA_ERROR_MALFORMED";
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                return "MEDIA_ERROR_UNSUPPORTED";
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                return "MEDIA_ERROR_TIMED_OUT";
            default:
                return "UNKNOWN_ERROR_CODE";
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

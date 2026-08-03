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

package app.tools.Players.all;

import android.view.SurfaceHolder;

import java.io.IOException;
import java.lang.ref.WeakReference;

import androidx.annotation.CallSuper;
import app.tools.StaticFunctions;
import io.reactivex.rxjava3.disposables.Disposable;
import server.tools.MediaProxyServlet;
import server.web.ErrorCodeApp;

import static app.tools.DisposableTools.waitMS;

public abstract class Player {

    protected final boolean videoOnly;

    private final Runnable onDisplaySet;
    private final Runnable onDisplayNull;

    protected Disposable releaser;
    protected Runnable preset;
    protected String link;
    protected boolean bufferingStarted;
    protected boolean prepared;
    protected boolean error;
    protected float bufferingCounter;
    protected boolean cleaned;
    protected boolean secondPlayer = false;

    public Player(boolean videoOnly)
    {
        this.videoOnly = videoOnly;

        if(!videoOnly)
        {
            onDisplaySet = () -> MediaProxyServlet.combinedOrAudio.bufferSize(MediaProxyServlet.bufferSizeForVideo());
            onDisplayNull = () -> MediaProxyServlet.combinedOrAudio.bufferSize(MediaProxyServlet.bufferSizeForAudio());
        }
        else{
            onDisplaySet = new StaticFunctions.Starter() {
                @Override
                protected void firstLaunch() {
                    MediaProxyServlet.videoOnly.bufferSize(MediaProxyServlet.bufferSizeForVideo());
                }

                @Override
                protected void secondLaunches() {

                }
            };
            onDisplayNull = onDisplaySet;
        }

        onDisplayNull.run();
    }

    public final boolean bufferingStarted() {
        return bufferingStarted;
    }

    public final boolean getError() {
        return error;
    }

    public final void saveLink(String Link)
    {
        link = Link;
    }

    public final boolean secondBufferingStarted()
    {
        return bufferingCounter>=2;
    }

    public final void secondPlayer(boolean yes)
    {
        secondPlayer = yes;
    }

    public final boolean isPrepared()
    {
        return prepared;
    }

    protected final void disposeReleaser()
    {
        if(releaser!=null&&!releaser.isDisposed())
            releaser.dispose();
    }

    public void basePreset(){}
    public void emptyPanelOpen(){}
    public void emptyPanelClose(){}

    @CallSuper
    public void setOptionsAfterPlayerCreated(boolean hardwareDecoding)
    {/*
        preset.run();

        if(ijkPlayerColorFormat!=0)
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", ijkPlayerColorFormat);
*/
        if(preset!=null)
            preset.run();
        if(hardwareDecoding)
            hardwareDecoding();
    }

    @CallSuper
    public void setOptionsBeforePlayerCreated(boolean hardwareDecoding)
    {/*
        preset.run();

        if(ijkPlayerColorFormat!=0)
            media.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", ijkPlayerColorFormat);
*/
        if(preset!=null)
            preset.run();
        if(hardwareDecoding)
            hardwareDecoding();
    }

    @CallSuper
    public WeakReference resetGC()
    {
        resetBase();
        return null;
    }

    @CallSuper
    public void reset()
    {
        resetBase();
    }

    @CallSuper
    public void release()
    {
        pauseBeforeRelease();
        cleaned = false;
        resetBase();
    }

    @CallSuper
    public void load(boolean HardwareDecoding) throws IOException {
        error = false;
    }


    @CallSuper
    protected void resetBase()
    {
        bufferingStarted = false;
        prepared = false;
        //mediaGetSeek.Reset();
    }

    @CallSuper
    protected void onPrepareMaking(){
        prepared = true;
    }

    public abstract void newMedia();
    public abstract void seekTo(long seek);
    public abstract void audioNormal();
    public abstract void dontSleep(boolean on);
    public abstract void hardwareDecoding();
    public abstract boolean listenersUpdate();
    protected abstract boolean onEndTriggered(long curPos);

    public final void setDisplaySurface(SurfaceHolder holder){
        try{
            onDisplaySet.run();
            modifySetDisplaySurface(holder);
        } catch (Exception e) {}
    }

    public final void nullDisplay(){
        try {
            onDisplayNull.run();
            modifyNullDisplay();
        } catch (Exception e) {}
    }
    public final void setVolume(float volume){
        try{
            modifySetVolume(volume);
        } catch (Exception e) {}
    }
    public final void start(){
        try {
            modifyStart();
        }
        catch (Exception e){}
    }
    public final void start(long seek){
        try {
            modifyStart(seek);
        } catch (Exception e) {}
    }
    public final void pause(){
        try {
            modifyPause();
        } catch (Exception e) {}
    }
    public final boolean isPlaying(){
        try {
            return modifyIsPlaying();
        } catch (Exception e) {
            return false;
        }
    }
    public final boolean isNull(){
        try {
            return modifyIsNull();
        } catch (Exception e) {
            return true;
        }
    }
    public final long getDuration(){
        try {
            return modifyGetDuration();
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }
    public final long getCurrentPosition(){
        try {
            return modifyGetCurrentPosition();
        } catch (Exception e) {
            return 0;
        }
    }
    protected final void pauseBeforeRelease(){
        try {
            modifyPauseBeforeRelease();
        } catch (Exception e) {}
    }


    protected abstract void modifySetVolume(float volume);
    protected abstract void modifyStart();
    protected abstract void modifyStart(long seek);
    protected abstract void modifyPause();
    protected abstract boolean modifyIsPlaying();
    protected abstract boolean modifyIsNull();
    protected abstract long modifyGetDuration();
    protected abstract long modifyGetCurrentPosition();
    protected abstract void modifyPauseBeforeRelease();
    protected abstract void modifySetDisplaySurface(SurfaceHolder holder);
    protected abstract void modifyNullDisplay();
}

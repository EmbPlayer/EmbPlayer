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

import java.io.IOException;
import java.lang.ref.WeakReference;

import androidx.annotation.CallSuper;
import app.tools.Players.PlayerController;
import server.web.ErrorCodeApp;
import server.web.Wait;

import static app.tools.DisposableTools.waitMS;
import static server.Home.app;

public abstract class Media<T extends Player> extends PlayerController {
    protected T data;
    protected final Listeners listeners;

    public Media(Listeners listeners) {
        super(listeners);

        this.listeners = listeners;

        runInConstructor();
    }

    @Override
    public void dispose()
    {
        super.dispose();
    }

    @Override
    public void listenersUpdate()
    {
        data.listenersUpdate();
    }

    @Override
    public boolean secondBufferingStarted()
    {
        return data.secondBufferingStarted();
    }

    @Override
    public boolean isNull()
    {
        return data.isNull();
    }

    @Override
    public long modifyGetCurrentPosition()
    {
        return data.getCurrentPosition();
    }

    @Override
    public void waitPlay() {
        do {
            waitMS(500);
        }
        while (app().globalGenerator.waitStarted());
    }

    @Override
    public boolean isPlayingDynamic(int tryCount, int millsecond, Runnable ifMaked) {

        long currentPos = data.getCurrentPosition();

        for(int i = 0; i<tryCount; i++)
        {
            waitMS(millsecond);
            long newPos = data.getCurrentPosition();

            if(actionStarted())
                return true;
            if(currentPos<newPos)
            {
                baseData().savedSeekByIsPlayingDynamic = newPos;
                ifMaked.run();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isPlayingDynamic(int tryCount, int millsecond) {

        long currentPos = data.getCurrentPosition();

        for(int i = 0; i<tryCount; i++)
        {
            waitMS(millsecond);
            long newPos = data.getCurrentPosition();
            if(currentPos<newPos)
            {
                baseData().savedSeekByIsPlayingDynamic = newPos;
                return true;
            }
        }

        return false;
    }


    @Override
    public void inLoadTriggeredLoad() throws IOException {
        //data.ListenersUpdate();
        data.audioNormal();
        data.load(baseData().hardwareDecoding);
    }/*

    public void LoadWithoutListeners() throws IOException {
        data.media.setAudioStreamType(AudioManager.STREAM_MUSIC);
        data.Load(hardwareDecoding);
    }*/

    @Override
    public void addData(String audioOrBase, String video) {
        data.saveLink(audioOrBase);
    }

    @Override
    public boolean prepared() {
        return data.isPrepared();
    }

    @Override
    public Runnable afterLoad_StartAndSeek() {

        Runnable outPut = super.afterLoad_StartAndSeek();

        if(outPut==null)
            outPut = ()->{
                modifyStart(baseData().getSeekSecond());
                loadVolume();
            };

        return outPut;
    }

    public void isPlayingDynamic()
    {
        isPlayingDynamic(100,50);
        Wait.webUIWaitStop();
    }

    @Override
    public long getDuration()
    {
        return data.getDuration();
    }

    @Override
    public boolean getError()
    {
        return data.getError();
    }

    @Override
    protected void modifySetVolume(float volume)
    {
        data.setVolume(volume);
    }

    @CallSuper
    protected void runInConstructor(){}

    @Override
    protected void modifyResetGC()
    {
        dispose();
        WeakReference<T> oldAudio = data.resetGC();

        System.gc();

        if(oldAudio.get()!=null)
        {
            oldAudio.get().release();
        }
    }


    @Override
    protected void modifyPause()
    {
        baseData().updateSeekSecond(data.getCurrentPosition());
        data.pause();
    }

    @Override
    protected Runnable afterLoad_Start() {
        return ()->{
            modifyStart();
            loadVolume();
        };
    }

    @Override
    protected void modifyStart(long mills) {
        baseData().seeking = true;
        data.start(mills);
        super.modifyStart(mills);
        baseData().seeking = false;
        isPlayingDynamic();
    }

    @Override
    protected void modifyStart()
    {
        baseData().seeking = true;
        data.start();
        super.modifyStart();
        baseData().seeking = false;
        isPlayingDynamic();
    }

    @Override
    protected void modifyReset()
    {
        dispose();
        data.reset();
    }

    @Override
    protected void modifyRelease() {
        dispose();
        data.release();
    }
}

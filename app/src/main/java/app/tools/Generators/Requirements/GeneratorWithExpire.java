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

package app.tools.Generators.Requirements;


import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.IOException;

import app.tools.Players.PlayerController;
import app.tools.Players.all.PlayerControllerBase;
import app.tools.SData;
import app.tools.StaticFunctions;

import app.tools.Players.all.Listeners;

import app.tools.DisposableTools.WaitDisposable;
import server.web.ErrorCodeApp;

import static app.tools.DisposableTools.killAll;
import static app.tools.StaticFunctions.onErrorSave;

public abstract class GeneratorWithExpire extends Generator {

    public final WaitDisposable mediaSession;
    private final Listeners listeners;
    private final boolean hardware;

    protected boolean isLoaded;
    protected int expireSeconds;

    protected String audioOrBaseStream;
    protected String videoStream;

    public GeneratorWithExpire(String videoURL,Listeners listeners, boolean hardware)
    {
        mediaSession = new WaitDisposable();//12600
        this.videoURL = videoURL;
        this.listeners = listeners;
        this.hardware = hardware;
    }

    @Override
    public void kill()
    {
        isKilled = true;
        killAll(mediaError,mediaSession);
    }

    public final WaitDisposable[] makeKillAnotherWhereAll()
    {
        isKilled = true;
        return new WaitDisposable[] {mediaError,mediaSession};
    }

    @Override
    public final PlayerControllerBase startPanel(boolean DisplayOn, boolean loop, boolean plalistLoop) {
        PlayerControllerBase mediaPlayer = null;

        try {
            mediaPlayer = PlayerController.getPlayer(listeners,loop,plalistLoop,hardware,DisplayOn, isLive(),audioOrBaseStream,videoStream);

            if(mediaPlayer==null)
                return onErrorLive(DisplayOn,loop,plalistLoop);
            else
                mediaPlayer.setMaxSeek(getMaxSeekForPlayer());
        }
        catch (Exception e)
        {
            onErrorSave("GeneratorWithExpire-StartPanel",e);
            return onErrorLive(DisplayOn,loop,plalistLoop);
        }

        return mediaPlayer;
    }

    public final void reloadContent() throws ExtractionException, IOException {
        generateInfo();
        loadContent();
    }

    public final boolean generateAndLoad(int expireSeconds) throws ExtractionException, IOException {

        if(generateLink(expireSeconds))
            reloadContent();

        return isLoaded;
    }

    public final boolean mediaSessionWorking()
    {
        return mediaSession.started;
    }

    public final boolean mediaIsExpired()
    {
        return !mediaSessionWorking()|| StaticFunctions.getCurrentTimeAsSeconds()> expireTime();
    }


    public final int expireTime()
    {
        return expireSeconds;
    }

    public final boolean generateLink(int secondsTimeout)
    {
        modifyGenerateLink();
        int i = 0;
        while (!generateContent())
        {
            if(i==secondsTimeout)
                return false;
            i++;
        }
        return true;
    }

    public final boolean generateContent(){
        expireSeconds = 0;
        audioOrBaseStream = null;
        videoStream = null;

        return modifyGenerateContent();
    }

    public final void loadContent(){
        modifyLoadContent();
        isLoaded = true;
        mediaSessionStart();
    }
    public final boolean IsLoaded()
    {
        return isLoaded;
    }

    public final String getAudioStream()
    {
        return audioOrBaseStream;
    }

    public final String getVideoStream()
    {
        return videoStream;
    }

    public final boolean isNotGenerated()
    {
        return audioOrBaseStream==null;
    }

    public final void generateInfo() throws ExtractionException, IOException{
        modifyGenerateInfo();
    };

    public abstract PlayerControllerBase onErrorLive(boolean DisplayOn, boolean loop, boolean playListLoop);
    protected abstract void modifyGenerateLink();
    protected abstract boolean modifyGenerateContent();
    protected abstract void modifyLoadContent();
    protected abstract void modifyGenerateInfo() throws ExtractionException, IOException;

    private void mediaSessionStart() {
        mediaSession.started = true;

        int waitTime;
        int currentUnixTime = 0;
        if(expireSeconds<1)
        {
            //waitTime = 10800;
            waitTime = 10800;
        }
        else
        {
            currentUnixTime = StaticFunctions.getCurrentTimeAsSeconds();
            int minutes10 = 600;
            waitTime = expireSeconds-currentUnixTime-minutes10;
        }

        mediaSession.setSecond(waitTime);

        mediaSession.startWithLongWaiting(o -> {}, () -> {
            mediaSession.started = false;

            mediaErrorRun();

            mediaSession.disposable.dispose();
        }, o -> {
            onErrorSave("YoutubeGenerator-MediaSessionError: ",o);
            mediaSession.disposable.dispose();
        });
    }
}


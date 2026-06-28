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

package app.App;

import org.json.JSONArray;
import org.json.JSONException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import app.tools.DisposableTools;
import app.tools.Generators.Requirements.MediaSourceProviders;
import app.tools.LinksDbHelper;
import app.tools.StaticFunctions;
import io.reactivex.rxjava3.disposables.Disposable;
import server.tools.HttpServletAdvanced;
import server.web.ErrorCodeApp;
import server.web.Sources;
import server.web.Wait;

import static app.tools.DisposableTools.forServer;
import static app.tools.DisposableTools.forkJoinPool;
import static app.tools.DisposableTools.waitMS;
import static app.tools.StaticFunctions.getAllForJson;
import static app.tools.StaticFunctions.setData;
import static server.Home.app;

public class AppControl extends HttpServletAdvanced {

    private static final String[] LANGUAGES;
    private static final Consumer<HttpServletRequest> emptyAction = (httpServletRequest)->{};
    private static final Consumer<HttpServletRequest> action = (req)-> {
        currentAction = emptyAction;
        ErrorCodeApp.postResiver = StaticFunctions.getInfo("currentAction")+System.lineSeparator();
        try {
            Wait.webUIWaitStart();

            JSONArray jsonArray = new JSONArray(new Scanner(req.getInputStream()).nextLine());

            actions.add(()->{
                try {
                    clientAction(jsonArray);
                } catch (JSONException | ExtractionException | IOException e) {
                    AppControl.currentAction = AppControl.action;
                }
            },()->waitAndIsWorkingStop(),forServer,"ClientAction-Error");
        } catch (Exception e) {
            AppControl.currentAction = AppControl.action;
        }
    };

    private static Consumer<HttpServletRequest> currentAction = action;

    static{
        LANGUAGES = getAllForJson(Arrays.stream(AppWeb.LANGUAGES).map(String::toUpperCase).toArray(String[]::new));
    }

    @Override
    protected void doGetAdvanced(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        app().mediaUpdateSeekPosition();

        String data = setData(StaticFunctions.objectsToString(
                app().volumePosition.getSave(), app().getSeekMax(),
                app().getSeekPosition(), app().timer.getInt(),
                app().setUp.getInt(), app().loop.getInt(1),
                app().mediaProviderClientSideID.getSave(), app().playlist.getInt(),
                app().loop.getInt(2), app().isSavable.getInt(), app().isLiveAsInt(),
                app().nameOfMedia()
        ));
        String sourceTypes = setData(MediaSourceProviders.ClientSide.getAllMediaSourceTypeName());
        String languagesOut = setData(LANGUAGES);

        resp.getWriter().write(setData(data,sourceTypes,languagesOut));
    }

    @Override
    protected void doPostAdvanced(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        currentAction.accept(req);
        resp.setStatus(HttpServletResponse.SC_ACCEPTED);
    }

    private static int getInt(JSONArray ReceivedInputs, int Index) throws JSONException {
        return Integer.parseInt(getString(ReceivedInputs,Index));
    }

    private static String getString(JSONArray receivedInputs, int index) throws JSONException {
        return receivedInputs.getString(index);
    }

    private static void waitLinkGenerate()
    {
        for(int i = 0; i<15; i++)
        {
            if(app().globalGenerator.waitStarted())
            {
                waitMS(1000);
            }
            else
                break;
        }
    }

    private static boolean waitStop(){
        Wait.webUIWaitStop();
        return true;
    }

    public static boolean workingStop(){
        //isWorking.set(false);
        AppControl.currentAction = AppControl.action;
        ErrorCodeApp.postResiver = ErrorCodeApp.postResiver+StaticFunctions.getInfo("workingStop")+System.lineSeparator();
        return true;
    }

    public static boolean waitAndIsWorkingStop(){
        //isWorking.set(false);
        AppControl.currentAction = AppControl.action;
        ErrorCodeApp.postResiver = ErrorCodeApp.postResiver+StaticFunctions.getInfo("waitAndIsWorkingStop")+System.lineSeparator();
        return waitStop();
    }

    private static boolean empty(){
        return true;
    }

    private static boolean sendLoop(){
        waitLinkGenerate();

        app().loopSwitch(1);
        return waitAndIsWorkingStop();
    }

    private static boolean sendVolume(JSONArray Obj) throws JSONException {
        app().mediaVolume(getString(Obj,1));
        return waitAndIsWorkingStop();
    }

    private static boolean sendVideoFromCollection(JSONArray Obj) throws JSONException, ExtractionException, IOException {
        app().startFromCollection(getInt(Obj,1), getString(Obj,2));
        return empty();
    }

    private static boolean sendFromSource(JSONArray Obj) throws JSONException {
        app().startFromJson(getString(Obj,1), getInt(Obj,2), getInt(Obj,3));
        return empty();
    }

    private static boolean sendLanguagesOfStream(JSONArray Obj) throws JSONException {
        Sources.getSourcesController().recreate(getString(Obj,1),()->waitAndIsWorkingStop());
        return empty();
    }

    private static boolean sendDeleteLink(JSONArray Obj) throws JSONException {
        app().deleteFromCollection(getInt(Obj,1), getString(Obj,2));
        return waitAndIsWorkingStop();
    }

    private static boolean clientAction(JSONArray Obj) throws JSONException, ExtractionException, IOException {

        int page = getInt(Obj,0);

        if(page == Action.sendLanguagesRefresh){
            Sources.getSourcesController().recreate(()->waitAndIsWorkingStop());
            return empty();
        }
        else if(app().setUp.get())
        {
            if(page == Action.sendURL){
                app().sendURL();
                return empty();
            }
            else if(!app().mediaIsNull())
            {
                switch (page)
                {
                    case Action.sendValue:

                        waitLinkGenerate();

                        int seek = getInt(Obj,1)+1;

                        if(app().getSeekMax()<seek)
                            return waitAndIsWorkingStop();

                        app().seekPosition(seek);

                        if(!app().timer.get()&&!app().mediaSeekStart())
                            return waitAndIsWorkingStop();

                        app().startVideo();
                        return workingStop();

                    case Action.sendStop:

                        waitLinkGenerate();

                        if(app().timer.get())
                        {
                            app().stopVideo(getString(Obj,1));
                            return waitAndIsWorkingStop();
                        }
                        else
                        {
                            app().startVideo();
                            return workingStop();
                        }

                    case Action.sendLoop:
                        return sendLoop();

                    case Action.sendVolume:
                        return sendVolume(Obj);

                    case Action.sendVideoChange:
                        app().videoChanger.updateChanger(getInt(Obj,1));
                        return workingStop();

                    case Action.sendSaveVideo:

                        if(!app().isSavable.get())
                            return waitAndIsWorkingStop();

                        String Name = null;

                        try
                        {
                            Name = getString(Obj,1);
                        }catch (Exception e){}

                        app().linkSave(LinksDbHelper.getTableNamesAsString()[app().getSelectedTable()],Name);
                        return waitAndIsWorkingStop();

                    case Action.sendVideoFromCollection:
                        return sendVideoFromCollection(Obj);

                    case Action.sendFromSource:
                        return sendFromSource(Obj);

                    case Action.sendLanguagesOfStream:
                        return sendLanguagesOfStream(Obj);

                    case Action.sendDeleteLink:
                        return sendDeleteLink(Obj);

                    case Action.sendCollectionLoop:
                        waitLinkGenerate();
                        app().loopSwitch(2);
                        return waitAndIsWorkingStop();
                }
            }
            else
            {

                switch (page)
                {
                    case Action.sendLoop:
                        return sendLoop();

                    case Action.sendVolume:
                        return sendVolume(Obj);

                    case Action.sendVideoFromCollection:
                        return sendVideoFromCollection(Obj);

                    case Action.sendFromSource:
                        return sendFromSource(Obj);

                    case Action.sendLanguagesOfStream:
                        return sendLanguagesOfStream(Obj);

                    case Action.sendDeleteLink:
                        return sendDeleteLink(Obj);
                }
            }
        }
        else
        {
            switch (page)
            {
                case Action.sendVolume:
                    return sendVolume(Obj);

                case Action.sendURL:
                    app().sendURLClose(getString(Obj,1));
                    return empty();

                case Action.sendFromSource:
                    return sendFromSource(Obj);

                case Action.sendVideoFromCollection:
                    return sendVideoFromCollection(Obj);

                case Action.sendSourceType:
                    app().mediaProviderClientSideID.set(getInt(Obj,1));
                    return waitAndIsWorkingStop();

                case Action.sendVideoResolution:
                    app().videoResolutionID.set(getInt(Obj,1));
                    return waitAndIsWorkingStop();

                case Action.sendVideoResolutionLive:
                    app().videoResolutionLiveID(getInt(Obj,1));
                    return waitAndIsWorkingStop();

                case Action.sendPlayer:
                    app().playerID.set(getInt(Obj,1));
                    return waitAndIsWorkingStop();

                case Action.sendLivePlayer:
                    app().livePlayerID.set(getInt(Obj,1));
                    return waitAndIsWorkingStop();

                case Action.sendRadioPlayer:
                    app().radioPlayerID.set(getInt(Obj,1));
                    return waitAndIsWorkingStop();

                case Action.sendYoutubePlayer:
                    app().youtubePlayerID.set(getInt(Obj,1));
                    return waitAndIsWorkingStop();

                case Action.sendYoutubeLegacyPlayer:
                    app().legacyYoutubePlayer.set(!app().legacyYoutubePlayer.get());
                    return waitAndIsWorkingStop();

                case Action.sendYoutubePlayerVideo:
                    app().youtubePlayerVideoID.set(getInt(Obj,1));
                    return waitAndIsWorkingStop();

                case Action.sendVideoLanguage:
                    app().videoLanguageID.set(getInt(Obj,1));
                    return waitAndIsWorkingStop();

                case Action.sendHardwareDecoding:
                    app().hardware.set(!app().hardware.get());
                    return waitAndIsWorkingStop();

                case Action.sendCheckMacAndSsid:
                    app().checkMacAndSsid.set(!app().checkMacAndSsid.get());
                    return waitAndIsWorkingStop();

                case Action.sendYoutubeCaching:
                    app().onExo.youtubeCaching.set(!app().onExo.youtubeCaching.get());
                    return waitAndIsWorkingStop();

                case Action.sendURLCaching:
                    app().onExo.urlCaching.set(!app().onExo.urlCaching.get());
                    return waitAndIsWorkingStop();

                case Action.sendLanguagesOfStream:
                    return sendLanguagesOfStream(Obj);

                case Action.sendDeleteLink:
                    return sendDeleteLink(Obj);

                case Action.sendColorFormat:
                    app().onIJK.colorFormatID(getInt(Obj,1));
                    return waitAndIsWorkingStop();
            }
        }

        return false;
    }

    public static class Action
    {
        private static final int sendURL = 0;
        private static final int sendValue = 1;
        private static final int sendStop = 2;
        private static final int sendVolume = 3;
        private static final int sendLoop = 4;
        private static final int sendVideoResolution = 5;
        private static final int sendPlayer = 6;
        private static final int sendVideoLanguage = 7;
        private static final int sendHardwareDecoding = 8;
        private static final int sendSourceType = 9;
        private static final int sendVideoChange = 10;
        private static final int sendSaveVideo = 11;
        private static final int sendDeleteLink = 12;
        private static final int sendVideoFromCollection = 13;
        private static final int sendCollectionLoop = 14;
        private static final int sendColorFormat = 15;
        private static final int sendFromSource = 16;
        private static final int sendLanguagesOfStream = 17;
        private static final int sendLanguagesRefresh = 18;
        private static final int sendLivePlayer = 19;
        private static final int sendYoutubePlayer = 20;
        private static final int sendYoutubePlayerVideo = 21;
        private static final int sendYoutubeCaching = 22;
        private static final int sendURLCaching = 23;
        private static final int sendVideoResolutionLive = 24;
        private static final int sendYoutubeLegacyPlayer = 25;
        private static final int sendCheckMacAndSsid = 26;
        private static final int sendRadioPlayer = 27;
    }
}

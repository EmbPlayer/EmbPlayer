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
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import app.tools.Generators.Requirements.MediaSourceProviders;
import app.tools.LinksDbHelper;
import app.tools.StaticFunctions;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import server.tools.HttpServletAdvanced;
import server.web.Sources;
import server.web.Wait;

import static app.tools.DisposableTools.forServer;
import static app.tools.DisposableTools.waitMS;
import static app.tools.StaticFunctions.onErrorSave;
import static app.tools.StaticFunctions.getAllForJson;
import static app.tools.StaticFunctions.setData;
import static server.Home.app;

public class AppControl extends HttpServletAdvanced {

    private static final Subject<JSONArray> postSubject;
    private static final String[] LANGUAGES;

    static{
        LANGUAGES = getAllForJson(Arrays.stream(AppWeb.LANGUAGES).map(String::toUpperCase).toArray(String[]::new));
        postSubject = PublishSubject.<JSONArray>create().toSerialized();
        postSubject
                .observeOn(forServer)
                .throttleFirst(400, TimeUnit.MILLISECONDS) // ignore subsequent posts for 300ms
                .subscribe((input) -> clientActionInAnotherThread(input),
                        throwable -> onErrorSave("Post-Error",throwable));
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
        try {
            JSONArray obj = new JSONArray(new Scanner(req.getInputStream()).nextLine());

            if(!clientActionBase(obj))
            {
                postSubject.onNext(obj);
            }
        } catch (Exception e) {
            // keep original behavior for errors
        }

        resp.setStatus(HttpServletResponse.SC_ACCEPTED);
    }

    private boolean clientActionBase(JSONArray Inputs) throws JSONException {
        Wait.webUIWaitStart();
        int page = getInt(Inputs,0);

        if(page == Action.sendLanguagesRefresh)
        {
            Sources.getSourcesController().recreate(()->Wait.webUIWaitStop());
        }
        else if(app().setUp.get() && page == Action.sendURL)
        {
            app().sendURL();
        }
        else
        {
            return false;
        }

        return true;
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

    private static void clientActionInAnotherThread(JSONArray Obj)
    {
        actions.add(()->{
            try {
                clientAction(Obj);
            } catch (JSONException | ExtractionException | IOException e) {
                throw new RuntimeException(e);
            }
        },forServer,"ClientAction-Error");
    }

    private static void clientAction(JSONArray Obj) throws JSONException, ExtractionException, IOException {

        int page = getInt(Obj,0);

        if(app().setUp.get())
        {
            if(!app().mediaIsNull())
            {
                switch (page)
                {
                    case Action.sendValue:

                        waitLinkGenerate();

                        int seek = getInt(Obj,1)+1;

                        if(app().getSeekMax()<seek)
                            break;

                        app().seekPosition(seek);

                        if(!app().timer.get()&&!app().mediaSeekStart())
                            break;

                        app().startVideo();
                        return;

                    case Action.sendStop:

                        waitLinkGenerate();

                        if(app().timer.get())
                        {
                            app().stopVideo(getString(Obj,1));
                            break;
                        }
                        else
                        {
                            app().startVideo();
                            return;
                        }

                    case Action.sendLoop:

                        waitLinkGenerate();

                        app().loopSwitch(1);
                        break;

                    case Action.sendVolume:
                        //

                        app().mediaVolume(getString(Obj,1));
                        break;

                    case Action.sendVideoChange:
                        app().videoChanger.updateChanger(getInt(Obj,1));
                        return;

                    case Action.sendSaveVideo:

                        if(!app().isSavable.get())
                            break;

                        String Name = null;

                        try
                        {
                            Name = getString(Obj,1);
                        }catch (Exception e){}

                        app().linkSave(LinksDbHelper.getTableNamesAsString()[app().getSelectedTable()],Name);

                        break;

                    case Action.sendVideoFromCollection:
                        app().startFromCollection(getInt(Obj,1), getString(Obj,2));
                        return;

                    case Action.sendFromSource:
                        app().startFromJson(getString(Obj,1), getInt(Obj,2), getInt(Obj,3));
                        return;

                    case Action.sendLanguagesOfStream:
                        Sources.getSourcesController().recreate(getString(Obj,1),()->Wait.webUIWaitStop());
                        return;

                    case Action.sendDeleteLink:

                        app().deleteFromCollection(getInt(Obj,1), getString(Obj,2));

                        break;

                    case Action.sendCollectionLoop:

                        waitLinkGenerate();

                        app().loopSwitch(2);
                        break;
                }
            }
            else
            {

                switch (page)
                {
                    case Action.sendLoop:

                        waitLinkGenerate();

                        app().loopSwitch(1);
                        break;

                    case Action.sendVolume:
                        //

                        app().mediaVolume(getString(Obj,1));
                        break;

                    case Action.sendVideoFromCollection:
                        app().startFromCollection(getInt(Obj,1), getString(Obj,2));
                        return;

                    case Action.sendFromSource:
                        app().startFromJson(getString(Obj,1), getInt(Obj,2), getInt(Obj,3));
                        return;

                    case Action.sendLanguagesOfStream:
                        Sources.getSourcesController().recreate(getString(Obj,1),()->Wait.webUIWaitStop());
                        return;

                    case Action.sendDeleteLink:

                        app().deleteFromCollection(getInt(Obj,1), getString(Obj,2));

                        break;
                }
            }
        }
        else
        {
            switch (page)
            {
                case Action.sendVolume:

                    app().mediaVolume(getString(Obj,1));
                    break;

                case Action.sendURL:
                    app().sendURLClose(getString(Obj,1));
                    return;

                case Action.sendFromSource:
                    app().startFromJson(getString(Obj,1), getInt(Obj,2), getInt(Obj,3));
                    return;

                case Action.sendVideoFromCollection:
                    app().startFromCollection(getInt(Obj,1), getString(Obj,2));
                    return;

                case Action.sendSourceType:
                    app().mediaProviderClientSideID.set(getInt(Obj,1));
                    break;

                case Action.sendVideoResolution:

                    app().videoResolutionID.set(getInt(Obj,1));
                    break;


                case Action.sendVideoResolutionLive:

                    app().videoResolutionLiveID(getInt(Obj,1));
                    break;

                case Action.sendPlayer:

                    app().playerID.set(getInt(Obj,1));
                    break;

                case Action.sendLivePlayer:

                    app().livePlayerID.set(getInt(Obj,1));
                    break;

                case Action.sendRadioPlayer:

                    app().radioPlayerID.set(getInt(Obj,1));
                    break;

                case Action.sendYoutubePlayer:

                    app().youtubePlayerID.set(getInt(Obj,1));
                    break;

                case Action.sendYoutubeLegacyPlayer:

                    app().legacyYoutubePlayer.set(!app().legacyYoutubePlayer.get());
                    break;

                case Action.sendYoutubePlayerVideo:

                    app().youtubePlayerVideoID.set(getInt(Obj,1));
                    break;

                case Action.sendVideoLanguage:

                    app().videoLanguageID.set(getInt(Obj,1));
                    break;

                case Action.sendHardwareDecoding:
                    app().hardware.set(!app().hardware.get());
                    break;

                case Action.sendCheckMacAndSsid:
                    app().checkMacAndSsid.set(!app().checkMacAndSsid.get());
                    break;

                case Action.sendYoutubeCaching:
                    app().onExo.youtubeCaching.set(!app().onExo.youtubeCaching.get());
                    break;

                case Action.sendURLCaching:
                    app().onExo.urlCaching.set(!app().onExo.urlCaching.get());
                    break;

                case Action.sendLanguagesOfStream:

                    Sources.getSourcesController().recreate(getString(Obj,1),()->Wait.webUIWaitStop());
                    return;

                case Action.sendDeleteLink:

                    app().deleteFromCollection(getInt(Obj,1), getString(Obj,2));

                    break;

                case Action.sendColorFormat:
                    app().onIJK.colorFormatID(getInt(Obj,1));
                    break;
            }
        }

        Wait.webUIWaitStop();
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

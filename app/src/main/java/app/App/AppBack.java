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

import com.emb.player.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import app.BasePanel;
import app.services.BaseServer;
import app.tools.AndroidOsUpdatesListener;
import app.tools.Generators.Requirements.GeneratorWithExpire;
import app.tools.Generators.Requirements.MediaSourceProviders;
import app.tools.Generators.SiteGenerator;
import app.tools.LinksDbHelper;
import app.tools.Players.all.PlayerControllerBase;
import app.tools.Generators.Requirements.Generator;
import app.tools.Players.all.IVideoPlayer;
import app.tools.Generators.UrlGenerator;
import app.tools.Generators.Requirements.Piped.VideoQuality;
import app.tools.Generators.Requirements.Piped.VideoResolution;
import app.Main;
import app.tools.Connection;
import app.tools.Generators.YoutubeGenerator;
import app.tools.Generators.YoutubePlayList;
import app.tools.Players.all.Listeners;
import app.tools.Players.all.Players;
import app.tools.Players.all.PlayersCollection;
import app.tools.Recyclable;
import app.tools.SData;
import app.tools.StaticFunctions;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.BiConsumer;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import app.tools.DisposableTools.WaitDisposable;

import static app.Main.getContext;
import static app.tools.DisposableTools.killAll;
import static app.tools.DisposableTools.forGenerators;
import static app.tools.DisposableTools.lifo;
import static app.tools.DisposableTools.ioThreadPoolScheduler;
import static app.tools.DisposableTools.waitMS;
import static app.tools.StaticFunctions.onErrorSave;
import static app.tools.StaticFunctions.onThrows;
import static server.Home.app;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.SurfaceHolder;
import server.tools.MediaProxyServlet;
import server.tools.VideoSettings;
import server.web.ErrorCodeApp;
import server.web.Sources;
import server.web.Wait;
import static app.App.AppBack.DetectorSet.*;

public class AppBack extends AppWeb {
    private static AppBack app;

    private static final BadSoundFixer badSoundFixer = new BadSoundFixer();
    private static final Recyclable.ListDisposable cleaningInBackground = new Recyclable.ListDisposable(AppBack.class);

    private static boolean appStarted;

    private final MediaStopOrSwitch onStop = new MediaStopOrSwitch();
    public final ChangeVideo videoChanger = new ChangeVideo();

    public final MediaReload mediaReload = new MediaReload();
    private final Sender sender = new Sender();
    private final JsonData jsonData = new JsonData();

    public AsyncRun errorHandel;
    public PlayerControllerBase mediaPlayer;
    public Generator globalGenerator;

    private VideoSettings videoSettings;
    private Runnable panelRun;
    private boolean mediaSeekStart;
    private int bufferedPercentage;


    public AppBack() {
        super();
        app = this;
        errorHandel();
        uiReset();
        badSoundFixer.run();
        volumeSetup();
        appStarted = true;
    }

    public static AppBack getApp() {
        return app;
    }

    public static void create() {
        new AppBack().loadMedia();
    }

    public static void recreateWithCleanData() {
        app().forceStopActivate();

        cleaningInBackground.clear();
        getApp().closePanel(() -> getApp().sendURLBeforeDestroy(),() -> {
            getApp().sendURLAfterDestroy();
            new AppBack();
        }, StaticFunctions.Empty.r);
    }

    public static void recreateWithoutCleanData() {
        app().forceStopActivate();

        cleaningInBackground.clear();
        getApp().closePanel(() -> getApp().sendURLBeforeDestroyWithoutCleanData(), () -> {
            getApp().sendURLAfterDestroy();
            create();
        }, StaticFunctions.Empty.r);
    }

    public String nameOfMedia() {
        if (globalGenerator == null || globalGenerator.nameOfMedia() == null)
            return StaticFunctions.asJsonFormat("");
        return StaticFunctions.asJsonFormat(globalGenerator.nameOfMedia());
    }

    public int isLiveAsInt() {
        if (globalGenerator != null && globalGenerator.isLive())
            return 1;
        return 0;
    }

    public boolean mediaIsNull() {
        return mediaPlayer == null;
    }

    public boolean mediaIsNullFully() {
        return mediaIsNull() || mediaPlayer.isNull();
    }


    public static boolean appStarted() {
        return appStarted;
    }

    public int saveSeek(int MediaPlayerCurrentPos) {
        return MediaPlayerCurrentPos / 1000;
    }

    public long seekLoad() {
        return getSeekPosition() * 1000L;
    }

    public void uiReset() {
        jsonData.jsonSelectedRes=null;
        timer.set(false);
        seekMax(0);
        seekPosition(-1);
        setUp.set(false);
    }

    public void closeDataAndPanelWithoutWaitReset() {
        closePanel(() -> {
            SData.resetToDefault();
            uiReset();
            detectionRecover();
        }, () -> onStop.mediaSessionStop(), () -> {
        });
    }

    public void sendURLWithoutCleanData() {
        cleaningInBackground.clear();

        closePanel(() -> {
            sendURLBeforeDestroyWithoutCleanData();
        }, () -> {
            sendURLAfterDestroy();
        }, () -> {
        });
    }

    public void sendURL() {
        cleaningInBackground.clear();

        closePanel(() -> {
            sendURLBeforeDestroy();
        }, () -> sendURLAfterDestroy(), () -> {
        });
    }

    public void stopSenderOnlyInCurrentThread(){
        sender.sendUrlStartedResetWithoutUIWait();
    }

    public void forceStopActivate(){
        try {
            if(mediaPlayer!=null)
                onStop.mediaForceStopActivate();
        }
        catch (Exception e){}
    }

    public void nullDisplay() {
        if (mediaIsNull()) return;
        ((IVideoPlayer) mediaPlayer).nullDisplay();
    }

    public void refreshDisplay(SurfaceHolder holder) {
        if (mediaIsNull()) return;
        ((IVideoPlayer) mediaPlayer).refreshDisplay(holder);
    }

    public boolean refreshDisplay() {
        return Panel.check(BasePanel.PanelInfo.Displaying);
    }

    public boolean displayOn() {
        return videoPanelIsActive() && !Main.notDisplaying();
    }

    public void startPanel() {
        panelRun.run();
        setUp.set(true);
    }

    public void reloadPanel(boolean refreshDisplay) {
        if (refreshDisplay)
            ((IVideoPlayer) mediaPlayer).setDisplay(Panel.getHolder());
    }


    public boolean videoPanelIsActive() {
        if (globalGenerator.isLive())
            return videoSettings.resolutionLive() != VideoResolution.Audio;

        return videoSettings.resolution() != VideoResolution.Audio;
    }

    public boolean notLoaded() {
        YoutubePlayList.current++;
        return false;
    }

    public void startFromCollection(int tableIndex, String id) throws ExtractionException, IOException {

        isSavable.set(false);
        sender.onMediaChangingUse(() -> {

            String[] all = savedLinks.getAllColumnValuesById(LinksDbHelper.getTableNamesAsString()[tableIndex], id);

            onStop.onMediaChanging(all[1], MediaSourceProviders.values()[Integer.parseInt(all[2])], all[0]);

            return true;
        }, () -> "StartFromCollection-Error");
    }

    public void startFromJson(String sourceName, int subCollectionIndex, int itemIndexInSubCollection) throws JSONException {

        isSavable.set(false);

        sender.onMediaChangingUse(() -> {

            int wi = 0;
            int he = 0;
            String jsRes = null;

            Sources.Source selectedSource = Sources.getSourcesController().getSource(sourceName);
            boolean isRadio = sourceName != null && sourceName.endsWith("_Radio");
            if (selectedSource == null) {
                sender.sendUrlStartedResetOnlyBoolean();
                AppControl.workingStop();
                return jsonData.fullUpdate(wi,he,jsRes);
            }
            JSONArray loadJson = selectedSource.jsonFile;
            JSONObject object = loadJson.getJSONObject(subCollectionIndex);
            JSONObject subDirectory = object.getJSONArray("sources").getJSONObject(itemIndexInSubCollection);

            MediaData link = null;

            try {
                MediaProxyServlet.mediaProxy(object.getBoolean("mediaProxy"));
            }
            catch (Exception e){
                MediaProxyServlet.mediaProxyDefault();
            }

            try{
                he = object.getInt("height");
                wi = object.getInt("width");
            }
            catch (Exception e){
                he = 0;
                wi = 0;
            }

            switch (object.getString("sourceType")) {
                case "SiteEx":

                    extractorPattern(object.getString("defaultPatternExtractStream"));
                    extractorExpirePattern(object.getString("defaultPatternExtractExpire"));

                    link = new MediaData(
                            subDirectory.getString("name"),
                            object.getString("url") + subDirectory.getString("directory"),
                            provider(isRadio, MediaSourceProviders.EXTRACTOR_AUDIO, MediaSourceProviders.EXTRACTOR)
                    );
                    break;
                case "Youtube":

                    link = new MediaData(
                            subDirectory.getString("name"),
                            object.getString("url") + subDirectory.getString("directory"),
                            MediaSourceProviders.YOUTUBE
                    );

                    break;
                case "URL":

                    Sources.CollectionSeletedItems[] correctItems = selectedSource.getCorrectSelection();

                    if (correctItems != null) {
                        boolean isHave = false;

                        for (Sources.CollectionSeletedItems collectionSeletedItems : correctItems) {
                            if (collectionSeletedItems.collectionIndex == subCollectionIndex) {

                                if (collectionSeletedItems.selecteditems[itemIndexInSubCollection] != Sources.Resolutions.Default) {
                                    isHave = true;

                                    String outLink = subDirectory.getString("directryEnd");
                                    jsRes = selectedSource.directoryOfResolution(collectionSeletedItems.selecteditems[itemIndexInSubCollection]);
                                    try {
                                        outLink = subDirectory.getString("directoryFirst") + jsRes + outLink;
                                        link = new MediaData(
                                                subDirectory.getString("name"),
                                                outLink,
                                                provider(isRadio, MediaSourceProviders.LIVE_AUDIO_URL, MediaSourceProviders.LIVE_VIDEO_URL)
                                        );
                                    } catch (Exception ed) {
                                        link = new MediaData(
                                                subDirectory.getString("name"),
                                                outLink,
                                                provider(isRadio, MediaSourceProviders.LIVE_AUDIO_URL, MediaSourceProviders.LIVE_VIDEO_URL)
                                        );

                                        onErrorSave("StartFromJson", ed);
                                        extractorPattern(subDirectory.getString("defaultPatternExtractStream"));
                                        extractorExpirePattern(subDirectory.getString("defaultPatternExtractExpire"));

                                        MediaData finalLink = link;
                                        onStop.onMediaChanging(finalLink.directory, finalLink.provider, finalLink.name);
                                        return jsonData.fullUpdate(wi,he,jsRes);
                                    }
                                }
                                break;
                            }
                        }

                        if (!isHave) {
                            link = new MediaData(
                                    subDirectory.getString("name"),
                                    subDirectory.getString("directory"),
                                    provider(isRadio, MediaSourceProviders.LIVE_AUDIO_URL, MediaSourceProviders.LIVE_VIDEO_URL)
                            );
                        }
                    } else {
                        link = new MediaData(
                                subDirectory.getString("name"),
                                subDirectory.getString("directory"),
                                provider(isRadio, MediaSourceProviders.LIVE_AUDIO_URL, MediaSourceProviders.LIVE_VIDEO_URL)
                        );
                    }
                    break;
            }

            if (link == null){
                sender.sendUrlStartedResetOnlyBoolean();
                AppControl.workingStop();
                return jsonData.fullUpdate(wi,he,jsRes);
            }

            MediaData finalLink1 = link;

            onStop.onMediaChanging(finalLink1.directory, finalLink1.provider, finalLink1.name);

            return jsonData.fullUpdate(wi,he,jsRes);
        }, () -> "StartFromJson-Error");
    }

    public void deleteFromCollection(int tableIndex, String id) {

        savedLinks.deleteById(LinksDbHelper.getTableNamesAsString()[tableIndex], id);
    }

    public void sendURLClose(String url) {
        cleaningInBackground.clear();
        isSavable.set(true);

        sender.sendUrlStart(() -> {
            loadDataWithoutChecking(url, mediaProviderClientSideID.getSave(), null);
            return true;
        }, () -> "SendURLClose-Error");
    }

    public void loadDataWithoutChecking(String url, int MediaProviderID, String nameOfMedia) {
        cleaningInBackground.add(() -> {
            mediaClientSideProvider = MediaSourceProviders.values()[MediaProviderID];
            loadData(url, mediaClientSideProvider, nameOfMedia);
        }, () -> sender.sendUrlStartedResetOnlyBooleanWithoutUIWait(), () -> {
        }, forGenerators, "Extractor");
    }

    private YoutubeGeneratorTryAndType tryGenerateYoutubeContent(YoutubeGenerator youtubeGenerator) {
        StreamingService.LinkType type = youtubeGenerator.getType();

        if (type == StreamingService.LinkType.PLAYLIST) {
            //SafeCallable.onlyReboot = true;
            YoutubePlayList.ensureNotDisposed();
            // Create collection and get first generator directly
            try {
                youtubeGenerator = YoutubePlayList.createCollection(baseUrl, videoSettings, new ListenersYoutube(), loopOn(), hardware.get());
            } catch (ExtractionException | IOException e) {
                onErrorSave("SendURLClose-YoutubePlayList.CreateCollection", e);
                return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.Error, type,youtubeGenerator);
            }

            if (youtubeGenerator == null) {
                // Fallback: wait and try to get first generator
                waitMS(1000);
                youtubeGenerator = YoutubePlayList.getFirst(10, 300);
                if (youtubeGenerator == null) {
                    return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.Error, type,youtubeGenerator);
                }
            }

            playlist.set(true);
            selectedTable(LinksDbHelper.TableName.YOUTUBE_PLAYLIST_LINKS.getIndex());
            try {
                youtubeGenerator.generateInfoAuto();
            } catch (ExtractionException | IOException e) {
                onErrorSave("SendURLClose-GenerateInfoAuto", e);
                return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.Error, type,youtubeGenerator);
            }
        } else {
            try {
                youtubeGenerator.generateInfoAuto();
            } catch (ExtractionException | IOException e) {
                onErrorSave("SendURLClose-GenerateInfoAuto", e);
                return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.Error, type,youtubeGenerator);
            }

            switch (youtubeGenerator.getInfo().getStreamType()) {
                case LIVE_STREAM:
                case POST_LIVE_STREAM:
                case AUDIO_LIVE_STREAM:
                    selectedTable(LinksDbHelper.TableName.YOUTUBE_LIVE_LINKS.getIndex());
                    break;
            }
        }

        new LoaderForPlayerYoutube(youtubeGenerator).updateLoader();

        // Load content and start media session
        youtubeGenerator.loadContent();
        Players.updateIsLive(youtubeGenerator.isLive());

        if (youtubeGenerator.isNotGenerated())
            return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.NotMade, type,youtubeGenerator);

        return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.Made, type,youtubeGenerator);
    }

    private void onYoutubeLoadEnd()
    {
        seekMax(globalGenerator.getMaxSeek());
        startPanel();
    }

    private void youtubeVideoScreenSizeUpdate(YoutubeGenerator youtubeGenerator,ListenersYoutube listenersYoutube){
        YoutubeGenerator.Size videoSize = youtubeGenerator.getSize();

        if(videoSize.getWidth() < 1 || videoSize.getHeight() < 1)
            listenersYoutube.setScreenUpdateToDefault();
        else
            Panel.aspectChange(videoSize.getWidth(), videoSize.getHeight());
    }

    public boolean loadData(String url, MediaSourceProviders sourceProvider, String nameOfMedia) {
        saveMedia(sourceProvider, url, nameOfMedia);

        this.baseUrl = url;
        playlist.set(false);
        timer.set(true);
        onExo.cachingFailed(false);
        onExo.setTemp(ExoPlayerOnly.Types.URL);

        errorHandel.detection.setup(2000,6000);

        loop.switchToNormal();
        switch (sourceProvider) {
            case YOUTUBE:

                if (!Connection.isHaveInternet())
                    return AppControl.waitAndIsWorkingStop();

                onExo.setTemp(ExoPlayerOnly.Types.Youtube);
                legacyYoutubePlayer.reset();
                PlayersCollection audioOrVideoP;
                PlayersCollection videoP = null;
                if (legacyYoutubePlayer.make())
                    audioOrVideoP = PlayersCollection.OEM;
                else {
                    audioOrVideoP = player(youtubePlayerID.getSave());
                    videoP = player(youtubePlayerVideoID.getSave());
                }

                selectedTable(LinksDbHelper.TableName.YOUTUBE_LINKS.getIndex());

                updateVideoSettings(audioOrVideoP, videoP, getLivePlayer());

                ListenersYoutube listenn = new ListenersYoutube();
                YoutubeGenerator youtubeGenerator = new YoutubeGenerator(baseUrl, videoSettings, listenn, hardware.get());
                YoutubeGeneratorTryAndType current = null;

                int i = 0;
                while (true) {
                    //start from here again if failed
                    if (!youtubeGenerator.generateLink(10))
                        return AppControl.waitAndIsWorkingStop();

                    current = tryGenerateYoutubeContent(youtubeGenerator);

                    if (current.made == YoutubeGeneratorTry.Made)
                        break;
                    else if (current.made == YoutubeGeneratorTry.Error || i > 5)
                        return AppControl.waitAndIsWorkingStop();

                    i++;
                }

                if(current.type == StreamingService.LinkType.PLAYLIST)
                {
                    youtubeVideoScreenSizeUpdate(current.youtubeGenerator,listenn);

                    loop.switchToPlaylist();

                    onYoutubeLoadEnd();

                    // Start background loading of next few videos (not all)
                    YoutubePlayList.loadInitialVideosInBackground(baseUrl, Math.min(3, YoutubePlayList.streamInfoItem.size() - 1));
                    YoutubePlayList.current = SData.getInt(SData.Data.SavedIndexPlayList);

                    break;
                }


                if (globalGenerator.isLive())
                    listenn.setScreenUpdateToDefault();
                else
                    youtubeVideoScreenSizeUpdate(youtubeGenerator,listenn);

                onYoutubeLoadEnd();
                break;

            case EXTRACTOR:
                updateVideoSettings(player(playerID.getSave()), player(playerID.getSave()), getLivePlayer());
                Players.updateIsLive(true);
                if (!extractorLoader(nameOfMedia))
                    return AppControl.waitAndIsWorkingStop();

                break;

            case EXTRACTOR_AUDIO:
                updateAudioSettings(player(playerID.getSave()), player(playerID.getSave()), getRadioPlayer());
                Players.updateIsLive(true);
                if (!extractorLoader(nameOfMedia))
                    return AppControl.waitAndIsWorkingStop();

                break;

            case VIDEO_URL:
                updateVideoSettings(player(playerID.getSave()), player(playerID.getSave()), getLivePlayer());
                Players.updateIsLive(false);
                urlLoader(false, nameOfMedia);
                    /*
            if(startedPanelOneTime)
                setUp.Set(true);*/
                break;

            case LIVE_VIDEO_URL:
                updateVideoSettings(player(playerID.getSave()), player(playerID.getSave()), getLivePlayer());
                Players.updateIsLive(true);
                urlLoader(true, nameOfMedia);
                break;

            case AUDIO_URL:
                updateAudioSettings(player(playerID.getSave()), player(playerID.getSave()), getRadioPlayer());
                Players.updateIsLive(false);
                urlLoader(false, nameOfMedia);
                break;

            case LIVE_AUDIO_URL:
                updateAudioSettings(player(playerID.getSave()), player(playerID.getSave()), getRadioPlayer());
                Players.updateIsLive(true);
                urlLoader(true, nameOfMedia);
                break;
        }

        if (globalGenerator.isLive())
            errorHandel.detection.setup(30,45000);

        return false;
    }

    public void startVideo(Runnable onEnd) {
        /*
        if(!GetTimer()&&!Connection.isHaveInternet()) {
            ErrorHandel.MediaErrorRun();
            return;
        }
        */

        if (mediaIsNull()) {
            return;
        }

        mediaPlayer.start(seekLoad());

        mediaPlayer.waitPlay(()->{
            timer.set(true);
            onEnd.run();
        });
    }

    public void stopVideo(String value) {
        stopVideo(Integer.parseInt(value));
    }

    public void stopVideo(int value) {
        if (mediaIsNull()) return;

        badSoundFixer.run();
        seekPosition(value);
        mediaPlayer.pause();
        timer.set(false);
    }

    public void loopSwitch(int mode) {
        Loops l;
        if (loop.get(mode)) {
            loop.set(0);
            l = loopDefault();
        } else {
            loop.set(mode);
            l = loopUpdate();
        }

        if (!mediaIsNull()) {
            mediaPlayer.loop(l.getLoop());
            mediaPlayer.playListLoop(l.getPlayListLoop());
        }
    }

    public Loops loopDefault() {
        return new Loops(false, false);
    }

    public Loops loopUpdate() {
        if (globalGenerator.isLive()) {
            loop.set(0);
        } else switch (loop.getSave()) {
            case 1:
                return new Loops(true, false);
            case 2:
                if (playlist.get()) {
                    return new Loops(false, true);
                } else {
                    break;
                }
        }

        return loopDefault();
    }

    public boolean loopOn() {
        return loop.get(1);
    }

    public boolean playlistLoopOn() {
        return loop.get(2);
    }

    public void muteVolume() {
        mediaPlayer.setVolume(0);
    }

    public void loadVolume(float volume) {
        if (mediaPlayer != null)
            mediaPlayer.setVolume(volume);
    }

    public void loadVolume() {
        loadVolume((float) volumePosition.getSave() / VOLUME_MAX);
    }

    public void mediaVolume(String value) {
        mediaVolume(Integer.parseInt(value));
    }


    public void mediaVolume(float percent) {
        volumePosition(percent);
        loadVolume(percent);
    }

    public void mediaUpdateSeekPosition() {
        try {
            if(mediaIsNullFully())
                return;

            if(globalGenerator.waitStarted())
                return;

            long curP = mediaPlayer.getCurrentPosition();

            if(curP==0)
                seekPosition(0);
            else
                seekPosition(saveSeek((int)curP));

        } catch (Exception e) {}
    }

    public boolean mediaSeekStart() {
        if (mediaSeekStart) {
            mediaSeekStart = false;
            return true;
        }
        return false;
    }

    protected void mediaVolume(int value) {
        volumePosition.set(value);
        loadVolume();
    }

    protected void errorHandel() {
        errorHandel = new AsyncRun();
    }

    private void preloadAdjacentVideos(int currentIndex) throws ExtractionException, IOException {
        if (YoutubePlayList.isLoadingMoreVideos() || YoutubePlayList.isDisposed()) {
            return;
        }

        int totalVideos = YoutubePlayList.getTotalVideosCount();

        // Pre-load next 2 videos
        for (int i = 1; i <= 2; i++) {
            int nextIndex = currentIndex + i;
            if (nextIndex < totalVideos) {
                YoutubeGenerator nextGen = YoutubePlayList.getGenerator(nextIndex);
                if (nextGen == null || !nextGen.IsLoaded()) {
                    YoutubePlayList.AddSingleElement(nextIndex);
                }
            }

            // Pre-load previous video (for going back)
            int prevIndex = currentIndex - i;
            if (prevIndex >= 0) {
                YoutubeGenerator prevGen = YoutubePlayList.getGenerator(prevIndex);
                if (prevGen == null || !prevGen.IsLoaded()) {
                    YoutubePlayList.AddSingleElement(prevIndex);
                }
            }
        }

        // Check if we need to load more pages
        boolean approachingEnd = currentIndex >= YoutubePlayList.getLoadedVideosCount() - 3;
        boolean hasMorePages = YoutubePlayList.hasMoreVideos();

        if (approachingEnd && hasMorePages && !YoutubePlayList.isLoadingMoreVideos()) {
            YoutubePlayList.incrementChangeCount();
            if (YoutubePlayList.getChangeCount() >= 2) {
                YoutubePlayList.resetChangeCount();
                // Load next page asynchronously
                cleaningInBackground.add(() -> {
                    try {
                        YoutubePlayList.reload();
                    } catch (Exception e) {
                        onErrorSave("PreloadAdjacentVideos", e);
                        // Silent fail
                    }
                }, ioThreadPoolScheduler, "LoadNextError-Preloader");
            }
        }
    }

    private MediaSourceProviders provider(boolean isAudio, MediaSourceProviders audio, MediaSourceProviders video) {

        if (isAudio)
            return audio;

        return video;
    }

    private void detectionRecover() {
        errorHandel.currentRecover.currentStopAndResetState();
        errorHandel.detection.stop();
    }

    private void closePanel(Runnable beforeOnDestroy, Runnable onDestroy, Runnable onError){

        cleaningInBackground.add(()->{

            beforeOnDestroy.run();

            if(Panel.panelIsNull()){
                onDestroy.run();
                //cleaningInBackground.addStartAfterWait(50,onDestroy,StaticFunctions.Empty.r,forGenerators,"onDestroyMedia");
                return;
            }

            try {
                Panel.close(()->{
                    cleaningInBackground.add(onDestroy,forGenerators,"onDestroyMedia");
                    //cleaningInBackground.addStartAfterWait(50,onDestroy,StaticFunctions.Empty.r,forGenerators,"onDestroyMedia");
                }).accept(cleaningInBackground);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }

        },onError,forGenerators,"closePanel");
    }

    private void sendURLAfterDestroy()
    {
        onExo.cachingFailed(false);

        onStop.mediaSessionStopAndWaitAndIsWorkingStop();
    }
    private void sendURLBeforeDestroyWithoutCleanData()
    {
        setUp.set(false);

        detectionRecover();

        sender.sendUrlStartedReset();
    }
    private void beforeDestroy(){
        SData.set(SData.Data.UndefiledError,false);
        mediaReload.reset();
    }
    private void sendURLBeforeDestroy()
    {
        beforeDestroy();

        jsonData.reset();
        MediaProxyServlet.mediaProxyDefault();
        SData.resetToDefault();
        timer.set(false);
        seekMax(0);
        seekPosition(-1);
        sendURLBeforeDestroyWithoutCleanData();
    }
    private boolean isStreamAvailable(String streamUrl) throws IOException {
        URL url = new URL(streamUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("HEAD");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        int responseCode = connection.getResponseCode();

        // Check if response indicates success (200-299)
        return responseCode >= 200 && responseCode < 300;
    }

    private void updateVideoSettings(PlayersCollection audioOrVideo, PlayersCollection video, PlayersCollection live)
    {
        Players.updateEngines(audioOrVideo,video,live,VideoResolution.values()[videoResolutionID.getSave()],VideoResolution.values()[getVideoResolutionLiveID()]);
        videoSettings = new VideoSettings(Players.resolutionLive(),Players.resolution(),VideoQuality.BEST_QUALITY, LANGUAGES[videoLanguageID.getSave()]);
    }

    private void updateAudioSettings(PlayersCollection audioOrVideo, PlayersCollection video, PlayersCollection live)
    {
        Players.updateEngines(audioOrVideo,video,live,VideoResolution.Audio,VideoResolution.Audio);
        videoSettings = new VideoSettings(Players.resolutionLive(),Players.resolution(),VideoQuality.BEST_QUALITY, LANGUAGES[videoLanguageID.getSave()]);
    }

    private <T extends ListenersSet> T listenerSet(T newListerForSiteGenerator){
        if(jsonData.height.get()!=0){
            newListerForSiteGenerator.setScreenUpdateToEmpty();
            Panel.aspectChange(jsonData.width.get(), jsonData.height.get());
        }
        return newListerForSiteGenerator;
    }

    private void urlLoader(boolean isLive,String nameOfMedia)
    {
        UrlGenerator urlGenerator = new UrlGenerator(isLive,baseUrl, listenerSet(new ListenersSet()), hardware.get(),nameOfMedia);

        new LoaderForPlayerURL(urlGenerator).updateLoaderAndKiller();

        selectedTable(LinksDbHelper.TableName.URLS.getIndex());

        startPanel();
    }

    private boolean extractorLoader(String nameOfMedia)
    {
        if(!Connection.isHaveInternet())
            return false;

        //"(https?://[^\"']*index\\.m3u8\\?e=[^\"']*)","e=(\\d+)"
        SiteGenerator siteGenerator = new SiteGenerator(jsonData.jsonSelectedRes,baseUrl,listenerSet(new ListenerSiteGenerator()), hardware.get(),true, getExtractorPattern(), getExtractorExpirePattern(),nameOfMedia);

        if(!siteGenerator.generateLink(10))
            return false;

        try {
            siteGenerator.generateInfo();
        }  catch (ExtractionException | IOException e) {
            onErrorSave("SendURLClose-GenerateInfo",e);
            return false;
        }

        new LoaderForSiteGenerator(siteGenerator).updateLoaderAndKiller();

        // Load content and start media session
        siteGenerator.loadContent();

        if(siteGenerator.isNotGenerated())
            return false;

        startPanel();

        return true;
    }
    private boolean loadMediaOnFail(){
        sender.sendUrlStartedResetOnlyBooleanWithoutUIWait();
        return true;
    }
    private void saveTempCheck(){
        ErrorCodeApp.checkIsEqualToSavedData.set("|| ip: "+BaseServer.getIP()+" oldIP: "+StaticFunctions.oldIP()+
                " BSSID: "+AndroidOsUpdatesListener.getCurrentBSSID()+" oldBSSID: "+StaticFunctions.oldBSSID()+" |");
    }
    private void loadMedia(){
        sender.sendUrlStart(()->{
            boolean undefi = SData.get(SData.Data.UndefiledError);

            if(mediaPlayer!=null || !undefi)
                return loadMediaOnFail();

            int s = 0;
            while (!BaseServer.getIP().equals(StaticFunctions.oldIP()) ||
                    !AndroidOsUpdatesListener.getCurrentBSSID().equals(StaticFunctions.oldBSSID()))
            {
                if(s<10){
                    s++;
                    waitMS(500);
                }
                else{
                    saveTempCheck();
                    ErrorCodeApp.checkIsEqualToSavedData.append(" NOT LOADED BECAUSE NOT SAME ||");
                    return loadMediaOnFail();
                }
            }

            saveTempCheck();
            SavedMedia recovered = getSavedMedia();

            if(recovered==null)
                return loadMediaOnFail();

            ErrorCodeApp.checkIsEqualToSavedData.append(System.lineSeparator()+
                    "| Name:"+recovered.getName()+
                    " URL:"+recovered.getURL()+
                    " Seek:"+recovered.getSeek()+
                    " ProviderID:"+recovered.getProviderID()+" ||");

            if(globalGenerator == null && mediaPlayer == null)
            {
                seekPosition((int)(recovered.getSeek()/1000));
                mediaClientSideProvider = MediaSourceProviders.values()[recovered.getProviderID()];
                int i = 0;
                while (i<20){
                    try{
                        if(loadData(recovered.getURL(), mediaClientSideProvider, recovered.getName())){
                            i++;
                            waitMS(500);
                        }
                        else{
                            return true;
                        }
                    } catch (Exception e) {
                        i++;
                        waitMS(500);
                    }
                }
            }

            return loadMediaOnFail();
        },()->{
            loadMediaOnFail();
            return "Loading-Error";
        });
    }
    private void loadOrLoadAndStart(boolean andStart, @NonNull Callable<Long> seek, @NonNull Runnable beforeForLoadAndStart)
    {
        if(andStart){
            beforeForLoadAndStart.run();
            try {
                mediaPlayer.loadAndStart(seek.call());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        else
            mediaPlayer.load();
    }

    private void onErrorFail(Exception e)
    {
        onErrorSave("AppBack-OnError",e);
        globalGenerator.mediaError.started = true;
        globalGenerator.mediaErrorRun();
    }
    private void volumeSetup()
    {
        if(volumePosition.getSave()==-1)
            mediaVolume(0.5f);
        else
            loadVolume();
    }

    public enum YoutubeGeneratorTry{
        NotMade, Error, Made
    }

    public class YoutubeGeneratorTryAndType{
        private YoutubeGeneratorTry made;
        private StreamingService.LinkType type;
        private YoutubeGenerator youtubeGenerator;

        public YoutubeGeneratorTryAndType(YoutubeGeneratorTry made,StreamingService.LinkType type,YoutubeGenerator youtubeGenerator){
            this.made = made;
            this.type = type;
            this.youtubeGenerator = youtubeGenerator;
        }
    }

    public class MediaReload {
        private final int MAX_TRY_COUNT = 3;
        private final int MAX_TRY_CHECK_COUNT = 3;

        // Standard variables are safe now because of the synchronized block
        private boolean isRan;
        private int currentTryCounts;

        private final Callable<Boolean> checkingOnlyIsRan = () -> isRan;

        private final Callable<Boolean> checkingMultiple = () -> {
            if (isRan)
                return true;

            if (currentTryCounts >= MAX_TRY_COUNT) {
                sendURL();
                return true;
            }

            currentTryCounts++;
            return false;
        };

        private Callable<Boolean> checking = checkingMultiple;

        // Synchronize state changes
        public synchronized void reset() {
            currentTryCounts = 0;
            isRan = false;
            checking = checkingMultiple;
        }

        // Synchronize state changes
        public synchronized void tryLoadAfterFirstPlay() {
            checking = checkingOnlyIsRan;
        }

        public void tryLoad(Callable<Boolean> ifTrueMake, @NonNull Runnable onEnd) {
            // Lock the critical check-and-update section so only one thread
            // can enter at a time. This fixes the Samsung memory cache issue!
            synchronized (this) {
                try {
                    if (checking.call())
                        return;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                isRan = true;
            }

            // Run tryBase OUTSIDE the synchronized block so it doesn't block
            // other non-critical operations while the media loads.
            closePanel(() -> {
                        try {
                            if (!ifTrueMake.call())
                                return;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }

                        detectionRecover();
                    },
                    () ->{
                        mediaKillOnly(()->{
                            startPanel();
                            onEnd.run();
                        });
                    },
                    onEnd);
        }
        private void mediaKillOnly(Runnable onComplete){
            if(mediaIsNull()){
                isRan = false;
                onComplete.run();
                return;
            }

            PlayerControllerBase oldPlayer = mediaPlayer;

            videoChanger.stop();

            globalGenerator.mediaErrorStop();

            if(oldPlayer != null)
            {
                Runnable onComp = ()->{
                    oldPlayer.release();
                    mediaPlayer = null;
                    isRan = false;
                    onComplete.run();
                };

                Callable<Boolean> check = new Callable<Boolean>() {
                    private int i;
                    @Override
                    public Boolean call() throws Exception {
                        boolean result = oldPlayer.notClosable() && i < MAX_TRY_CHECK_COUNT;
                        i++;
                        return result;
                    }
                };

                cleaningInBackground.addPollingTaskWithTimeOut(
                        check,
                        StaticFunctions.Empty.r,
                        onComp,
                        onComp,
                        ()->{
                            try {
                                onComp.run();
                            } catch (Exception e) {}
                        },
                        StaticFunctions.Empty.a,
                        1500,
                        20000,
                        lifo,
                        lifo,
                        "mediaKillOnly");

                return;
            }

            mediaPlayer = null;
            isRan = false;
            onComplete.run();
        }
    }

    private static final class ChangerData {
        final int plusOrMinus;
        YoutubeGenerator selected;
        int newIndex;
        int currentIndex;
        boolean generate;

        private volatile BooleanSupplier cancelledSupplier = () -> false;

        ChangerData(int plusOrMinus) {
            this.plusOrMinus = plusOrMinus;
        }

        /** Wired up once, right before this run's pipeline starts. */
        void bindCancellation(BooleanSupplier supplier) {
            this.cancelledSupplier = supplier;
        }

        boolean isCancelled() {
            return cancelledSupplier.getAsBoolean() || Thread.currentThread().isInterrupted();
        }
    }

    public class ChangeVideo {

        private final Runnable pureUpdate = () -> updateChanger(1, 10, 1000);
        private final Runnable updateInNewTask = () ->
                cleaningInBackground.add(pureUpdate, StaticFunctions.Empty.a, StaticFunctions.Empty.r, lifo, "PlayListLoop");

        private final StaticFunctions.Starter playlistLoop = new StaticFunctions.Starter() {
            @Override
            protected void firstLaunch() {
                current.run();
            }

            @Override
            protected void secondLaunches() {
            }
        };

        private Runnable current = updateInNewTask;
        private Disposable mediaChanger;

        public synchronized Disposable updateChanger(int plusOrMinus) {
            return updateChanger(plusOrMinus, 1, 0);
        }

        private synchronized Disposable updateChanger(int plusOrMinus, int maxAttempts, long delayMillis) {
            badSoundFixer.run();
            disposeChanger();
            mediaPlayer.resetOnlyIsEnded();
            SData.setLong(SData.Data.SavedSeek, 0);

            ChangerData ch = new ChangerData(plusOrMinus);
            int attempts = Math.max(1, maxAttempts);

            mediaChanger = Completable
                    .create(emitter -> {
                        ch.bindCancellation(emitter::isDisposed);
                        runPipeline(ch, attempts, delayMillis);
                        if (!emitter.isDisposed()) {
                            emitter.onComplete();
                        }
                    })
                    .subscribeOn(forGenerators)
                    .subscribe(() -> {}, t -> onErrorSave("ChangeVideo-Error", t));

            return mediaChanger;
        }

        public synchronized void stop() {
            disposeChanger();
            current = updateInNewTask;
            playlistLoop.reset();
            ErrorCodeApp.videoChanger.set("videoChanger: ");
        }

        public synchronized void onPlaylistLoop() {
            playlistLoop.run();
        }

        public synchronized void onPlaylistLoopInCurrentTask() {
            current = pureUpdate;
            playlistLoop.run();
            current = updateInNewTask;
        }

        /** check -> (maybe) generate -> apply, retried up to maxAttempts times on transient failures. */
        private void runPipeline(ChangerData ch, int maxAttempts, long delayMillis) throws ExtractionException, IOException {
            int attempt = 0;
            while (true) {
                try {
                    if (!inFirstCheck(ch)) {
                        ErrorCodeApp.videoChanger.append("|inFirstCheck did not pass| ");
                    }
                    else if (ch.generate && !ifNotGeneratedGenerate(ch)) {
                        ErrorCodeApp.videoChanger.append("|ifNotGeneratedGenerate did not pass| ");
                    }
                    else if (!ifNotLoadedAgain(ch)) {
                        ErrorCodeApp.videoChanger.append("|ifNotLoadedAgain did not pass| ");
                    }
                    return;
                } catch (IOException | ExtractionException retryable) {
                    attempt++;
                    if (attempt >= maxAttempts || ch.isCancelled()) {
                        throw retryable;
                    }
                    waitMS(delayMillis);
                }
            }
        }

        private synchronized void disposeChanger() {
            if (mediaChanger != null && !mediaChanger.isDisposed()) {
                mediaChanger.dispose();
            }
            mediaChanger = null;
        }

        private boolean inFirstCheck(ChangerData ch) {
            mediaPlayer.startLoading();

            if (YoutubePlayList.isDisposed() || YoutubePlayList.getTotalVideosCount() == 0) {
                return false;
            }

            ch.currentIndex = YoutubePlayList.current;
            int totalVideos = YoutubePlayList.getTotalVideosCount();
            ch.newIndex = ch.currentIndex + ch.plusOrMinus;

            if (ch.plusOrMinus == -1 && ch.currentIndex == 0) {
                ch.newIndex = totalVideos - 1;
            } else if (ch.currentIndex == totalVideos - 1 && ch.plusOrMinus == 1) {
                ch.newIndex = 0;
            } else {
                boolean shouldLoop = playlistLoopOn();
                if (ch.newIndex < 0) {
                    ch.newIndex = shouldLoop ? totalVideos - 1 : 0;
                } else if (ch.newIndex >= totalVideos) {
                    ch.newIndex = shouldLoop ? 0 : totalVideos - 1;
                }
            }

            if (ch.newIndex < 0 || ch.newIndex >= totalVideos) {
                return notLoaded();
            }

            ch.selected = YoutubePlayList.getGenerator(ch.newIndex);

            if (ch.selected == null || !ch.selected.IsLoaded()) {
                YoutubePlayList.AddSingleElement(ch.newIndex);

                int maxWaitAttempts = 10;
                for (int waitAttempt = 0; waitAttempt < maxWaitAttempts; waitAttempt++) {
                    if (ch.isCancelled()) return notLoaded();
                    waitMS(300);
                    if (ch.isCancelled()) return notLoaded();

                    ch.selected = YoutubePlayList.getGenerator(ch.newIndex);
                    if (ch.selected != null && ch.selected.IsLoaded()) {
                        break;
                    }

                    if (YoutubePlayList.isDisposed()) {
                        return notLoaded();
                    }
                }

                if (ch.selected == null || !ch.selected.IsLoaded()) {
                    try {
                        if (YoutubePlayList.streamInfoItem != null && ch.newIndex < YoutubePlayList.streamInfoItem.size()) {
                            String videoUrl = YoutubePlayList.streamInfoItem.get(ch.newIndex).getUrl();
                            ch.selected = new YoutubeGenerator(
                                    videoUrl,
                                    YoutubePlayList.videoSettings,
                                    YoutubePlayList.listeners,
                                    YoutubePlayList.hardware
                            );
                            ch.generate = true;
                        } else {
                            return notLoaded();
                        }
                    } catch (Exception e) {
                        onErrorSave("ChangeVideo", e);
                        return notLoaded();
                    }
                }
            }

            return true;
        }

        private boolean ifNotGeneratedGenerate(ChangerData ch) throws ExtractionException, IOException {
            if (ch.selected.generateLink(5)) {
                ch.selected.reloadContent();
                if (YoutubePlayList.getGenerator(ch.newIndex) == null) {
                    YoutubePlayList.youtubeGenerators.add(ch.selected);
                }
                return true;
            }
            return notLoaded();
        }

        private boolean ifNotLoadedAgain(ChangerData ch) throws ExtractionException, IOException {
            if (ch.selected == null || !ch.selected.IsLoaded()) {
                return notLoaded();
            }

            YoutubeGenerator.Size videoSize = ch.selected.getSize();
            Panel.updateScreen(videoSize.getWidth(), videoSize.getHeight());

            YoutubeGenerator oldGenerator = (YoutubeGenerator) globalGenerator;

            YoutubePlayList.current = ch.newIndex;
            YoutubePlayList.changed = true;

            new LoaderForPlayerYoutube(ch.selected).updateLoaderAndKillerWithYoutubePlayListDispose();

            if (oldGenerator != null && oldGenerator != globalGenerator) {
                try {
                    YoutubePlayList.updateToDefault(oldGenerator);
                } catch (Exception e) {
                    onErrorSave("ChangeVideo-YoutubePlayList.UpdateToDefault", e);
                }
            }

            globalGenerator.mediaError.started = true;

            saveMedia(MediaSourceProviders.YOUTUBE,
                    globalGenerator.getVideoUrl() + "&list=" + YoutubePlayList.youtubePlaylistId(),
                    globalGenerator.nameOfMedia());

            globalGenerator.mediaErrorRun();

            for (int i = 0; i < 5; i++) {
                if (ch.isCancelled()) return notLoaded();
                waitMS(200);
                if (ch.isCancelled()) return notLoaded();
                if (!globalGenerator.mediaError.started) break;
            }

            if (mediaPlayer.isPlayingDynamic(60, 50)) {
                SData.setInt(SData.Data.SavedIndexPlayList, ch.currentIndex);
            }

            preloadAdjacentVideos(ch.newIndex);

            Wait.webUIWaitStop();
            playlistLoop.reset();
            return true;
        }
    }

    public static class Panel extends BasePanel
    {
        private static Consumer<SurfaceHolder> loader;

        public static void loadPanel(Consumer<SurfaceHolder> onLoad)
        {
            loader = onLoad;

            cleaningInBackground.add(() -> Main.loadPage(Panel.class),
                    ()-> AppControl.workingStop(),
                    ()-> AppControl.workingStop(),
                    AndroidSchedulers.mainThread(),
                    "LoadPanel-Error");
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        @Override
        protected void setOnLoadVideo(SurfaceHolder holder) throws Throwable {
            loader.accept(holder);
        }

        @Override
        protected void setOnBackPressed() {
            getApp().sendURL();
        }

        @Override
        protected void setRefreshDisplay(SurfaceHolder holder) {
            getApp().refreshDisplay(holder);
        }

        @Override
        protected void setOnNullDisplay() {
            getApp().nullDisplay();
        }
    }
    public static class Loops
    {
        private final boolean loop;
        private final boolean playlistLoop;

        public Loops(boolean loop, boolean playlistLoop)
        {
            this.loop = loop;
            this.playlistLoop = playlistLoop;
        }

        public boolean getLoop()
        {
            return loop;
        }
        public boolean getPlayListLoop()
        {
            return playlistLoop;
        }
    }

    public class ListenerSiteGenerator extends ListenersSet{
        @Override
        protected void onErrorEnd(){
            try {
                SiteGenerator localGenerator = (SiteGenerator)globalGenerator;
                localGenerator.generateContent();
                localGenerator.reloadContent();
                super.onErrorEnd();
            }
            catch (Exception e){}
        }
    }

    public class ListenersSet extends Listeners
    {
        private final long msDelay = 10000;
        private long triggeredTime;

        private final StaticFunctions.StarterWithBoolean checkTryFix = new StaticFunctions.StarterWithBoolean() {
            @Override
            protected Boolean firstLaunch() {
                triggeredTime = System.currentTimeMillis();
                return true;
            }

            @Override
            protected Boolean secondLaunches() {
                if(triggeredTime + msDelay < System.currentTimeMillis()){
                    firstLaunch();
                    return true;
                }
                return false;
            }
        };

        private void onNotLoaded() {
            try {
                if(checkTryFix.call())
                    mediaReload.tryLoad(()->{
                        if(onExo.cachingFailed())
                            return false;

                        if(onExo.getTemp())
                            onExo.cachingFailed(true);
                        else
                            return false;

                        return true;
                    },StaticFunctions.Empty.r);
            } catch (Exception e) {}
        }

        private void onError(){
            try {
                if(checkTryFix.call())
                {
                    badSoundFixer.run();
                    mediaPlayer.beforeOnErrorStarted();
                    cleaningInBackground.add(()->{

                        if(AndroidOsUpdatesListener.isHaveConnection()&& globalGenerator.mediaError.started)
                            return;

                        try {
                        /*if(IfIsNotCollectionOrCollectionIsNotLoopingWillBeClosePlayerIfIsNotMakedFirstPlay())
                           return;*/

                            if(mediaIsNullFully())
                                return;

                            if(playlist.get()&& playlistLoopOn()&&
                                    mediaPlayer.getDuration()<mediaPlayer.getSeekAfterIsPlayingDynamic()+10)
                            {
                                //videoChanger.UpdateChanger(1);
                                videoChanger.onPlaylistLoopInCurrentTask();
                                return;
                            }

                            globalGenerator.mediaError.started = true;
                            onErrorEnd();
                            globalGenerator.mediaError.started = false;
                        } catch (Exception e) {
                            onErrorFail(e);
                        }
                    },StaticFunctions.Empty.a,StaticFunctions.Empty.r,forGenerators,"OnErrorListener");
                }
            } catch (Exception e) {}
        }

        private BiConsumer<Integer,Integer> screenUpdate;

        public ListenersSet(){
            onCreate();
        }

        protected void onCreate(){
            setScreenUpdateToDefault();
        }

        @CallSuper
        protected void onErrorEnd(){
            mediaPlayer.seekAfterIsPlayingDynamicUpdate();
            mediaPlayer.resetWithoutResetPlayingState();

            //if(timer.Get())
            loadOrLoadAndStart(mediaPlayer.isPlaying(),()->mediaPlayer.getSeekAfterIsPlayingDynamic(),()->{
            });
        }
        
        public void setScreenUpdateToDefault(){
            screenUpdate = (width,height) ->
                    Panel.updateScreen(width, height);
        }

        public void setScreenUpdateToEmpty(){
            screenUpdate = StaticFunctions.Empty.bC;
        }

        @Override
        public void onVideoSizeChangedListener(int width, int height)
        {
            try {
                screenUpdate.accept(width,height);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onStarted(){
            try {
                badSoundFixer.stop();
                globalGenerator.waitMake(() -> mediaPlayer,globalGenerator.isLive(),cleaningInBackground,()->{
                    seekMax(globalGenerator.getMaxSeek());
                    AppControl.waitAndIsWorkingStop();
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public final void onNotLoadedTryAgainToLoad() {
            onNotLoaded();
        }

        @Override
        public final void onErrorListener() {
            onError();
        }

        @Override
        public final void onBufferingUpdateListener(int percent)
        {
            bufferedPercentage = percent;
        }

        @Override
        public final void onCompletionListener()
        {
            badSoundFixer.run();
            SData.setLong(SData.Data.SavedSeek,0);
            timer.set(false);
        }
    }
    public class ListenersYoutube extends ListenersSet
    {
        @Override
        protected void onCreate(){
            setScreenUpdateToEmpty();
        }

        @Override
        public void onPlayListLoop()
        {
            super.onPlayListLoop();
            videoChanger.onPlaylistLoop();
        }

        @Override
        public void onStarted(){
            badSoundFixer.stop();
            AppControl.waitAndIsWorkingStop();
        }
    }
    public abstract class LoaderForPlayer<T extends Generator>
    {
        protected final T localGenerator;

        public LoaderForPlayer(T localGenerator)
        {
            this.localGenerator = localGenerator;
            globalGenerator = localGenerator;
        }

        void loadOrLoadAndStartAndStartDetection(boolean andStart,
                                                 @NonNull Callable<Long> seek,
                                                 @NonNull Runnable beforeForLoadAndStart)
        {
            loadOrLoadAndStart(andStart,seek,beforeForLoadAndStart);
            errorHandel.detection.startDetectionLost();
        }

        void loadVideo(SurfaceHolder holder) throws IOException {
            if(mediaIsNull())
                return;

            ((IVideoPlayer)mediaPlayer).setDisplay(holder);

            loadOrLoadAndStartAndStartDetection(timer.get(),()->{
                long seek = seekLoad();
                if(seek == 0)
                    seek = 1;
                return seek;
            },StaticFunctions.Empty.r);
        }

        protected void resetPlayer(@NonNull GeneratorWithExpire generatorWithExpire)
        {
            mediaPlayer.resetWithoutResetPlayingState();
            mediaPlayer.addData(generatorWithExpire.getAudioStream(),generatorWithExpire.getVideoStream());
        }

        public Runnable getLoader()
        {
            return () -> {
                Loops l = loopUpdate();
                if(displayOn())
                {
                    Panel.loadPanel((hol)->{
                        mediaPlayer = globalGenerator.startPanel(true,l.getLoop(),l.getPlayListLoop());
                        /*if(!Panel.blackPanelFix)
                            waitMS(500);*/

                        loadVideo(hol);
                    });
                }
                else
                {
                    mediaPlayer = globalGenerator.startPanel(false,l.getLoop(),l.getPlayListLoop());

                    loadOrLoadAndStartAndStartDetection(timer.get(),()-> seekLoad(),StaticFunctions.Empty.r);
                }
            };
        }

        public abstract Callable<Boolean> onUpdateError();

        public void updateLoader()
        {
            localGenerator.onErrorUpdate(onUpdateError());
            panelRun = getLoader();
        }

        @CallSuper
        public void updateLoaderAndKiller()
        {
            updateLoader();
        }
    }
    public class LoaderForPlayerYoutube extends LoaderForPlayer<YoutubeGenerator>
    {
        public LoaderForPlayerYoutube(YoutubeGenerator localGenerator) {
            super(localGenerator);
        }

        public Callable<Boolean> onUpdateError()
        {
            return () -> {

                localGenerator.mediaError.started = true;
                Connection.ifNotHaveConnectionWaitInfinityTime(cleaningInBackground,()->{
                    try {
                        mediaPlayer.beforeOnErrorStarted();

                        if(localGenerator.mediaIsExpired())
                        {
                            localGenerator.generateContent();
                            localGenerator.reloadContent();
                        }

                        //HereErr

                        //youtubeGenerator.mediaError.started = true;


                        //BadSoudFixOn();

                        errorHandel.mediaBufferingStop();

                        boolean reload = refreshDisplay();

                        if(playlist.get()&&YoutubePlayList.changed)
                        {
                            if(reload)
                            {
                                if(localGenerator.getVideoStream()==null)
                                {
                                    localGenerator.generateAndLoad(10);

                                    if(localGenerator.getVideoStream()==null/* || vvideoStream == vv*/)
                                    {
                                        return;
                                    }
                                }
                            }
                            else
                            {
                                if(localGenerator.getAudioStream()==null)
                                {
                                    localGenerator.generateAndLoad(10);

                                    if(localGenerator.getAudioStream() == null/*||aaudioStream == aa*/)
                                    {
                                        return;
                                    }
                                }
                            }

                            resetPlayer(localGenerator);

                            seekPosition(0);
                            mediaPlayer.seekAfterIsPlayingDynamicReset();
                            seekMax(localGenerator.getMaxSeek());

                            YoutubePlayList.changed = false;
                        }
                        else
                        {
                            //ErrorHandel.GetSeek();
                            resetPlayer(localGenerator);
                        }
                        reloadPanel(reload);

                        //if(timer.Get())

                        loadOrLoadAndStart(mediaPlayer.isPlaying(),()->mediaPlayer.getSeekAfterIsPlayingDynamic(),StaticFunctions.Empty.r);

                        errorHandel.posSaved = false;
                        localGenerator.mediaError.started = false;
                    }
                    catch (ExtractionException | IOException e)
                    {
                        onErrorSave("OnError-UpdateYou",e);
                        localGenerator.mediaError.started = false;
                    }
                });

                return true;
            };
        }

        @Override
        public void updateLoaderAndKiller()
        {
            super.updateLoaderAndKiller();
        }

        public void updateLoaderAndKillerWithYoutubePlayListDispose()
        {
            super.updateLoaderAndKiller();
        }
    }
    public class LoaderForSiteGenerator extends LoaderForPlayer<SiteGenerator>
    {

        public LoaderForSiteGenerator(SiteGenerator localGenerator) {
            super(localGenerator);
        }

        @Override
        public Callable<Boolean> onUpdateError() {
            return () -> {
                localGenerator.mediaError.started = true;
                Connection.ifNotHaveConnectionWaitInfinityTime(cleaningInBackground,()->{
                    mediaPlayer.beforeOnErrorStarted();
                    try {
                        if(localGenerator.mediaIsExpired())
                        {
                            localGenerator.generateContent();
                            localGenerator.reloadContent();
                        }

                        //BadSoudFixOn();

                        errorHandel.mediaBufferingStop();

                        boolean reload = refreshDisplay();

                        //ErrorHandel.GetSeek();
                        resetPlayer(localGenerator);
                        reloadPanel(reload);

                        //if(timer.Get())

                        loadOrLoadAndStart(mediaPlayer.isPlaying(),()->mediaPlayer.getSeekAfterIsPlayingDynamic(),StaticFunctions.Empty.r);

                        errorHandel.posSaved = false;
                        localGenerator.mediaError.started = false;
                    }
                    catch (ExtractionException | IOException e)
                    {
                        onErrorSave("OnError-UpdateYouSiteGenerator",e);
                        localGenerator.mediaError.started = false;
                        //youtubeGenerator.mediaError.started = true;
                        //youtubeGenerator.mediaError.Wait();
                        //youtubeGenerator.GetOnError().call();
                    }
                });

                return true;
            };
        }

        @Override
        public void updateLoaderAndKiller()
        {
            super.updateLoaderAndKiller();
        }
    }
    public class LoaderForPlayerURL extends LoaderForPlayer<UrlGenerator>
    {
        public LoaderForPlayerURL(UrlGenerator genera) {
            super(genera);
        }

        @Override
        public Runnable getLoader(){

            return () -> {

                /*try {
                    if(!isStreamAvailable(url))
                    {
                        mediaPlayer.WaitStop();
                        return;
                    }
                } catch (IOException e) {

                }*/

                Loops l = loopUpdate();
                if(displayOn())
                {
                    Panel.loadPanel((hol)->{
                        mediaPlayer = globalGenerator.startPanel(true,l.getLoop(),l.getPlayListLoop());
                        /*if(!Panel.blackPanelFix)
                            waitMS(500);*/

                        loadVideo(hol);
                    });
                }
                else
                {/*
                    if(!startedPanelOneTime)
                        return;*/
                    mediaPlayer = globalGenerator.startPanel(false,l.getLoop(),l.getPlayListLoop());
                    //urlPlayer.Load();

                    loadOrLoadAndStartAndStartDetection(timer.get(),()-> seekLoad(),StaticFunctions.Empty.r);
                }
            };
        }

        @Override
        public Callable<Boolean> onUpdateError(){
            return () -> {
                globalGenerator.mediaError.started = true;
                Connection.ifNotHaveConnectionWaitInfinityTime(cleaningInBackground,()->{
                    try {
                        mediaPlayer.beforeOnErrorStarted();
                        errorHandel.mediaBufferingStop();

                        boolean reload = refreshDisplay();

                        mediaPlayer.resetWithoutResetPlayingState();
                        reloadPanel(reload);

                        loadOrLoadAndStart(mediaPlayer.isPlaying(),()->mediaPlayer.getSeekAfterIsPlayingDynamic(),StaticFunctions.Empty.r);

                        errorHandel.posSaved = false;
                        globalGenerator.mediaError.started = false;
                    }
                    catch (Exception e)
                    {
                        onErrorSave("SendURLClose-urlGenerator.OnErrorUpdate",e);
                        globalGenerator.mediaError.started = false;
                    }
                });

                return true;
            };
        }

        @Override
        public void updateLoaderAndKiller()
        {
            super.updateLoaderAndKiller();
        }
    }
    public static abstract class DetectorSet{
        private static Detector detector;
        private static Exception exception;

        public static void update(){

            if(detector != null){
                String outP = detector.name();

                if(detector == Detector.RecoveryError && exception!=null)
                    outP = outP + ": " + System.lineSeparator() + exception.getMessage();

                ErrorCodeApp.detector.set("detector: " + outP);
            }

            ErrorCodeApp.stoppingTime.set("stoping time: " + SData.getLong(SData.Data.StoppingTime));
            ErrorCodeApp.disposableErrors.set("Disposable Errors: "+SData.getString(SData.Data.SavedDisposableErrors));
            ErrorCodeApp.mediaPlayerErrors.set("MediaPlayer Errors: "+SData.getString(SData.Data.SavedListenersErrors));
            ErrorCodeApp.dataLoader.set(SData.getString(SData.Data.SavedDataLoaderActions));
        }

        public enum Detector{
            Idle,
            StartingRecovery,
            RecoveryStarted,
            RecoveryCompleted,
            RecoveryError,
            No_Connection_SkippingRecovery,
            Detection_Player_Is_Null_1,
            PlayerAction_NotCompleted,
            PlayerNotLoaded_SkippingDetection,
            RecoveryInProgress_Skipping,
            CheckingPlayerState,
            PlayerStarting_Healthy_Paused,
            CheckingPlayerState_Recovering,
            PlayerStarting_Skipping,
            PlayerIsPlaying_Healthy,
            MediaPlayer_Is_Null,
            Detection_Player_Is_Null
        }
    }
    public class AsyncRun
    {
        public class PlayerFreezeDetection
        {
            private static final int MAX_CHECK_OF_NOT_CREATED_MS = 15000;

            private int maxCheckOfIsNotPlayingMS;
            private int intervalMS;

            private int maxCheckOfNotCreatedCalculated;
            private int maxCheckOfIsNotPlaying;
            private long savedSeek;
            private int counter;
            private Disposable detector;


            private void setup(int intervalMS,int maxCheckOfIsNotPlayingMS){
                this.intervalMS = intervalMS;
                this.maxCheckOfIsNotPlayingMS = maxCheckOfIsNotPlayingMS;
            }

            private void stop()
            {
                if(detector !=null && !detector.isDisposed())
                    detector.dispose();

                //mediaIsChanged.Reset();
            }

            private void recover()
            {
                badSoundFixer.run();

                //String report = getWebUIWaitStopReport();
                //ErrorCodeApp.code40 = ErrorCodeApp.code40 + System.lineSeparator() + report;

                if (AndroidOsUpdatesListener.isHaveConnection()) {
                    DetectorSet.detector = Detector.StartingRecovery;
                    errorHandel.currentRecover.recoverStarted = true;

                    // Single recovery attempt with proper cleanup
                    currentRecover.actionStart(() -> {
                        if (globalGenerator.mediaError.started)
                            return true;
                        try {
                            DetectorSet.detector = Detector.RecoveryStarted;

                            // Dispose current player
                            if (!mediaIsNull()) {
                                mediaPlayer.dispose();
                            }

                            // Stop generator
                            if (globalGenerator != null) {
                                globalGenerator.mediaErrorStop();
                            }

                            if(!AndroidOsUpdatesListener.isHaveConnection())
                                return true;
                            //OnException();
                            if (playlist.get()&& playlistLoopOn())
                                videoChanger.updateChanger(1);
                            else
                                globalGenerator.mediaErrorRun();

                            DetectorSet.detector = Detector.RecoveryCompleted;
                            return true;

                        } catch (Exception e) {
                            onErrorSave("Player-OnCompletionListener",e);

                            if (globalGenerator.mediaError.started)
                                return true;
                            if(!AndroidOsUpdatesListener.isHaveConnection())
                                return true;

                            DetectorSet.detector = Detector.RecoveryError;
                            DetectorSet.exception = e;
                            //TryIP();
                            if (playlist.get()&& playlistLoopOn())
                                videoChanger.updateChanger(1);
                            else
                                globalGenerator.mediaErrorRun();/*
                        if(!mediaPlayer.IsLoaded())
                        {
                        }*/
                            return false;
                        } finally {
                            cleaningInBackground.addStartAfterWait(2000,()->{
                                errorHandel.currentRecover.recoverStarted = false;
                                currentRecover.currentStopAndResetStateAndUIWait();
                            },StaticFunctions.Empty.r, lifo,"RecoverError-After-2-Seconds-Delay");
                        }
                    }, () -> "RecoverError");

                } else {
                    DetectorSet.detector = Detector.No_Connection_SkippingRecovery;
                }
            }

            private boolean startedRecover(int count){
                if(count< counter)
                {
                    counter = 0;
                    mediaPlayer.dispose();
                    recover();
                    return true;
                }
                counter++;
                return false;
            }

            private void startDetectionLost() {
                maxCheckOfIsNotPlaying = maxCheckOfIsNotPlayingMS / intervalMS;
                maxCheckOfNotCreatedCalculated = MAX_CHECK_OF_NOT_CREATED_MS / intervalMS;

                detector = Observable.interval(intervalMS, TimeUnit.MILLISECONDS, lifo)
                        .retryWhen(errors -> errors.delay(2, TimeUnit.SECONDS, lifo))
                        .subscribe(tick -> {
                            try
                            {
                                DetectorSet.detector = Detector.Idle;

                                if(globalGenerator!=null&&!globalGenerator.mediaError.started && setUp.get() && timer.get() && AndroidOsUpdatesListener.isHaveConnection())
                                {

                                    // SKIP DETECTION if first load hasn't happened yet

                                    if(mediaIsNull())
                                    {
                                        DetectorSet.detector = Detector.Detection_Player_Is_Null_1;
                                        return;
                                    }
                                    else
                                    {
                                        if(mediaPlayer.actionStarted())
                                        {
                                            DetectorSet.detector = Detector.PlayerAction_NotCompleted;
                                            return;
                                        }

                                        if(!mediaPlayer.isCreated())
                                        {
                                            if(!startedRecover(maxCheckOfNotCreatedCalculated))
                                                DetectorSet.detector = Detector.PlayerNotLoaded_SkippingDetection;
                                            return;
                                        }
                                    }

                                    if(!mediaPlayer.isLoaded())
                                    {
                                        recover();
                                        return;
                                    }

                                    if(!mediaPlayer.firstPlayed())
                                    {
                                        counter = 0;

                                        if(mediaPlayer.firstPlayTrigger(50,500))
                                        {
                                            mediaReload.tryLoadAfterFirstPlay();
                                            sender.sendUrlStartedReset();
                                        }
                                        else
                                            sender.sendUrlStartedReset();
                                    }

                                    // Check if already in recovery mode
                                    if (errorHandel.currentRecover.recoverStarted) {
                                        DetectorSet.detector = Detector.RecoveryInProgress_Skipping;
                                        return;
                                    }

                                    DetectorSet.detector = Detector.CheckingPlayerState;

                                    if (!mediaIsNull()) {

                                        // Check if player is actually paused
                                        if(!mediaPlayer.isPlaying())
                                        {
                                            DetectorSet.detector = Detector.PlayerStarting_Healthy_Paused;
                                            if(!globalGenerator.isLive())
                                                return;

                                            DetectorSet.detector = Detector.CheckingPlayerState_Recovering;
                                            recover();

                                            return;
                                        }

                                        // Skip if player is still starting up
                                        if (mediaPlayer.waitStarted()) {
                                            DetectorSet.detector = Detector.PlayerStarting_Skipping;
                                            return;
                                        }

                                        // Check if player is actually playing
                                        long curP = mediaPlayer.getCurrentPositionPure();
                                        if (curP!=savedSeek){
                                            savedSeek = curP;
                                            counter = 0;
                                            DetectorSet.detector = Detector.PlayerIsPlaying_Healthy;
                                            SData.setLong(SData.Data.StoppingTime,System.currentTimeMillis());
                                            if(!globalGenerator.isLive())
                                                SData.setLong(SData.Data.SavedSeek,mediaPlayer.getSeekAfterIsPlayingDynamic());
                                            return;
                                        }
                                        startedRecover(maxCheckOfIsNotPlaying);
                                        return;
                                    } else {
                                        DetectorSet.detector = Detector.MediaPlayer_Is_Null;
                                    }


                                    recover();
                                } else {
                                    errorHandel.currentRecover.recoverStarted = false;
                                }
                            }
                            catch (Exception e)
                            {
                                if(mediaIsNull())
                                {
                                    DetectorSet.detector = Detector.Detection_Player_Is_Null;
                                }
                            }
                        },onError -> {});
            }
        }

        public class Recover extends StaticFunctions.ActionWait{
            private boolean recoverStarted;
            @Override
            public synchronized void onResetState(){
                super.onResetState();
                recoverStarted = false;
            }
        }

        private final Recover currentRecover;
        private final WaitDisposable mediaBuffering;
        private final WaitDisposable mediaGetSeek;
        private final PlayerFreezeDetection detection;
        //private final WaitDisposable waitReset;
        //private WaitDisposable mediaBufferingStop;
        private boolean posSaved;

        public AsyncRun()
        {
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                    StaticFunctions.onThrows(thread,throwable));

            RxJavaPlugins.setErrorHandler(error ->
            {
                try {
                    onErrorSave("RX-UndeliverableException",error);
                }
                catch (Exception e){}
            });

            mediaBuffering = new WaitDisposable(300);
            mediaGetSeek = new WaitDisposable(100);
            detection = new PlayerFreezeDetection();
            currentRecover = new Recover();
            //waitReset = new WaitDisposable(10);
            //mediaBufferingStop = new WaitDisposable(70);
        }

        public void recover()
        {
            detection.recover();
        }

        public void mediaBufferingStop()
        {
            mediaBuffering.started = false;
            mediaBuffering.dispose();

            /*
            mediaBufferingStop.Dispose();

            mediaBufferingStop.Start(() -> {
                if (mediaPlayer.IsPlayingDinamic(5,mediaBufferingStop.second))
                    mediaBuffering.Dispose();
                return true;
            }, o -> {
                mediaBufferingStop.disposable.dispose();
            });

             */
        }

        public void getSeek()
        {
            if(timer.get()&&!posSaved&&!mediaIsNullFully())
            {
                AtomicInteger savedSeek = new AtomicInteger(-1);

                mediaGetSeek.started = true;
                mediaGetSeek.start(() ->
                {
                    while (savedSeek.get()==-1)
                    {
                        savedSeek.set(saveSeek((int)mediaPlayer.getCurrentPosition()));
                        waitMS(1000);
                    }
                    mediaGetSeek.started = false;
                    return true;
                });

                int timeOut = 0;
                while (mediaGetSeek.started)
                {
                    waitMS(1000);
                    if(timeOut == mediaGetSeek.getSecond())
                    {
                        savedSeek.set((bufferedPercentage * getSeekMax()) / 100);
                        break;
                    }
                    timeOut++;
                }

                seekPosition(savedSeek.get());
                posSaved = true;
            }
        }

        public void mediaBufferingStart()
        {/*
            mediaBuffering.Start(() ->
            {
                mediaBuffering.started = true;
                mediaBuffering.Wait();
                return true;
            }, error -> {
                //SendURL();

                MediaErrorRun();
                mediaBuffering.disposable.dispose();
            });*/

            mediaBuffering.startWithLongWaiting(disposable -> {
                mediaBuffering.started = true;
            }, () -> {
                if(mediaBuffering.started)
                    globalGenerator.mediaErrorRun();
                mediaBuffering.disposable.dispose();
            }, throwable -> {
                onErrorSave("MediaBufferingError: ",throwable);
                mediaBuffering.disposable.dispose();
            });
        }
    }
    public static class Sender
    {
        private final SenderFuncuanality sender;

        public Sender()
        {
            sender = new SenderFuncuanality();
        }

        public synchronized void onMediaChangingUse(Callable<Boolean> Base, Callable<String> OnError) {
            sender.onMediaChangingUse(Base,OnError);
        }

        public synchronized void sendUrlStart(Callable<Boolean> Base, Callable<String> OnError) {
            sender.actionStart(Base,OnError);
        }

        public synchronized void sendUrlStartedReset() {
            sender.currentStopAndResetStateAndUIWait();
        }

        public synchronized void sendUrlStartedResetWithoutUIWait(){
            sender.currentStopAndResetState();
        }

        public synchronized void sendUrlStartedResetOnlyBoolean()
        {
            sender.resetStateAndUIWait();
        }

        public synchronized void sendUrlStartedResetOnlyBooleanWithoutUIWait()
        {
            sender.onResetState();
        }

        private class SenderFuncuanality extends StaticFunctions.ActionWait{

            private final StaticFunctions.Starter onFirst = new StaticFunctions.Starter() {
                @Override
                protected void firstLaunch() {

                }

                @Override
                protected void secondLaunches() {
                    onDispose();
                }
            };

            @Override
            public void onDispose()
            {
                app().closeDataAndPanelWithoutWaitReset();
                waitMS(300);
            }

            @Override
            public void onDisposeMissed(){
                onFirst.run();
            }

            public synchronized void onMediaChangingUse(Callable<Boolean> Base, Callable<String> OnError){
                if (isActivated())
                    return;

                activate();

                super.disposeOnly();
                run(Base,OnError);
            }
        }
    }
    private static class BadSoundFixer extends StaticFunctions.Starter
    {
        private final MediaPlayer emptyAudio;

        public BadSoundFixer()
        {
            super();
            emptyAudio = MediaPlayer.create(getContext(), R.raw.empty);
            emptyAudio.setLooping(true);
        }

        @Override
        protected void firstLaunch() {
            emptyAudio.start();
        }

        @Override
        protected void secondLaunches() {}

        protected void stop()
        {
            emptyAudio.pause();
            reset();
        }
    }

    private class MediaStopOrSwitch {
        private final MediaStop mediaStop = new MediaStop();
        private final WaitAndIsWorkingStop mediaStopAndWait = new WaitAndIsWorkingStop();
        private final OnMediaChanging mediaStopAndMediaChange = new OnMediaChanging();

        private String url;
        private MediaSourceProviders sourceProvider;
        private String nameOfMedia;

        public void mediaSessionStopAndWaitAndIsWorkingStop(){
            reset();
            mediaStopAndWait.mediaSessionStopBase();
        }

        public void mediaSessionStop(){
            mediaStop.mediaSessionStopBase();
        }

        public void onMediaChanging(String url, MediaSourceProviders sourceProvider, String nameOfMedia) {
            mediaStopAndMediaChange.onMediaChanging(url,sourceProvider,nameOfMedia);
        }

        public void mediaForceStopActivate(){
            mediaPlayer.activateForceRelease();
        }

        private void reset(){
            url = null;
            sourceProvider = null;
            nameOfMedia = null;
        }

        public class MediaStop{
            final void mediaSessionStopBase(){

                if(mediaIsNull()){
                    make();
                    return;
                }

                errorHandel.posSaved = false;

                badSoundFixer.run();

                StaticFunctions.Starter tick = new StaticFunctions.Starter() {
                    private Runnable playListClean;
                    private Generator oldGenerator;

                    @Override
                    protected void firstLaunch() {
                        playListClean = YoutubePlayList.softDispose();
                        killAll(errorHandel.mediaBuffering, errorHandel.mediaGetSeek);

                        oldGenerator = globalGenerator;
                        globalGenerator = null;
                    }

                    @Override
                    protected void secondLaunches() {
                        if(oldGenerator != null){
                            oldGenerator.kill();
                            oldGenerator = null;
                        }
                        if(playListClean != null)
                        {
                            playListClean.run();
                            playListClean = null;
                        }
                    }
                };
                tick.run();

                PlayerControllerBase oldPlayer = mediaPlayer;

                Runnable onComple = ()->{
                    tick.run();
                    oldPlayer.release();
                    mediaPlayer = null;
                    make();
                };

                videoChanger.stop();

                if(oldPlayer !=null){
                    cleaningInBackground.addPollingTaskWithTimeOut(
                            ()->oldPlayer.notClosable(),
                            tick,
                            onComple,
                            onComple,
                            ()->{
                                try {
                                    onComple.run();
                                } catch (Exception e) {}
                            },
                            StaticFunctions.Empty.a,
                            1500,
                            20000,
                            lifo,
                            lifo,
                            "mediaSessionStop"
                    );
                    return;
                }

                tick.run();
                mediaPlayer = null;
                make();
            }

            protected void make(){}
        }

        public class WaitAndIsWorkingStop extends MediaStop{
            @Override
            protected void make(){
                AppControl.waitAndIsWorkingStop();
            }
        }

        public class OnMediaChanging extends MediaStop{

            public void onMediaChanging(String url, MediaSourceProviders sourceProvider, String nameOfMedia) {
                reset();
                cleaningInBackground.clear();

                MediaStopOrSwitch.this.url = url;
                MediaStopOrSwitch.this.sourceProvider = sourceProvider;
                MediaStopOrSwitch.this.nameOfMedia = nameOfMedia;

                beforeDestroy();
                closePanel(
                        () -> {
                            SData.resetToDefault();
                            uiReset();
                            detectionRecover();
                        },
                        () -> mediaSessionStopBase(),
                        () -> {}
                );
            }

            @Override
            protected void make(){
                try {
                    waitMS(100);
                    if(loadData(url, sourceProvider,nameOfMedia))
                        mediaSessionStopBase();
                }
                catch (Exception e){
                    mediaSessionStopBase();
                }
            }
        }
    }

    public static final class JsonData{
        private final JSize width;
        private final JSize height;
        private String jsonSelectedRes;

        public JsonData(){
            width = new JSize(SData.Data.Jwidth);
            height = new JSize(SData.Data.Jheight);
            jsonSelectedRes = null;
        }

        public void reset(){
            width.reset();
            height.reset();
            jsonSelectedRes = null;
        }

        public boolean fullUpdate(int wi,int he,String jsRes){
            width.update(wi);
            height.update(he);
            jsonSelectedRes = jsRes;
            return true;
        }

        private final class JSize{
            private final SData.Data savedData;
            private int data;

            public JSize(SData.Data savedData){
                this.savedData = savedData;
                data = SData.getInt(savedData);
            }

            public int get(){
                return data;
            }

            public void update(int data){
                this.data = data;
                SData.setInt(savedData,data);
            }

            public void reset(){
                update(0);
            }
        }
    }

    public static class MediaData{
        private final String name;
        private final String directory;
        private final MediaSourceProviders provider;

        public MediaData(String name, String directory,MediaSourceProviders provider){
            this.name = name;
            this.directory = directory;
            this.provider = provider;
        }
    }

    /*protected void Volume()
    {
        mediaVolume = (AudioManager) getContext().getSystemService(AUDIO_SERVICE);

        volumePlayerMax = mediaVolume.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }*/

    /*public void ChangeVideoWithoutReturn(int plusOrMinus)
    {
        RunInThreadPoolIO((Callable<Boolean>) () -> {
            ChangeVideo(plusOrMinus);
            return true;
        }, () -> {
            SaveError("NotChanged Video");
            return true;
        });
    }*/

//    public class ChangeVideo {
//
//        /**
//         * Per-run immutable state container.
//         * Made static to avoid accidental capture of outer instance.
//         */
//        public class ChangerData {
//            public final int plusOrMinus;
//            public volatile YoutubeGenerator selected;
//            public int newIndex;
//            public int currentIndex;
//            public boolean generate;
//            public ChangerData(int plusOrMinus) { this.plusOrMinus = plusOrMinus; }
//        }
//
//        private final AtomicBoolean maked = new AtomicBoolean(false);
//        private Disposable mediaChanger;
//        private ChangerData data;
//
//        /**
//         * Replace the active changer data and start a run.
//         * Synchronized to avoid races with Dispose().
//         */
//        public synchronized Disposable UpdateChanger(int plusOrMinus) {
//            DisposeChanger();
//            data = new ChangerData(plusOrMinus);
//            // start immediately and return the Disposable so caller can keep a handle if desired
//            return start(throwable -> OnErrorSave("ChangeVideo-Error", throwable),
//                    () -> {});
//        }
//
//        public synchronized boolean IsMaked()
//        {
//            return maked.get();
//        }
//
//        private boolean Maked()
//        {
//            maked.set(true);
//            return true;
//        }
//
//        private synchronized void DisposeChanger() {
//            maked.set(false);
//            if (mediaChanger != null && !mediaChanger.isDisposed()) {
//                mediaChanger.dispose();
//            }
//            mediaChanger = null;
//        }
//
//        private class FlowStopException extends RuntimeException {
//            FlowStopException(String message) { super(message); }
//        }
//
//        private boolean isCancelled() {
//            return ((mediaChanger != null && mediaChanger.isDisposed()) || Thread.currentThread().isInterrupted());
//        }
//
//        /**
//         * Single-run start: runs the 3-step flow once and returns the Disposable.
//         * The Single payload is ChangerData for clarity.
//         */
//        private Disposable start(Consumer<Throwable> onError, Action onComplete) {
//            if (data == null) {
//                // nothing to run
//                return null;
//            }
//
//            mediaChanger = Single.fromCallable(() -> {
//                        boolean ok = InFirstCheck();
//                        if (!ok) throw new FlowStopException("InFirstCheck returned false");
//                        return data; // return the per-attempt state
//                    })
//                    // InFirstCheck contains blocking waits -> use computation or IO depending on your DisposableTools mapping.
//                    .subscribeOn(DisposableTools.computationScheduler())
//
//                    .flatMap(ch -> Single.fromCallable(() -> {
//                        if (ch.generate) {
//                            try {
//                                boolean genOk = IfNotGeneratedGenerate();
//                                if (!genOk) throw new FlowStopException("IfNotGeneratedGenerate returned false");
//                            } catch (ExtractionException | IOException e) {
//                                throw e;
//                            }
//                        }
//                        return ch;
//                    }).subscribeOn(DisposableTools.ioThreadPoolScheduler()))
//
//                    .flatMap(ch -> Single.fromCallable(() -> {
//                        try {
//                            boolean finalOk = IfNotLoadedAgain();
//                            if (!finalOk) throw new FlowStopException("IfNotLoadedAgain returned false");
//                            return true;
//                        } catch (ExtractionException | IOException e) {
//                            throw e;
//                        }
//                    }).subscribeOn(DisposableTools.computationScheduler()))
//
//                    .ignoreElement()
//                    .subscribe(
//                            () -> {
//                                try { if (onComplete != null) onComplete.run(); } catch (Exception ignored) {}
//                            },
//                            throwable -> {
//                                if (throwable instanceof FlowStopException) {
//                                    try { if (onComplete != null) onComplete.run(); } catch (Exception ignored) {}
//                                } else {
//                                    try { if (onError != null) onError.accept(throwable); } catch (Exception ignored) {}
//                                }
//                            }
//                    );
//
//            return mediaChanger;
//        }
//
//    /* -------------------------
//       Original methods with cancellation checks
//       ------------------------- */
//
//        private boolean InFirstCheck() {
//            if (data == null) return NotLoaded();
//
//            ErrorCodeApp.code22 = ErrorCodeApp.code22 + " +";
//            mediaPlayer.StartLoading();
//
//            if (YoutubePlayList.isDisposed() || YoutubePlayList.getTotalVideosCount() == 0) {
//                return false;
//            }
//
//            data.currentIndex = YoutubePlayList.current;
//            int totalVideos = YoutubePlayList.getTotalVideosCount();
//            data.newIndex = data.currentIndex + data.plusOrMinus;
//
//            if (data.plusOrMinus == -1 && data.currentIndex == 0) {
//                data.newIndex = totalVideos - 1;
//            } else if (data.currentIndex == totalVideos - 1 && data.plusOrMinus == 1) {
//                data.newIndex = 0;
//            } else {
//                boolean shouldLoop = PlaylistLoopOn();
//                if (data.newIndex < 0) {
//                    data.newIndex = shouldLoop ? totalVideos - 1 : 0;
//                } else if (data.newIndex >= totalVideos) {
//                    data.newIndex = shouldLoop ? 0 : totalVideos - 1;
//                }
//            }
//
//            if (data.newIndex < 0 || data.newIndex >= totalVideos) {
//                return NotLoaded();
//            }
//
//            data.selected = YoutubePlayList.GetGenerator(data.newIndex);
//
//            if (data.selected == null || !data.selected.IsLoaded()) {
//                YoutubePlayList.AddSingleElement(data.newIndex);
//
//                int maxWaitAttempts = 10;
//                int waitAttempt = 0;
//
//                while (waitAttempt < maxWaitAttempts) {
//                    if (isCancelled()) return NotLoaded();
//                    waitMS(300);
//                    if (isCancelled()) return NotLoaded();
//
//                    data.selected = YoutubePlayList.GetGenerator(data.newIndex);
//                    if (data.selected != null && data.selected.IsLoaded()) {
//                        break;
//                    }
//
//                    waitAttempt++;
//
//                    if (YoutubePlayList.isDisposed()) {
//                        return NotLoaded();
//                    }
//                }
//
//                if (data.selected == null || !data.selected.IsLoaded()) {
//                    try {
//                        if (YoutubePlayList.streamInfoItem != null && data.newIndex < YoutubePlayList.streamInfoItem.size()) {
//                            String videoUrl = YoutubePlayList.streamInfoItem.get(data.newIndex).getUrl();
//                            data.selected = new YoutubeGenerator(
//                                    videoUrl,
//                                    YoutubePlayList.videoSettings,
//                                    YoutubePlayList.listeners,
//                                    YoutubePlayList.hardware
//                            );
//                            data.generate = true;
//                        } else {
//                            return NotLoaded();
//                        }
//                    } catch (Exception e) {
//                        OnErrorSave("ChangeVideo", e);
//                        return NotLoaded();
//                    }
//                }
//            }
//
//            return Maked();
//        }
//
//        private boolean IfNotGeneratedGenerate() throws ExtractionException, IOException {
//            if (data == null) return NotLoaded();
//            if (data.selected.GenerateLink(5)) {
//                data.selected.ReLoadContent();
//                if (YoutubePlayList.GetGenerator(data.newIndex) == null) {
//                    YoutubePlayList.youtubeGenerators.add(data.selected);
//                }
//                return Maked();
//            }
//            return NotLoaded();
//        }
//
//        private boolean IfNotLoadedAgain() throws ExtractionException, IOException {
//            if (data == null) return NotLoaded();
//            if (data.selected == null || !data.selected.IsLoaded()) {
//                return NotLoaded();
//            }
//
//            YoutubeGenerator.Size videoSize = data.selected.GetSize();
//            Panel.updateScreen(videoSize.Width(), videoSize.Height());
//
//            YoutubeGenerator oldGenerator = (YoutubeGenerator) globalGenerator;
//            int oldIndex = YoutubePlayList.current;
//
//            YoutubePlayList.current = data.newIndex;
//            YoutubePlayList.changed = true;
//
//            new LoaderForPlayerYoutube(data.selected).UpdateLoaderAndKillerWithYoutubePlayListDispose();
//
//            if (oldGenerator != null && oldGenerator != globalGenerator) {
//                try {
//                    YoutubePlayList.UpdateToDefault(oldGenerator);
//                } catch (Exception e) {
//                    OnErrorSave("ChangeVideo-YoutubePlayList.UpdateToDefault", e);
//                }
//            }
//
//            globalGenerator.mediaError.started = true;
//
//            SData.SetString(SData.Data.SavedUrl, globalGenerator.GetVideoUrl() + "&list=" + YoutubePlayList.youtubePlaylistId());
//
//            globalGenerator.MediaErrorRun();
//
//            for (int i = 0; i < 5; i++) {
//                if (isCancelled()) return NotLoaded();
//                waitMS(200);
//                if (isCancelled()) return NotLoaded();
//                if (!globalGenerator.mediaError.started) break;
//            }
//
//            if (mediaPlayer.IsPlayingDynamic(60, 50)) {
//                SData.SetInt(SData.Data.SavedIndexPlayList, data.currentIndex);
//            }
//
//            PreloadAdjacentVideos(data.newIndex);
//
//            Wait.webUIWaitStop();
//            return Maked();
//        }
//    }

    /*public boolean ChangeVideo(int plusOrMinus) throws ExtractionException, IOException {
        ErrorCodeApp.code22 = ErrorCodeApp.code22 + " +";
        mediaPlayer.StartLoading();
        // First, check if playlist is available and has videos
        if (YoutubePlayList.isDisposed() || YoutubePlayList.getTotalVideosCount() == 0) {
            return false;
        }

        int currentIndex = YoutubePlayList.current;
        int totalVideos = YoutubePlayList.getTotalVideosCount();
        int newIndex = currentIndex + plusOrMinus;

        // Special handling for -1 (go to end) and overflow (go to start)
        if (plusOrMinus == -1 && currentIndex == 0) {
            // If at start and receiving -1, go to end
            newIndex = totalVideos - 1;
        } else if (currentIndex == totalVideos - 1 && plusOrMinus == 1) {
            // If at end and receiving +1, go to start
            newIndex = 0;
        } else {
            // Handle normal wrap-around logic
            boolean shouldLoop = PlaylistLoopOn();
            if (newIndex < 0) {
                newIndex = shouldLoop ? totalVideos - 1 : 0;
            } else if (newIndex >= totalVideos) {
                newIndex = shouldLoop ? 0 : totalVideos - 1;
            }
        }

        // Ensure index is within bounds
        if (newIndex < 0 || newIndex >= totalVideos) {
            return NotLoaded();
        }

        // Check if the target video is already loaded
        YoutubeGenerator selected = YoutubePlayList.GetGenerator(newIndex);

        // If not loaded, load it synchronously
        if (selected == null || !selected.IsLoaded()) {
            // Load the specific video
            YoutubePlayList.AddSingleElement(newIndex);

            // Wait for it to load with reasonable timeout
            int maxWaitAttempts = 10;
            int waitAttempt = 0;

            while (waitAttempt < maxWaitAttempts) {
                WaitS(300);
                selected = YoutubePlayList.GetGenerator(newIndex);

                if (selected != null && selected.IsLoaded()) {
                    break;
                }

                waitAttempt++;

                if (YoutubePlayList.isDisposed()) {
                    return NotLoaded();
                }
            }

            // If still not loaded after waiting, try to create it directly
            if (selected == null || !selected.IsLoaded()) {
                try {
                    // Get the URL from the stream info item
                    if (YoutubePlayList.streamInfoItem != null && newIndex < YoutubePlayList.streamInfoItem.size()) {
                        String videoUrl = YoutubePlayList.streamInfoItem.get(newIndex).getUrl();
                        selected = new YoutubeGenerator(
                                videoUrl,
                                YoutubePlayList.videoSettings,
                                YoutubePlayList.listeners,
                                YoutubePlayList.hardware
                        );

                        if (selected.GenerateLink(5)) {
                            selected.ReLoadContent();
                            // Add to the list if it's not already there
                            if (YoutubePlayList.GetGenerator(newIndex) == null) {
                                YoutubePlayList.youtubeGenerators.add(selected);
                            }
                        } else {
                            return NotLoaded();
                        }
                    } else {
                        return NotLoaded();
                    }
                } catch (Exception e) {
                    OnErrorSave("ChangeVideo",e);
                    return NotLoaded();
                }
            }
        }

        // If we still don't have a valid generator, abort
        if (selected == null || !selected.IsLoaded()) {
            return NotLoaded();
        }

        // Update Panel Size after load new generator
        YoutubeGenerator.Size videoSize = selected.GetSize();
        Panel.UpdateScreen(videoSize.Width(),videoSize.Height());

        // Store old generator for cleanup
        YoutubeGenerator oldGenerator = (YoutubeGenerator) globalGenerator;
        int oldIndex = YoutubePlayList.current;

        // Update current index in playlist
        YoutubePlayList.current = newIndex;
        YoutubePlayList.changed = true;

        // Update media player with new content
        new LoaderForPlayerYoutube(selected).UpdateLoaderAndKillerWithYoutubePlayListDispose();

        // Update the OLD generator to default state (not the new one)
        if (oldGenerator != null && oldGenerator != globalGenerator) {
            try {
                YoutubePlayList.UpdateToDefault(oldGenerator);
            } catch (Exception e) {
                OnErrorSave("ChangeVideo-YoutubePlayList.UpdateToDefault",e);
                // Handle exception if UpdateToDefault fails
            }
        }

        // Handle media errors for the NEW generator
        globalGenerator.mediaError.started = true;

        SData.SetString(SData.Data.SavedUrl, globalGenerator.GetVideoUrl()+"&list="+YoutubePlayList.youtubePlaylistId());
        globalGenerator.MediaErrorRun();

        // Wait for error handling with timeout
        for (int i = 0; i < 5; i++) {
            WaitS(200);
            if (!globalGenerator.mediaError.started) break;
        }

        // Check if playing and update video resolution
        if(mediaPlayer.IsPlayingDynamic(6, 500))
        {
            SData.SetInt(SData.Data.SavedIndexPlayList,currentIndex);
        }

        // Pre-load adjacent videos for smoother navigation
        PreloadAdjacentVideos(newIndex);

        return true;
    }*/
}
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
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
import app.tools.LoadedData;
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
import io.reactivex.rxjava3.core.CompletableOnSubscribe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.BiConsumer;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import app.tools.DisposableTools.WaitDisposable;

import static app.Main.getContext;
import static app.tools.DisposableTools.forCleaner;
import static app.tools.DisposableTools.forDetector;
import static app.tools.DisposableTools.killAll;
import static app.tools.DisposableTools.forGenerators;
import static app.tools.DisposableTools.lifo;
import static app.tools.DisposableTools.ioThreadPoolScheduler;
import static app.tools.DisposableTools.waitMS;
import static app.tools.StaticFunctions.onErrorSave;
import static server.Home.app;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.SystemClock;
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


    private Loops loopData;
    private VideoSettings videoSettings;
    private boolean mediaSeekStart;
    private int bufferedPercentage;
    private boolean isAudioOnly;

    static {
        PlayerControllerBase.activateGlobalReleaser();
    }

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

        cleaningInBackground.clear(()-> getApp().closePanel(() -> getApp().sendURLBeforeDestroy(),() -> {
            getApp().sendURLAfterDestroy();
            new AppBack();
        }, StaticFunctions.Empty.r), forCleaner);
    }

    public static void recreateWithoutCleanData() {
        app().forceStopActivate();

        cleaningInBackground.clear(()-> getApp().closePanel(() -> getApp().sendURLBeforeDestroyWithoutCleanData(), () -> {
            getApp().sendURLAfterDestroy();
            create();
        }, StaticFunctions.Empty.r), forCleaner);
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
        if(!Panel.panelIsNull())
            return false;

        if(Panel.check(BasePanel.PanelInfo.Created))
            return false;

        if(mediaIsNull())
            return true;

        return false;
        //return mediaPlayer.isNull();
    }


    public static boolean appStarted() {
        return appStarted;
    }

    public void uiReset() {
        jsonData.jsonSelectedRes=null;
        timer.set(false);
        seekMaxMS(0);
        seekPositionMS(-1);
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
        cleaningInBackground.clear(()->closePanel(() -> {
            sendURLBeforeDestroyWithoutCleanData();
        }, () -> {
            sendURLAfterDestroy();
        }, () -> {
        }), forCleaner);
    }

    public void sendURL() {
        cleaningInBackground.clear(()->closePanel(() -> {
            sendURLBeforeDestroy();
        }, () -> sendURLAfterDestroy(), () -> {
        }), forCleaner);
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
        return videoPanelIsActive() && !Main.notDisplaying() && !isAudioOnly;
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
            isAudioOnly = sourceName != null && sourceName.endsWith("_Radio");
            if (selectedSource == null) {
                sender.sendUrlStartedResetOnlyBoolean();
                AppControl.workingStop();

                jsonData.fullUpdate(wi,he,jsRes);
                return true;
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
                            provider(MediaSourceProviders.EXTRACTOR_AUDIO, MediaSourceProviders.EXTRACTOR)
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
                                                provider(MediaSourceProviders.LIVE_AUDIO_URL, MediaSourceProviders.LIVE_VIDEO_URL)
                                        );
                                    } catch (Exception ed) {
                                        link = new MediaData(
                                                subDirectory.getString("name"),
                                                outLink,
                                                provider(MediaSourceProviders.LIVE_AUDIO_URL, MediaSourceProviders.LIVE_VIDEO_URL)
                                        );

                                        onErrorSave("StartFromJson", ed);
                                        extractorPattern(subDirectory.getString("defaultPatternExtractStream"));
                                        extractorExpirePattern(subDirectory.getString("defaultPatternExtractExpire"));

                                        jsonData.fullUpdate(wi,he,jsRes);
                                        onStop.onMediaChanging(link.directory, link.provider, link.name);
                                        return true;
                                    }
                                }
                                break;
                            }
                        }

                        if (!isHave) {
                            link = new MediaData(
                                    subDirectory.getString("name"),
                                    subDirectory.getString("directory"),
                                    provider(MediaSourceProviders.LIVE_AUDIO_URL, MediaSourceProviders.LIVE_VIDEO_URL)
                            );
                        }
                    } else {
                        link = new MediaData(
                                subDirectory.getString("name"),
                                subDirectory.getString("directory"),
                                provider(MediaSourceProviders.LIVE_AUDIO_URL, MediaSourceProviders.LIVE_VIDEO_URL)
                        );
                    }
                    break;
            }

            jsonData.fullUpdate(wi,he,jsRes);
            if (link == null){
                sender.sendUrlStartedResetOnlyBoolean();
                AppControl.workingStop();
                return true;
            }

            onStop.onMediaChanging(link.directory, link.provider, link.name);

            return true;
        }, () -> "StartFromJson-Error");
    }

    private MediaSourceProviders provider(MediaSourceProviders audio, MediaSourceProviders video) {

        if (isAudioOnly)
            return audio;

        return video;
    }

    public void deleteFromCollection(int tableIndex, String id) {

        savedLinks.deleteById(LinksDbHelper.getTableNamesAsString()[tableIndex], id);
    }

    public void sendURLClose(String url) {
        cleaningInBackground.clear(()->{
            isSavable.set(true);

            sender.sendUrlStart(() -> {
                loadDataWithoutChecking(url, mediaProviderClientSideID.getSave(), null);
                return true;
            }, () -> "SendURLClose-Error");
        }, forCleaner);
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
                return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.Error, type,youtubeGenerator,null);
            }

            if (youtubeGenerator == null) {
                // Fallback: wait and try to get first generator
                waitMS(1000);
                youtubeGenerator = YoutubePlayList.getFirst(10, 300);
                if (youtubeGenerator == null) {
                    return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.Error, type,youtubeGenerator,null);
                }
            }

            playlist.set(true);
            selectedTable(LinksDbHelper.TableName.YOUTUBE_PLAYLIST_LINKS.getIndex());
            try {
                youtubeGenerator.generateInfoAuto();
            } catch (ExtractionException | IOException e) {
                onErrorSave("SendURLClose-GenerateInfoAuto", e);
                return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.Error, type,youtubeGenerator,null);
            }
        } else {
            try {
                youtubeGenerator.generateInfoAuto();
            } catch (ExtractionException | IOException e) {
                onErrorSave("SendURLClose-GenerateInfoAuto", e);
                return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.Error, type,youtubeGenerator,null);
            }

            switch (youtubeGenerator.getInfo().getStreamType()) {
                case LIVE_STREAM:
                case POST_LIVE_STREAM:
                case AUDIO_LIVE_STREAM:
                    selectedTable(LinksDbHelper.TableName.YOUTUBE_LIVE_LINKS.getIndex());
                    break;
            }
        }

        UpdateErrorForYoutube ly = new UpdateErrorForYoutube(youtubeGenerator);

        // Load content and start media session
        youtubeGenerator.loadContent();
        Players.updateIsLive(youtubeGenerator.isLive());

        if (youtubeGenerator.isNotGenerated())
            return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.NotMade, type,youtubeGenerator,ly);

        return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.Made, type,youtubeGenerator,ly);
    }

    private void youtubeVideoScreenSizeUpdate(YoutubeGenerator youtubeGenerator,ListenersYoutube listenersYoutube){
        YoutubeGenerator.Size videoSize = youtubeGenerator.getSize();

        if(videoSize.getWidth() < 1 || videoSize.getHeight() < 1)
            listenersYoutube.setScreenUpdateToDefault();
        else
            Panel.aspectChange(videoSize.getWidth(), videoSize.getHeight());
    }

    private boolean onLoadDataFail(){
        //setUp.set(false);
        return AppControl.waitAndIsWorkingStop();
    }

    private boolean onLoadDataFailFully(){
        sendURL();
        return true;
    }

    public boolean loadData(String url, MediaSourceProviders sourceProvider, String nameOfMedia) {
        if(!mediaIsNullFully()){
            onStop.onMediaChanging(url,sourceProvider,nameOfMedia);
            return true;
        }

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
                    return onLoadDataFail();

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
                        return onLoadDataFail();

                    current = tryGenerateYoutubeContent(youtubeGenerator);

                    if (current.made == YoutubeGeneratorTry.Made)
                        break;
                    else if (current.made == YoutubeGeneratorTry.Error || i > 5)
                        return onLoadDataFail();

                    i++;
                }

                if(current.type == StreamingService.LinkType.PLAYLIST)
                {
                    youtubeVideoScreenSizeUpdate(current.youtubeGenerator,listenn);

                    loop.switchToPlaylist();

                    current.youtubeLoader.onYoutubeLoadEnd();

                    // Start background loading of next few videos (not all)
                    YoutubePlayList.loadInitialVideosInBackground(baseUrl, Math.min(3, YoutubePlayList.streamInfoItem.size() - 1));
                    YoutubePlayList.current = SData.getInt(SData.Data.SavedIndexPlayList);

                    break;
                }


                if (globalGenerator.isLive())
                    listenn.setScreenUpdateToDefault();
                else
                    youtubeVideoScreenSizeUpdate(youtubeGenerator,listenn);

                current.youtubeLoader.onYoutubeLoadEnd();
                break;

            case EXTRACTOR:
                updateVideoSettings(player(playerID.getSave()), player(playerID.getSave()), getLivePlayer());
                Players.updateIsLive(true);
                if (!extractorLoader(nameOfMedia))
                    return onLoadDataFail();

                break;

            case EXTRACTOR_AUDIO:
                isAudioOnly = true;
                updateAudioSettings(player(playerID.getSave()), player(playerID.getSave()), getRadioPlayer());
                Players.updateIsLive(true);
                if (!extractorLoader(nameOfMedia))
                    return onLoadDataFail();

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
                isAudioOnly = true;
                updateAudioSettings(player(playerID.getSave()), player(playerID.getSave()), getRadioPlayer());
                Players.updateIsLive(false);
                urlLoader(false, nameOfMedia);
                break;

            case LIVE_AUDIO_URL:
                isAudioOnly = true;
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

        mediaPlayer.start(getSeekPositionMS());

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
        seekPositionMS(value);
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

            long curP = mediaPlayer.getCurrentPositionMS();

            if(curP==0)
                seekPositionMS(0);
            else
                seekPositionMS(curP);

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

    /** Errors since the player last played normally. Two in a row mean "reloading the same link does not help". */
    private final AtomicInteger errorStreak = new AtomicInteger();

    /**
     * A cached generator is only reused while its link is known to stay valid for a while:
     * its media session (the timer that refreshes the link before it expires) is still running
     * and the link has more than this many seconds left.
     */
    private static final int MIN_LINK_REMAINING_SEC = 20 * 60;

    /**
     * Set when a failure shows that the current link is dead although it has not expired
     * (for example the device's IP changed, YouTube links are tied to the IP). The next link
     * refresh then regenerates the link instead of trusting the expiry time.
     */
    private volatile boolean forceNewLink;

    private boolean isFresh(YoutubeGenerator g) {
        if (g == null || !g.IsLoaded() || !g.mediaSessionWorking())
            return false;
        int expire = g.expireTime();
        return expire < 1 || expire - StaticFunctions.getCurrentTimeAsSeconds() > MIN_LINK_REMAINING_SEC;
    }

    /** Regenerates the current link and reloads the player with it, now, on the calling thread. */
    private void recoverWithFreshLinkNow() {
        if (mediaIsNullFully() || globalGenerator == null)
            return;
        if (!AndroidOsUpdatesListener.isHaveConnection())
            return;                                   // the detector tries again when the network is back
        if (globalGenerator.mediaError.started)
            return;                                   // a refresh is already running
        errorStreak.set(0);
        forceNewLink = true;
        globalGenerator.mediaError.started = true;
        globalGenerator.mediaErrorRun();
    }

    private void recoverWithFreshLink() {
        cleaningInBackground.add(() -> recoverWithFreshLinkNow(),
                StaticFunctions.Empty.a, StaticFunctions.Empty.r, forGenerators, "FreshLinkRecover");
    }

    private static final int PRELOAD_AHEAD = 4;
    private static final int PRELOAD_BEHIND = 2;

    private void preloadIndex(int index, int totalVideos) {
        if (index < 0 || index >= totalVideos) return;
        if (YoutubePlayList.isUnavailable(index)) return;          // never ask again

        YoutubeGenerator gen = YoutubePlayList.getGenerator(index);
        if (!isFresh(gen)) {
            YoutubePlayList.AddSingleElement(index);               // ignores an index already loading
        }
    }

    private void preloadAdjacentVideos(int currentIndex) throws ExtractionException, IOException {
        if (YoutubePlayList.isDisposed()) {
            return;
        }

        int totalVideos = YoutubePlayList.getTotalVideosCount();

        // nearest first, so the video that plays next is ready first
        for (int i = 1; i <= PRELOAD_AHEAD; i++) {
            preloadIndex(currentIndex + i, totalVideos);
        }
        for (int i = 1; i <= PRELOAD_BEHIND; i++) {
            preloadIndex(currentIndex - i, totalVideos);
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
        isAudioOnly = false;
        jsonData.reset();
        MediaProxyServlet.mediaProxyDefault();
        SData.resetToDefault();
        timer.set(false);
        seekMaxMS(0);
        seekPositionMS(-1);
        sendURLBeforeDestroyWithoutCleanData();
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

        UpdateErrorForURL lu = new UpdateErrorForURL(urlGenerator);

        selectedTable(LinksDbHelper.TableName.URLS.getIndex());

        createAndLoad();
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

        UpdateErrorForSiteGenerator lsg = new UpdateErrorForSiteGenerator(siteGenerator);

        // Load content and start media session
        siteGenerator.loadContent();

        if(siteGenerator.isNotGenerated())
            return false;

        createAndLoad();

        return true;
    }
    private boolean loadMediaOnFail(){
        sender.sendUrlStartedResetOnlyBooleanWithoutUIWait();
        return true;
    }
    private void saveTempCheck(){
        ErrorCodeApp.checkIsEqualToSavedData.set("|| ip: "+BaseServer.getIP()+" oldIP: "+ LoadedData.oldIP.get()+
                " BSSID: "+AndroidOsUpdatesListener.getCurrentBSSID()+" oldBSSID: "+ LoadedData.oldBSSID.get()+" |");
    }
    private void loadMedia(){
        sender.sendUrlStart(()->{
            if(mediaPlayer!=null || !LoadedData.isSaved.get())
                return loadMediaOnFail();

            int s = 0;
            while (!BaseServer.getIP().equals(LoadedData.oldIP.get()) ||
                    !AndroidOsUpdatesListener.getCurrentBSSID().equals(LoadedData.oldBSSID.get()))
            {
                if(s<10){
                    s++;
                    waitMS(500);
                }
                else{
                    saveTempCheck();
                    ErrorCodeApp.checkIsEqualToSavedData.append(" NOT LOADED BECAUSE NOT SAME ||");

                    SData.set(SData.Data.UndefiledError,false);

                    return loadMediaOnFail();
                }
            }

            saveTempCheck();
            SavedMedia recovered = SavedMedia.getSavedMedia();

            if(recovered==null)
                return loadMediaOnFail();

            ErrorCodeApp.checkIsEqualToSavedData.append(System.lineSeparator()+
                    "| Name:"+recovered.getName()+
                    " URL:"+recovered.getURL()+
                    " Seek:"+recovered.getSeekMS()+
                    " ProviderID:"+recovered.getProviderID()+" ||");

            if(globalGenerator == null && mediaPlayer == null)
            {
                seekPositionMS(recovered.getSeekMS());
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

    private void volumeSetup()
    {
        if(volumePosition.getSave()==-1)
            mediaVolume(0.5f);
        else
            loadVolume();
    }

    private void loadOrAndStartAndDetection(){
        loadOrAndStartAndDetection(()-> getSeekPositionMS());
        setUp.set(true);
    }

    private void loadOrAndStartAndDetection(@NonNull Callable<Long> seek)
    {
        loadOrLoadAndStart(timer.get(),seek,StaticFunctions.Empty.r);
        errorHandel.detection.startDetectionLost();
    }

    private void resetPlayerAndUpdateLink(@NonNull GeneratorWithExpire generatorWithExpire)
    {
        mediaPlayer.resetWithoutResetPlayingState();
        mediaPlayer.addData(generatorWithExpire.getAudioStream(),generatorWithExpire.getVideoStream());
    }

    private void createAndLoadOrAndStartAndDetection(){
        mediaPlayer = globalGenerator.startPanel(false, loopData.getLoop(), loopData.getPlayListLoop());
        loadOrAndStartAndDetection();
    }

    private void createAndLoadOrAndStartAndPanelAndDetection(){
        Panel.loadPanel((hol)->{
            mediaPlayer = globalGenerator.startPanel(true, loopData.getLoop(), loopData.getPlayListLoop());

            if(mediaIsNull())
                return;

            ((IVideoPlayer)mediaPlayer).setDisplay(hol);

            loadOrAndStartAndDetection(()->{
                long seek = getSeekPositionMS();
                if(seek == 0)
                    seek = 1;
                return seek;
            });
        });
        setUp.set(true);
    }

    private void createAndLoad(){

        if(!mediaIsNullFully()){
            onLoadDataFailFully();
            return;
        }

        loopData = null;
        loopData = loopUpdate();

        if(displayOn())
            createAndLoadOrAndStartAndPanelAndDetection();
        else
            createAndLoadOrAndStartAndDetection();
    }

    public enum YoutubeGeneratorTry{
        NotMade, Error, Made
    }

    public class YoutubeGeneratorTryAndType{
        private final YoutubeGeneratorTry made;
        private final StreamingService.LinkType type;
        private final YoutubeGenerator youtubeGenerator;
        private final UpdateErrorForYoutube youtubeLoader;

        public YoutubeGeneratorTryAndType(YoutubeGeneratorTry made, StreamingService.LinkType type, YoutubeGenerator youtubeGenerator, UpdateErrorForYoutube youtubeLoader){
            this.made = made;
            this.type = type;
            this.youtubeGenerator = youtubeGenerator;
            this.youtubeLoader = youtubeLoader;
        }
    }

    public class MediaReload {
        private final int MAX_TRY_COUNT = 3;
        private final int MAX_TRY_CHECK_COUNT = 3;

        private boolean isRan;
        private int currentTryCounts;

        private final Callable<Boolean> checkingOnlyIsRan = () -> isRan;

        private final Runnable recreateMWithPanel = ()->{
            mediaPlayer.release();
            createAndLoadOrAndStartAndPanelAndDetection();
            onCompleteDefault();
        };

        private final Runnable recreateM = ()->{
            mediaPlayer.release();
            createAndLoadOrAndStartAndDetection();
            onCompleteDefault();
        };

        private final Runnable reloadM = ()->{
            mediaPlayer.reset();
            loadOrAndStartAndDetection();
            onCompleteDefault();
        };

        private final Callable<Boolean> checking = new Callable<Boolean>() {
            private int i;
            @Override
            public Boolean call() throws Exception {
                boolean result = mediaPlayer.notClosable() && i < MAX_TRY_CHECK_COUNT;
                i++;
                return result;
            }
        };

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

        private Callable<Boolean> checkingCurrent = checkingMultiple;

        public final MediaRecreate recreate = new MediaRecreate();
        public final MediaReloadWithCheck reloadWithCheck = new MediaReloadWithCheck();
        public final MediaRecreateWithTask recreateWithTask = new MediaRecreateWithTask();

        public synchronized void reset() {
            currentTryCounts = 0;
            isRan = false;
            checkingCurrent = checkingMultiple;
        }

        public synchronized void tryLoadAfterFirstPlay() {
            checkingCurrent = checkingOnlyIsRan;
        }

        private void onCompleteDefault(){
            isRan = false;
        }

        private class MediaReloadBase {
            private void beforeTryLoadStop(){
                modifyBeforeTryLoadStop();
                videoChanger.stop();
                globalGenerator.mediaErrorStop();
            }

            @CallSuper
            protected void modifyBeforeTryLoadStop(){
                detectionRecover();
            }

            protected void tryLoad(Runnable onComp) {
                synchronized (this) {
                    try {
                        if (checkingCurrent.call())
                            return;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    isRan = true;
                }

                beforeTryLoadStop();

                cleaningInBackground.addPollingTaskWithTimeOut(
                        checking,
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
            }
        }

        public class MediaRecreate extends MediaReloadBase {
            public void reload(){
                tryLoad(recreateM);
            }
            public void reloadWithPanel(){
                tryLoad(recreateMWithPanel);
            }
        }

        public class MediaReloadWithCheck extends MediaReloadBase {
            private Callable<Boolean> detectionRecoverStop;

            @Override
            protected void modifyBeforeTryLoadStop(){
                try {
                    if (detectionRecoverStop.call())
                        super.modifyBeforeTryLoadStop();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            public void reload(@NonNull Callable<Boolean> detectionRecoverStop){
                this.detectionRecoverStop = detectionRecoverStop;
                tryLoad(reloadM);
            }
        }

        public class MediaRecreateWithTask extends MediaReloadBase {

            private Runnable task;

            @Override
            protected void modifyBeforeTryLoadStop(){
                task.run();
                super.modifyBeforeTryLoadStop();
            }

            public void reloadWithPanel(@NonNull Runnable task){
                this.task = task;
                tryLoad(recreateMWithPanel);
            }
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

        /** Longest a run waits for a background load of the next video. It stops earlier as soon
         *  as that load finishes or fails, so this is only a safety cap. */
        private static final int MAX_BACKGROUND_WAIT_MS = 8000;
        private static final int MAX_LOOP_RETRIES = 5;

        /** Next index in the given direction (same wrap rules as before). */
        private int stepIndex(int from, int plusOrMinus, int total) {
            int next = from + plusOrMinus;

            if (plusOrMinus == -1 && from == 0) {
                next = total - 1;
            } else if (from == total - 1 && plusOrMinus == 1) {
                next = 0;
            } else {
                boolean shouldLoop = playlistLoopOn();
                if (next < 0) {
                    next = shouldLoop ? total - 1 : 0;
                } else if (next >= total) {
                    next = shouldLoop ? 0 : total - 1;
                }
            }
            return next;
        }

        /**
         * Replaces AppBack.notLoaded() inside ChangeVideo. Moves the playlist one step in the run's
         * direction (not always +1), and does nothing for a cancelled run: a cancelled run was
         * replaced by a newer one, and moving the index here would corrupt that newer run.
         */
        private boolean notLoaded(ChangerData ch) {
            int total = YoutubePlayList.getTotalVideosCount();
            if (!ch.isCancelled() && !YoutubePlayList.isDisposed() && total > 0) {
                YoutubePlayList.current = stepIndex(YoutubePlayList.current, ch.plusOrMinus, total);
            }
            return false;
        }

        private final Looper playlistLoop = new Looper();

        private Disposable mediaChanger;

        public synchronized Disposable updateChanger(int plusOrMinus) {
            return updateChanger(plusOrMinus, 1, 0);
        }

        private synchronized Disposable updateChanger(int plusOrMinus, int maxAttempts, long delayMillis) {
            NewData item = new NewData(plusOrMinus,maxAttempts,delayMillis);

            mediaChanger = Completable
                    .create(item.getCompletable)
                    .subscribeOn(forGenerators)
                    .subscribe(() -> {}, t -> onErrorSave("ChangeVideo-Error", t));

            return mediaChanger;
        }

        public synchronized void stop() {
            disposeChanger();
            playlistLoop.resetTask();
            playlistLoop.reset();
            ErrorCodeApp.videoChanger.set("videoChanger: ");
        }

        /**
         * check -> (maybe) generate -> apply.
         * Temporary failures (network) are retried up to maxAttempts times.
         * Permanent failures (deleted / private / blocked video) are never retried: the index is
         * marked unavailable and the run continues with the next video.
         */
        private void runPipeline(ChangerData ch, int maxAttempts, long delayMillis) throws ExtractionException, IOException {
            int attempt = 0;
            while (true) {
                // a retry must start clean, otherwise a stale ch.generate=true regenerates a loaded video
                ch.selected = null;
                ch.generate = false;

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
                } catch (IOException | ExtractionException e) {
                    if (ch.isCancelled()) {
                        throw e;
                    }

                    if (YoutubePlayList.isPermanentFailure(e)) {
                        // Dead video: remember it and go round again. inFirstCheck skips every
                        // marked index, so each pass moves on, and the loop ends after at most
                        // "playlist size" passes. This does not count as a retry attempt.
                        YoutubePlayList.markUnavailable(ch.newIndex);
                        ErrorCodeApp.videoChanger.append("|skipped unavailable #" + ch.newIndex + "| ");
                        continue;
                    }

                    attempt++;
                    if (attempt >= maxAttempts) {
                        throw e;
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
            final int totalVideos = YoutubePlayList.getTotalVideosCount();

            int from = ch.currentIndex;

            // At most one pass over the playlist, so an all-dead playlist ends instead of looping.
            for (int hops = 0; hops < totalVideos; hops++) {
                int idx = stepIndex(from, ch.plusOrMinus, totalVideos);

                if (idx < 0 || idx >= totalVideos) {
                    return notLoaded(ch);
                }

                // Known dead: step over it at once, no network and no waiting.
                if (YoutubePlayList.isUnavailable(idx)) {
                    from = idx;
                    continue;
                }

                ch.newIndex = idx;
                ch.selected = YoutubePlayList.getGenerator(idx);

                if (isFresh(ch.selected)) {
                    return true;                                  // preloaded and recent, nothing to wait for
                }

                YoutubePlayList.AddSingleElement(idx);

                // Wait only while that background load is really running. It stops the moment the
                // video is loaded, marked dead, or the load ended without a result.
                final long deadline = System.currentTimeMillis() + MAX_BACKGROUND_WAIT_MS;
                while (System.currentTimeMillis() < deadline) {
                    if (ch.isCancelled()) return notLoaded(ch);

                    ch.selected = YoutubePlayList.getGenerator(idx);
                    if (isFresh(ch.selected)) break;
                    if (YoutubePlayList.isUnavailable(idx)) break;
                    if (YoutubePlayList.isDisposed()) return notLoaded(ch);
                    if (!YoutubePlayList.isLoadingIndex(idx)) break;   // finished without a result

                    waitMS(25);
                }

                if (ch.isCancelled()) return notLoaded(ch);

                if (YoutubePlayList.isUnavailable(idx)) {
                    from = idx;                                   // found dead while waiting
                    continue;
                }

                if (isFresh(ch.selected)) {
                    return true;
                }

                // Not loaded (or too old) and not known dead (temporary failure or too slow):
                // build our own generator, ifNotGeneratedGenerate loads it.
                try {
                    if (YoutubePlayList.streamInfoItem != null && idx < YoutubePlayList.streamInfoItem.size()) {
                        String videoUrl = YoutubePlayList.streamInfoItem.get(idx).getUrl();
                        ch.selected = new YoutubeGenerator(
                                videoUrl,
                                YoutubePlayList.videoSettings,
                                YoutubePlayList.listeners,
                                YoutubePlayList.hardware
                        );
                        ch.generate = true;
                        return true;
                    }
                    return notLoaded(ch);
                } catch (Exception e) {
                    onErrorSave("ChangeVideo", e);
                    return notLoaded(ch);
                }
            }

            ErrorCodeApp.videoChanger.append("|every video in the playlist is unavailable| ");
            return false;
        }

        private boolean ifNotGeneratedGenerate(ChangerData ch) throws ExtractionException, IOException {
            if (ch.selected.generateLink(5)) {
                ch.selected.reloadContent();      // throws for a dead video, runPipeline skips it

                // stored at its real playlist index, not appended at the end of the list
                YoutubeGenerator existing = YoutubePlayList.getGenerator(ch.newIndex);
                if (existing != null && existing != ch.selected && isFresh(existing)) {
                    ch.selected.kill();           // the background load finished first, use that one
                    ch.selected = existing;
                } else {
                    YoutubePlayList.putGenerator(ch.newIndex, ch.selected);
                }
                return true;
            }
            return notLoaded(ch);
        }

        private boolean ifNotLoadedAgain(ChangerData ch) throws ExtractionException, IOException {
            if (ch.selected == null || !ch.selected.IsLoaded()) {
                return notLoaded(ch);
            }

            YoutubeGenerator.Size videoSize = ch.selected.getSize();
            Panel.updateScreen(videoSize.getWidth(), videoSize.getHeight());

            YoutubeGenerator oldGenerator = (YoutubeGenerator) globalGenerator;

            YoutubePlayList.current = ch.newIndex;
            YoutubePlayList.changed = true;

            UpdateErrorForYoutube lys = new UpdateErrorForYoutube(ch.selected);

            if (oldGenerator != null && oldGenerator != globalGenerator) {
                try {
                    YoutubePlayList.updateToDefault(oldGenerator);
                } catch (Exception e) {
                    onErrorSave("ChangeVideo-YoutubePlayList.UpdateToDefault", e);
                }
            }

            if (!isFresh(ch.selected))
                forceNewLink = true;                      // cached link too old: regenerate it now

            globalGenerator.mediaError.started = true;

            saveMedia(MediaSourceProviders.YOUTUBE,
                    globalGenerator.getVideoUrl() + "&list=" + YoutubePlayList.youtubePlaylistId(),
                    globalGenerator.nameOfMedia());

            globalGenerator.mediaErrorRun();

            for (int i = 0; i < 5; i++) {
                if (ch.isCancelled()) return notLoaded(ch);
                waitMS(200);
                if (ch.isCancelled()) return notLoaded(ch);
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

        private class NewData{
            private final int maxAttempts;
            private final CompletableOnSubscribe getCompletable;
            private ChangerData ch;

            public NewData(int plusOrMinus, int maxAttempts, long delayMillis){
                badSoundFixer.run();
                SData.setLong(SData.Data.SavedSeekMS, 0);

                disposeChanger();

                reset(plusOrMinus);

                this.maxAttempts = Math.max(1, maxAttempts);

                getCompletable = emitter -> {
                    ch.bindCancellation(emitter::isDisposed);
                    runPipeline(ch, this.maxAttempts, delayMillis);
                    if (!emitter.isDisposed()) {
                        emitter.onComplete();
                    }
                };
            }

            private void reset(int plusOrMinus){
                mediaPlayer.resetOnlyIsEnded();

                int posNeg = plusOrMinus;

                if(posNeg == 0)
                    posNeg = 1;

                ch = new ChangerData(posNeg);
            }
        }

        private class Looper extends StaticFunctions.Starter{
            private final Runnable pureUpdate = () -> updateChangerLoop(1, 3, 1000);//max = 10
            private final Runnable updateInNewTask = () ->
                    cleaningInBackground.add(pureUpdate, StaticFunctions.Empty.a, StaticFunctions.Empty.r, lifo, "PlayListLoop");

            private Runnable current = updateInNewTask;

            private synchronized Disposable updateChangerLoop(int plusOrMinus, int maxAttempts, long delayMillis) {
                NewData item = new NewData(plusOrMinus, maxAttempts, delayMillis);

                mediaChanger = Completable
                        .create(item.getCompletable)
                        .subscribeOn(forGenerators)
                        .doOnError(t -> {
                            // short pause before a temporary failure is tried again
                            if (!YoutubePlayList.isPermanentFailure(t)) {
                                waitMS(delayMillis);
                            }
                            item.reset(plusOrMinus);
                        })
                        .retry((count, t) -> count < MAX_LOOP_RETRIES
                                && !YoutubePlayList.isPermanentFailure(t)
                                && !YoutubePlayList.isDisposed())
                        // Every way a run can end (done, failed, cancelled, returned without playing)
                        // frees the latch. Before, only a fully successful run did, and after one failed
                        // run every later playlist advance was silently ignored.
                        .doFinally(() -> reset())
                        .subscribe(() -> {}, t -> onErrorSave("ChangeVideo-LoopError", t));

                return mediaChanger;
            }

            @Override
            protected void firstLaunch() {
                current.run();
            }

            @Override
            protected void secondLaunches() {
            }

            public final void resetTask(){
                current = updateInNewTask;
            }

            public final synchronized void onPlaylistLoop() {
                run();
            }

            public final synchronized void onPlaylistLoopInCurrentTask() {
                current = pureUpdate;
                run();
                current = updateInNewTask;
            }
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

        private BiConsumer<Integer,Integer> screenUpdate;

        public ListenersSet(){
            onCreate();
        }

        protected void onCreate(){
            setScreenUpdateToDefault();
        }

        public void setScreenUpdateToDefault(){
            screenUpdate = (width,height) ->
                    Panel.updateScreen(width, height);
        }

        public void setScreenUpdateToEmpty(){
            screenUpdate = StaticFunctions.Empty.bC;
        }

        @Override
        public final void onNotLoadedTryAgainToLoad() {
            try {
                if(checkTryFix.call())
                {
                    if(onExo.cachingFailed() || !onExo.getTemp())
                    {
                        // Already tried without the cache (or it was never used) and the server
                        // still refuses the link. Reloading the same link can not help: that is what
                        // used to happen, three times, and then sendURL() ended the whole session.
                        recoverWithFreshLink();
                    }
                    else
                        mediaReload.reloadWithCheck.reload(()->{
                            if(onExo.cachingFailed())
                                return false;

                            if(onExo.getTemp())
                                onExo.cachingFailed(true);
                            else
                                return false;

                            return true;
                        });
                }
            } catch (Exception e) {}
        }

        @Override
        public final void onErrorListener() {
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

                            // A real "track ended" only: a failed player reports a negative or unset
                            // duration, which used to look like "ended" and skipped to the next
                            // item without ever fetching a fresh link.
                            long duration = mediaPlayer.getDurationMS();
                            if(playlist.get()&& playlistLoopOn()&&
                                    duration > 0 && duration != Long.MAX_VALUE &&
                                    duration<mediaPlayer.getSeekAfterIsPlayingDynamicMS()+10)
                            {
                                //videoChanger.UpdateChanger(1);
                                videoChanger.playlistLoop.onPlaylistLoopInCurrentTask();
                                return;
                            }

                            // Second error in a row: the same link will fail again (expired / refused).
                            // Use the same recovery as the freeze detector instead of reloading it.
                            if(errorStreak.incrementAndGet() >= 2)
                            {
                                recoverWithFreshLinkNow();
                                return;
                            }

                            globalGenerator.mediaError.started = true;
                            onErrorEnd();
                            globalGenerator.mediaError.started = false;
                        } catch (Exception e) {
                            onErrorSave("AppBack-OnError",e);
                            globalGenerator.mediaError.started = true;
                            globalGenerator.mediaErrorRun();
                        }
                    },StaticFunctions.Empty.a,StaticFunctions.Empty.r,forGenerators,"OnErrorListener");
                }
            } catch (Exception e) {}
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
                    seekMaxMS(globalGenerator.getMaxSeekMS());
                    AppControl.waitAndIsWorkingStop();
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @CallSuper
        protected void onErrorEnd(){
            mediaPlayer.seekAfterIsPlayingDynamicUpdate();
            mediaPlayer.resetWithoutResetPlayingState();

            //if(timer.Get())
            loadOrLoadAndStart(mediaPlayer.isPlaying(),()->mediaPlayer.getSeekAfterIsPlayingDynamicMS(),()->{
            });
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
            SData.setLong(SData.Data.SavedSeekMS,0);
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
            videoChanger.playlistLoop.onPlaylistLoop();
        }

        @Override
        public void onStarted(){
            badSoundFixer.stop();
            AppControl.waitAndIsWorkingStop();
        }
    }

    public abstract class UpdateError<T extends Generator>
    {
        protected final T localGenerator;

        public UpdateError(T localGenerator)
        {
            this.localGenerator = localGenerator;
            globalGenerator = this.localGenerator;
            globalGenerator.onErrorUpdate(onUpdateError());
        }

        protected abstract Callable<Boolean> onUpdateError();
    }

    public class UpdateErrorForYoutube extends UpdateError<YoutubeGenerator>
    {
        public UpdateErrorForYoutube(YoutubeGenerator localGenerator) {
            super(localGenerator);
        }

        public Callable<Boolean> onUpdateError()
        {
            return () -> {

                localGenerator.mediaError.started = true;
                Connection.ifNotHaveConnectionWaitInfinityTime(cleaningInBackground,()->{
                    try {
                        mediaPlayer.beforeOnErrorStarted();

                        if(forceNewLink || localGenerator.mediaIsExpired())
                        {
                            forceNewLink = false;
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

                            resetPlayerAndUpdateLink(localGenerator);

                            seekPositionMS(0);
                            mediaPlayer.seekAfterIsPlayingDynamicReset();
                            seekMaxMS(localGenerator.getMaxSeekMS());

                            YoutubePlayList.changed = false;
                        }
                        else
                        {
                            //ErrorHandel.GetSeek();
                            resetPlayerAndUpdateLink(localGenerator);
                        }
                        reloadPanel(reload);

                        //if(timer.Get())

                        loadOrLoadAndStart(mediaPlayer.isPlaying(),()->mediaPlayer.getSeekAfterIsPlayingDynamicMS(),StaticFunctions.Empty.r);

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

        public final void onYoutubeLoadEnd()
        {
            seekMaxMS(globalGenerator.getMaxSeekMS());
            createAndLoad();
        }
    }

    public class UpdateErrorForSiteGenerator extends UpdateError<SiteGenerator>
    {

        public UpdateErrorForSiteGenerator(SiteGenerator localGenerator) {
            super(localGenerator);
        }

        @Override
        public Callable<Boolean> onUpdateError() {
            return () -> {
                localGenerator.mediaError.started = true;
                Connection.ifNotHaveConnectionWaitInfinityTime(cleaningInBackground,()->{
                    mediaPlayer.beforeOnErrorStarted();
                    try {
                        if(forceNewLink || localGenerator.mediaIsExpired())
                        {
                            forceNewLink = false;
                            localGenerator.generateContent();
                            localGenerator.reloadContent();
                        }

                        //BadSoudFixOn();

                        errorHandel.mediaBufferingStop();

                        boolean reload = refreshDisplay();

                        //ErrorHandel.GetSeek();
                        resetPlayerAndUpdateLink(localGenerator);
                        reloadPanel(reload);

                        //if(timer.Get())

                        loadOrLoadAndStart(mediaPlayer.isPlaying(),()->mediaPlayer.getSeekAfterIsPlayingDynamicMS(),StaticFunctions.Empty.r);

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
    }

    public class UpdateErrorForURL extends UpdateError<UrlGenerator>
    {
        public UpdateErrorForURL(UrlGenerator genera) {
            super(genera);
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

                        loadOrLoadAndStart(mediaPlayer.isPlaying(),()->mediaPlayer.getSeekAfterIsPlayingDynamicMS(),StaticFunctions.Empty.r);

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
            Detection_Player_Is_Null,
            RecoverySkip_Global_Recovery_Started,
            RecoveryErrorNull, GeneratorNull,
            InSendState,Paused,
            TickStuckRestarted, TickFailed
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

            private static final long TICK_STUCK_MS = 45_000;
            private static final long GUARD_PERIOD_S = 10;

            private Disposable tickDisposable;
            private Disposable guardDisposable;
            private final AtomicInteger generation = new AtomicInteger();
            private volatile long tickStartedAt;      // 0 while no tick is running
            private volatile long lastTickAt;         // when a tick last started or finished
            private long lastStoppingWrite;           // throttles the StoppingTime disk write
            private volatile boolean wanted;
            private long firstPlaySeed = Long.MIN_VALUE;
            private int notPlayingTicks;
            private int maxNotPlayingTicks;


            private void setup(int intervalMS,int maxCheckOfIsNotPlayingMS){
                this.intervalMS = intervalMS;
                this.maxCheckOfIsNotPlayingMS = maxCheckOfIsNotPlayingMS;
            }

            private synchronized void stop()
            {
                wanted = false;
                generation.incrementAndGet();     // any tick still running becomes a no-op
                disposeTicker();
                if(guardDisposable != null)
                    guardDisposable.dispose();
                guardDisposable = null;
            }

            /**
             * Stops the ticker. fifoForDetector is an interruptible scheduler, so a tick that is
             * already running is interrupted; the generation check also makes it do nothing.
             */
            private synchronized void disposeTicker()
            {
                if(tickDisposable != null)
                    tickDisposable.dispose();
                tickDisposable = null;
            }

            private void recover()
            {
                DetectorSet.detector = Detector.StartingRecovery;

                currentRecover.actionStart(() -> {
                    if (globalGenerator.mediaError.started){
                        DetectorSet.detector = Detector.RecoverySkip_Global_Recovery_Started;
                        return true;
                    }

                    badSoundFixer.run();

                    if (!AndroidOsUpdatesListener.isHaveConnection()){
                        DetectorSet.detector = Detector.No_Connection_SkippingRecovery;
                        return true;
                    }

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

                        if(!AndroidOsUpdatesListener.isHaveConnection()){
                            DetectorSet.detector = Detector.No_Connection_SkippingRecovery;
                            return true;
                        }

                        if (playlist.get()&& playlistLoopOn())
                            videoChanger.updateChanger(1);
                        else
                            globalGenerator.mediaErrorRun();

                        DetectorSet.detector = Detector.RecoveryCompleted;
                        return true;

                    } catch (Exception e) {
                        DetectorSet.detector = Detector.RecoveryError;
                        DetectorSet.exception = e;
                        onErrorSave("Player-OnCompletionListener",e);

                        if (globalGenerator.mediaError.started){
                            DetectorSet.detector = Detector.RecoverySkip_Global_Recovery_Started;
                            return true;
                        }
                        if(!AndroidOsUpdatesListener.isHaveConnection()){
                            DetectorSet.detector = Detector.No_Connection_SkippingRecovery;
                            return true;
                        }


                        if (playlist.get()&& playlistLoopOn())
                            videoChanger.updateChanger(1);
                        else
                            globalGenerator.mediaErrorRun();

                        return false;
                    }
                }, () -> {
                    DetectorSet.detector = Detector.RecoveryErrorNull;
                    return "RecoverError";
                });
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
                // an error flag must stay for at least ~10 s before the detector recovers
                maxNotPlayingTicks = Math.max(1, 10000 / intervalMS);

                synchronized (this) {
                    stop();                       // never two detector loops at the same time
                    firstPlaySeed = Long.MIN_VALUE;
                    notPlayingTicks = 0;
                    wanted = true;
                    startTicker();

                    startGuard();
                }
            }

            private synchronized void startGuard() {
                guardDisposable = Observable
                        .interval(GUARD_PERIOD_S, GUARD_PERIOD_S, TimeUnit.SECONDS, forDetector)
                        .subscribe(n -> guard(), e -> {});      // guard() catches everything itself
            }

            private synchronized void startTicker() {
                final int gen = generation.incrementAndGet();
                tickStartedAt = 0;
                lastTickAt = SystemClock.elapsedRealtime();   // a new ticker gets a full grace period
                disposeTicker();

                // wait, run one tick, then wait again (fixed DELAY: ticks never overlap).
                // safeTick catches Throwable, and retry() resubscribes even if something slips through.
                tickDisposable = Completable
                        .timer(intervalMS, TimeUnit.MILLISECONDS, forDetector)
                        .andThen(Completable.fromAction(() -> safeTick(gen)))
                        .repeat()
                        .retry()
                        .subscribe(() -> {}, e -> {});
            }

            private void safeTick(int gen) {
                if (gen != generation.get())
                    return;                       // a replaced ticker must do nothing
                tickStartedAt = SystemClock.elapsedRealtime();
                lastTickAt = tickStartedAt;
                try {
                    tick();
                } catch (Throwable e) {           // Throwable: an Error used to kill the detector for good
                    try {
                        DetectorSet.detector = Detector.TickFailed;
                        StaticFunctions.onErrorSave("PlayerFreezeDetection: ", e);
                    } catch (Throwable ignore) {}
                } finally {
                    if (gen == generation.get()) {
                        tickStartedAt = 0;
                        lastTickAt = SystemClock.elapsedRealtime();
                    }
                }
            }

            /**
             * Restarts the ticker if its subscription died, one tick has been running too long,
             * or no tick has run for a long time (for example the pool never ran the timer task,
             * so the subscription still looks alive).
             */
            private synchronized void guard() {
                try {
                    if (!wanted)
                        return;
                    long started = tickStartedAt;
                    boolean stuck = started != 0
                            && SystemClock.elapsedRealtime() - started > TICK_STUCK_MS;
                    boolean dead = tickDisposable == null || tickDisposable.isDisposed();
                    boolean silent = started == 0
                            && SystemClock.elapsedRealtime() - lastTickAt > Math.max(30_000L, intervalMS * 20L);

                    if (stuck || dead || silent) {
                        Detector last = DetectorSet.detector;
                        DetectorSet.detector = Detector.TickStuckRestarted;
                        StaticFunctions.onErrorSave("PlayerFreezeDetection restarted, stuck=" + stuck
                                + " dead=" + dead + " silent=" + silent + " last=" + last, new IllegalStateException("detector"));
                        startTicker();                // disposes (and interrupts) the old ticker first
                    }
                } catch (Throwable ignore) {
                    // the guard must never die either
                }
            }

            private void tick() throws Exception
            {
                DetectorSet.detector = Detector.Idle;

                if(globalGenerator == null){
                    DetectorSet.detector = Detector.GeneratorNull;
                    return;
                }

                if(globalGenerator.mediaError.started){
                    DetectorSet.detector = Detector.RecoverySkip_Global_Recovery_Started;
                    return;
                }

                if(!setUp.get()){
                    DetectorSet.detector = Detector.InSendState;
                    return;
                }

                if(!timer.get()){
                    DetectorSet.detector = Detector.Paused;
                    return;
                }

                if(!AndroidOsUpdatesListener.isHaveConnection()){
                    DetectorSet.detector = Detector.No_Connection_SkippingRecovery;
                    return;
                }

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
                    DetectorSet.detector = Detector.PlayerStarting_Skipping;

                    // Non-blocking: compare the position with the previous tick (the old
                    // firstPlayTrigger(50,500) could block this tick for 25 s).
                    long p = mediaPlayer.getCurrentPositionPureMS();
                    boolean moved = firstPlaySeed != Long.MIN_VALUE && p > firstPlaySeed;
                    firstPlaySeed = p;

                    if(moved)
                    {
                        mediaPlayer.markFirstPlayed();
                        mediaPlayer.seekAfterIsPlayingDynamicUpdate(p,(s)->{});
                        mediaReload.tryLoadAfterFirstPlay();
                    }
                    sender.sendUrlStartedReset();
                }

                // Check if already in recovery mode
                if (errorHandel.currentRecover.isActivated()) {
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
                        {
                            // The user wants playback (timer is on) but the player stopped because it
                            // failed: it will never start by itself. A plain pause or an ended track
                            // has no error flag and is left alone.
                            if(!mediaPlayer.isEnded() && !mediaPlayer.waitStarted()
                                    && mediaPlayer.getError()
                                    && ++notPlayingTicks >= maxNotPlayingTicks)
                            {
                                notPlayingTicks = 0;
                                forceNewLink = true;
                                DetectorSet.detector = Detector.CheckingPlayerState_Recovering;
                                recover();
                            }
                            return;
                        }

                        DetectorSet.detector = Detector.CheckingPlayerState_Recovering;
                        recover();

                        return;
                    }

                    notPlayingTicks = 0;

                    // Skip if player is still starting up
                    if (mediaPlayer.waitStarted()) {
                        DetectorSet.detector = Detector.PlayerStarting_Skipping;
                        return;
                    }

                    // Check if player is actually playing
                    long curP = mediaPlayer.getCurrentPositionPureMS();
                    if (curP!=savedSeek){
                        savedSeek = curP;
                        counter = 0;
                        DetectorSet.detector = Detector.PlayerIsPlaying_Healthy;
                        errorStreak.set(0);
                        long nowMs = System.currentTimeMillis();
                        if (nowMs - lastStoppingWrite >= 1000) {      // not on every tick: commit() is a disk write
                            lastStoppingWrite = nowMs;
                            SData.setLong(SData.Data.StoppingTime, nowMs);
                        }

                        if(!globalGenerator.isLive())
                            mediaPlayer.seekAfterIsPlayingDynamicUpdate(savedSeek,(seekk)->
                                    SData.setLong(SData.Data.SavedSeekMS,seekk));
                        return;
                    }
                    startedRecover(maxCheckOfIsNotPlaying);
                    return;
                } else {
                    DetectorSet.detector = Detector.MediaPlayer_Is_Null;
                }

                recover();

            }
        }

        public class Recover extends StaticFunctions.ActionWait{
            @Override
            protected synchronized long maxWaitMs()
            {
                // Must be longer than a full recovery (release waits up to 10 s). With 5 s the next
                // detector tick disposed the recovery that was still running, so it never finished.
                return 30000;
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
                AtomicLong savedSeek = new AtomicLong(-1);

                mediaGetSeek.started = true;
                mediaGetSeek.start(() ->
                {
                    while (savedSeek.get()==-1)
                    {
                        savedSeek.set(mediaPlayer.getCurrentPositionMS());
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
                        savedSeek.set((bufferedPercentage * getSeekMaxMS()) / 100);
                        break;
                    }
                    timeOut++;
                }

                seekPositionMS(savedSeek.get());
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
        private final SenderFunctionality sender;

        public Sender()
        {
            sender = new SenderFunctionality();
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

        private static class SenderFunctionality extends StaticFunctions.ActionWait{

            private final StaticFunctions.Starter onFirst = new StaticFunctions.Starter() {
                @Override
                protected void firstLaunch() {

                }

                @Override
                protected void secondLaunches() {
                    onDispose();
                }
            };

            public final synchronized void currentStopAndResetStateAndUIWait() {
                disposeOnly();
                resetStateAndUIWait();
            }

            public final synchronized void resetStateAndUIWait()
            {
                onResetState();
                Wait.webUIWaitStop();
            }

            @Override
            protected synchronized void onSuccessEnd(){}

            @Override
            protected synchronized void onEnteredInError(){
                currentStopAndResetStateAndUIWait();
            }

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

        private int tryLoadCount;
        private final int MAX_TRY_LOAD_COUNT = 4;

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
            tryLoadCount = 0;
        }

        public class MediaStop{

            private final StaticFunctions.Starter tick = new StaticFunctions.Starter() {
                private Runnable playListClean;
                private Generator oldGenerator;

                @Override
                protected void firstLaunch() {
                    videoChanger.stop();
                    errorHandel.posSaved = false;
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

            private final StaticFunctions.Starter onComple = new StaticFunctions.Starter() {
                @Override
                protected void firstLaunch() {
                    tick.run();
                    mediaPlayer.release();
                    mediaPlayer = null;
                    make();
                }

                @Override
                protected void secondLaunches() {}
            };

            private final StaticFunctions.StarterWithBoolean checking = new StaticFunctions.StarterWithBoolean(){

                @Override
                protected Boolean firstLaunch() throws Exception {
                    tick.run();
                    return mediaPlayer.notClosable();
                }

                @Override
                protected Boolean secondLaunches() throws Exception {
                    return mediaPlayer.notClosable();
                }
            };


            final void mediaSessionStopBase(){
                badSoundFixer.run();

                if(mediaIsNull()){
                    makeOnMediaIsNull();
                    return;
                }

                onComple.reset();
                checking.reset();
                tick.reset();

                cleaningInBackground.addPollingTaskWithTimeOut(checking,
                        tick,
                        onComple,
                        onComple,
                        onComple,
                        StaticFunctions.Empty.a,
                        1500,
                        20000,
                        lifo,
                        lifo,
                        "mediaSessionStop"
                );
            }

            protected void make(){}

            protected void makeOnMediaIsNull(){
                make();
            }
        }

        public class WaitAndIsWorkingStop extends MediaStop{
            @Override
            protected void make(){
                AppControl.waitAndIsWorkingStop();
            }
        }

        public class OnMediaChanging extends MediaStop{

            private final Runnable onE = ()->{
                reset();
                onLoadDataFailFully();
            };

            public void onMediaChanging(String url, MediaSourceProviders sourceProvider, String nameOfMedia) {
                reset();
                cleaningInBackground.clear(()->{
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
                }, forCleaner);
            }

            @Override
            protected void makeOnMediaIsNull(){
                cleaningInBackground.addWithOnTimeOut(()->make(),onE,onE,lifo,"Fail-Load",20000);
            }

            @Override
            protected void make(){
                try {
                    waitMS(100);
                    if(!loadData(url, sourceProvider,nameOfMedia)){
                        return;
                    }
                }
                catch (Exception e){}

                if(tryLoadCount<MAX_TRY_LOAD_COUNT){
                    tryLoadCount++;
                    mediaSessionStopBase();
                    return;
                }

                reset();
                onLoadDataFailFully();
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

        public void fullUpdate(int wi,int he,String jsRes){
            width.update(wi);
            height.update(he);
            jsonSelectedRes = jsRes;
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
}
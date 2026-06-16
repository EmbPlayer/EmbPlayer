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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import app.BasePanel;
import app.tools.AndroidOsUpdatesListener;
import app.tools.Generators.Requirements.GeneratorWithExpire;
import app.tools.Generators.Requirements.MediaSourceProviders;
import app.tools.Generators.SiteGenerator;
import app.tools.LinksDbHelper;
import app.tools.Players.PlayerControllerChangeable;
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
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import app.tools.DisposableTools.WaitDisposable;

import static app.Main.getContext;
import static app.tools.DisposableTools.killAll;
import static app.tools.DisposableTools.forGenerators;
import static app.tools.DisposableTools.forkJoinPool;
import static app.tools.DisposableTools.ioThreadPoolScheduler;
import static app.tools.DisposableTools.waitMS;
import static app.tools.StaticFunctions.onErrorSave;
import static app.tools.StaticFunctions.onThrows;
import static server.Home.app;

import android.media.MediaPlayer;
import android.view.SurfaceHolder;
import server.tools.VideoSettings;
import server.web.ErrorCodeApp;
import server.web.Sources;
import server.web.Wait;

public class AppBack extends AppWeb {
    private static AppBack app;

    private static final BadSoundFixer badSoundFixer = new BadSoundFixer();
    private static final Recyclable.ListDisposable cleaningInBackground = new Recyclable.ListDisposable(AppBack.class);

    private static boolean appStarted;

    public final ChangeVideo videoChanger = new ChangeVideo();
    private final Sender sender = new Sender();

    public AsyncRun errorHandel;
    public PlayerControllerBase mediaPlayer;
    public Generator globalGenerator;

    private String jsonSelectedRes;
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

    public static void recreate() {
        getApp().sendURLWithoutCleanData(() -> new AppBack());
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
        jsonSelectedRes = null;
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
        }, () -> mediaSessionStop(), () -> {
        });
    }

    public void onMediaChanging(String url, MediaSourceProviders sourceProvider, String nameOfMedia) {
        cleaningInBackground.clear();

        closePanel(
                () -> {
                    SData.resetToDefault();
                    uiReset();
                    detectionRecover();
                },
                () -> {
                    mediaSessionStop();
                    loadData(url, sourceProvider, nameOfMedia);
                    },
                () -> {}
        );
    }

    public void reloadMedia(Callable<Boolean> ifTrueMake, @NonNull Runnable onEnd) {
        app().setUp.set(false);

        closePanel(() -> {
            try {
                if (!ifTrueMake.call())
                    return;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            errorHandel.currentRecover.currentStopAndResetState();
            errorHandel.detection.stop();
        }, () -> {
            mediaKillOnly();

            startPanel();

            onEnd.run();
        }, onEnd);
    }

    public void sendURLWithoutCleanData(Runnable task) {
        cleaningInBackground.clear();

        closePanel(() -> {
            sendURLBeforeDestroyWithoutCleanData();
        }, () -> {
            sendURLAfterDestroy();
            task.run();
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
        sender.sendUrlStart(() -> {

            String[] all = savedLinks.getAllColumnValuesById(LinksDbHelper.getTableNamesAsString()[tableIndex], id);

            onMediaChanging(all[1], MediaSourceProviders.values()[Integer.parseInt(all[2])], all[0]);

            return true;
        }, () -> "StartFromCollection-Error");
    }

    public void startFromJson(String sourceName, int subCollectionIndex, int itemIndexInSubCollection) throws JSONException {

        isSavable.set(false);

        sender.sendUrlStart(() -> {

            Sources.Source selectedSource = Sources.getSourcesController().getSource(sourceName);
            boolean isRadio = sourceName != null && sourceName.endsWith("_Radio");
            if (selectedSource == null) {
                sender.sendUrlStartedResetOnlyBoolean();
                return true;
            }
            JSONArray loadJson = selectedSource.jsonFile;
            JSONObject object = loadJson.getJSONObject(subCollectionIndex);
            JSONObject subDirectory = object.getJSONArray("sources").getJSONObject(itemIndexInSubCollection);

            MediaData link = null;

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
                                    jsonSelectedRes = selectedSource.directoryOfResolution(collectionSeletedItems.selecteditems[itemIndexInSubCollection]);
                                    try {
                                        outLink = subDirectory.getString("directoryFirst") + jsonSelectedRes + outLink;
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
                                        onMediaChanging(finalLink.directory, finalLink.provider, finalLink.name);
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

            if (link == null)
                return true;

            MediaData finalLink1 = link;

            onMediaChanging(finalLink1.directory, finalLink1.provider, finalLink1.name);

            return true;
        }, () -> "StartFromJson-Error");
    }

    public void deleteFromCollection(int tableIndex, String id) {

        savedLinks.deleteById(LinksDbHelper.getTableNamesAsString()[tableIndex], id);
    }

    public void sendURLClose(String url) {
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
        }, () -> sender.sendUrlStartedResetOnlyBoolean(), () -> {
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
                return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.Error, type);
            }

            if (youtubeGenerator == null) {
                // Fallback: wait and try to get first generator
                waitMS(1000);
                youtubeGenerator = YoutubePlayList.getFirst(10, 300);
                if (youtubeGenerator == null) {
                    return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.Error, type);
                }
            }

            playlist.set(true);
            selectedTable(LinksDbHelper.TableName.YOUTUBE_PLAYLIST_LINKS.getIndex());
            try {
                youtubeGenerator.generateInfoAuto();
            } catch (ExtractionException | IOException e) {
                onErrorSave("SendURLClose-GenerateInfoAuto", e);
                return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.Error, type);
            }
        } else {
            try {
                youtubeGenerator.generateInfoAuto();
            } catch (ExtractionException | IOException e) {
                onErrorSave("SendURLClose-GenerateInfoAuto", e);
                return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.Error, type);
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
            return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.NotMade, type);

        return new YoutubeGeneratorTryAndType(YoutubeGeneratorTry.Made, type);
    }

    public boolean loadData(String url, MediaSourceProviders sourceProvider, String nameOfMedia) {


        saveMedia(sourceProvider, url, nameOfMedia);

        this.baseUrl = url;
        playlist.set(false);
        timer.set(true);
        onExo.cachingFailed(false);

        errorHandel.detection.detectionSeconds = 5;
        errorHandel.detection.dynamicDetectionDelayMS = 500;
        errorHandel.detection.dynamicTryCount = 15;

        switch (sourceProvider) {
            case YOUTUBE:

                if (!Connection.isHaveInternet())
                    return false;

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

                YoutubeGenerator youtubeGenerator = new YoutubeGenerator(baseUrl, videoSettings, new ListenersYoutube(), hardware.get());
                YoutubeGeneratorTryAndType current = null;

                int i = 0;
                while (true) {
                    //start from here again if failed
                    if (!youtubeGenerator.generateLink(10))
                        return false;

                    current = tryGenerateYoutubeContent(youtubeGenerator);

                    if (current.made == YoutubeGeneratorTry.Made)
                        break;
                    else if (current.made == YoutubeGeneratorTry.Error || i > 5)
                        return false;

                    i++;
                }

                YoutubeGenerator.Size videoSize = youtubeGenerator.getSize();

                if (!globalGenerator.isLive())
                    Panel.aspectChange(videoSize.getWidth(), videoSize.getHeight());

                startPanel();
                seekMax(globalGenerator.getMaxSeek());

                if (current.type == StreamingService.LinkType.PLAYLIST) {
                    // Start background loading of next few videos (not all)
                    YoutubePlayList.loadInitialVideosInBackground(baseUrl, Math.min(3, YoutubePlayList.streamInfoItem.size() - 1));

                    YoutubePlayList.current = SData.getInt(SData.Data.SavedIndexPlayList);
                }

                break;

            case EXTRACTOR:
                updateVideoSettings(player(playerID.getSave()), player(playerID.getSave()), getLivePlayer());
                Players.updateIsLive(true);
                if (!extractorLoader(nameOfMedia))
                    return false;

                break;

            case EXTRACTOR_AUDIO:
                updateAudioSettings(player(playerID.getSave()), player(playerID.getSave()), getRadioPlayer());
                Players.updateIsLive(true);
                if (!extractorLoader(nameOfMedia))
                    return false;

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

        if (globalGenerator.isLive()) {
            errorHandel.detection.detectionSeconds = 1;
            errorHandel.detection.dynamicDetectionDelayMS = 25;
            errorHandel.detection.dynamicTryCount = 300;
        }

        return true;
    }

    public void startVideo() {
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

        mediaPlayer.waitPlay();
        timer.set(true);
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
        SData.setInt(SData.Data.SavedLoop, loop.getSave());
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
        if (!mediaIsNullFully() && timer.get() && !globalGenerator.waitStarted())
            seekPosition(saveSeek((int) mediaPlayer.getCurrentPosition()));
    }

    public boolean mediaSeekStart() {
        if (mediaSeekStart) {
            mediaSeekStart = false;
            return true;
        }
        return false;
    }

    public boolean isPlayingWithWait() {
        if (mediaIsNullFully()) return false;

        int k = (int) mediaPlayer.getCurrentPosition();

        for (int i = 0; i < 10; i++) {
            waitMS(1000);
            if (k < mediaPlayer.getCurrentPosition())
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
                cleaningInBackground.addStartAfterTimeout(50,onDestroy,()->{},forGenerators,"onDestroyMedia");
                return;
            }

            try {
                Panel.close(()->{
                    cleaningInBackground.addStartAfterTimeout(50,onDestroy,()->{},forGenerators,"onDestroyMedia");
                }).accept(cleaningInBackground);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }

        },onError,forGenerators,"closePanel");
    }

    private void sendURLAfterDestroy()
    {
        onExo.cachingFailed(false);

        mediaSessionStop();

        Wait.webUIWaitStop();
    }
    private void sendURLBeforeDestroyWithoutCleanData()
    {
        setUp.set(false);

        detectionRecover();

        sender.sendUrlStartedReset();
    }
    private void sendURLBeforeDestroy()
    {
        SData.resetToDefault();

        jsonSelectedRes = null;
        timer.set(false);
        seekMax(0);
        seekPosition(-1);
        sendURLBeforeDestroyWithoutCleanData();

        SData.set(SData.Data.UndefiledError,false);
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

    private void urlLoader(boolean isLive,String nameOfMedia)
    {
        UrlGenerator urlGenerator = new UrlGenerator(isLive,baseUrl, new ListenersSet(), hardware.get(),nameOfMedia);

        new LoaderForPlayerURL(urlGenerator).updateLoaderAndKiller();

        selectedTable(LinksDbHelper.TableName.URLS.getIndex());

        startPanel();

        seekMax(urlGenerator.getMaxSeek());
    }

    private boolean extractorLoader(String nameOfMedia)
    {
        if(!Connection.isHaveInternet())
            return false;

        //"(https?://[^\"']*index\\.m3u8\\?e=[^\"']*)","e=(\\d+)"
        SiteGenerator siteGenerator = new SiteGenerator(jsonSelectedRes,baseUrl,new ListenersSet(), hardware.get(),true, getExtractorPattern(), getExtractorExpirePattern(),nameOfMedia);

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
        seekMax(globalGenerator.getMaxSeek());

        return true;
    }
    private void loadMedia(){
        boolean undefi = SData.get(SData.Data.UndefiledError);
        if(mediaPlayer!=null||!undefi){
            return;
        }

        SavedMedia recovered = getSavedMedia();

        if(recovered==null){
            return;
        }

        ErrorCodeApp.currentDebug = ErrorCodeApp.currentDebug + "_" +" Name:"+recovered.getName()+
                " URL"+recovered.getURL()+" Seek"+recovered.getSeek()+" ProviderID"+recovered.getProviderID();

        sender.sendUrlStart(()->{
            if(recovered==null)
                return true;

            if(globalGenerator==null&&mediaPlayer==null)
            {
                seekPosition((int)(recovered.getSeek()/1000));
                loadDataWithoutChecking(recovered.getURL(),recovered.getProviderID(),recovered.getName());

                cleaningInBackground.addStartAfterTimeout(750,()->{
                    if(!mediaPlayer.isCreated()/* && !mediaPlayer.SecondBufferingStarted()*/)
                    {
                        mediaPlayer.dispose();
                        errorHandel.recover();
                    }
                },()->{},forkJoinPool,"fistTrigger");
            }
            return true;
        },()->"Loading-Error");
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
    private void mediaKillOnly()
    {
        if(mediaIsNull())
            return;

        PlayerControllerBase oldPlayer = mediaPlayer;

        videoChanger.disposeChanger();

        globalGenerator.mediaErrorStop();

        if(oldPlayer != null)
        {
            while (oldPlayer.notClosable())
            {
                waitMS(1500);
            }

            oldPlayer.release();
        }

        mediaPlayer = null;
    }

    private void mediaSessionStop() {
        if(mediaIsNull())
            return;

        errorHandel.posSaved = false;

        badSoundFixer.run();

        Runnable playListClean = YoutubePlayList.softDispose();
        killAll(errorHandel.mediaBuffering, errorHandel.mediaGetSeek);

        Generator oldGenerator = globalGenerator;
        globalGenerator = null;
        PlayerControllerBase oldPlayer = mediaPlayer;

        videoChanger.disposeChanger();

        Generator generatorC = oldGenerator;
        Runnable playlistC = playListClean;

        if(oldPlayer !=null)
        {
            while (oldPlayer.notClosable())
            {
                if(generatorC != null){
                    generatorC.kill();
                    generatorC = null;
                }
                else if(playlistC != null)
                {
                    playlistC.run();
                    playlistC = null;
                }
                else
                {
                    waitMS(1500);
                }
            }

            oldPlayer.release();
        }

        if(generatorC != null){
            generatorC.kill();
        }

        if(playlistC != null)
        {
            playlistC.run();
        }

        mediaPlayer = null;
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

        public YoutubeGeneratorTryAndType(YoutubeGeneratorTry made,StreamingService.LinkType type){
            this.made = made;
            this.type = type;
        }
    }

    public class ChangeVideo {
        /*
         * Per-run immutable state container.
         * Made static to avoid accidental capture of outer instance.
         */

        private Disposable mediaChanger;
        private ChangerData data;

        public class ChangerData {
            public final int plusOrMinus;
            public YoutubeGenerator selected;
            public int newIndex;
            public int currentIndex;
            public boolean generate;

            public ChangerData(int plusOrMinus) {
                this.plusOrMinus = plusOrMinus;
            }
        }

        /*
         * Replace the active changer data and start a run.
         * Synchronized to avoid races with Dispose().
         */
        public synchronized Disposable updateChanger(int plusOrMinus) {
            badSoundFixer.run();
            disposeChanger();
            data = new ChangerData(plusOrMinus);
            // start immediately and return the Disposable so caller can keep a handle if desired
            return start(throwable -> onErrorSave("ChangeVideo-Error", throwable),
                    () -> {});
        }

        /*
         * Overload that accepts a plusOrMinus and retry parameters.
         * It constructs a fresh ChangerData with the requested plusOrMinus.
         * (We create a new ChangerData rather than mutating the provided one.)
         */
        public synchronized Disposable updateChanger(int plusOrMinus, int maxTry, long delayMills) {
            badSoundFixer.run();
            disposeChanger();
            data = new ChangerData(plusOrMinus);
            return startWithRetry(maxTry, delayMills,
                    throwable -> onErrorSave("ChangeVideo-Error", throwable),
                    () -> {});
        }

        /*
         * Dispose the currently running flow (if any).
         * Synchronized to avoid races with UpdateChanger.
         */
        private synchronized void disposeChanger() {
            if (mediaChanger != null && !mediaChanger.isDisposed()) {
                mediaChanger.dispose();
            }
            mediaChanger = null;
        }

        private boolean isCancelled() {
            return ((mediaChanger != null && mediaChanger.isDisposed()) || Thread.currentThread().isInterrupted());
        }

        /*
         * Single-run start: runs the 3-step flow once and returns the Disposable.
         * The Single payload is ChangerData for clarity.
         */
        private Disposable start(Consumer<Throwable> onError, Action onComplete) {
            if (data == null) {
                // nothing to run
                return null;
            }

            mediaChanger = Single.fromCallable(() -> {
                        boolean ok = inFirstCheck();
                        if (!ok) throw new FlowStopException("InFirstCheck returned false");
                        return data; // return the per-attempt state
                    })
                    // InFirstCheck contains blocking waits -> use computation or IO depending on your DisposableTools mapping.
                    .subscribeOn(forGenerators)

                    .flatMap(ch -> Single.fromCallable(() -> {
                        if (ch.generate) {
                            try {
                                boolean genOk = ifNotGeneratedGenerate();
                                if (!genOk) throw new FlowStopException("IfNotGeneratedGenerate returned false");
                            } catch (ExtractionException | IOException e) {
                                throw e;
                            }
                        }
                        return ch;
                    }).subscribeOn(forGenerators))

                    .flatMap(ch -> Single.fromCallable(() -> {
                        try {
                            boolean finalOk = ifNotLoadedAgain();
                            if (!finalOk) throw new FlowStopException("IfNotLoadedAgain returned false");
                            return true;
                        } catch (ExtractionException | IOException e) {
                            throw e;
                        }
                    }).subscribeOn(forGenerators))

                    .ignoreElement()
                    .subscribe(
                            () -> {
                                try { if (onComplete != null) onComplete.run(); } catch (Exception ignored) {}
                            },
                            throwable -> {
                                if (throwable instanceof FlowStopException) {
                                    try { if (onComplete != null) onComplete.run(); } catch (Exception ignored) {}
                                } else {
                                    try { if (onError != null) onError.accept(throwable); } catch (Exception ignored) {}
                                }
                            }
                    );

            return mediaChanger;
        }

        /*
         * Retrying start: retries the entire flow from step 1 on transient throwables
         * up to maxAttempts times, waiting delayMillis between attempts.
         */
        private Disposable startWithRetry(int maxAttempts, long delayMillis, Consumer<Throwable> onError, Action onComplete) {
            if (data == null) {
                return null;
            }
            if (maxAttempts < 1) maxAttempts = 1;
            final AtomicInteger attempt = new AtomicInteger(0);

            Single<ChangerData> flowSingle = Single.defer(() -> {
                attempt.incrementAndGet();
                return Single.fromCallable(() -> {
                            boolean ok = inFirstCheck();
                            if (!ok) throw new FlowStopException("InFirstCheck returned false");
                            return data;
                        })
                        .subscribeOn(forGenerators)

                        .flatMap(ch -> Single.fromCallable(() -> {
                            if (ch.generate) {
                                try {
                                    boolean genOk = ifNotGeneratedGenerate();
                                    if (!genOk) throw new FlowStopException("IfNotGeneratedGenerate returned false");
                                } catch (ExtractionException | IOException e) {
                                    throw e;
                                }
                            }
                            return ch;
                        }).subscribeOn(forGenerators))

                        .flatMap(ch -> Single.fromCallable(() -> {
                            try {
                                boolean finalOk = ifNotLoadedAgain();
                                if (!finalOk) throw new FlowStopException("IfNotLoadedAgain returned false");
                                return ch;
                            } catch (ExtractionException | IOException e) {
                                throw e;
                            }
                        }).subscribeOn(forGenerators));
            });

            final int finalMaxAttempts = maxAttempts;
            mediaChanger = flowSingle
                    .toObservable()
                    .retryWhen(new Function<Observable<Throwable>, Observable<?>>() {
                        @Override
                        public Observable<?> apply(Observable<Throwable> errors) {
                            return errors.flatMap((Function<Throwable, Observable<?>>) throwable -> {
                                // Do not retry on intentional stop
                                if (throwable instanceof FlowStopException) {
                                    return Observable.error(throwable);
                                }
                                // Only retry on transient exceptions
                                if (throwable instanceof IOException || throwable instanceof ExtractionException) {
                                    if (attempt.get() >= finalMaxAttempts) {
                                        return Observable.error(throwable);
                                    }
                                    return Observable.timer(delayMillis, TimeUnit.MILLISECONDS);
                                }
                                // Other exceptions: do not retry
                                return Observable.error(throwable);
                            });
                        }
                    })
                    .singleOrError()
                    .ignoreElement()
                    .subscribe(
                            () -> {
                                try { if (onComplete != null) onComplete.run(); } catch (Exception ignored) {}
                            },
                            throwable -> {
                                if (throwable instanceof FlowStopException) {
                                    try { if (onComplete != null) onComplete.run(); } catch (Exception ignored) {}
                                } else {
                                    try { if (onError != null) onError.accept(throwable); } catch (Exception ignored) {}
                                }
                            }
                    );

            return mediaChanger;
        }

       /* -------------------------
       Original methods with cancellation checks
       ------------------------- */

        private boolean inFirstCheck() {
            if (data == null) return notLoaded();

            mediaPlayer.startLoading();

            if (YoutubePlayList.isDisposed() || YoutubePlayList.getTotalVideosCount() == 0) {
                return false;
            }

            data.currentIndex = YoutubePlayList.current;
            int totalVideos = YoutubePlayList.getTotalVideosCount();
            data.newIndex = data.currentIndex + data.plusOrMinus;

            if (data.plusOrMinus == -1 && data.currentIndex == 0) {
                data.newIndex = totalVideos - 1;
            } else if (data.currentIndex == totalVideos - 1 && data.plusOrMinus == 1) {
                data.newIndex = 0;
            } else {
                boolean shouldLoop = playlistLoopOn();
                if (data.newIndex < 0) {
                    data.newIndex = shouldLoop ? totalVideos - 1 : 0;
                } else if (data.newIndex >= totalVideos) {
                    data.newIndex = shouldLoop ? 0 : totalVideos - 1;
                }
            }

            if (data.newIndex < 0 || data.newIndex >= totalVideos) {
                return notLoaded();
            }

            data.selected = YoutubePlayList.getGenerator(data.newIndex);

            if (data.selected == null || !data.selected.IsLoaded()) {
                YoutubePlayList.AddSingleElement(data.newIndex);

                int maxWaitAttempts = 10;
                int waitAttempt = 0;

                while (waitAttempt < maxWaitAttempts) {
                    if (isCancelled()) return notLoaded();
                    waitMS(300);
                    if (isCancelled()) return notLoaded();

                    data.selected = YoutubePlayList.getGenerator(data.newIndex);
                    if (data.selected != null && data.selected.IsLoaded()) {
                        break;
                    }

                    waitAttempt++;

                    if (YoutubePlayList.isDisposed()) {
                        return notLoaded();
                    }
                }

                if (data.selected == null || !data.selected.IsLoaded()) {
                    try {
                        if (YoutubePlayList.streamInfoItem != null && data.newIndex < YoutubePlayList.streamInfoItem.size()) {
                            String videoUrl = YoutubePlayList.streamInfoItem.get(data.newIndex).getUrl();
                            data.selected = new YoutubeGenerator(
                                    videoUrl,
                                    YoutubePlayList.videoSettings,
                                    YoutubePlayList.listeners,
                                    YoutubePlayList.hardware
                            );
                            data.generate = true;
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

        private boolean ifNotGeneratedGenerate() throws ExtractionException, IOException {
            if (data == null) return notLoaded();
            if (data.selected.generateLink(5)) {
                data.selected.reloadContent();
                if (YoutubePlayList.getGenerator(data.newIndex) == null) {
                    YoutubePlayList.youtubeGenerators.add(data.selected);
                }
                return true;
            }
            return notLoaded();
        }

        private boolean ifNotLoadedAgain() throws ExtractionException, IOException {
            if (data == null) return notLoaded();
            if (data.selected == null || !data.selected.IsLoaded()) {
                return notLoaded();
            }

            YoutubeGenerator.Size videoSize = data.selected.getSize();
            Panel.updateScreen(videoSize.getWidth(), videoSize.getHeight());

            YoutubeGenerator oldGenerator = (YoutubeGenerator) globalGenerator;
            int oldIndex = YoutubePlayList.current;

            YoutubePlayList.current = data.newIndex;
            YoutubePlayList.changed = true;

            new LoaderForPlayerYoutube(data.selected).updateLoaderAndKillerWithYoutubePlayListDispose();

            if (oldGenerator != null && oldGenerator != globalGenerator) {
                try {
                    YoutubePlayList.updateToDefault(oldGenerator);
                } catch (Exception e) {
                    onErrorSave("ChangeVideo-YoutubePlayList.UpdateToDefault", e);
                }
            }

            globalGenerator.mediaError.started = true;

            saveMedia(MediaSourceProviders.YOUTUBE,globalGenerator.getVideoUrl() + "&list=" + YoutubePlayList.youtubePlaylistId(),globalGenerator.nameOfMedia());

            globalGenerator.mediaErrorRun();

            for (int i = 0; i < 5; i++) {
                if (isCancelled()) return notLoaded();
                waitMS(200);
                if (isCancelled()) return notLoaded();
                if (!globalGenerator.mediaError.started) break;
            }

            if (mediaPlayer.isPlayingDynamic(60, 50)) {
                SData.setInt(SData.Data.SavedIndexPlayList, data.currentIndex);
            }

            preloadAdjacentVideos(data.newIndex);

            Wait.webUIWaitStop();
            return true;
        }

        private class FlowStopException extends RuntimeException {
            FlowStopException(String message) { super(message); }
        }
    }
    public static class Panel extends BasePanel
    {
        private static Consumer<SurfaceHolder> loader;

        public static void loadPanel(Consumer<SurfaceHolder> onLoad)
        {
            loader = onLoad;

            cleaningInBackground.addUI(
                    () -> Main.loadPage(Panel.class),
                    "LoadPanel-Error");
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
    public class ListenersSet extends Listeners
    {
        private final StaticFunctions.Starter onNotLoaded = new StaticFunctions.Starter() {
            @Override
            protected void firstLaunch() {
                reloadMedia(()->{
                    if(onExo.cachingFailed())
                        return false;

                    if(getSavedSource() == MediaSourceProviders.YOUTUBE)
                    {
                        if(onExo.youtubeCaching.get())
                            onExo.cachingFailed(true);
                        else
                            return false;
                    }
                    else
                    {
                        if(onExo.urlCaching.get())
                            onExo.cachingFailed(true);
                        else
                            return false;
                    }

                    return true;
                },()->reset());
            }

            @Override
            protected void secondLaunches() {}
        };
        private final StaticFunctions.Starter onError = new StaticFunctions.Starter() {

            @Override
            protected void firstLaunch() {
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
                            videoChanger.updateChanger(1,10,1000);
                            return;
                        }

                        globalGenerator.mediaError.started = true;
                        mediaPlayer.seekAfterIsPlayingDynamicUpdate();
                        mediaPlayer.resetWithoutResetPlayingState();

                        //if(timer.Get())
                        loadOrLoadAndStart(mediaPlayer.isPlaying(),()->mediaPlayer.getSeekAfterIsPlayingDynamic(),()->{
                        });
                        globalGenerator.mediaError.started = false;
                    } catch (Exception e) {
                        onErrorFail(e);
                    }
                },() -> reset(),()->{},forGenerators,"OnErrorListener");
            }

            @Override
            protected void secondLaunches() {}
        };

        @CallSuper
        @Override
        public void onVideoSizeChangedListener(int width, int height)
        {
            Panel.updateScreen(width, height);
        }

        @CallSuper
        @Override
        public void onPlayListLoop()
        {
            setToNull();
        }

        @Override
        public final void onStarted(){
            badSoundFixer.stop();
        }

        @Override
        public final void onNotLoadedTryAgainToLoad() {
            onNotLoaded.run();
        }

        @Override
        public final void onErrorListener() {
            onError.run();
        }

        @Override
        public final void onBufferingStart() {
            /*ErrorHandel.MediaBufferingStart();*/
        }

        @Override
        public final void onBufferingEnd() {
            /*ErrorHandel.MediaBufferingStop();*/
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
            setToNull();
        }

        private void setToNull()
        {
            SData.setLong(SData.Data.SavedSeek,0);
        }
    }
    public class ListenersYoutube extends ListenersSet
    {
        private final StaticFunctions.Starter playlistLoopTask = new StaticFunctions.Starter() {
            @Override
            protected void firstLaunch() {
                cleaningInBackground.add(()->{
                    ListenersYoutube.super.onPlayListLoop();
                    videoChanger.updateChanger(1,10,1000);
                },()->reset(),()->{},forkJoinPool,"PlayListLoop");
            }

            @Override
            protected void secondLaunches() {}
        };

        @Override
        public void onVideoSizeChangedListener(int width, int height)
        {
            if(globalGenerator.isLive())
                super.onVideoSizeChangedListener(width,height);
        }

        @Override
        public void onPlayListLoop()
        {
            super.onPlayListLoop();
            playlistLoopTask.run();
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
            },()->{});
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

                    loadOrLoadAndStartAndStartDetection(timer.get(),()-> seekLoad(),()->{});

                /*try {
                    youtubeGenerator.Wait(() -> mediaPlayer);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }*/

                    //StartVideo();
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


                try {
                    localGenerator.mediaError.started = true;

                    mediaPlayer.beforeOnErrorStarted();

                    Connection.ifNotHaveConnectionWaitInfinityTime();

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
                                    return false;
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
                                    return false;
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

                    loadOrLoadAndStart(mediaPlayer.isPlaying(),()->mediaPlayer.getSeekAfterIsPlayingDynamic(),()->{});

                    errorHandel.posSaved = false;
                    localGenerator.mediaError.started = false;
                }
                catch (ExtractionException | IOException e)
                {
                    onErrorSave("OnError-UpdateYou",e);
                    localGenerator.mediaError.started = false;
                    //youtubeGenerator.mediaError.started = true;
                    //youtubeGenerator.mediaError.Wait();
                    //youtubeGenerator.GetOnError().call();
                }

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


                try {
                    localGenerator.mediaError.started = true;


                    mediaPlayer.beforeOnErrorStarted();

                    Connection.ifNotHaveConnectionWaitInfinityTime();

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

                    loadOrLoadAndStart(mediaPlayer.isPlaying(),()->mediaPlayer.getSeekAfterIsPlayingDynamic(),()->{});

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

                    loadOrLoadAndStartAndStartDetection(timer.get(),()-> seekLoad(),()->{});
                }

                try {
                    globalGenerator.waitMake(() -> mediaPlayer,globalGenerator.isLive());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        }

        @Override
        public Callable<Boolean> onUpdateError(){
            return () -> {


                try {
                    globalGenerator.mediaError.started = true;


                    mediaPlayer.beforeOnErrorStarted();

                    Connection.ifNotHaveConnectionWaitInfinityTime();
                    //HereErr

                    //youtubeGenerator.mediaError.started = true;


                    //BadSoudFixOn();

                    errorHandel.mediaBufferingStop();

                    boolean reload = refreshDisplay();

                        /*if(!urlGenerator.IsLive())
                        {
                            ErrorHandel.GetSeek();
                        }*/
                    mediaPlayer.resetWithoutResetPlayingState();
                    reloadPanel(reload);

                    //if(timer.Get())

                    loadOrLoadAndStart(mediaPlayer.isPlaying(),()->mediaPlayer.getSeekAfterIsPlayingDynamic(),()->{});

                    errorHandel.posSaved = false;
                    globalGenerator.mediaError.started = false;
                }
                catch (Exception e)
                {
                    onErrorSave("SendURLClose-urlGenerator.OnErrorUpdate",e);
                    globalGenerator.mediaError.started = false;
                    //youtubeGenerator.mediaError.started = true;
                    //youtubeGenerator.mediaError.Wait();
                    //youtubeGenerator.GetOnError().call();
                }

                return true;
            };
        }

        @Override
        public void updateLoaderAndKiller()
        {
            super.updateLoaderAndKiller();
        }
    }
    public class AsyncRun
    {
        public class PlayerFreezeDetection
        {/*
            private class MediaIsChanged
            {
                private int i;
                private Runnable media;

                public MediaIsChanged()
                {
                    Reset();
                }

                public void run()
                {
                    media.run();
                }

                public void Reset()
                {
                    i = 0;
                    media = ()->{};
                }

                public void Check()
                {
                    media = ()->{

                        if(videoChanger.IsMaked())
                        {
                            Reset();
                        }
                        else if(i>3)
                        {
                            videoChanger.UpdateChanger(1);
                            i = 0;
                        }
                        else
                            i++;
                    };
                }
            }*/

            //public final MediaIsChanged mediaIsChanged = new MediaIsChanged();
            private int detectionSeconds;
            private int dynamicDetectionDelayMS;
            private int dynamicTryCount;
            private final int maxCheckOfNotCreated = 15;
            private int countOfChecksOfNotCreated;
            private Disposable detector;

            private void stop()
            {
                if(detector !=null && !detector.isDisposed())
                    detector.dispose();

                //mediaIsChanged.Reset();
            }

            public void recover()
            {
                badSoundFixer.run();

                //String report = getWebUIWaitStopReport();
                //ErrorCodeApp.code40 = ErrorCodeApp.code40 + System.lineSeparator() + report;

                if (AndroidOsUpdatesListener.isHaveConnection()) {
                    ErrorCodeApp.detector = "StartingRecovery";
                    errorHandel.errorFixerIsRan = true;

                    // Single recovery attempt with proper cleanup
                    currentRecover.actionStart(() -> {
                        if (globalGenerator.mediaError.started)
                            return true;
                        try {
                            ErrorCodeApp.detector = "RecoveryStarted";

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

                            ErrorCodeApp.detector = "RecoveryCompleted";
                            return true;

                        } catch (Exception e) {
                            onErrorSave("Player-OnCompletionListener",e);

                            if (globalGenerator.mediaError.started)
                                return true;
                            if(!AndroidOsUpdatesListener.isHaveConnection())
                                return true;

                            ErrorCodeApp.detector = "RecoveryError: " + e.getMessage();
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
                            cleaningInBackground.addStartAfterTimeout(2000,()->{
                                errorHandel.errorFixerIsRan = false;
                                currentRecover.currentStopAndResetStateAndUIWait();
                            },()->{},forkJoinPool,"RecoverError-After-2-Seconds-Delay");
                        }
                    }, () -> "RecoverError");

                } else {
                    ErrorCodeApp.detector = "NoConnection - SkippingRecovery";
                }
            }

            public void startDetectionLost() {
                int maxCheckOfNotCreatedCalculated = maxCheckOfNotCreated/detectionSeconds;

                detector = Observable.interval(detectionSeconds, TimeUnit.SECONDS)
                        .subscribeOn(forkJoinPool)
                        .observeOn(forkJoinPool)
                        .retryWhen(errors -> errors.delay(2, TimeUnit.SECONDS))
                        .subscribe(tick -> {
                            try
                            {
                                //mediaIsChanged.run();

                                ErrorCodeApp.detector = "DetectionTick: " + System.currentTimeMillis();
                                //ErrorCodeApp.code40 = Wait.lastStopCallerInfo;
                                //ErrorCodeApp.code16 = "MaxSeek: "+mediaPlayer.GetDuration();

                                //Breaking-disposable-problem-have-currently-disabled
                                //ErrorCodeApp.getCurrentAppMemoryUsage();

                                //ErrorCodeApp.code23 = ErrorCodeApp.code23+SData.GetLong(SData.Data.SavedSeek)+" ";
                                ErrorCodeApp.stoppingTime = "stoping time: " + SData.getLong(SData.Data.StoppingTime);
                                ErrorCodeApp.disposableErrors = "Disposable Errors: "+SData.getString(SData.Data.SavedDisposableErrors);
                                ErrorCodeApp.mediaPlayerErrors = "MediaPlayer Errors: "+SData.getString(SData.Data.SavedListenersErrors);
                                ErrorCodeApp.dataLoader = SData.getString(SData.Data.SavedDataLoaderActions);

                                if(globalGenerator!=null&&!globalGenerator.mediaError.started && setUp.get() && timer.get() && AndroidOsUpdatesListener.isHaveConnection())
                                {

                                    // SKIP DETECTION if first load hasn't happened yet

                                    if(mediaIsNull())
                                    {
                                        ErrorCodeApp.detector = "Detection - Player Is - Null1";
                                        return;
                                    }
                                    else
                                    {
                                        if(mediaPlayer.actionStarted())
                                        {
                                            ErrorCodeApp.detector = "PlayerAction-NotCompleted";
                                            return;
                                        }

                                        if(!mediaPlayer.isCreated()/* && !mediaPlayer.SecondBufferingStarted()*/)
                                        {
                                            if(maxCheckOfNotCreatedCalculated<countOfChecksOfNotCreated)
                                            {
                                                countOfChecksOfNotCreated = 0;
                                                mediaPlayer.dispose();
                                                recover();
                                                return;
                                            }
                                            countOfChecksOfNotCreated++;
                                            ErrorCodeApp.detector = "PlayerNotLoaded - SkippingDetection";
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
                                        countOfChecksOfNotCreated = 0;

                                        if(mediaPlayer.firstPlayTrigger(50,500))
                                        {
                                            sender.sendUrlStartedReset();
                                            /*sender.SendUrlStartedResetWithoutUIReset();*/
                                        }
                                        else
                                            sender.sendUrlStartedReset();
                                    /*
                                    if(IfIsNotCollectionOrCollectionIsNotLoopingWillBeClosePlayerIfIsNotMakedFirstPlay())
                                        return;*/
                                    }

                                    // Check if already in recovery mode
                                    if (errorFixerIsRan) {
                                        ErrorCodeApp.detector = "RecoveryInProgress - Skipping";
                                        return;
                                    }

                                    ErrorCodeApp.detector = "CheckingPlayerState";

                                    if (!mediaIsNull()) {

                                        // Check if player is actually paused
                                        if(!mediaPlayer.isPlaying())
                                        {
                                            ErrorCodeApp.detector = "PlayerStarting - Healthy - Paused";
                                            if(!globalGenerator.isLive())
                                                return;

                                            ErrorCodeApp.detector = ErrorCodeApp.detector + " - Recovering";
                                            recover();

                                            return;
                                        }

                                        // Skip if player is still starting up
                                        if (mediaPlayer.waitStarted()) {
                                            ErrorCodeApp.detector = "PlayerStarting - Skipping";
                                            return;
                                        }

                                        // Check if player is actually playing
                                        if (mediaPlayer.isPlayingDynamic(dynamicTryCount, dynamicDetectionDelayMS,()->{
                                            ErrorCodeApp.detector = "PlayerIsPlaying - Healthy";
                                            SData.setLong(SData.Data.StoppingTime,System.currentTimeMillis());
                                            if(!globalGenerator.isLive())
                                                SData.setLong(SData.Data.SavedSeek,mediaPlayer.getSeekAfterIsPlayingDynamic());
                                        })){
                                            return;
                                        }
                                    } else {
                                        ErrorCodeApp.detector = "MediaPlayerIsNull";
                                    }


                                    recover();
                                } else {
                                    errorFixerIsRan = false;
                                }
                            }
                            catch (Exception e)
                            {
                                if(mediaIsNull())
                                {
                                    //OnErrorSave("Player-Null-StartDetection",e);
                                    ErrorCodeApp.detector = "Detection - Player Is - Null";
                                    //OnException();
                                }
                            }
                        },onError -> {});/*, throwable -> {
                        ErrorCodeApp.code = "DetectionError: " + throwable.getMessage();
                        System.err.println("RxJava Sync Error: " + throwable.getMessage());
                    });*/
            }
        }

        private final StaticFunctions.ActionWait currentRecover;
        private final WaitDisposable mediaBuffering;
        private final WaitDisposable mediaGetSeek;
        private final PlayerFreezeDetection detection;
        //private final WaitDisposable waitReset;
        //private WaitDisposable mediaBufferingStop;
        private boolean posSaved;
        private boolean errorFixerIsRan;

        public AsyncRun()
        {
            //ErrorCodeApp.code4 = "";
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
            {
                if (PlayerControllerChangeable.isNotHaveErrorFromPlayers(throwable))
                    return;

                StaticFunctions.onThrows(thread,throwable);
            });
            RxJavaPlugins.setErrorHandler((error)->{
                onErrorSave("RX-UndeliverableException",error);
            });
            mediaBuffering = new WaitDisposable(300);
            mediaGetSeek = new WaitDisposable(100);
            detection = new PlayerFreezeDetection();
            currentRecover = new StaticFunctions.ActionWait();
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
        /*
                private void ResetMediaGCOrg(boolean getPos)
                {
                    MediaBufferingStop();

                    createdMedias.add(mediaPlayer);
                    mediaPlayer = null;
                    mediaPlayer = MediaPlayer();

                    BadSoudFixOn();

                    WeakReference<MediaPlayer> oldMedia;

                    if(createdMedias.size()!=0)
                    {
                        oldMedia = new WeakReference<>(createdMedias.get(0));

                        if(getPos&&GetTimer()&&!posSaved&&createdMedias.get(0)!=null)
                        {
                            AtomicInteger savedSeek = new AtomicInteger(-1);

                            mediaGetSeek.started = true;
                            mediaGetSeek.Start(Observable.fromCallable(() ->
                                    {
                                        while (savedSeek.get()==-1)
                                        {
                                            savedSeek.set(SaveSeek(oldMedia.get().getCurrentPosition()));
                                            AppBack.Wait(1000);
                                        }
                                        mediaGetSeek.started = false;
                                        return true;
                                    })
                                    .subscribeOn(Schedulers.io()) // Run the task on a background thread
                                    .observeOn(Schedulers.single()) // Optional: Observe the result on another thread
                                    .subscribe(error -> {
                                        mediaGetSeek.disposable.dispose();
                                    }));

                            int timeOut = 0;
                            while (mediaGetSeek.started)
                            {
                                AppBack.Wait(1000);
                                if(timeOut == mediaGetSeek.second)
                                {
                                    savedSeek.set((bufferedPercentage * GetSeekMax()) / 100);
                                    break;
                                }
                                timeOut++;
                            }

                            SeekPosion(savedSeek.get());
                            posSaved = true;
                        }
                    }
                    else
                        oldMedia = null;

                    createdMedias.clear();

                    System.gc();

                    if(oldMedia==null)
                        return;
                    while (oldMedia.get()!=null)
                    {
                        Wait(100);
                    }
                }
        */
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
        private final StaticFunctions.ActionWait sender;

        public Sender()
        {
            sender = new StaticFunctions.ActionWait(){

                @Override
                public void onDispose()
                {
                    super.onDispose();
                    app().closeDataAndPanelWithoutWaitReset();
                    waitMS(300);
                }
            };
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

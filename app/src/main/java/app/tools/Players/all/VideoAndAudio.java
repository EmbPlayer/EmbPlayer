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
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import app.App.AppBack;
import app.BasePanel;
import app.tools.Players.PlayerController;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import server.web.ErrorCodeApp;
import server.web.Wait;

import static app.tools.DisposableTools.addTask;
import static app.tools.DisposableTools.forMainMedia;
import static app.tools.DisposableTools.forMediaChecking;
import static app.tools.DisposableTools.forSecondMedia;
import static app.tools.DisposableTools.waitMS;

public abstract class VideoAndAudio extends PlayerController implements IVideoPlayer  {
    // Ideal polling interval for extreme low latency
    private static final long SYNC_INTERVAL_MS = 4000; // 4000ms
    // The maximum acceptable delay before an emergency seek/jump
    private static final long MAX_DELAY_MS = 90; // 90ms

    private final MediaTools videoT = new MediaTools() {
        @Override
        protected void onNotDisplaying(Runnable onMediaSeeked) {
            audio.data.start();
            video.pause();

            audioT.onNotDisplaying(onMediaSeeked);
        }

        @Override
        protected boolean secondStart(Runnable onMediaSeeked) {
            audio.data.start();
            return true;
        }

        @Override
        protected boolean secondIsSynced() {
            return audioT.synced;
        }
    };
    private final MediaTools audioT = new MediaTools() {
        @Override
        protected void onNotDisplaying(Runnable onMediaSeeked) {
            baseData().seeking = false;
            onMediaSeeked.run();

            audio.isPlayingDynamic();
        }

        @Override
        protected boolean secondStart(Runnable onMediaSeeked) {

            if(displayOFF()){
                onNotDisplaying(onMediaSeeked);
                return false;
            }
            video.start();

            return true;
        }

        @Override
        protected boolean secondIsSynced() {
            return videoT.synced;
        }
    };
    private final BaseData baseData;

    public boolean hardwareDecoding;

    protected Media<Player> audio;
    protected Player video;

    private boolean dontLoadAudio;
    private Disposable audioSyncInBackground;
    private Disposable delayDetector;
    private SurfaceHolder holder;
    private Display displayOff;

    public VideoAndAudio(Listeners videoPlayer, boolean loop, boolean HardwareDecoding, PlayersCollection AudioClass, PlayersCollection VideoClass)
    {
        super(videoPlayer);

        baseData = new BaseData();

        video = createPlayer(VideoClass,true);
        //audio = new Audio(videoPlayer, loop, HardwareDecoding,true);
        audio = new Media<Player>(videoPlayer) {
            @Override
            protected void preset() {
                //data.VOnPrepareDontStart();
                //data.GetPlayer().V3M();
                //data.BasePreset();
            }
            @Override
            protected void runInConstructor()
            {
                super.runInConstructor();
                data = createPlayer(AudioClass,false);
                preset();
                hardwareDecoding = HardwareDecoding;
            }

            @Override
            protected BaseData baseData() {
                return VideoAndAudio.this.baseData();
            }
        };
        loop(loop);
        preset();
        hardwareDecoding = HardwareDecoding;
    }

    @Override
    public void dispose()
    {
        disposeVideoElements();
        super.dispose();
    }

    @Override
    public void listenersUpdate()
    {
        audio.listenersUpdate();
        video.listenersUpdate();
    }

    @Override
    public boolean secondBufferingStarted()
    {
        return audio.secondBufferingStarted() && video.secondBufferingStarted();
    }

    @Override
    public Scheduler mainScheduler()
    {
        return forSecondMedia;
    }

    public void waitBuffering()
    {
        while (video.bufferingStarted() || audio.data.bufferingStarted())
        {
            waitMS(500);
        }
    }

    @Override
    public Runnable afterLoad_StartAndSeek() {

        Runnable outPut = super.afterLoad_StartAndSeek();

        if(outPut==null)
            outPut = loadAndStartBase(() -> {
                if(!displayOFF())
                    return false;
                audio.afterLoad_StartAndSeek().run();
                return true;
            });

        return outPut;
    }

    @CallSuper
    @Override
    public void loadVolume()
    {
        video.emptyPanelClose();
        super.loadVolume();
    }

    @CallSuper
    @Override
    public void beforeOnErrorStarted()
    {
        video.emptyPanelOpen();
    }
    //
    @Override
    public boolean isNull()
    {
        if(displayOFF())
            return audio.isNull();

        return video.isNull()|| audio.data.isNull();
    }
    @Override
    public long modifyGetCurrentPosition()
    {/*
            if(displayOff)
                return audio.GetCurrentPosition();

            long pos1 = 0;
            long pos2 = 0;
            pos2 = video.GetCurrentPosition();
            pos1 = audio.data.GetCurrentPosition();

*//*
        if(pos1<pos2)
            return pos2;
        return pos1;*//*
            return Math.max(pos1, pos2);*/
        if(displayOFF())
            return audio.modifyGetCurrentPosition();
        return video.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        if(displayOFF())
            return audio.getDuration();
        return video.getDuration();
    }

    @Override
    public boolean getError()
    {
        return (audio.getError()||video.getError());
    }

    @Override
    public void setScreenOnWhilePlaying(boolean on) {
        video.dontSleep(on);
    }

    @Override
    public void addData(String audioUrl, String videoUrl) {
        audio.addData(audioUrl,null);
        video.saveLink(videoUrl);
    }

    @CallSuper
    public void setDisplay(SurfaceHolder holder)
    {
        this.holder = holder;
        displayOff = Display.DisplayONUpdate;
    }

    @Override
    public void nullDisplay()
    {
        displayOff = Display.DisplayOFFUpdate;
        nullBase();
    }

    @Override
    public void refreshDisplay(SurfaceHolder holder) {
        setDisplay(holder);
        refresh(true);
    }

    @Override
    public boolean isPlayingDynamic(int tryCount, int millisecond, Runnable ifMaked) {

        if(displayOFF())
            return audio.isPlayingDynamic(tryCount,millisecond,ifMaked);

        long currentPos = video.getCurrentPosition();

        for(int i = 0; i<tryCount; i++)
        {
            waitMS(millisecond);
            long newPos = video.getCurrentPosition();

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

        if(displayOFF())
            return audio.isPlayingDynamic(tryCount,millsecond);

        long currentPos = video.getCurrentPosition();

        for(int i = 0; i<tryCount; i++)
        {
            waitMS(millsecond);
            long newPos = video.getCurrentPosition();
            if(currentPos<newPos)
            {
                baseData().savedSeekByIsPlayingDynamic = newPos;
                return true;
            }
        }

        return false;
    }

    @Override
    public void loop(boolean on)
    {
        super.loop(on);
        audio.loop(on);
    }

    @Override
    public void waitPlay() {
        audio.waitPlay();
    }

    @Override
    public boolean prepared() {
        return video.isPrepared() && audio.data.isPrepared();
    }

    @Override
    public void inLoadTriggeredLoad() throws IOException {
        if(!dontLoadAudio){
            audio.inLoadTriggeredLoad();
            audioT.isLoaded = true;
        }
        dontLoadAudio = false;

        if(!displayOFF())
        {
            //video.ListenersUpdate();
            video.load(hardwareDecoding);
            videoT.isLoaded = true;
        }
    }

    @Override
    protected void modifySetVolume(float volume)
    {
        audio.modifySetVolume(volume);
    }

    protected int maxTryingsInSeeking()
    {
        return 80;
    }

    @Override
    protected void modifyStart(long mills) {
        baseData().updateSeekSecondAndResetEndState(mills);
        modifyStartBase(() -> {
            if(!displayOFF())
                return false;

            audio.modifyStart(mills);

            return true;
        });
    }

    @Override
    protected void modifyStart() {
        modifyStartBase(() -> {
            if(!displayOFF())
                return false;

            audio.modifyStart();
            return true;
        });
    }

    @Override
    protected Runnable afterLoad_Start() {
        return loadAndStartBase(() -> {
            if(!displayOFF())
                return false;
            audio.afterLoad_Start().run();
            return true;
        });
    }

    @Override
    protected void modifyResetGC()
    {
        disposeVideoElements();
        if(displayOFF())
        {
            audio.modifyResetGC();
            return;
        }

        audio.dispose();

        WeakReference<Player> oldVideo = video.resetGC();
        WeakReference<Player> oldAudio = audio.data.resetGC();

        System.gc();

        if(oldVideo.get()!=null)
        {
            oldVideo.get().release();
        }
        if(oldAudio.get()!=null)
        {
            oldAudio.get().release();
        }
    }

    @Override
    protected void modifyReset()
    {
        disposeVideoElements();

        audio.modifyReset();
        if(!displayOFF())
        {
            video.reset();
        }
    }


    @CallSuper
    @Override
    protected void modifyRelease() {

        video.emptyPanelClose();
        disposeVideoElements();

        audio.modifyRelease();
        if(!displayOFF())
        {
            video.release();
        }
    }

    @Override
    protected void modifyPause()
    {
        if(!displayOFF())
        {
            video.pause();
        }
        audio.modifyPause();
    }

    protected void preset() {
        //SetScreenOnWhilePlaying(true);
        audio.data.secondPlayer(true);
        //video.V7();
        //video.V2();
        video.basePreset();
    }

    @Override
    protected BaseData baseData(){
        return baseData;
    }

    protected void syncInBackGroud()
    {
        if(delayDetector ==null|| delayDetector.isDisposed())
            delayDetector = Observable.interval(SYNC_INTERVAL_MS, TimeUnit.MILLISECONDS)
                    // 2. Schedule the timer itself to run on a background thread (computation)
                    .subscribeOn(forMediaChecking)
                    .observeOn(forMediaChecking)
                    // 3. Schedule the actual synchronization logic to run on the Android Main Thread
                    //.observeOn(DisposableTools.uiScheduler())
                    // 4. Subscribe and define the action for each interval
                    .subscribe(tick -> {
                        // This block executes every SYNC_INTERVAL_MS on the Main Thread
                        if(!displayOFF())
                            performSyncCheck();
                        else
                            delayDetector.dispose();
                    }, throwable -> {
                        // Handle errors in the stream (e.g., if a player access throws an exception)
                        System.err.println("RxJava Sync Error: " + throwable.getMessage());
                    });
    }

    private boolean displayOFF()
    {
        switch (displayOff)
        {
            case DisplayON:
                return false;

            case DisplayOFF:
                return true;

            case DisplayONUpdate:

                if(AppBack.Panel.check(BasePanel.PanelInfo.notDisplaying)){
                    displayOff = Display.DisplayOFF;
                    return true;
                }

                video.setDisplaySurface(holder);
                video.secondPlayer(false);
                audio.data.secondPlayer(true);

                displayOff = Display.DisplayON;

                return false;

            case DisplayOFFUpdate:

                nullBase();

                displayOff = Display.DisplayOFF;

                return true;
        }
        return true;
    }

    private void cleanSync()
    {
        audioT.synced = false;
        videoT.synced = false;

        if(audioSyncInBackground ==null|| audioSyncInBackground.isDisposed())
            return;
        audioSyncInBackground.dispose();
    }

    private void disposeVideoElements()
    {
        videoT.isLoaded = false;
        audioT.isLoaded = false;

        if(delayDetector !=null&&!delayDetector.isDisposed())
            delayDetector.dispose();

        cleanSync();
    }

    private boolean delayIsHave()
    {
        long audioTime = audio.data.getCurrentPosition();
        long videoTime = video.getCurrentPosition();
        long delay = audioTime - videoTime;

        return delay > MAX_DELAY_MS;
    }

    private void performSyncCheck() {
        if (!waitStarted() && isPlaying()) {

            if(delayIsHave())
            {
                    /*
                    AppBack.Wait(20000);

                    if(delayIsHave())*/
                start(audio.data.getCurrentPosition()+3000);
            }
        }
    }

    private void syncer(Player main, @NonNull MediaTools mainT,
                        boolean forceStarter, long seek,
                        @NonNull Runnable onMediaSeeked) throws Exception {
        if(forceStarter)
        {
            main.start();
            long posN = main.getCurrentPosition();
            for(int l = 0; l<20; l++)
            {
                waitMS(100);
                if(posN+100<main.getCurrentPosition())
                    break;
            }
        }

        main.start(seek);

        for(int i = 0; i< maxTryingsInSeeking()*250; i++)
        {
            if(displayOFF()){
                mainT.synced();
                mainT.onNotDisplaying(onMediaSeeked);
                return;
            }

            waitMS(1);
            if(seek+1 < main.getCurrentPosition())
                break;
        }

        mainT.synced();

        if(displayOFF())
        {
            mainT.onNotDisplaying(onMediaSeeked);
            return;
        }

        if(mainT.secondIsSynced())
        {
            for(int i = 0; i<10; i++)
            {
                if(mainT.secondStart(onMediaSeeked))
                    waitMS(1);
                else
                    return;
            }

            baseData().seeking = false;
            onMediaSeeked.run();
            syncInBackGroud();
            Wait.webUIWaitStop();
            return;
        }

        main.pause();
    }

    private boolean baseFunctionForLoadAndStartAudioIsOnReturn(@NonNull Callable<Boolean> ifIsAudio, @NonNull Runnable onMediaSeeked) throws Exception {

        if(ifIsAudio.call()){
            onMediaSeeked.run();
            return true;
        }

        if(baseData().getSeekSecond()<300)
        {
            baseData().updateSeekSecondAndResetEndState(100);
        }

        boolean forceStarter = baseData().getSeekSecond()>500;

        baseData().seeking = true;
        long seekM = baseData().getSeekSecond()+1;

        cleanSync();
        audioSyncInBackground = addTask(()->{
            syncer(audio.data,audioT,forceStarter,seekM,onMediaSeeked);
            return true;
        },() -> "SynkroPureError", forMainMedia);

        syncer(video,videoT,forceStarter,seekM,onMediaSeeked);
        return true;
    }

    private void modifyStartBase(@NonNull Callable<Boolean> ifIsAudio)
    {
        try {
            baseFunctionForLoadAndStartAudioIsOnReturn(ifIsAudio,()-> mediaStart());
        } catch (Exception e) {
            //throw new RuntimeException(e);
        }
    }

    private Runnable loadAndStartBase(@NonNull Callable<Boolean> ifIsAudio)
    {
        return ()->{
            try {
                baseFunctionForLoadAndStartAudioIsOnReturn(ifIsAudio,()->{
                    mediaStart();
                    loadVolume();
                });
            } catch (Exception e) {
                //throw new RuntimeException(e);
            }
        };
    }

    private void nullBase()
    {
        audio.data.secondPlayer(false);
        video.secondPlayer(true);
        video.pause();
        video.nullDisplay();
        holder = null;
        //video.SetDisplay(null);
    }

    private void refresh(boolean runInAnotherThread) {
        try {
            refresh(runInAnotherThread,()->true,()->false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void refresh(boolean runInAnotherThread, Callable<Boolean> onTrue, Callable<Boolean> onFalse) throws Exception {
        if(baseData().seeking||!isPlaying())
        {
            onTrue.call();
            return;
        }

        boolean currPosUpdate;
        if(runInAnotherThread && notClosable()){
            waitActionCompleteAndStart();
            currPosUpdate = false;
        } else {
            currPosUpdate = true;
        }

        reSeekOrLoad(runInAnotherThread,()->
        {
            if(currPosUpdate)
                baseData().updateSeekSecondAndResetEndState(audio.getCurrentPosition());

            if(displayOFF()||videoT.isLoaded)
            {
                modifyStart(baseData().getSeekSecond());

                return onTrue.call();
            }

            video.reset();
            onCleanWithoutResetPlayingState();

            dontLoadAudio = audioT.isLoaded;

            return onFalse.call();
        },(onT,onF)-> {
            try {
                refresh(false,onT,onF);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        },()->{
            mediaStart();
            loadVolume();

            if(!displayOFF())
                syncInBackGroud();

            Wait.webUIWaitStop();
            return true;
        },()->{
            loadVolume();
            return false;
        });
    }

    protected abstract Player createPlayer(PlayersCollection player, boolean videoOnly);

    private enum Display
    {
        DisplayOFF,DisplayON, DisplayONUpdate,DisplayOFFUpdate
    }

    private abstract static class MediaTools
    {
        private boolean synced;
        private boolean isLoaded;

        public final void synced()
        {
            synced = true;
        }

        protected abstract void onNotDisplaying(Runnable onMediaSeeked);
        protected abstract boolean secondStart(Runnable onMediaSeeked);
        protected abstract boolean secondIsSynced();
    }
}
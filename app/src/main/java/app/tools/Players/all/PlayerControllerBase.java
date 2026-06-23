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
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

import androidx.annotation.NonNull;
import app.App.AppBack;
import app.tools.SData;
import app.tools.StaticFunctions;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import server.web.ErrorCodeApp;

import androidx.annotation.CallSuper;
import server.web.Wait;

import static app.tools.DisposableTools.addTask;
import static app.tools.DisposableTools.forMainMedia;
import static app.tools.DisposableTools.waitMS;
import static app.tools.StaticFunctions.onErrorSave;
import static server.Home.app;
import static app.tools.StaticFunctions.TryToComplete;

public abstract class PlayerControllerBase {
    private final TryToComplete isLoadedChecking = new TryToComplete() {

        @Override
        protected Boolean completion() {

            for (int i = 0; i<75; i++)
            {
                waitMS(250);
                if(loading != Loading.Loading)
                    break;
            }

            return loading == Loading.Loaded;
        }
    };

    private final TryToComplete isNotPreparedChecking = new TryToComplete() {

        @Override
        protected Boolean completion() {

            for(int i = 0; i< maxTryingsInPreparing(); i++)
            {
                waitMS(250);
                if(prepared())
                {
                    loading = Loading.Loaded;
                    return false;
                }
            }
            onNotLoaded();

            return true;
        }
    };

    protected Listeners listeners;
    private Loading loading = Loading.Loading;

    public PlayerControllerBase(Listeners listeners)
    {
        this.listeners = listeners;
    }

    public final long getCurrentPosition()
    {
        if(isEnded()){
            return getDuration();
        }

        if(isLive()){
            return 0;
        }

        return modifyGetCurrentPosition();
    }

    public final boolean isEnded()
    {
        return baseData().seekAndEnd.isEnded();
    }

    public final void setVolume(float volume)
    {
        modifySetVolume(calculateVolumeCorrectly(volume));
    }

    public final void startLoading()
    {
        loading = Loading.Loading;
        isNotPreparedChecking.reset();
        isLoadedChecking.reset();
    }

    public final boolean isLoaded()
    {
        try {
            return isLoadedChecking.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public final void start(long mills)
    {
        baseData().waitSeek.onStartOrPause(()->{
            baseData().updateSeekSecondAndResetEndState(mills);
            modifyStart(baseData().getSeekSecond());
            listeners.onStarted();
            return true;
        },()->"Start-Seek-Error");
    }

    public final void start()
    {
        baseData().waitSeek.onStartOrPause(()->{
            modifyStart();
            listeners.onStarted();
            return true;
        },()->"Start-Error");
    }

    public final void pause()
    {
        baseData().waitSeek.onStartOrPause(()->{
            pauseForce();
            return true;
        },()->"Pause-Error");
    }

    public final void resetGC()
    {
        onClean();
        modifyResetGC();
    }

    public final void reset()
    {
        onClean();
        modifyReset();
        System.gc();
    }

    public final void resetWithoutResetPlayingState()
    {
        onCleanWithoutResetPlayingState();
        modifyReset();
        System.gc();
    }

    public final void release()
    {
        AppBack.Panel.blackPanelFix = false;
        onClean();
        modifyRelease();
        System.gc();
    }

    public final void load()
    {
        baseData().waitSeek.actionStart(()->{
            modifyLoad();
            Wait.webUIWaitStop();
            return true;
        },()->"LoadError");
    }

    public final long getSeekAfterIsPlayingDynamic()
    {
        return baseData().savedSeekByIsPlayingDynamic;
    }

    public final void seekAfterIsPlayingDynamicReset()
    {
        baseData().savedSeekByIsPlayingDynamic = 0;
    }
    public final void seekAfterIsPlayingDynamicUpdate()
    {
        long newSeek = getCurrentPosition();
        if(baseData().savedSeekByIsPlayingDynamic<newSeek)
            baseData().savedSeekByIsPlayingDynamic = newSeek;
    }

    public final boolean waitStarted()
    {
        return app().globalGenerator.waitStarted()|| actionStarted();
    }

    public final boolean isPlaying()
    {
        return !baseData().mediaIsPaused;
    }

    public final boolean notClosable()
    {
        return baseData().waitSeek.isActivated();
    }

    public final boolean isCreated()
    {
        return loading!=Loading.Loading;
    }

    public final boolean firstPlayed()
    {
        return baseData().firstPlayIsStarted;
    }

    public final void setMaxSeek(long seek)
    {
        baseData().maxSeek = seek;
    }

    @CallSuper
    public final boolean firstPlayTrigger(int tryCount, int millSecond)
    {
        boolean output = isPlayingDynamic(tryCount,millSecond);
        baseData().firstPlayIsStarted = output;
        return output;
    }

    public final void loadAndStart(long seek)
    {
        baseData().waitSeek.loadAndAfter(canLoad(),()->{

            baseData().updateSeekSecondAndResetEndState(seek);
            afterLoad_StartAndSeek().run();
            listeners.onStarted();

        }, ()->"LoadAndStart-Error");
    }

    public final void playListLoop(boolean on)
    {
        baseData().playListLoop = on;
    }

    @CallSuper
    public boolean actionStarted()
    {
        return notClosable()|| baseData().waitSeek.isTryingToStartOrStop();
    }

    @CallSuper
    public void loadVolume()
    {
        app().loadVolume();
    }

    @CallSuper
    public void dispose(){
        baseData().waitSeek.currentStopAndResetState();
    }

    public boolean isLive()
    {
        return false;
    }

    @CallSuper
    public void loop(boolean on)
    {
        baseData().loop = on;
    }

    public void beforeOnErrorStarted(){}

    public Scheduler mainScheduler()
    {
        return forMainMedia;
    }

    public Loading currentLoadingState()
    {
        return loading;
    }

    /*public void ChangeListeners(IPlayer media, Listeners listeners)
    {
        media.ListenersChange(listeners);
    }*/

    protected int maxTryingsInPreparing()
    {
        return 120;
    }

    protected void reSeekOrLoad(boolean runInAnotherThread,
                                @NonNull Callable<Boolean> seeking,
                                BiConsumer<Callable<Boolean>,Callable<Boolean>> afterLoadIfIsPlaying,
                                Callable<Boolean> OnTrue, Callable<Boolean> OnFalse) throws Exception {
        baseData().waitSeek.reSeekOrLoad(canLoad(),runInAnotherThread,seeking,afterLoadIfIsPlaying,OnTrue,OnFalse);
    }

    protected void onClean()
    {
        mediaStop();
        onCleanWithoutResetPlayingState();
    }

    protected void onCleanWithoutResetPlayingState()
    {
        startLoading();
        baseData().pauseAfterLoad = true;
        baseData().firstPlayIsStarted = false;
    }

    protected float calculateVolumeCorrectly(float input)
    {
        float outPut = input;

        for(int i = 0; i<2; i++)
        {
            outPut = outPut*input;
        }

        return outPut;
    }

    @CallSuper
    protected boolean notPreparedBase()
    {
        try {
            return isNotPreparedChecking.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected boolean notPreparedInLoadAndStarting()
    {
        if(notPreparedBase()){
            return true;
        }

        onPrepared();

        return false;
    }

    protected boolean notPreparedForLoad()
    {
        return notPreparedBase();
    }

    protected final void mediaStart()
    {
        baseData().mediaIsPaused = false;
    }

    protected final void end(Callable<Boolean> OnEnd)
    {
        if(isEnded()|| isLive())
            return;

        try {
            if(OnEnd.call())
                baseData().seekAndEnd.reset();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected final void pauseForce()
    {
        mediaStop();
        modifyPause();
        Wait.webUIWaitStop();
    }

    protected final void modifyLoad(){
        baseData().firstPlayIsStarted = false;
        baseData().pauseAfterLoad = true;
        if(loadAndWait())
            notPreparedForLoad();
        loadVolume();
    }

    protected final void mediaStop()
    {
        baseData().seeking = false;
        baseData().mediaIsPaused = true;
    }

    protected final void onPrepared()
    {
        baseData().seekAndEnd.run();
    }

    protected final void waitLittlePlaying()
    {
        isPlayingDynamic(5, 750);
    }

    protected final void waitActionCompleteAndStart(Runnable base){
        for(int i = 0; i<1000; i++)
        {
            waitMS(50);
            if(!notClosable())
            {
                base.run();
                return;
            }
        }
        baseData().waitSeek.currentStopAndResetState();
        base.run();
    }


    protected final void waitActionCompleteAndStart(){
        for(int i = 0; i<1000; i++)
        {
            waitMS(50);
            if(!notClosable())
            {
                return;
            }
        }
        baseData().waitSeek.currentStopAndResetState();
    }

    protected final boolean loadAndWait()
    {
        boolean returnState;

        //App().MuteVolume();
        try {
            inLoadTriggeredLoad();
            returnState = true;
        } catch (IOException e) {
            onErrorSave("MediaPlayerBase-LoadAndWait",e);
            onNotLoaded();
            returnState = false;
        }

        return returnState;
    }

    @CallSuper
    protected Runnable afterLoad_StartAndSeek()
    {
        if(isLive())
        {
            baseData().updateSeekSecondAndResetEndState(0);
            return afterLoad_Start();
        }
        else if(baseData().getSeekSecond() == 0)
        {
            return afterLoad_Start();
        }

        return null;
    }
    protected void onEnded()
    {
        end(()->{

            //GetSeekAfterIsPlayingDynamic();
            if (getCurrentPosition()<(getDuration()-1500))
                return false;

            //GetSeekAfterIsPlayingDynamic()

            if (baseData().getLoop()){
                PlayerControllerBase.this.waitActionCompleteAndStart(()-> PlayerControllerBase.this.start(0));
                return false;
            }

            if(baseData().getPlayListLoop()){
                listeners.onPlayListLoop();
                return false;
            }

            mediaStop();
            listeners.onCompletionListener();

            return true;
        });
    }
    protected boolean onEndTriggered(long curPos)
    {
        long duration= getDuration();

        if(duration>curPos)
            return false;

        onEnded();
        return true;
    }
    protected void onPrepareMaking()
    {
        if(baseData().getPauseAfterLoad())
            pauseForce();
    }
    protected void modifyStart(long mills)
    {
        mediaStart();
    }
    protected void modifyStart()
    {
        mediaStart();
    }

    private void onNotLoaded()
    {
        baseData().pauseAfterLoad = true;
        loading = Loading.NotLoaded;
    }
    private Callable<Boolean> canLoad()
    {
        return () -> loadAndWait() && !notPreparedInLoadAndStarting();
    }

    public abstract boolean isNull();
    public abstract long getDuration();
    public abstract boolean getError();
    public abstract boolean isPlayingDynamic(int tryCount, int millSecond);
    public abstract boolean isPlayingDynamic(int tryCount, int millSecond, Runnable ifMaked);
    public abstract void waitPlay();
    public abstract void addData(String audioUrl, String videoUrl);
    public abstract boolean prepared();
    public abstract boolean secondBufferingStarted();

    protected abstract BaseData baseData();
    protected abstract Runnable afterLoad_Start();
    protected abstract long modifyGetCurrentPosition();
    protected abstract void modifyPause();
    protected abstract void inLoadTriggeredLoad() throws IOException;
    protected abstract void modifyResetGC();
    protected abstract void modifyReset();
    protected abstract void modifyRelease();
    protected abstract void modifySetVolume(float volume);
    protected abstract void preset();
    protected abstract void listenersUpdate();

    public enum Loading {
        NotLoaded, Loading, Loaded;
    }
    public class SeekAndEnd extends StaticFunctions.Starter
    {
        private boolean isEnded;

        public final boolean isEnded()
        {
            return isEnded;
        }

        public final void updateTemp(long tempSeek)
        {
            if(!isLive()&&tempSeek< getDuration())
            {
                SData.setLong(SData.Data.SavedSeek,tempSeek);
            }
        }

        @Override
        public void reset(){
            isEnded = true;
            SData.set(SData.Data.UndefiledError,false);
            super.reset();
        }

        @Override
        protected void firstLaunch() {
            isEnded = false;
            SData.set(SData.Data.UndefiledError,true);
        }

        @Override
        protected void secondLaunches() {
        }
    }
    public class WaitSeek extends StaticFunctions.ActionWait
    {
        private boolean isTryingToStopOrStart;

        public synchronized void loadAndAfter(Callable<Boolean> CanLoad, Runnable Start, Callable<String> OnError) {

            if (isActivated())
                return;

            activate();

            onDispose();

            baseData().firstPlayIsStarted = false;
            baseData().pauseAfterLoad = false;
            //BaseData().pauseAfterLoad = true;

            currentAction = onThread(() -> {
                boolean output = CanLoad.call();

                resetState();

                if(output)
                    Start.run();

                onSuccessEnd();

                return output;
            }, () -> {
                String output = OnError.call();
                currentStopAndResetStateAndUIWait();
                return output;
            });
        }

        public void reSeekOrLoad(@NonNull Callable<Boolean> CanLoad, boolean runInAnotherThread,
                                 @NonNull Callable<Boolean> seeking,
                                 @NonNull BiConsumer<Callable<Boolean>,Callable<Boolean>> afterLoadIfIsPlaying,
                                 @NonNull Callable<Boolean> OnTrue, @NonNull Callable<Boolean> OnFalse) throws Exception {
            if(runInAnotherThread)
                onStartOrPause(()->{
                    reSeekBase(seeking,CanLoad,afterLoadIfIsPlaying,OnTrue,OnFalse);
                    return true;
                },()->"Error-Seek");
            else
                reSeekBase(seeking,CanLoad,afterLoadIfIsPlaying,OnTrue,OnFalse);
        }

        public boolean isTryingToStartOrStop()
        {
            return isTryingToStopOrStart;
        }

        protected final synchronized void onStartOrPause(Callable<Boolean> Base, Callable<String> OnError){
            if (isActivated())
                return;

            isTryingToStopOrStart = true;

            disposeAndRun(() -> {
                boolean output = Base.call();
                isTryingToStopOrStart = false;
                return output;
            },()->{
                String output = OnError.call();
                isTryingToStopOrStart = false;
                return output;
            });
        }

        protected synchronized long maxWaitMs()
        {
            return 10000;
        }

        @Override
        protected synchronized Disposable onThread(Callable<Boolean> Base, Callable<String> OnError)
        {
            return addTask(Base,OnError, mainScheduler());
        }

        @Override
        protected void onSuccessEnd()
        {
            currentStopAndResetState();
        }

        private void reSeekBase(@NonNull Callable<Boolean> seeking,
                                @NonNull Callable<Boolean> CanLoad,
                                @NonNull BiConsumer<Callable<Boolean>,Callable<Boolean>> afterLoadTryPlay,
                                @NonNull Callable<Boolean> OnTrue,
                                @NonNull Callable<Boolean> OnFalse) throws Exception {
            if(seeking.call())
                return;

            if (isActivated())
                return;

            activate();

            if(isPlaying())
            {
                baseData().firstPlayIsStarted = false;
                baseData().pauseAfterLoad = false;
                //BaseData().pauseAfterLoad = true;

                boolean output = false;
                try {
                    output = CanLoad.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                resetState();

                if(output)
                    afterLoadTryPlay.accept(()->{
                        boolean outp = OnTrue.call();
                        onSuccessEnd();
                        return outp;
                    },OnFalse);
            }
            else
            {
                //LoadOnly
                modifyLoad();
                onSuccessEnd();
            }
        }
    }
    public class BaseData
    {
        public final WaitSeek waitSeek = new WaitSeek();
        private final SeekAndEnd seekAndEnd = new SeekAndEnd();

        public boolean seeking;
        public boolean hardwareDecoding;
        public long savedSeekByIsPlayingDynamic;

        private boolean mediaIsPaused;
        private boolean firstPlayIsStarted;
        private long maxSeek;
        private boolean loop;
        private boolean playListLoop;
        private boolean pauseAfterLoad;
        private long seekSecond;

        public final void updateSeekSecondAndResetEndState(long mills)
        {
            updateSeekSecond(mills);
            seekAndEnd.run();
        }
        public final void updateSeekSecond(long mills)
        {
            seekSecond = mills;
            seekAndEnd.updateTemp(seekSecond);
        }
        
        public long getMaxSeek()
        {
            return maxSeek;
        }
        public boolean getLoop()
        {
            return loop;
        }
        public boolean getPlayListLoop()
        {
            return playListLoop;
        }
        public boolean getPauseAfterLoad()
        {
            return pauseAfterLoad;
        }
        public long getSeekSecond()
        {
            return seekSecond;
        }
    }
}
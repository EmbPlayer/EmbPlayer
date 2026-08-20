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
import java.util.concurrent.Callable;
import app.tools.DisposableTools.WaitDisposable;
import app.tools.Players.all.PlayerControllerBase;
import app.tools.Recyclable;
import app.tools.StaticFunctions;

import static app.tools.DisposableTools.forkJoinPool;
import static app.tools.DisposableTools.waitMS;

public abstract class Generator {

    public final MaxSeek maxSeek;
    public final WaitDisposable mediaError;

    protected boolean isKilled;
    protected boolean isLive;
    protected String videoURL;
    protected Callable<Boolean> onError;

    private WaitMake maker;

    public Generator()
    {
        maxSeek = new MaxSeek();
        mediaError = new WaitDisposable(2);
    }

    public final void wait(boolean on)
    {
        mediaError.started = on;
    }

    public final boolean isLive()
    {
        return isLive;
    }

    public final WaitDisposable makeKillAnotherWhere()
    {
        isKilled = true;
        return mediaError;
    }

    public final void mediaErrorRun()
    {
        mediaError.start(onError);
    }

    public final void mediaErrorStop()
    {
        mediaError.dispose();
    }

    public final boolean waitStarted()
    {
        return mediaError.started;
    }

    public final void onErrorUpdate(Callable<Boolean> onError)
    {
        this.onError = onError;
    }

    public final String getVideoUrl() {
        return videoURL; // Assuming you have this field
    }

    public void kill()
    {
        isKilled = true;
        mediaErrorStop();
    }

    public long getMaxSeekForPlayer()
    {
        return maxSeek.maxSeek;
    }

    public int getMaxSeek()
    {
        return (int)maxSeek.maxSeek/1000;
    }

    public void waitMake(Callable<PlayerControllerBase> playerGetter, boolean isLive, Recyclable.ListDisposable disposableCollection, Runnable onEnd) throws Exception {

        maker = new WaitMake(playerGetter,isLive,disposableCollection,onEnd,15,200);
        maker.make();
        /*
        if(maxSeek.UpdateMaxSeek(player))
            break;*/
    }

    public boolean isKilled()
    {
        return isKilled;
    }

    public Callable<Boolean> getOnError()
    {
        return onError;
    }

    public abstract PlayerControllerBase startPanel(boolean DisplayOn, boolean loop, boolean playListLoop);
    public abstract String nameOfMedia();

    private class WaitMake{
        private Callable<PlayerControllerBase> playerGetter;
        private boolean isLive;
        private Recyclable.ListDisposable disposableCollection;
        private Runnable onEnd;
        private int count;
        private int mills;

        private PlayerControllerBase player;
        private int i;
        private boolean isHaveError;

        public WaitMake(Callable<PlayerControllerBase> playerGetter, boolean isLive, Recyclable.ListDisposable disposableCollection, Runnable onEnd, int count, int mills) throws Exception {

            this.playerGetter = playerGetter;
            this.isLive = isLive;
            this.disposableCollection = disposableCollection;
            this.player = playerGetter.call();
            this.onEnd = onEnd;
            if(isLive)
                this.count = 3;
            else
                this.count = count;
            this.mills = mills;
        }

        private void make(){
            disposableCollection.addPollingTaskWithTimeOut(()->{
                if(i>=count)
                    return false;

                if(player==null)
                    player = playerGetter.call();
                else
                {
                    try
                    {
                        if(player.getError())
                        {
                            isHaveError = true;
                            return false;
                        }
                        else
                        {
                            i++;
                        }
                    }
                    catch (Exception ignored)
                    {

                    }
                }

                return true;
            }, StaticFunctions.Empty.r,()->{
                if(!isHaveError&&!isLive)
                {
                    int count = 100;
                    int mills = 150;

                    if(player.isPlaying()&&player.isPlayingDynamic(count,mills))
                        maxSeek.UpdateMaxSeekWithTryCountdown(count,mills,player);
                }

                onEnd.run();
            },StaticFunctions.Empty.r,StaticFunctions.Empty.r,StaticFunctions.Empty.r,mills,-1,forkJoinPool,"waitMake");
        }
    }

    public static class MaxSeek
    {
        public long maxSeek;

        public void UpdateMaxSeekWithTryCountdown(int count, int mills, PlayerControllerBase player)
        {
            for(int j = 0; j<count; j++)
            {
                waitMS(mills);

                if(UpdateMaxSeek(player))
                    break;
            }
        }

        public boolean UpdateMaxSeek(PlayerControllerBase player)
        {
            maxSeek = player.getDuration();
            return maxSeek != 0;
        }
    }
}

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

import static app.tools.DisposableTools.waitMS;

public abstract class Generator {

    public final MaxSeek maxSeek;
    public final WaitDisposable mediaError;

    protected boolean isKilled;
    protected boolean isLive;
    protected String videoURL;
    protected Callable<Boolean> onError;

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

    public void waitMake(Callable<PlayerControllerBase> playerGetter, boolean isLive) throws Exception {
        int count = 60;
        int mills = 50;
        int i = 0;

        PlayerControllerBase player = playerGetter.call();

        if(isLive)
        {
            count = 3;
        }

        while (i<count)
        {
            waitMS(mills);

            if(player==null)
                player = playerGetter.call();
            else
            {
                try
                {
                    if(player.getError())
                    {
                        return;
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
        }

        if(!isLive)
        {
            count = 100;
            mills = 150;

            if(player.isPlaying()&&player.isPlayingDynamic(count,mills))
                maxSeek.UpdateMaxSeekWithTryCountdown(count,mills,player);
        }

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

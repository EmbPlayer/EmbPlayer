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

package app.tools.Players.all.Ijk;

import androidx.annotation.CallSuper;
import app.tools.Players.all.PlayersCollection;
import app.tools.Players.all.Audio;
import app.tools.Players.all.Listeners;
import app.tools.Players.all.Player;
import app.tools.Players.all.Video;
import app.tools.Players.PlayerController;
import app.tools.Players.all.PlayerControllerBase;
import app.tools.Players.all.oem.OemPlayers;

import static server.Home.app;

public class IjkPlayers extends OemPlayers {
    @Override
    protected PlayerControllerBase getLiveVideo(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine)
    {
        if(engine==PlayersCollection.IJK)
            return new IjkVideoLive(listeners,loop,HardwareDecoding);

        return super.getLiveVideo(listeners,loop,HardwareDecoding,engine);
    }

    @Override
    protected PlayerControllerBase getVideo(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine)
    {
        if(engine==PlayersCollection.IJK)
            return new IjkVideo(listeners,loop,HardwareDecoding);

        return super.getVideo(listeners,loop,HardwareDecoding,engine);
    }

    @Override
    protected PlayerControllerBase getAudioLive(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine)
    {
        if(engine==PlayersCollection.IJK)
            return new IjkAudioLive(listeners,loop,HardwareDecoding);

        return super.getAudioLive(listeners,loop,HardwareDecoding,engine);
    }

    @Override
    protected PlayerControllerBase getAudio(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine)
    {
        if(engine==PlayersCollection.IJK)
            return new IjkAudio(listeners,loop,HardwareDecoding);

        return super.getAudio(listeners,loop,HardwareDecoding,engine);
    }

    @Override
    protected PlayerControllerBase getVideoAndAudio(Listeners videoPlayer, boolean loop, boolean HardwareDecoding, PlayersCollection AudioClass, PlayersCollection VideoClass) {
        return new IjkVideoAndAudio(videoPlayer,loop,HardwareDecoding,AudioClass,VideoClass);
    }

    private static int getColorFormatOfIJKPlayer()
    {
        return app().onIJK.getColorFormatIDAsNative();
    }

    private static class IjkAudio extends Audio<PlayerController.IJKInner> {

        public IjkAudio(Listeners listeners, boolean loop, boolean HardwareDecoding)
        {
            super(listeners);
            data = new IJKInner(false,getColorFormatOfIJKPlayer());
            loop(loop);
            preset();
            baseData().hardwareDecoding = HardwareDecoding;
        }

        @Override
        protected void preset() {
            //data.VOnPrepareDontStart();

            //data.V3M();
        }
    }
    private static class IjkAudioLive extends IjkAudio {
        public IjkAudioLive(Listeners listeners, boolean loop, boolean HardwareDecoding) {
            super(listeners, loop, HardwareDecoding);
        }

        @Override
        public boolean isLive()
        {
            return true;
        }

        @Override
        protected void preset() {
            data.livePreset();
        }
    }

    private static class IjkVideo extends Video<PlayerController.IJKInner> {

        public IjkVideo(Listeners listeners, boolean loop, boolean HardwareDecoding)
        {
            super(listeners);
            data = new IJKInner(false,getColorFormatOfIJKPlayer());
            loop(loop);
            this.preset();
            hardwareDecoding = HardwareDecoding;
        }

        @Override
        protected void preset() {
            super.preset();
            //data.VOnPrepareDontStart();
            //data.V3M();
        }
    }
    private static class IjkVideoLive extends IjkVideo
    {
        public IjkVideoLive(Listeners listeners, boolean loop, boolean HardwareDecoding) {
            super(listeners, loop, HardwareDecoding);
        }

        @Override
        public boolean isLive()
        {
            return true;
        }

        @Override
        protected void preset() {
            super.preset();
            data.livePreset();
        }

        protected void backgroundPreset() {
            //data.OnScreenChangeListenerSetup();

            data.livePreset();
        }
    }

    protected static class IjkVideoAndAudio extends OemVideoAndAudio
    {
        public IjkVideoAndAudio(Listeners videoPlayer, boolean loop, boolean HardwareDecoding, PlayersCollection AudioClass, PlayersCollection VideoClass) {
            super(videoPlayer, loop, HardwareDecoding, AudioClass, VideoClass);
        }

        @CallSuper
        @Override
        protected Player createPlayer(PlayersCollection player, boolean videoOnly) {
            if(player==PlayersCollection.IJK)
                return new IJKInner(videoOnly,getColorFormatOfIJKPlayer());

            return super.createPlayer(player,videoOnly);
        }
    }
}

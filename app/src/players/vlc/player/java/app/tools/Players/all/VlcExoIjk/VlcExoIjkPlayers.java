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

package app.tools.Players.all.VlcExoIjk;

import androidx.annotation.CallSuper;

import app.tools.Players.all.PlayersCollection;
import app.tools.Players.all.Audio;
import app.tools.Players.all.Listeners;
import app.tools.Players.all.Player;
import app.tools.Players.all.Video;
import app.tools.Players.PlayerController;
import app.tools.Players.all.PlayerControllerBase;
import app.tools.Players.all.ExoIjk.ExoIjkPlayers;

public class VlcExoIjkPlayers extends ExoIjkPlayers {
    @Override
    protected PlayerControllerBase getLiveVideo(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine)
    {
        if(engine==PlayersCollection.VLC)
            return new VlcVideoLive(listeners,loop,HardwareDecoding);

        return super.getLiveVideo(listeners,loop,HardwareDecoding,engine);
    }

    @Override
    protected PlayerControllerBase getVideo(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine)
    {
        if(engine==PlayersCollection.VLC)
            return new VlcVideo(listeners,loop,HardwareDecoding);

        return super.getVideo(listeners,loop,HardwareDecoding,engine);
    }

    @Override
    protected PlayerControllerBase getAudioLive(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine)
    {
        if(engine==PlayersCollection.VLC)
            return new VlcAudioLive(listeners,loop,HardwareDecoding);

        return super.getAudioLive(listeners,loop,HardwareDecoding,engine);
    }

    @Override
    protected PlayerControllerBase getAudio(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine)
    {
        if(engine==PlayersCollection.VLC)
            return new VlcAudio(listeners,loop,HardwareDecoding);

        return super.getAudio(listeners,loop,HardwareDecoding,engine);
    }

    @Override
    protected PlayerControllerBase getVideoAndAudio(Listeners videoPlayer, boolean loop, boolean HardwareDecoding, PlayersCollection AudioClass, PlayersCollection VideoClass) {
        return new VlcExoIjkVideoAndAudio(videoPlayer,loop,HardwareDecoding,AudioClass,VideoClass);
    }

    private static class VlcAudio extends Audio<PlayerController.VLCInner> {

        public VlcAudio(Listeners listeners, boolean loop, boolean HardwareDecoding)
        {
            super(listeners);
            data = new VLCInner(false);
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
    private static class VlcAudioLive extends VlcAudio {
        public VlcAudioLive(Listeners listeners, boolean loop, boolean HardwareDecoding) {
            super(listeners, loop, HardwareDecoding);
        }

        @Override
        public boolean isLive()
        {
            return true;
        }

        @Override
        protected void preset() {
            //data.VOnPrepareDontStart();
        }
    }

    private static class VlcVideo extends Video<PlayerController.VLCInner> {

        public VlcVideo(Listeners listeners, boolean loop, boolean HardwareDecoding)
        {
            super(listeners);
            data = new VLCInner(false);
            loop(loop);
            this.preset();
            hardwareDecoding = HardwareDecoding;
        }

        @Override
        protected void preset() {
            super.preset();
            //data.OnScreenChangeListenerSetup();

            //data.VOnPrepareDontStart();
            //data.V3M();
        }
    }
    private static class VlcVideoLive extends VlcVideo
    {
        public VlcVideoLive(Listeners listeners, boolean loop, boolean HardwareDecoding) {
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
            //data.OnScreenChangeListenerSetup();

            //data.VOnPrepareDontStart();
        }

        protected void backgroudPreset() {
            //data.OnScreenChangeListenerSetup();

            //data.VOnPrepareDontStart();
        }
    }

    protected static class VlcExoIjkVideoAndAudio extends ExoIjkVideoAndAudio
    {
        public VlcExoIjkVideoAndAudio(Listeners videoPlayer, boolean loop, boolean HardwareDecoding, PlayersCollection AudioClass, PlayersCollection VideoClass) {
            super(videoPlayer, loop, HardwareDecoding, AudioClass, VideoClass);
        }

        @CallSuper
        @Override
        protected Player createPlayer(PlayersCollection player, boolean videoOnly) {

            if(player==PlayersCollection.VLC)
                return new VLCInner(videoOnly);

            return super.createPlayer(player,videoOnly);
        }
    }
}

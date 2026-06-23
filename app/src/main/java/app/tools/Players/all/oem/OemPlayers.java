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

package app.tools.Players.all.oem;

import androidx.annotation.CallSuper;
import app.tools.Players.PlayerController;
import app.tools.Players.all.Audio;
import app.tools.Players.all.Listeners;
import app.tools.Players.all.Player;
import app.tools.Players.all.PlayerControllerBase;
import app.tools.Players.all.Players;
import app.tools.Players.all.PlayersCollection;
import app.tools.Players.all.Video;
import app.tools.Players.all.VideoAndAudio;

public class OemPlayers extends Players {

    @Override
    protected PlayerControllerBase getLiveVideo(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine)
    {
        return new OemVideoLive(listeners,loop,HardwareDecoding);
    }

    @Override
    protected PlayerControllerBase getVideo(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine)
    {
        return new OemVideo(listeners,loop,HardwareDecoding);
    }

    @Override
    protected PlayerControllerBase getAudioLive(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine)
    {
        return new OemAudioLive(listeners,loop,HardwareDecoding);
    }

    @Override
    protected PlayerControllerBase getAudio(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine)
    {
        return new OemAudio(listeners,loop,HardwareDecoding);
    }

    @Override
    protected PlayerControllerBase getVideoAndAudio(Listeners videoPlayer, boolean loop, boolean HardwareDecoding, PlayersCollection AudioClass, PlayersCollection VideoClass) {
        return new OemVideoAndAudio(videoPlayer,loop,HardwareDecoding,AudioClass,VideoClass);
    }

    private static class OemAudio extends Audio<PlayerController.OemInner> {

        public OemAudio(Listeners listeners, boolean loop, boolean HardwareDecoding)
        {
            super(listeners);
            data = new OemInner(false);
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
    private static class OemAudioLive extends OemAudio {
        public OemAudioLive(Listeners listeners, boolean loop, boolean HardwareDecoding) {
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

    private static class OemVideo extends Video<PlayerController.OemInner> {

        public OemVideo(Listeners listeners, boolean loop, boolean HardwareDecoding)
        {
            super(listeners);
            data = new OemInner(false);
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
    private static class OemVideoLive extends OemVideo
    {
        public OemVideoLive(Listeners listeners, boolean loop, boolean HardwareDecoding) {
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

        protected void BackgroudPreset() {
            //data.OnScreenChangeListenerSetup();

            data.livePreset();
        }
    }

    protected static class OemVideoAndAudio extends VideoAndAudio
    {
        public OemVideoAndAudio(Listeners videoPlayer, boolean loop, boolean HardwareDecoding, PlayersCollection AudioClass, PlayersCollection VideoClass) {
            super(videoPlayer, loop, HardwareDecoding, AudioClass, VideoClass);
        }

        @CallSuper
        @Override
        protected Player createPlayer(PlayersCollection player, boolean videoOnly) {
            return new OemInner(videoOnly);
        }
    }
}

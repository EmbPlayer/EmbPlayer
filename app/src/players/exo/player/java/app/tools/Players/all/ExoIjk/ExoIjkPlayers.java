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

package app.tools.Players.all.ExoIjk;

import androidx.annotation.CallSuper;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import app.Main;
import app.tools.Generators.Requirements.MediaSourceProviders;
import app.tools.Players.all.PlayersCollection;
import app.tools.Players.all.Player;
import app.tools.Players.all.PlayerControllerBase;
import app.tools.Players.all.ExoIjk.Piped.mediasourcebuilders.GenericMediaSourceBuilder;
import app.tools.Players.all.ExoIjk.Piped.mediasourcebuilders.YouTubeMediaSourceBuilder;
import app.tools.Players.all.Audio;
import app.tools.Players.all.Listeners;
import app.tools.Players.all.Video;
import app.tools.Players.PlayerController;
import app.tools.Players.all.Ijk.IjkPlayers;
import server.tools.MediaProxyServlet;

import static server.Home.app;

public class ExoIjkPlayers extends IjkPlayers {
    @Override
    protected PlayerControllerBase getLiveVideo(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine)
    {
        if(engine==PlayersCollection.EXO)
            return new ExoVideoLive(listeners,loop,HardwareDecoding);

        return super.getLiveVideo(listeners,loop,HardwareDecoding,engine);
    }

    @Override
    protected PlayerControllerBase getVideo(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine)
    {
        if(engine==PlayersCollection.EXO)
            return new ExoVideo(listeners,loop,HardwareDecoding);

        return super.getVideo(listeners,loop,HardwareDecoding,engine);
    }

    @Override
    protected PlayerControllerBase getAudioLive(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine)
    {
        if(engine==PlayersCollection.EXO)
            return new ExoAudioLive(listeners,loop,HardwareDecoding);

        return super.getAudioLive(listeners,loop,HardwareDecoding,engine);
    }

    @Override
    protected PlayerControllerBase getAudio(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine)
    {
        if(engine==PlayersCollection.EXO)
            return new ExoAudio(listeners,loop,HardwareDecoding);

        return super.getAudio(listeners,loop,HardwareDecoding,engine);
    }

    @Override
    protected PlayerControllerBase getVideoAndAudio(Listeners videoPlayer, boolean loop, boolean HardwareDecoding, PlayersCollection AudioClass, PlayersCollection VideoClass) {
        return new ExoIjkVideoAndAudio(videoPlayer,loop,HardwareDecoding,AudioClass,VideoClass);
    }

    private static MediaSourceProviders getSourceType()
    {
        return app().getSavedSource();
    }

    @OptIn(markerClass = UnstableApi.class)
    private static void addDataBase(boolean urlCaching, boolean youtubeCaching, ExoPlayer media, String mediaLinkPure, boolean isLive, boolean videoOnly)
    {
        //String mediaLink = MediaProxyServlet.getPure(mediaLinkPure,videoOnly);
        String mediaLink = mediaLinkPure;

        if(getSourceType() == MediaSourceProviders.YOUTUBE)
            if(youtubeCaching)
                media.setMediaSource(new YouTubeMediaSourceBuilder(Main.getContext()).buildMediaSource(mediaLink,isLive,videoOnly));
            else
                media.setMediaItem(new MediaItem.Builder()
                        .setUri(mediaLink)
                        .setMimeType(MimeTypes.APPLICATION_MP4) // Optional but recommended
                        .build());
        else
        if(urlCaching)
            media.setMediaSource(new GenericMediaSourceBuilder(Main.getContext()).buildMediaSource(mediaLink,isLive,videoOnly));
        else
            media.setMediaItem(new MediaItem.Builder()
                    .setUri(mediaLink)
                    .build());
    }

    @OptIn(markerClass = UnstableApi.class)
    private static void addDataBase(ExoPlayer media, String audioOrBase, boolean isLive, boolean videoOnly)
    {
        if(app().onExo.cachingFailed()){
            addDataBase(false,false,media,audioOrBase,isLive,videoOnly);
            return;
        }

        addDataBase(app().onExo.urlCaching.get(), app().onExo.youtubeCaching.get(),media,audioOrBase,isLive,videoOnly);
    }

    private static class ExoAudio extends Audio<PlayerController.ExoInner> {
        public ExoAudio(Listeners listeners, boolean loop, boolean HardwareDecoding)
        {
            super(listeners);

            data = new ExoInner(false)
            {
                @Override
                protected void setData()
                {
                    //MediaItem mediaItem = MediaItem.fromUri(Uri.parse(link));
                    addDataBase(media,link, isLive(),false);
                }
            };

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
    private static class ExoAudioLive extends ExoAudio {
        public ExoAudioLive(Listeners listeners, boolean loop, boolean HardwareDecoding) {
            super(listeners, loop, HardwareDecoding);
        }

        @Override
        public boolean isLive()
        {
            return true;
        }

        @Override
        protected void preset() {
            //data.LivePreset();
        }
    }

    private static class ExoVideo extends Video<PlayerController.ExoInner> {
        public ExoVideo(Listeners listeners, boolean loop, boolean HardwareDecoding)
        {
            super(listeners);

            data = new ExoInner(false)
            {
                @Override
                protected void setData()
                {
                    //MediaItem mediaItem = MediaItem.fromUri(Uri.parse(link));
                    addDataBase(media,link, isLive(),true);
                }
            };
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
    private static class ExoVideoLive extends ExoVideo
    {
        public ExoVideoLive(Listeners listeners, boolean loop, boolean HardwareDecoding) {
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
            //data.LivePreset();
        }

        protected void backgroudPreset() {
            //data.OnScreenChangeListenerSetup();

            //data.LivePreset();
        }
    }

    protected static class ExoIjkVideoAndAudio extends IjkVideoAndAudio {
        public ExoIjkVideoAndAudio(Listeners videoPlayer, boolean loop, boolean HardwareDecoding, PlayersCollection AudioClass, PlayersCollection VideoClass) {
            super(videoPlayer, loop, HardwareDecoding, AudioClass, VideoClass);
        }

        @CallSuper
        @Override
        protected Player createPlayer(PlayersCollection player, boolean videoOnly) {

            if(player==PlayersCollection.EXO)
                return new ExoInner(videoOnly)
                {
                    @Override
                    public void setData()
                    {
                        addDataBase(media,link,false,videoOnly);
                    }
                };

            return super.createPlayer(player,videoOnly);
        }
    }
}

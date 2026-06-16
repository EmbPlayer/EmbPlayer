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

import app.App.AppBack;
import app.tools.Generators.Requirements.Piped.VideoResolution;

public abstract class Players {

    private static PlayersCollection audioEngineSelected;
    private static PlayersCollection videoEngineSelected;
    private static PlayersCollection mediaLiveEngineSelected;
    private static VideoResolution resolution_;
    private static VideoResolution resolutionLive_;
    private static boolean isLive_;

    public final PlayerControllerBase getPlayer(Listeners listeners, boolean loop,
                                                boolean playlistLoop, boolean HardwareDecoding,
                                                boolean video, boolean LiveStream,
                                                String AudioOrBaseStream, String VideoStream)
    {
        PlayerControllerBase player;

        if(video)
        {
            if(VideoStream!=null) {

                AppBack.Panel.setCorrectThread(videoEngineSelected);

                player = getVideoAndAudio(listeners, loop, HardwareDecoding, audioEngineSelected, videoEngineSelected);

            }
            else
            {
                AppBack.Panel.setCorrectThread(audioEngineSelected);

                if(LiveStream)
                    player = getLiveVideo(listeners,loop,HardwareDecoding,mediaLiveEngineSelected);
                else{
                    player = getVideo(listeners,loop,HardwareDecoding,audioEngineSelected);
                }
            }
        }
        else
        {
            if(LiveStream)
                player = getAudioLive(listeners,loop,HardwareDecoding,mediaLiveEngineSelected);
            else{
                player = getAudio(listeners,loop,HardwareDecoding,audioEngineSelected);
            }
        }

        player.playListLoop(playlistLoop);
        player.addData(AudioOrBaseStream,VideoStream);

        return player;
    }

    public static void updateEngines(PlayersCollection audioOrVideo,PlayersCollection video,PlayersCollection mediaLive,VideoResolution resolution,VideoResolution resolutionLive){
        audioEngineSelected = audioOrVideo;
        videoEngineSelected = video;
        mediaLiveEngineSelected = mediaLive;
        resolution_ = resolution;
        resolutionLive_ = resolutionLive;
    }

    public static void updateIsLive(boolean isLive){
        isLive_ = isLive;
    }

    public static VideoResolution resolution(){
        return resolution_;
    }

    public static VideoResolution resolutionLive(){
        return resolutionLive_;
    }
    public static boolean isLive(){
        return isLive_;
    }

    protected abstract PlayerControllerBase getLiveVideo(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine);
    protected abstract PlayerControllerBase getVideo(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine);
    protected abstract PlayerControllerBase getAudioLive(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine);
    protected abstract PlayerControllerBase getAudio(Listeners listeners, boolean loop, boolean HardwareDecoding, PlayersCollection engine);
    protected abstract PlayerControllerBase getVideoAndAudio(Listeners videoPlayer, boolean loop, boolean HardwareDecoding, PlayersCollection AudioClass, PlayersCollection VideoClass);
}

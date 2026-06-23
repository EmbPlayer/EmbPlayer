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

package server.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import app.tools.Generators.Requirements.Piped.VideoResolution;
import app.tools.Players.all.PlayersCollection;
import app.tools.Players.all.Ijk.ColorFormats;
import app.tools.SData;
import app.tools.StaticFunctions;
import server.tools.HttpServletAdvanced;

import static app.tools.StaticFunctions.setData;
import static server.Home.app;

public class VideoProperties extends HttpServletAdvanced {

    private final String[] resolutions;
    private final String[] playersCollection;
    private final String[] colorFormats;

    public VideoProperties()
    {
        resolutions = VideoResolution.getAllVideoResolutionsKeys();
        colorFormats = ColorFormats.getAllColorFormatsName();

        List<String> players = new ArrayList<>();
        players.add(StaticFunctions.asJsonFormat(PlayersCollection.OEM.name()));
        players.add(StaticFunctions.asJsonFormat(PlayersCollection.IJK.name()));

        if(SData.get(SData.Data.ExoPlayerOn))
            players.add(StaticFunctions.asJsonFormat(PlayersCollection.EXO.name()));

        if(SData.get(SData.Data.VLCPlayerOn))
            players.add(StaticFunctions.asJsonFormat(PlayersCollection.VLC.name()));

        playersCollection = players.stream().toArray(String[]::new);
        //quality = Data(new int[]{Piped.VideoQuality.LEAST_BANDWITH.ordinal(),Piped.VideoQuality.BEST_QUALITY.ordinal()});
    }

    protected String get()
    {
        String[] selected = data(new int[] {
                app().videoResolutionID.getSave(), app().playerID.getSave(),
                app().videoLanguageID.getSave(), app().hardware.getInt(),
                app().onIJK.getColorFormatID(), app().youtubePlayerID.getSave(),
                app().livePlayerID.getSave(), app().youtubePlayerVideoID.getSave(),
                app().onExo.youtubeCaching.getInt(), app().onExo.urlCaching.getInt(),
                app().getVideoResolutionLiveID(), app().legacyYoutubePlayer.getInt(),
                app().checkMacAndSsid.getInt(), app().radioPlayerID.getSave()
        } );

        return setData(new String[] {setData(selected),setData(resolutions),setData(playersCollection),setData(colorFormats)});
    }

    @Override
    protected void doGetAdvanced(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.getWriter().write(get());
    }

    private String[] data(int[] dataArr)
    {
        String[] data = new String[dataArr.length];
        for(int i = 0; i<data.length; i++)
        {
            data[i] = Integer.toString(dataArr[i]);
        }
        return data;
    }
}

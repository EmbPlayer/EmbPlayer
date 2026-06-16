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

package server.tools;

import app.tools.Generators.Requirements.Piped.VideoQuality;
import app.tools.Generators.Requirements.Piped.VideoResolution;

public class VideoSettings {
    private VideoResolution resolution;
    private VideoResolution resolutionLive;
    private VideoQuality quality;
    private String language;

    private VideoResolution savedLiveResolution;
    private VideoResolution savedResolution;
    private VideoQuality savedQuality;
    private String savedLanguage;

    public VideoSettings(VideoResolution resLive,VideoResolution res, VideoQuality quali, String languageISO2)
    {
        resolutionLive = resLive;
        resolution = res;
        quality = quali;
        language = languageISO2;
    }

    public VideoResolution resolution()
    {
        return resolution;
    }
    public VideoResolution resolutionLive()
    {
        return resolutionLive;
    }
    public VideoQuality quality()
    {
        return quality;
    }
    public String languageISO2()
    {
        return language;
    }

    public void resetToDefault()
    {
        resolutionLive = savedLiveResolution;
        resolution = savedResolution;
        quality = savedQuality;
        language = savedLanguage;
    }

    public void backgroudMod()
    {
        save();
        quality = VideoQuality.LEAST_BANDWIDTH;
        resolution = VideoResolution.Audio;
        resolutionLive = VideoResolution.Audio;
    }

    private void save()
    {
        savedLiveResolution = resolutionLive;
        savedResolution = resolution;
        savedQuality = quality;
        savedLanguage = language;
    }
}

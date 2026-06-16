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

package server;

import app.App.AppBack;
import app.App.AppControl;
import server.tools.GetLinks;

import app.tools.LinksDbHelper;
//import server.web.DiscoveredDevices;
import server.web.SavedLinkList;
import server.web.Sources;
import server.web.Tables;
import server.web.VideoProperties;
import server.web.Wait;

public class Home {
    protected final Wait waiter;
    protected final VideoProperties quality;
    protected final GetLinks links;
    //protected final DiscoveredDevices devices;
    protected final SavedLinkList youtubeLiveLinks;
    protected final SavedLinkList youtubeLinks;
    protected final SavedLinkList youtubePlaylistLinks;
    protected final SavedLinkList urls;
    protected final Tables tables;
    protected final AppControl appControl;
    protected Sources sources;
    //protected final SavedLinkList urlsFast;


    public Home()
    {
        appControl = new AppControl();
        waiter = new Wait();
        quality = new VideoProperties();
        links = new GetLinks();
        //devices = new DiscoveredDevices();
        youtubeLinks = new SavedLinkList(LinksDbHelper.TableName.YOUTUBE_LINKS.getTableName());
        youtubeLiveLinks = new SavedLinkList(LinksDbHelper.TableName.YOUTUBE_LIVE_LINKS.getTableName());
        youtubePlaylistLinks = new SavedLinkList(LinksDbHelper.TableName.YOUTUBE_PLAYLIST_LINKS.getTableName());
        urls = new SavedLinkList(LinksDbHelper.TableName.URLS.getTableName());
        //urlsFast = new SavedLinkList(LinksDbHelper.TableName.URLS_FAST.getTableName());
        tables = new Tables();
    }

    public static AppBack app()
    {
        return AppBack.getApp();
    }
}

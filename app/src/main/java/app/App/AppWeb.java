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

package app.App;

import android.database.Cursor;

import com.emb.player.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import app.Main;
import app.tools.Generators.Requirements.MediaSourceProviders;
import app.tools.Generators.Requirements.Piped.VideoResolution;
import app.tools.LinksDbHelper;
import app.tools.Players.all.Ijk.ColorFormats;
import app.tools.Players.all.PlayersCollection;
import app.tools.SData;
import app.tools.StaticFunctions;
import app.tools.StaticFunctions.ItemWithId;
import server.web.ErrorCodeApp;
import server.web.Sources;

import static app.tools.StaticFunctions.onThrows;

public class AppWeb{
    public static final int VOLUME_MAX = 100;
    public static final String[] LANGUAGES;

    static {
        LANGUAGES = Arrays.stream(Locale.getISOLanguages())
                .filter(s -> s.length() == 2).toArray(String[]::new);
    }

    String baseUrl;

    public final LegacyYoutubePlayer legacyYoutubePlayer;
    public final IJKPlayerOnly onIJK;
    public final ExoPlayerOnly onExo;
    public final ButtonBoolean isSavable;
    protected final LinksDbHelper savedLinks;
    protected MediaSourceProviders mediaClientSideProvider;

    public final ButtonInteger videoResolutionID;
    public final ButtonInteger radioPlayerID;
    public final ButtonInteger playerID;
    public final ButtonInteger livePlayerID;
    public final ButtonInteger youtubePlayerID;
    public final ButtonInteger youtubePlayerVideoID;
    public final ButtonInteger videoLanguageID;
    public final ButtonInteger mediaProviderClientSideID;
    public final ButtonInteger loop;
    public final ButtonInteger volumePosition;

    public final ButtonBoolean timer;
    public final ButtonBoolean setUp;
    public final ButtonBoolean playlist;
    public final ButtonBoolean hardware;
    public final ButtonBoolean checkMacAndSsid;

    private String extractorPattern;
    private String extractorExpirePattern;
    private int selectedTable;
    private int seekMax;
    private int seekPosition;
    private int videoResolutionLiveID;

    public AppWeb()
    {
        legacyYoutubePlayer = new LegacyYoutubePlayer();
        savedLinks = new LinksDbHelper(Main.getContext());
        timer = new ButtonBoolean();
        setUp = new ButtonBoolean();
        playlist = new ButtonBoolean();

        onIJK = new IJKPlayerOnly();
        onExo = new ExoPlayerOnly();
        isSavable = new SavedButtonBoolean(SData.Data.IsSavable,false);
        loop =  new SavedButtonInteger(SData.Data.SavedLoop,0);
        hardware = new SavedButtonBoolean(SData.Data.HardwareDecoding,false);
        checkMacAndSsid = new SavedButtonBoolean(SData.Data.CheckMacAndSsid,false);

        mediaProviderClientSideID = new SavedButtonInteger(SData.Data.MediaProviderClientSideID,0);
        videoLanguageID = new SavedButtonInteger(SData.Data.LanguageIndex,0);

        radioPlayerID = new SavedButtonInteger(SData.Data.RadioPlayerIndex,0);
        playerID = new SavedButtonInteger(SData.Data.PlayerIndex,0);
        livePlayerID = new SavedButtonInteger(SData.Data.LivePlayerIndex,0);
        youtubePlayerID = new SavedButtonInteger(SData.Data.YoutubePlayerIndex,0);
        youtubePlayerVideoID = new SavedButtonInteger(SData.Data.YoutubeVideoPlayerIndex,0);

        videoResolutionID = new SavedButtonInteger(SData.Data.ResolutionIndex,0);
        videoResolutionLiveID = SData.getInt(SData.Data.ResolutionLiveIndex);
        volumePosition = new SavedButtonInteger(SData.Data.VolumePosition,-1);

        extractorPattern = SData.getString(SData.Data.SavedExtractorPattern);
        extractorExpirePattern = SData.getString(SData.Data.SavedExtractorExpirePattern);
    }

    public static PlayersCollection player(int indexPlayerEngine)
    {
        return PlayersCollection.values()[indexPlayerEngine];
    }

    public static SavedMedia getSavedMedia(){

        String recoveredData = SData.getString(SData.Data.SavedMedia);
        if(recoveredData==null||recoveredData.isEmpty())
            return null;

        String[] data = recoveredData.split(System.lineSeparator());
        return new SavedMedia(
                Integer.parseInt(data[0]),
                data[1],
                getNameFromData(2,data),
                SData.getLong(SData.Data.SavedSeek)
        );
    }

    public static void saveMedia(MediaSourceProviders provider, String url, String name){
        SData.setString(SData.Data.SavedMedia,String.join(System.lineSeparator(),StaticFunctions.objectsToString(provider.id(),url,name)));
    }

    public MediaSourceProviders getSavedSource()
    {
        return mediaClientSideProvider;
    }

    protected void volumePosition(float percent)
    {
        volumePosition.set((int)(VOLUME_MAX * percent));
    }

    public void seekMax(int value)
    {
        seekMax = value;
    }
    
    public int getSeekMax(){ return seekMax; }

    public void seekPosition(int value)
    {
        seekPosition = value;
    }
    public int getSeekPosition()
    {
        return seekPosition;
    }

    public void videoResolutionLiveID(int VideoResolutionID)
    {
        SData.setInt(SData.Data.ResolutionLiveIndex,VideoResolutionID);
        videoResolutionLiveID = VideoResolutionID;
        try {
            Sources.getSourcesController().updateSources(Sources.getAsResolution(VideoResolution.values()[videoResolutionLiveID]));
        } catch (Exception e) {
            onThrows(e);
            //throw new RuntimeException(e);
        }
    }
    public int getVideoResolutionLiveID()
    {
        return videoResolutionLiveID;
    }

    public PlayersCollection getLivePlayer()
    {
        return player(livePlayerID.getSave());
    }

    public PlayersCollection getRadioPlayer()
    {
        return player(radioPlayerID.getSave());
    }

    public void extractorPattern(String pattern)
    {
        extractorPattern = pattern;
        SData.setString(SData.Data.SavedExtractorPattern, extractorPattern);
    }
    public String getExtractorPattern()
    {
        return extractorPattern;
    }
    public void extractorExpirePattern(String pattern)
    {
        extractorExpirePattern = pattern;
        SData.setString(SData.Data.SavedExtractorExpirePattern, extractorExpirePattern);
    }
    public String getExtractorExpirePattern()
    {
        return extractorExpirePattern;
    }

    public void linkSave(String tableName, String name)
    {
        if(!savedLinks.doesLinkExist(tableName, baseUrl))
            savedLinks.insertData(tableName,name, baseUrl, mediaProviderClientSideID.getSave());
    }

    public List<ItemWithId> getLinks(String tableName)
    {
        List<ItemWithId> items = new ArrayList<>();

        Cursor commandRun = savedLinks.getAllData(tableName);

        if(commandRun.getCount()<1)
            return items;


        while (commandRun.moveToNext())
        {
            String id = commandRun.getString(commandRun.getColumnIndexOrThrow(
                    LinksDbHelper.COLUMN_ID));
            String name = commandRun.getString(commandRun.getColumnIndexOrThrow(
                    LinksDbHelper.COLUMN_NAME));
            String link = commandRun.getString(commandRun.getColumnIndexOrThrow(
                    LinksDbHelper.COLUMN_LINK));

            // Handle potential NULL values
            if (name == null) {
                name = ""; // or "Unnamed" or whatever default you prefer
            }

            items.add(new ItemWithId(id,name,link));
        }


        return items;
    }

    public boolean linkIsHave(String tableName, int id)
    {
        return savedLinks.doesIdExist(tableName,id);
    }

    public void removeLink(String tableName, String id)
    {
        savedLinks.deleteById(tableName,id);
    }

    public void selectedTable(int tableIndex)
    {
        selectedTable = tableIndex;
    }

    public int getSelectedTable()
    {
        return selectedTable;
    }

    private static String getNameFromData(int startIndex,String[] rawData){
        if(startIndex==rawData.length-1)
            return rawData[startIndex];

        List<String> output = new ArrayList<>();

        for(int i = startIndex; i<rawData.length; i++){
            output.add(rawData[i]);
        }

        return String.join(System.lineSeparator(),output);
    }

    public static class SavedMedia
    {
        private final int providerId;
        private final String url;
        private final String name;
        private final long seek;

        public SavedMedia(int providerId, String url, String name, long seek)
        {
            this.providerId = providerId;
            this.url = url;
            this.name = name;
            this.seek = seek;
        }

        public String getURL()
        {
            return url;
        }

        public String getName(){
            return name;
        }

        public int getProviderID()
        {
            return providerId;
        }

        public long getSeek()
        {
            return seek;
        }
    }

    public static class ButtonBoolean
    {
        protected boolean on;

        public void set(boolean On) {on=On;}
        public boolean get(){return on;}
        public int getInt(){return get() ? 1 : 0;}
    }

    public static class ButtonInteger
    {
        private int on;

        public void set(int On) {on=On;}
        public boolean get(int mode){return on==mode;}
        public int getInt(int mode){return get(mode) ? 1 : 0;}
        public int getSave(){return on;}
    }

    public static class SavedButtonInteger extends ButtonInteger
    {
        private final SData.Data data;

        public SavedButtonInteger(SData.Data data, int ifNull)
        {
            this.data = data;
            super.set((SData.getInt(data,ifNull)));
        }

        @Override
        public void set(int On)
        {
            super.set(On);
            SData.setInt(data,On);
        }
    }

    public static class SavedButtonBoolean extends ButtonBoolean
    {
        private final SData.Data data;

        public SavedButtonBoolean(SData.Data data, boolean ifNull)
        {
            this.data = data;
            super.set((SData.get(data,ifNull)));
        }

        @Override
        public void set(boolean On)
        {
            super.set(On);
            SData.set(data,On);
        }
    }

    public static class ExoPlayerOnly
    {
        private boolean cachingFailed;
        public final ButtonBoolean youtubeCaching;
        public final ButtonBoolean urlCaching;

        public ExoPlayerOnly()
        {
            youtubeCaching = new SavedButtonBoolean(SData.Data.YoutubeCaching,false);
            urlCaching = new SavedButtonBoolean(SData.Data.URLCaching,false);
        }

        public boolean cachingFailed()
        {
            return cachingFailed;
        }

        public void cachingFailed(boolean cachingFailed)
        {
            this.cachingFailed = cachingFailed;
        }
    }

    public static class IJKPlayerOnly
    {
        private int colorFormatID;

        public IJKPlayerOnly(){
            colorFormatID = SData.getInt(SData.Data.ColorFormatIndex);
        }

        public void colorFormatID(int ColorFormatID)
        {
            colorFormatID = ColorFormatID;
            SData.setInt(SData.Data.ColorFormatIndex,colorFormatID);
        }
        public int getColorFormatID()
        {
            return colorFormatID;
        }

        public int getColorFormatIDAsNative()
        {
            return ColorFormats.IjkPlayerColorFormatID(colorFormatID);
        }
    }

    public class LegacyYoutubePlayer extends SavedButtonBoolean
    {
        private final StaticFunctions.StarterWithBoolean resolutionCorrect = new StaticFunctions.StarterWithBoolean() {
            private boolean yes;

            @Override
            protected Boolean firstLaunch() throws Exception {

                int loaded = AppWeb.this.videoResolutionID.getSave();
                yes = get() && (loaded == VideoResolution._360P.ordinal() || loaded == VideoResolution.Audio.ordinal());

                return yes;
            }

            @Override
            protected Boolean secondLaunches() throws Exception {
                return yes;
            }
        };

        public LegacyYoutubePlayer() {
            super(SData.Data.LegacyYoutubePlayer, false);
        }

        public boolean make()
        {
            try {
                return resolutionCorrect.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void reset()
        {
            resolutionCorrect.reset();
        }
    }
}

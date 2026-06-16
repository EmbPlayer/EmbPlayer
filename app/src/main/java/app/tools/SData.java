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

package app.tools;

import android.content.Context;
import android.content.SharedPreferences;

import com.emb.player.R;

import java.util.function.Consumer;

import app.Main;

import static android.content.Context.MODE_PRIVATE;

public class SData {

    private static final String dataName = "Em";

    private static Consumer<Context> load = new Consumer<Context>() {
        @Override
        public void accept(Context context) {

            data = context.getSharedPreferences(dataName, MODE_PRIVATE);

            if(!SData.get(Data.FirstStartMade))
                defaultData();

            load = context1 -> {};
        }
    };

    private static SharedPreferences data;

    public static void LoadData(Context context)
    {
        load.accept(context);
    }


    public static boolean get(Data key)
    {
        return data.getBoolean(getData(key), false);
    }

    public static boolean get(Data key, boolean ifNull)
    {
        return data.getBoolean(getData(key), ifNull);
    }

    public static void set(Data key, boolean value)
    {
        SharedPreferences.Editor editor = data.edit();
        editor.putBoolean(getData(key), value);
        saveData(editor);
    }

    public static int getInt(Data key)
    {
        return data.getInt(getData(key),0);
    }

    public static int getInt(Data key, int outputIfNotHave)
    {
        return data.getInt(getData(key),outputIfNotHave);
    }

    public static void setInt(Data key, int value)
    {
        SharedPreferences.Editor editor = data.edit();
        editor.putInt(getData(key), value);
        saveData(editor);
    }



    public static long getLong(Data key)
    {
        return data.getLong(getData(key),0);
    }

    public static long getLong(Data key, long outputIfNotHave)
    {
        return data.getLong(getData(key),outputIfNotHave);
    }

    public static void setLong(Data key, long value)
    {
        SharedPreferences.Editor editor = data.edit();
        editor.putLong(getData(key), value);
        saveData(editor);
    }


    public static String getString(Data key)
    {
        return data.getString(getData(key), null);
    }

    public static String getString(Data key, String ifNull)
    {
        return data.getString(getData(key),ifNull);
    }

    public static void setString(Data key, String value)
    {
        SharedPreferences.Editor editor = data.edit();
        editor.putString(getData(key), value);
        saveData(editor);
    }

    public static void resetToDefault()
    {
        SData.setString(SData.Data.SavedMedia,null);
        SData.setLong(SData.Data.SavedSeek,0);
        SData.setInt(SData.Data.SavedIndexPlayList,0);
    }

    public static void nullData(){
        resetToDefault();
        SData.set(SData.Data.UndefiledError,false);
    }

    private static void saveData(SharedPreferences.Editor editor){
        editor.commit();
        //editor.apply(); //for async saving
    }

    private static String getData(Data key)
    {
        return dataName+key.name();
    }

    private static void defaultData(){
        // Integers
        SData.setInt(Data.ColorFormatIndex,
                Main.getContext().getResources().getInteger(R.integer.IjkColorFormatsID));

        SData.setInt(Data.ResolutionIndex,
                Main.getContext().getResources().getInteger(R.integer.ResolutionID));

        SData.setInt(Data.ResolutionLiveIndex,
                Main.getContext().getResources().getInteger(R.integer.ResolutionIDLive));

        SData.setInt(Data.RadioPlayerIndex,
                Main.getContext().getResources().getInteger(R.integer.RadioMediaEngineID));

        SData.setInt(Data.LivePlayerIndex,
                Main.getContext().getResources().getInteger(R.integer.LiveMediaEngineID));

        SData.setInt(Data.PlayerIndex,
                Main.getContext().getResources().getInteger(R.integer.URLMediaEngineID));

        SData.setInt(Data.YoutubePlayerIndex,
                Main.getContext().getResources().getInteger(R.integer.YoutubeAudioEngineID));

        SData.setInt(Data.YoutubeVideoPlayerIndex,   // note: field name matches the resValue name
                Main.getContext().getResources().getInteger(R.integer.YoutubeVideoEngineID));

        // Booleans
        SData.set(Data.ExoPlayerOn,
                Main.getContext().getResources().getBoolean(R.bool.EXO));

        SData.set(Data.VLCPlayerOn,
                Main.getContext().getResources().getBoolean(R.bool.VLC));

        SData.set(Data.HardwareDecoding,
                Main.getContext().getResources().getBoolean(R.bool.HardwareDecoding));

        SData.set(Data.YoutubeCaching,
                Main.getContext().getResources().getBoolean(R.bool.ExoYoutubeCaching));

        SData.set(Data.URLCaching,
                Main.getContext().getResources().getBoolean(R.bool.ExoURLCaching));

        SData.set(Data.LegacyYoutubePlayer,
                Main.getContext().getResources().getBoolean(R.bool.OemLegacyPlayer));
    }

    public enum Data {
        AutoStart, FirstStartMade,
        YoutubeVideoPlayerIndex,YoutubePlayerIndex,
        RadioPlayerIndex,PlayerIndex,
        LivePlayerIndex,ResolutionLiveIndex,
        ResolutionIndex,MediaProviderClientSideID,
        LanguageIndex, VolumePosition,
        HardwareDecoding, Port,
        UndefiledError, SavedSeek,
        SavedLoop, StoppingTime,
        SavedIndexPlayList, SavedDisposableErrors,
        SavedListenersErrors, SavedIPorMac,
        SavedJsonNames, SavedDataLoaderActions,
        SavedExtractorPattern, SavedExtractorExpirePattern,
        YoutubeCaching,URLCaching,
        IsSavable, ColorFormatIndex,
        LegacyYoutubePlayer,SavedMedia,
        CheckMacAndSsid,
        ExoPlayerOn,VLCPlayerOn
    }
}

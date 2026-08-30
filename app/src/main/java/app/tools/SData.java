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

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.emb.player.R;

import java.util.function.Consumer;

import app.Main;
import io.reactivex.rxjava3.core.Completable;

import static android.content.Context.MODE_PRIVATE;

public class SData {

    // 1. Configuration Variables
    public static int RAM_APPEND_TRIGGER_LENGTH = 2000;
    public static int RAM_RETAIN_AFTER_CLEANUP = 500;
    public static int MAX_DISK_ARCHIVE_ROWS_PER_KEY = 400;

    private static final String dataName = "Em";

    // 2. SQLite Archiving System
    private static ArchiveDbHelper dbHelper;

    private static Consumer<Context> load = new Consumer<Context>() {
        @Override
        public void accept(Context context) {
            data = context.getSharedPreferences(dataName, MODE_PRIVATE);
            dbHelper = new ArchiveDbHelper(context); // Initialize DB Helper

            if(!SData.get(Data.FirstStartMade))
                defaultData();

            load = StaticFunctions.Empty.c;
        }
    };

    private static SharedPreferences data;

    public static void LoadData(Context context)
    {
        load.accept(context);
    }

    // 3. Background Archiving as a FIFO Queue
    private static void archiveValue(Data key, String type, String value) {
        if (dbHelper == null) return;

        Completable.fromAction(() -> {
                    SQLiteDatabase db = dbHelper.getWritableDatabase();

                    // Insert new value
                    ContentValues values = new ContentValues();
                    values.put("key_name", key.name());
                    values.put("data_type", type);
                    values.put("data_value", value);
                    db.insert("data_archive", null, values);

                    // Enforce MAX_DISK_ARCHIVE_ROWS_PER_KEY
                    Cursor c = db.rawQuery("SELECT COUNT(*) FROM data_archive WHERE key_name = ?", new String[]{key.name()});
                    if (c.moveToFirst()) {
                        int rowCount = c.getInt(0);
                        if (rowCount > MAX_DISK_ARCHIVE_ROWS_PER_KEY) {
                            int rowsToDelete = rowCount - MAX_DISK_ARCHIVE_ROWS_PER_KEY;
                            db.execSQL("DELETE FROM data_archive WHERE _id IN " +
                                            "(SELECT _id FROM data_archive WHERE key_name = ? ORDER BY timestamp ASC LIMIT ?)",
                                    new Object[]{key.name(), rowsToDelete});
                        }
                    }
                    c.close();
                })
                .subscribeOn(DisposableTools.ioThreadPoolScheduler) // Use existing IO Scheduler
                .onErrorComplete()
                .subscribe();
    }

    // 4. The "Zero-Loss" String Appender
    public static void appendStringWithLimits(Data key, String newData) {
        if (
                newData!=null &&
                !newData.isEmpty()&&
                newData.length() > RAM_APPEND_TRIGGER_LENGTH)
        {
            int cutIndex = newData.length() - RAM_RETAIN_AFTER_CLEANUP;
            if (cutIndex < 0) cutIndex = 0; // Safety fallback

            String choppedText = newData.substring(0, cutIndex);
            String retainedText = newData.substring(cutIndex);

            archiveValue(key, "String", choppedText);
            setStringToRam(key, retainedText);
            return;
        }

        setStringToRam(key, newData);
    }

    // 5. History Retrieval Methods
    /*public static String getHistory(Data key) {
        if (dbHelper == null) return "";

        StringBuilder history = new StringBuilder();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT timestamp, data_value FROM data_archive WHERE key_name = ? ORDER BY timestamp ASC", new String[]{key.name()});

        if (c.moveToFirst()) {
            history.append("\n\n--- Archive History ---");
            do {
                String timestamp = c.getString(0);
                String val = c.getString(1);
                history.append("\n[").append(timestamp).append("] : [").append(val).append("]");
            } while (c.moveToNext());
        }
        c.close();
        return history.toString();
    }*/

    // 5. History Retrieval Methods
    public static String getHistory(Data key) {
        if (dbHelper == null) return "";

        StringBuilder history = new StringBuilder();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // Removed timestamp from the SELECT clause since it's no longer needed in the output,
        // but kept it in ORDER BY to maintain chronological order.
        Cursor c = db.rawQuery("SELECT data_value FROM data_archive WHERE key_name = ? ORDER BY timestamp ASC", new String[]{key.name()});

        if (c.moveToFirst()) {
            do {
                String val = c.getString(0);
                history.append(val).append("\n");
            } while (c.moveToNext());
        }
        c.close();

        // .trim() removes the final trailing newline
        return history.toString().trim();
    }

    public static String getStringWithArchive(Data key) {
        return getHistory(key) + getString(key, "");
    }

    public static String getIntWithArchive(Data key) {
        return getHistory(key) + getInt(key);
    }

    // --- Original Methods Below ---

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

    public static void setString(Data key,String value){
        appendStringWithLimits(key,value);
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

    private static void setStringToRam(Data key, String value)
    {
        SharedPreferences.Editor editor = data.edit();
        editor.putString(getData(key), value);
        saveData(editor);
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
        SavedAsPlaylist,SavedLoop,
        SavedLoopForPlaylist, StoppingTime,
        SavedIndexPlayList, SavedDisposableErrors,
        SavedListenersErrors, SavedIPorMac,
        SavedJsonNames, SavedDataLoaderActions,
        SavedExtractorPattern, SavedExtractorExpirePattern,
        YoutubeCaching,URLCaching,
        IsSavable, ColorFormatIndex,
        LegacyYoutubePlayer,SavedMedia,
        CheckMacAddress,
        ExoPlayerOn,VLCPlayerOn,
        MediaProxy,MediaProxyDefault,
        Jwidth,Jheight
    }

    private static class ArchiveDbHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "SDataArchive.db";
        private static final int DATABASE_VERSION = 1;
        private static final String TABLE_NAME = "data_archive";

        ArchiveDbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "key_name TEXT, " +
                    "data_type TEXT, " +
                    "data_value TEXT, " +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db);
        }
    }
}
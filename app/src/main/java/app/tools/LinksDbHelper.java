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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import app.tools.StaticFunctions.Item;

public class LinksDbHelper extends SQLiteOpenHelper{
    // Common column names
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_LINK = "link";
    public static final String COLUMN_MEDIA_SOURCE_INDEX = "mediasourceindex";

    // Create table query template (name can be NULL)
    private static final String CREATE_TABLE_TEMPLATE =
            "CREATE TABLE %s (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_NAME + " TEXT, " +  // Changed to allow NULL values
                    COLUMN_LINK + " TEXT NOT NULL," +
                    COLUMN_MEDIA_SOURCE_INDEX + " INTEGER NOT NULL" +
                    ");";

    // Database Information
    private static final String DATABASE_NAME = "Saved_Links_DB.db";
    private static final int DATABASE_VERSION = 1;
    private static TableName[] tables;

    public LinksDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        tables = TableName.values();
        if(!doesTableExist(TableName.YOUTUBE_LINKS.getTableName()))
            onCreate();
    }

    public static Item[] getTableNames()
    {
        Item[] tables = new Item[LinksDbHelper.tables.length];

        for(int i = 0; i<tables.length; i++)
        {
            tables[i] = new Item(LinksDbHelper.tables[i].tableName, LinksDbHelper.tables[i].output);
        }

        return tables;
    }

    public static String[] getTableNamesAsString()
    {
        String[] tables = new String[LinksDbHelper.tables.length];

        for(int i = 0; i<tables.length; i++)
        {
            tables[i] = LinksDbHelper.tables[i].tableName;
        }

        return tables;
    }

    public void onCreate(SQLiteDatabase db,String... tableNames)
    {
        for (String name : tableNames)
        {
            db.execSQL(String.format(CREATE_TABLE_TEMPLATE, name));
        }
    }

    public void onUpgrade(SQLiteDatabase db,String... tableNames)
    {
        for (String name : tableNames)
        {
            db.execSQL("DROP TABLE IF EXISTS " + name);
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create all tables using the template

        onCreate(db, getTableNamesAsString());
    }

    public void onCreate()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        onCreate(db);
        db.close();
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        onUpgrade(db, getTableNamesAsString());

        // Create tables again
        onCreate(db);
    }

    // Generic method to insert data into any table
    public long insertData(String tableName, String name, String link,Integer sourceTypeIndex) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_NAME, name);
        values.put(COLUMN_LINK, link);
        values.put(COLUMN_MEDIA_SOURCE_INDEX,sourceTypeIndex);

        long result = db.insert(tableName, null, values);
        db.close();
        return result;
    }

    // Generic method to get all data from any table
    public Cursor getAllData(String tableName) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(tableName, null, null, null, null, null, null);
    }

    // Generic method to update data in any table
    public int updateData(String tableName, String id, String name, String link) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, name);
        values.put(COLUMN_LINK, link);

        return db.update(tableName, values, COLUMN_ID + " = ?", new String[]{id});
    }

    // Generic method to delete data from any table
    public int deleteData(String tableName, String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(tableName, COLUMN_ID + " = ?", new String[]{id});
    }

    // Method to get data by name from any table
    public Cursor getDataByName(String tableName, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(tableName, null, COLUMN_NAME + " = ?",
                new String[]{name}, null, null, null);
    }
    // Get total count of records in a table
    public int getCount(String tableName) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + tableName, null);
        int count = 0;
        if (cursor != null) {
            cursor.moveToFirst();
            count = cursor.getInt(0);
            cursor.close();
        }
        db.close();
        return count;
    }

    // Method to check if ID exists in a table
    public boolean doesIdExist(String tableName, int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(tableName,
                new String[]{COLUMN_ID},
                COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)},
                null, null, null);

        boolean exists = (cursor != null && cursor.getCount() > 0);

        if (cursor != null) {
            cursor.close();
        }
        db.close();
        return exists;
    }

    public boolean deleteById(String tableName, int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsDeleted = db.delete(tableName, COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)});
        db.close();
        return rowsDeleted > 0;
    }
    public boolean deleteById(String tableName, String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsDeleted = db.delete(tableName, COLUMN_ID + " = ?", new String[]{id});
        db.close();
        return rowsDeleted > 0;
    }
    // Method to check if a table exists in the database
    public boolean doesTableExist(String tableName) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{tableName}
        );

        boolean exists = (cursor != null && cursor.getCount() > 0);

        if (cursor != null) {
            cursor.close();
        }
        db.close();
        return exists;
    }

    /**
     * Retrieves all column values for a specific item by its ID
     *
     * @param tableName The name of the table to search in
     * @param id The ID of the element to find
     * @return String array containing all column values in order: [id, name, link, media_source_index],
     *         or null if not found
     */
    public String[] getAllColumnValuesById(String tableName, int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columnValues = null;
        Cursor cursor = null;

        try {
            // Query the database for all columns of the specific ID
            cursor = db.query(tableName,
                    null, // null selects all columns
                    COLUMN_ID + " = ?",
                    new String[]{String.valueOf(id)},
                    null, null, null);

            // If cursor has data and can move to first row
            if (cursor != null && cursor.moveToFirst()) {
                // Create array for all 3 columns
                columnValues = new String[3];
/*
                // Get values from cursor - using column names directly
                columnValues[0] = String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)));*/
                columnValues[0] = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)); // Can be null
                columnValues[1] = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LINK));
                columnValues[2] = String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MEDIA_SOURCE_INDEX)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Always close the cursor and database
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }

        return columnValues;
    }

    // Overloaded method that accepts String ID
    public String[] getAllColumnValuesById(String tableName, String id) {
        try {
            int Id = Integer.parseInt(id);
            return getAllColumnValuesById(tableName, Id);
        } catch (NumberFormatException e) {
            return null; // Invalid ID format
        }
    }

    /**
     * Retrieves the media source index from a specific table by its ID
     *
     * @param tableName The name of the table to search in
     * @param id The ID of the element to find
     * @return The media source index if found, or -1 if not found
     */
    public int getMediaSourceIndexById(String tableName, int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        int mediaSourceIndex = -1;
        Cursor cursor = null;

        try {
            // Query the database for the specific ID
            cursor = db.query(tableName,
                    new String[]{COLUMN_MEDIA_SOURCE_INDEX},  // Only select the media source index column
                    COLUMN_ID + " = ?",
                    new String[]{String.valueOf(id)},
                    null, null, null);

            // If cursor has data and can move to first row
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(COLUMN_MEDIA_SOURCE_INDEX);
                if (columnIndex != -1) {
                    mediaSourceIndex = cursor.getInt(columnIndex);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Always close the cursor and database
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }

        return mediaSourceIndex;
    }

    // Overloaded method that accepts String ID
    public int getMediaSourceIndexById(String tableName, String id) {
        try {
            int Id = Integer.parseInt(id);
            return getMediaSourceIndexById(tableName, Id);
        } catch (NumberFormatException e) {
            return -1; // Invalid ID format
        }
    }

    /**
     * Retrieves the link from a specific table by its ID
     *
     * @param tableName The name of the table to search in
     * @param id The ID of the element to find
     * @return The link string if found, or null if not found
     */
    public String getLinkById(String tableName, int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        String link = null;
        Cursor cursor = null;

        try {
            // Query the database for the specific ID
            cursor = db.query(tableName,
                    new String[]{COLUMN_LINK},  // Only select the link column
                    COLUMN_ID + " = ?",
                    new String[]{String.valueOf(id)},
                    null, null, null);

            // If cursor has data and can move to first row
            if (cursor != null && cursor.moveToFirst()) {
                int linkIndex = cursor.getColumnIndex(COLUMN_LINK);
                if (linkIndex != -1) {
                    link = cursor.getString(linkIndex);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Always close the cursor and database
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }

        return link;
    }

    // Overloaded method that accepts String ID
    public String getLinkById(String tableName, String id) {
        try {
            int Id = Integer.parseInt(id);
            return getLinkById(tableName, Id);
        } catch (NumberFormatException e) {
            return null; // Invalid ID format
        }
    }

    // Overloaded method that accepts TableName enum
    public String getLinkById(TableName tableName, int id) {
        return getLinkById(tableName.getTableName(), id);
    }

    // Overloaded method that accepts TableName enum and String ID
    public String getLinkById(TableName tableName, String id) {
        return getLinkById(tableName.getTableName(), id);
    }

    // Add these methods to your LinksDbHelper class

    /**
     * Checks if a link already exists in the specified table
     * @param tableName The table to check in
     * @param link The link to check for
     * @return true if the link exists, false otherwise
     */
    public boolean doesLinkExist(String tableName, String link) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        boolean exists = false;

        try {
            cursor = db.query(tableName,
                    new String[]{COLUMN_ID},
                    COLUMN_LINK + " = ?",
                    new String[]{link},
                    null, null, null);

            exists = (cursor != null && cursor.getCount() > 0);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }

        return exists;
    }

    public enum TableName {
        YOUTUBE_LIVE_LINKS("YoutubeLiveLinks","YOUTUBE - LIVE"),      // ordinal: 0
        YOUTUBE_LINKS("YoutubeLinks","YOUTUBE"),               // ordinal: 1
        YOUTUBE_PLAYLIST_LINKS("YoutubePlaylistLinks","YOUTUBE - PLAYLIST"), // ordinal: 2
        URLS("Urls","URLS");                                // ordinal: 3
        //URLS_FAST("UrlsFast","URLS - FAST");                       // ordinal: 4

        private final String tableName;
        private final String output;

        TableName(String tableName,String output) {
            this.tableName = tableName;
            this.output = output;
        }

        public String getTableName() {
            return tableName;
        }

        public String getOutput()
        {
            return output;
        }

        public int getIndex() {
            return this.ordinal();
        }

        @Override
        public String toString() {
            return tableName;
        }
    }
}

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
import java.io.File;
import java.util.*;

public class JsonFileLister {
    
    private final Context context;
    
    public JsonFileLister(Context context) {
        this.context = context;
    }
    
    // Get all JSON file paths in specified directory sorted by modification date (oldest first)
    public List<String> getJsonFilePaths(String directory) {
        List<File> jsonFiles = getJsonFilesSortedByDate(directory);
        List<String> filePaths = new ArrayList<>();
        
        for (File file : jsonFiles) {
            filePaths.add(file.getAbsolutePath());
        }
        
        return filePaths;
    }

    // Add debug to your JsonFileLister.deleteAllFiles method
    public void deleteAllFiles(String directory) {
        SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) +
                "[JsonFileLister.deleteAllFiles CALLED for: " + directory + "]");

        File dir = new File(context.getFilesDir(), directory);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) +
                        "[Deleting " + files.length + " files from " + directory + "]");
                for (File file : files) {
                    boolean deleted = file.delete();
                    SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) +
                            "[Deleted " + file.getName() + ": " + deleted + "]");
                }
            }
        }
    }
    
    // Get all JSON file paths sorted by modification date (newest first)
    public List<String> getJsonFilePathsNewestFirst(String directory) {
        List<File> jsonFiles = getJsonFilesSortedByDate(directory);
        List<String> filePaths = new ArrayList<>();
        
        // Reverse to get newest first
        for (int i = jsonFiles.size() - 1; i >= 0; i--) {
            filePaths.add(jsonFiles.get(i).getAbsolutePath());
        }
        
        return filePaths;
    }
    
    // Get file paths with their modification dates
    public List<FileInfo> getJsonFilePathsWithDates(String directory) {
        List<File> jsonFiles = getJsonFilesSortedByDate(directory);
        List<FileInfo> fileInfos = new ArrayList<>();
        
        for (File file : jsonFiles) {
            fileInfos.add(new FileInfo(
                file.getAbsolutePath(),
                file.getName(),
                file.length(),
                file.lastModified(),
                new Date(file.lastModified())
            ));
        }
        
        return fileInfos;
    }
    
    // Get just the file names sorted by date
    public List<String> getJsonFileNames(String directory) {
        List<File> jsonFiles = getJsonFilesSortedByDate(directory);
        List<String> fileNames = new ArrayList<>();
        
        for (File file : jsonFiles) {
            fileNames.add(file.getName());
        }
        
        return fileNames;
    }
    
    // Get File objects sorted by date
    public List<File> getJsonFiles(String directory) {
        return getJsonFilesSortedByDate(directory);
    }
    
    // Check if directory exists and has JSON files
    public boolean hasJsonFiles(String directory) {
        File dir = getDirectory(directory);
        if (!dir.exists()) return false;
        
        File[] files = dir.listFiles();
        if (files == null) return false;
        
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().endsWith(".json")) {
                return true;
            }
        }
        return false;
    }
    
    // Get total number of JSON files in directory
    public int getJsonFileCount(String directory) {
        return getJsonFileNames(directory).size();
    }
    
    // Get the directory path
    public String getDirectoryPath(String directory) {
        return getDirectory(directory).getAbsolutePath();
    }
    
    // Check if directory exists
    public boolean directoryExists(String directory) {
        return getDirectory(directory).exists();
    }
    
    // Get all files (not just JSON) sorted by date
    public List<File> getAllFilesSortedByDate(String directory) {
        File dir = getDirectory(directory);
        List<File> allFiles = new ArrayList<>();
        
        if (!dir.exists()) {
            return allFiles;
        }
        
        File[] files = dir.listFiles();
        if (files == null) {
            return allFiles;
        }
        
        for (File file : files) {
            if (file.isFile()) {
                allFiles.add(file);
            }
        }
        
        // Sort by last modified (oldest first)
        Collections.sort(allFiles, new Comparator<File>() {
            @Override
            public int compare(File file1, File file2) {
                return Long.compare(file1.lastModified(), file2.lastModified());
            }
        });
        
        return allFiles;
    }
    
    private File getDirectory(String directory) {
        return new File(context.getFilesDir(), directory);
    }
    
    private List<File> getJsonFilesSortedByDate(String directory) {
        List<File> jsonFiles = new ArrayList<>();
        File dir = getDirectory(directory);
        
        if (!dir.exists()) {
            return jsonFiles;
        }
        
        File[] files = dir.listFiles();
        if (files == null) {
            return jsonFiles;
        }
        
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().endsWith(".json")) {
                jsonFiles.add(file);
            }
        }
        
        // Sort by last modified (oldest first)
        Collections.sort(jsonFiles, new Comparator<File>() {
            @Override
            public int compare(File file1, File file2) {
                return Long.compare(file1.lastModified(), file2.lastModified());
            }
        });
        
        return jsonFiles;
    }
    
    public static class FileInfo {
        public final String filePath;
        public final String fileName;
        public final long fileSize;
        public final long lastModified;
        public final Date modifiedDate;
        
        public FileInfo(String filePath, String fileName, long fileSize, long lastModified, Date modifiedDate) {
            this.filePath = filePath;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.lastModified = lastModified;
            this.modifiedDate = modifiedDate;
        }
        
        @Override
        public String toString() {
            return "FileInfo{" +
                    "filePath='" + filePath + '\'' +
                    ", fileName='" + fileName + '\'' +
                    ", fileSize=" + fileSize +
                    ", lastModified=" + lastModified +
                    ", modifiedDate=" + modifiedDate +
                    '}';
        }
    }
}
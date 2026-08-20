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

import android.app.ActivityManager;
import android.content.Context;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import app.Main;
import server.tools.HttpServletAdvanced;

public class ErrorCodeApp extends HttpServletAdvanced {

    // 1. Configuration Variables
    private static final int TRIGGER_LENGTH = 10000;
    private static final int RETAIN_LENGTH = 2000;

    // 3. Initialize properties using the new SmartString class
    public static final SmartString ramUsageInApp = new SmartString(TRIGGER_LENGTH, RETAIN_LENGTH, "ramUsageInApp");

    public static final SmartString detector = new SmartString(TRIGGER_LENGTH, RETAIN_LENGTH, "detector");
    public static final SmartString disposableErrors = new SmartString(TRIGGER_LENGTH, RETAIN_LENGTH, "disposableErrors");
    public static final SmartString stoppingTime = new SmartString(TRIGGER_LENGTH, RETAIN_LENGTH, "stoppingTime");
    public static final SmartString mediaPlayerErrors = new SmartString(TRIGGER_LENGTH, RETAIN_LENGTH, "mediaPlayerErrors");
    public static final SmartString macAddressUpdate = new SmartString(TRIGGER_LENGTH, RETAIN_LENGTH, "macAddressUpdate");

    public static final SmartString errorAdditional = new SmartString(TRIGGER_LENGTH, RETAIN_LENGTH, "errorAdditional");
    public static final SmartString dataLoader = new SmartString(TRIGGER_LENGTH, RETAIN_LENGTH, "dataLoader");

    public static final SmartString currentDebug = new SmartString(TRIGGER_LENGTH, RETAIN_LENGTH, "currentDebug: ");
    public static final SmartString postResiver = new SmartString(TRIGGER_LENGTH, RETAIN_LENGTH, "");
    public static final SmartString newpipe = new SmartString(TRIGGER_LENGTH, RETAIN_LENGTH, "newpipe: ");

    public static void getSystemMemoryInfo(Context context) {
        String output = "[[System]";

        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);

        long totalMemory = memoryInfo.totalMem;
        long availableMemory = memoryInfo.availMem;
        long usedMemory = totalMemory - availableMemory;
        boolean isLowMemory = memoryInfo.lowMemory;
        long threshold = memoryInfo.threshold;

        output = output + System.lineSeparator() + "SystemMemory " + "Total RAM: " + formatSize(totalMemory);
        output = output + System.lineSeparator() + "SystemMemory " + "Available RAM: " + formatSize(availableMemory);
        output = output + System.lineSeparator() + "SystemMemory " + "Used RAM: " + formatSize(usedMemory);
        output = output + System.lineSeparator() + "SystemMemory " + "Low Memory: " + isLowMemory;
        output = output + System.lineSeparator() + "SystemMemory " + "Low Memory Threshold: " + formatSize(threshold);
        output = output + "]";

        // Use .append() instead of redefining the string
        ramUsageInApp.append(System.lineSeparator() + output);
    }

    // Get current app's memory usage
    public static void getCurrentAppMemoryUsage() {
        // Reset the value instead of setting to ""
        ramUsageInApp.set("");

        Runtime runtime = Runtime.getRuntime();

        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        String output = "[[RamInApp]";

        output = output + System.lineSeparator() + "totalMemory: " + formatSize(totalMemory);
        output = output + System.lineSeparator() + "freeMemory: " + formatSize(freeMemory);
        output = output + System.lineSeparator() + "usedMemory: " + formatSize(usedMemory);
        output = output + System.lineSeparator() + "maxMemory: " + formatSize(maxMemory);
        output = output + "]";

        ramUsageInApp.append(output);

        getSystemMemoryInfo(Main.getContext());
    }

    @Override
    protected void doGetAdvanced(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Pull .getString() when building the response
        String k = ramUsageInApp.getString() +
                System.lineSeparator() + errorAdditional.getString() + System.lineSeparator() + macAddressUpdate.getString() +
                System.lineSeparator() + detector.getString() + System.lineSeparator() + disposableErrors.getString() +
                System.lineSeparator() + mediaPlayerErrors.getString() + System.lineSeparator() + stoppingTime.getString() +
                System.lineSeparator() + dataLoader.getString() + System.lineSeparator() + currentDebug.getString() +
                System.lineSeparator() + postResiver.getString() + System.lineSeparator() + newpipe.getString();

        resp.getWriter().write(k);
    }

    private static String formatSize(long size) {
        String suffix = "B";
        if (size >= 1024) {
            size /= 1024;
            suffix = "KB";
        }
        if (size >= 1024) {
            size /= 1024;
            suffix = "MB";
        }
        if (size >= 1024) {
            size /= 1024;
            suffix = "GB";
        }
        return size + " " + suffix;
    }

    // 2. Custom "String" Wrapper to automatically clean old characters
    public static class SmartString {
        private final StringBuilder builder;
        private final int triggerLimit;
        private final int retainLimit;

        public SmartString(int triggerLimit, int retainLimit, String initialValue) {
            this.triggerLimit = triggerLimit;
            this.retainLimit = retainLimit;
            this.builder = new StringBuilder(initialValue != null ? initialValue : "");
        }

        // Set overwrites the whole string
        public void set(String text) {
            builder.setLength(0);
            if (text != null) {
                builder.append(text);
                enforceLimits();
            }
        }

        // Append adds to the string and cleans up if it gets too big
        public void append(String text) {
            if (text != null) {
                builder.append(text);
                enforceLimits();
            }
        }

        // Checks and cuts old chars automatically
        private void enforceLimits() {
            if (builder.length() > triggerLimit) {
                int cutIndex = builder.length() - retainLimit;
                if (cutIndex < 0) cutIndex = 0;
                builder.delete(0, cutIndex); // Deletes from index 0 to cutIndex
            }
        }

        // This is the custom getString() you requested
        public String getString() {
            enforceLimits(); // Double check before returning just in case
            return builder.toString();
        }
    }
}
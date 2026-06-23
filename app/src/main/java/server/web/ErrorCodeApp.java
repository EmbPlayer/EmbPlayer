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
import app.tools.SData;
import server.tools.HttpServletAdvanced;

public class ErrorCodeApp extends HttpServletAdvanced {
    public static String ramUsageInApp = "ramUsageInApp";

    public static String detector = "detector";
    public static String disposableErrors = "disposableErrors";
    public static String stoppingTime = "stoppingTime";
    public static String mediaPlayerErrors = "mediaPlayerErrors";
    public static String macAddressUpdate = "macAddressUpdate";

    public static String errorAdditional = "errorAdditional";
    public static String dataLoader = "dataLoader";

    public static String currentDebug = "currentDebug: ";

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

        ramUsageInApp = ramUsageInApp + System.lineSeparator() + output;
    }

    // Get current app's memory usage
    public static void getCurrentAppMemoryUsage() {
        ramUsageInApp = "";

        Runtime runtime = Runtime.getRuntime();

        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        String output = "[[RamInApp]";

        output = output + System.lineSeparator() + "totalMemory: " +formatSize(totalMemory);
        output = output + System.lineSeparator() + "freeMemory: " +formatSize(freeMemory);
        output = output + System.lineSeparator() + "usedMemory: " +formatSize(usedMemory);
        output = output + System.lineSeparator() + "maxMemory: " +formatSize(maxMemory);
        output = output + "]";

        ramUsageInApp = output;

        getSystemMemoryInfo(Main.getContext());
    }

    @Override
    protected void doGetAdvanced(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String k = ramUsageInApp+
                System.lineSeparator()+ errorAdditional +System.lineSeparator()+ macAddressUpdate +
                System.lineSeparator()+ detector +System.lineSeparator()+ disposableErrors +
                System.lineSeparator()+ mediaPlayerErrors +System.lineSeparator()+ stoppingTime +
                System.lineSeparator()+ dataLoader+System.lineSeparator()+currentDebug;
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
}

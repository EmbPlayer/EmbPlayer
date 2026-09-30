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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;

import static app.tools.DisposableTools.lifo;
import static app.tools.DisposableTools.ioThreadPoolScheduler;
import static app.tools.StaticFunctions.onErrorSave;

public class Connection {

    // Check global internet connectivity with an HTTP request
    public static boolean isInternetAvailable(URL url) {
        try {
            HttpURLConnection urlConnection = (HttpURLConnection) (url.openConnection());
            urlConnection.setRequestProperty("User-Agent", "Test");
            urlConnection.setRequestProperty("Connection", "close");
            urlConnection.setConnectTimeout(1500); // Timeout after 1.5 seconds
            urlConnection.connect();
            return (urlConnection.getResponseCode() == 200);
        } catch (IOException e) {
            onErrorSave("isInternetAvailable",e);
            //e.printStackTrace();
        }
        return false;
    }

    public static boolean isHaveInternet()
    {
        try {
            // Pinging Google's DNS server
            Process process = Runtime.getRuntime().exec("/system/bin/ping -c 1 8.8.8.8");
            int returnVal = process.waitFor();
            return (returnVal == 0);
        } catch (Exception e) {
            onErrorSave("isHaveInternet",e);
            //e.printStackTrace();
        }
        return false;
    }

    public static void ifNotHaveConnectionWaitInfinityTime(Recyclable.ListDisposable collection,Runnable onComplete)
    {
        if(isHaveInternet()){
            collection.add(onComplete, lifo,"internetChecker");
            return;
        }

        collection.addPollingTaskWithTimeOut(
                ()->!isHaveInternet(),
                StaticFunctions.Empty.r,
                onComplete,
                StaticFunctions.Empty.r,
                StaticFunctions.Empty.r,
                StaticFunctions.Empty.a,
                250,
                -1,
                ioThreadPoolScheduler,
                lifo,
                "internetChecker"
        );
    }

    public static void ifNotHaveConnectionWaitInfinityTime(Recyclable.ListDisposable collection,Callable<Boolean> breaker,Runnable onComplete) throws Exception {
        if(isHaveInternet()){
            collection.add(onComplete, lifo,"internetChecker");
            return;
        }

        collection.addPollingTaskWithTimeOut(
                ()->!isHaveInternet() && !breaker.call(),
                StaticFunctions.Empty.r,
                onComplete,
                StaticFunctions.Empty.r,
                StaticFunctions.Empty.r,
                StaticFunctions.Empty.a,
                250,
                -1,
                ioThreadPoolScheduler,
                lifo,
                "internetChecker");
    }
}

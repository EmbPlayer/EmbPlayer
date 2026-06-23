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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;

import static app.Main.getContext;
import static app.tools.DisposableTools.waitMS;
import static app.tools.StaticFunctions.onErrorSave;

public class Connection {

    /**
     * Check if device is connected via Ethernet OR WiFi
     */
    public static boolean isConnectedToRouter(Context context) {
        return isConnectedViaWifi(context) || isConnectedViaEthernet(context);
    }

    /**
     * Check WiFi connection
     */
    public static boolean isConnectedViaWifi(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M+ (API 23+)
            android.net.Network network = cm.getActiveNetwork();
            if (network == null) return false;

            android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null &&
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
        } else {
            // Pre-Android M
            NetworkInfo wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            return wifiInfo != null && wifiInfo.isConnected();
        }
    }

    /**
     * Check Ethernet connection
     */
    public static boolean isConnectedViaEthernet(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M+ (API 23+)
            android.net.Network network = cm.getActiveNetwork();
            if (network == null) return false;

            android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null &&
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET);
        } else {
            // Pre-Android M (Ethernet added in API 13 - Android 3.2)
            NetworkInfo ethernetInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_ETHERNET);
            return ethernetInfo != null && ethernetInfo.isConnected();
        }
    }


    // Check if any network (Wi-Fi, mobile data, etc.) is available
    public static boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

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

    // Method to check global internet connectivity using ping
    public static boolean isInternetAvailableWithPing() {
        try {
            // Pinging Google's DNS server
            Process process = Runtime.getRuntime().exec("/system/bin/ping -c 1 8.8.8.8");
            int returnVal = process.waitFor();
            return (returnVal == 0);
        } catch (Exception e) {
            onErrorSave("isInternetAvailableWithPing",e);
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

    public static void ifNotHaveConnectionWaitInfinityTime()
    {
        if(isHaveInternet())
            return;

        do {
            waitMS(100);
        } while(!isHaveInternet());
    }

    public static boolean ifNotHaveConnectionWaitInfinityTime(Callable<Boolean> breaker) throws Exception {
        if(breaker.call())
            return false;

        if(isHaveInternet())
            return true;

        do {
            waitMS(100);

            if(breaker.call())
                return false;
        } while(!isHaveInternet());

        return true;
    }

    public static void waitConnectionWithPing() throws InterruptedException {
      if(!isInternetAvailableWithPing())
      {
          Thread.sleep(1000);
          waitConnectionWithPing();
      }
    }

    public static boolean waitForStableConnection(int timeoutSeconds) {
        int attempts = 0;
        int maxAttempts = timeoutSeconds * 2; // Check every 500ms

        while (attempts < maxAttempts) {
            if (Connection.isNetworkAvailable() && Connection.isHaveInternet()) {
                // Double-check stability
                waitMS(500);
                if (Connection.isNetworkAvailable() && Connection.isHaveInternet()) {
                    return true;
                }
            }
            waitMS(500);
            attempts++;
        }
        return false;
    }

    public static boolean networkConnectionHave()
    {
        // Check network connectivity
        NetworkInfo info = ((ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return info != null && info.isConnectedOrConnecting();
    }

    private static String normalizeUrl(String url) {
        // If it's already a full URL with protocol, return as is
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }

        // If it has a port number or path, assume HTTP
        if (url.contains(":") || url.contains("/")) {
            // Check if it already has a protocol
            if (!url.startsWith("http")) {
                return "http://" + url;
            }
        }

        // Plain domain or IP without protocol
        return "http://" + url;
    }
}

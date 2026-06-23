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

package server.tools;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;

public class mDNSService {
    private static final String TAG = "mDNSService";

    private NsdManager mNsdManager;
    private NsdManager.RegistrationListener mRegistrationListener;

    private String mServiceName;
    private String mServiceType;
    private int mServicePort;
    private Map<String, String> mTxtRecords;

    public mDNSService(Context context, String serviceName, String serviceType, int port) {
        this.mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.mServiceName = serviceName;
        this.mServiceType = serviceType;
        this.mServicePort = port;
        this.mTxtRecords = new HashMap<>();

        // Add default TXT records
        mTxtRecords.put("version", "1.0");
        mTxtRecords.put("device", "Android");
        mTxtRecords.put("model", android.os.Build.MODEL);
        mTxtRecords.put("timestamp", String.valueOf(System.currentTimeMillis()));

        initializeRegistrationListener();
    }

    public mDNSService(Context context) {
        this(context, "AndroidService", "_http._tcp.", 8080);
    }

    // Method to get all TXT records
    public Map<String, String> getTxtRecords() {
        return new HashMap<>(mTxtRecords);
    }

    // Getters
    public String getServiceName() { return mServiceName; }
    public String getServiceType() { return mServiceType; }
    public int getServicePort() { return mServicePort; }

    // Setters
    public void setServiceName(String name) { this.mServiceName = name; }
    public void setServiceType(String type) { this.mServiceType = type; }
    public void setServicePort(int port) { this.mServicePort = port; }

    public void start() {
        if (mNsdManager == null) {
            Log.e(TAG, "NSD Manager is null");
            return;
        }

        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(mServiceName);
        serviceInfo.setServiceType(mServiceType);
        serviceInfo.setPort(mServicePort);

        // Note: Standard Android NsdServiceInfo doesn't support setting TXT records directly
        // The data is stored separately and would need to be handled by your application logic

        try {
            mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
            Log.i(TAG, "Service registration started with data: " + mTxtRecords.toString());
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid service info: " + e.getMessage());
        }
    }

    public void stop() {
        if (mNsdManager != null && mRegistrationListener != null) {
            try {
                mNsdManager.unregisterService(mRegistrationListener);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error unregistering: " + e.getMessage());
            }
        }
    }
    // Method to add custom TXT record data
    public void addTxtRecord(String key, String value) {
        mTxtRecords.put(key, value);
        Log.d(TAG, "Added TXT record: " + key + " = " + value);
    }

    public void updateServiceInfo(String newName, int newPort) {
        stop();
        this.mServiceName = newName;
        this.mServicePort = newPort;
        start();
    }
    // Method to log TXT records
    private void logTxtRecords() {
        Log.i(TAG, "Current TXT Records:");
        for (Map.Entry<String, String> entry : mTxtRecords.entrySet()) {
            Log.i(TAG, "  " + entry.getKey() + ": " + entry.getValue());
        }
    }

    private void initializeRegistrationListener() {
        mRegistrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                mServiceName = serviceInfo.getServiceName();
                Log.i(TAG, "Service registered: " + mServiceName);
                logTxtRecords();
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Registration failed: " + errorCode);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                Log.i(TAG, "Service unregistered: " + serviceInfo.getServiceName());
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Unregistration failed: " + errorCode);
            }
        };
    }
}
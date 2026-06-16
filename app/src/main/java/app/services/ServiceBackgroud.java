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

package app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public abstract class ServiceBackgroud extends Service
{
    private static int iconID;
    private static String channelID;
    private static String notificationName;
    private static String title;
    private static String settingsPanelTitle;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        intent.putExtra(notificationName,"Server On");

        String input = intent.getStringExtra(notificationName);

        // Create the notification
        Notification notification = new NotificationCompat.Builder(this, channelID)
                .setContentTitle(title)
                .setContentText(input)
                .setSmallIcon(iconID)
                .build();

        // Start the service in the foreground
        startForeground(1, notification);

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected static void setup(String channelID, String notificationName, String title, String settingsPanelTitle, int iconID)
    {
        ServiceBackgroud.channelID = channelID;
        ServiceBackgroud.notificationName = notificationName;
        ServiceBackgroud.title = title;
        ServiceBackgroud.settingsPanelTitle = settingsPanelTitle;
        ServiceBackgroud.iconID = iconID;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    channelID, // Use your unique ID
                    settingsPanelTitle, // settings panel name
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
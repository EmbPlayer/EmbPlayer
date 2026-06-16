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

import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;

import app.App.AppBack;
import app.tools.AndroidOsUpdatesListener;
import app.tools.Recyclable;
import app.tools.SData;
import app.tools.StaticFunctions;
import server.JettyServer;

import androidx.annotation.Nullable;
import app.Main;
//import it.ennova.zerxconf.model.NetworkServiceDiscoveryInfo;

import com.emb.player.R;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;

import static app.tools.DisposableTools.forServer;
import static app.tools.DisposableTools.waitMS;
import static server.Home.app;

public class BaseServer extends ServiceBackgroud {
    public static final Maker createServer = new Maker();
    private static final AtomicBoolean isDestroying = new AtomicBoolean();
    private static final Recyclable.ListDisposable tasks = new Recyclable.ListDisposable(BaseServer.class);

    private static boolean serverIsNotCreated;
    private static boolean hostname;
    private static int port;
    private static String localhostIP;
    private static String localhostHostName;
    private static JettyServer server;

    private static PowerManager.WakeLock wakeLock;

    //private static UniversalMDNS domain;
    //private static Mdns domain;

    @Override
    public void onCreate() {
        tasks.add(()->{
            setup("ForegroundServiceChannel", "inputExtra", "EMB Remote Player", "EMB Remote Player", R.mipmap.noti);
            super.onCreate();
        },forServer,"onCreate");
    }

    // In BaseServer.java
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        tasks.add(()->{
            super.onStartCommand(intent, flags, startId);

            PowerManager powerManager = (PowerManager) Main.getContext().getSystemService(POWER_SERVICE);

            // PARTIAL_WAKE_LOCK allows screen off but prevents deep sleep
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"KeepWifiAlive:PreventDeepSleep");
            wakeLock.acquire();

            //domain = new UniversalMDNS("EmbPlayer",getBaseContext());
            createServer.run();

            /*

            domain = new Mdns(
                    getBaseContext(),
                    "EmbPlayer",          // Instance name (will prefix service)
                    "embplayer.local.",   // Hostname (must end with dot)
                    BaseServer.GetLocalhost(),      // Your device's LAN IP
                    port                  // Port
            );*//**/

/*
            domain = new Mdns(
                    getBaseContext(),
                    "EmbPlayer",          // Instance name (will prefix service)
                    "embplayer.lan.",   // Hostname (must end with dot)
                    BaseServer.GetLocalhostIP(),      // Your device's LAN IP
                    port                  // Port
            );*/
        },forServer,"onStartCommand");

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if(isDestroying.getAndSet(true))
            return;

        tasks.add(()->{
            app().sendURLWithoutCleanData(()->{});
            super.onDestroy();
        },forServer,"onDestroying");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent)
    {
        tasks.add(()->{
            SData.nullData();
            StaticFunctions.kill();
        },forServer,"onDestroyingTrigger");
    }

    public static void restart(){

        if(isDestroying.getAndSet(true))
            return;

        createServer.run();
    }

    public static boolean isDestroying()
    {
        return isDestroying.get();
    }

    public static int getPort()
    {
        return port;
    }

    public static boolean serverIsNotCreated()
    {
        return serverIsNotCreated;
    }

    public static void ipAddressLoad()
    {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            hosts:
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        localhostIP = address.getHostAddress();
                        break hosts;
                    }
                }
            }
        } catch (SocketException ex) {
            StaticFunctions.onErrorSave("ipAddressLoad",ex);
        }
    }

    public static void updateLocalhost() throws UnknownHostException {

        for(int i = 0; i<60; i++)
        {
            try
            {
                localhostHostName = InetAddress.getByName(localhostIP).getHostName();
                break;
            }
            catch (Exception e)
            {
                waitMS(500);
            }
        }

        if(localhostIP.equals(localhostHostName))
            hostname = false;
        else
        {
            hostname = true;

            //remove .lan
            if(localhostHostName.endsWith(".lan"))
                localhostHostName = localhostHostName.substring(0,localhostHostName.length()-4);
        }
    }

    public static String getIP()
    {
        return localhostIP;
    }

    public static String getLocalhost()
    {
        if(hostname)
            return localhostHostName;

        return localhostIP;
    }

    public static boolean isHaveHostname()
    {
        return hostname;
    }

    public static class Maker extends StaticFunctions.Starter{

        private final StaticFunctions.Starter portLoad = new StaticFunctions.Starter() {
            @Override
            protected void firstLaunch() {
                portLoad();
                AndroidOsUpdatesListener.connectionSetUP(Main.getContext());
            }

            @Override
            protected void secondLaunches() {
                portLoad();
            }

            private void portLoad(){
                BaseServer.port = SData.getInt(SData.Data.Port,-1);
            }
        };

        @Override
        protected void firstLaunch() {
            tasks.add(()->{
                portLoad.run();

                if(!updateAndCheckIsHaveConnection()){
                    reset();
                    return;
                }

                Main.createUI();

                AppBack.create();

                try {
                    server = new JettyServer(port);
                    serverIsNotCreated = false;
                } catch (Exception e) {
                    reset();
                }
            },forServer,"Can't_Make_Server");
        }

        @Override
        protected void secondLaunches() {
            tasks.add(()->{
                portLoad.run();

                if(!updateAndCheckIsHaveConnection())
                    return;

                Main.createUI();

                AppBack.recreate();

                try {
                    server.recreateServer(port);
                    serverIsNotCreated = false;
                }
                catch(Exception e){}

                isDestroying.set(false);
            },forServer,"Can't_Make_Server");
        }

        private boolean updateAndCheckIsHaveConnection(){
            if(AndroidOsUpdatesListener.isHaveConnection())
                return true;

            serverIsNotCreated = true;
            return false;
        }
    }
}

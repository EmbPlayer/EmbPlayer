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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Log;

import app.App.AppBack;
import app.Main;
import app.services.BaseServer;
import server.web.ErrorCodeApp;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.function.Consumer;

import static app.tools.DisposableTools.forkJoinPool;
import static app.tools.StaticFunctions.onErrorSave;
import static server.Home.app;

public class AndroidOsUpdatesListener extends BroadcastReceiver {
    private static final Recyclable.ListDisposable tasks = new Recyclable.ListDisposable(AndroidOsUpdatesListener.class);
    private static final StaticFunctions.Starter starter = new StaticFunctions.Starter() {
        @Override
        protected void firstLaunch() {

        }

        @Override
        protected void secondLaunches() {
            if(BaseServer.isDestroying())
                return;

            ErrorCodeApp.macAddressUpdate = "MAC_ADDRESS: "+ getCurrentRouterMac();
            if(AppBack.appStarted())
            {
                if(!ipIsChanged()&& app().setUp.get()&& app().globalGenerator.isLive())
                {
                    app().errorHandel.recover();
                }
            }
            else if(BaseServer.serverIsNotCreated())
            {
                try {
                    BaseServer.createServer.run();
                } catch (Exception e) {

                    throw new RuntimeException(e);
                }
            }

            Log.d("ConnectionInfo", "Type: " + currentConnectionType + ", MAC: " + currentRouterMac);
        }
    };

    private static ConnectivityManager cmB;
    private static boolean oldOn;
    private static boolean connectedToRouter;
    private static String currentRouterMac; // Store the current router MAC
    private static String currentConnectionType; // Store connection type

    @Override
    public void onReceive(Context context, Intent intent) {
        tasks.add(()->{
            String o = intent.getAction();

            if(o == null)
                return;

            if(o == Intent.ACTION_BOOT_COMPLETED)
            {
                SData.LoadData(context);
                boolean autoStart = SData.get(SData.Data.AutoStart);
                if(autoStart)
                {
                    Intent launchIntent = new Intent(context, Main.class);
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launchIntent);
                }

                return;
            }

            if(oldOn && o == ConnectivityManager.CONNECTIVITY_ACTION)
            {
                oldUpdateNetwork(context);
            }

        /*
        if (Intent.ACTION_SHUTDOWN.equals(intent.getAction())) {
            SData.Set(SData.undefinitedError,true);
        }*/

        },forkJoinPool,"onReceive");
    }

    public static void connectionSetUP(Context context)
    {
        // in Application or Service
        cmB = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
        {
            oldOn = true;
            oldUpdateNetwork(context);
        }
        else
        {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) // Crucial for real internet checks
                    .build();

            // 3. Register it
            cmB.registerNetworkCallback(request,new ConnectivityManagerM());

// cm.unregisterNetworkCallback(netCallback) when stopping

            modernUpdateNetwork(cmB.getActiveNetwork(),context);

            ConnectivityManagerM.onCon = (n)-> tasks.add(
                    ()->modernUpdateNetwork(n,context),
                    forkJoinPool,
                    "ConnectivityManagerMonConnection");

            ConnectivityManagerM.onLos = () -> tasks.add(
                    ()->onLostConnection(),
                    forkJoinPool,
                    "ConnectivityManagerMonLost");
        }

        starter.setToSecond();
    }

    public static boolean isHaveConnection() {
        return connectedToRouter;
    }

    public static String getCurrentRouterMac() {
        return currentRouterMac;
    }

    public static String getConnectionType() {
        return currentConnectionType;
    }

    private static String normalize(String s, String fallback) {
        if (s == null) return fallback;
        s = s.trim();
        if (s.isEmpty() || s.equals("<unknown ssid>") || s.equals("0.0.0.0")) return fallback;
        return s;
    }

    private static String formatGateway(int gw) {
        if (gw == 0) return "NO_GW";
        return String.format("%d.%d.%d.%d",
                (gw & 0xff), (gw >> 8 & 0xff),
                (gw >> 16 & 0xff), (gw >> 24 & 0xff));
    }

    private static boolean equalsSafe(String a, String b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private static void logD(String tag, String msg) {
        try { Log.d(tag, msg); } catch (Throwable ignored) {}
    }

    // ---------- Wi‑Fi handling (API 14 compatible) ----------
    private static void setWifiIdAndNotify(String ssid, String bssid, String gateway, String hostnameFallback) {
        connectedToRouter = true;
        currentConnectionType = ConnectionTypes.WIFI;

        if (!"NO_BSSID".equals(bssid)) {
            currentRouterMac = ConnectionTypes.WIFI + "|" + ssid + "|" + bssid + "|" + gateway;
        } else {
            if (!"NO_GW".equals(gateway)) {
                currentRouterMac = ConnectionTypes.WIFI + "|IP|" + gateway;
            } else if (hostnameFallback != null && hostnameFallback.length() > 0) {
                currentRouterMac = ConnectionTypes.WIFI + "|HOST|" + hostnameFallback;
            } else {
                currentRouterMac = ConnectionTypes.WIFI + "|UNKNOWN";
            }
        }

        logD("ConnectionInfo", "WiFi id set: " + currentRouterMac);
        onIsHaveConnection();
    }

    // ---------- Cellular / Hotspot handling (API 14+ compatible) ----------
    private static void setCellularIdAndNotify(String carrier, String networkType, String dataIp) {
        connectedToRouter = true;
        currentConnectionType = ConnectionTypes.MOBILE;

        if (carrier != null && !carrier.isEmpty() && !"UNKNOWN_CARRIER".equals(carrier)) {
            currentRouterMac = ConnectionTypes.MOBILE + "|CARRIER|" + carrier + "|" + networkType + "|" + dataIp;
        } else if (dataIp != null && !"NO_IP".equals(dataIp)) {
            currentRouterMac = ConnectionTypes.MOBILE + "|IP|" + dataIp;
        } else {
            currentRouterMac = ConnectionTypes.MOBILE + "|UNKNOWN";
        }

        logD("ConnectionInfo", "Cellular id set: " + currentRouterMac);
        onIsHaveConnection();
    }

    private static void onHaveConnectionGetCellularId(final Context context) {
        String carrier = "UNKNOWN_CARRIER";
        String networkType = "UNKNOWN";
        String dataIp = "NO_IP";

        // Get carrier name (works on all API levels)
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                // getNetworkOperatorName() is deprecated from API 30 but works on all.
                // For API 30+, we can use getSimOperatorName() as fallback.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Use new API, but keep fallback
                    try {
                        String name = tm.getSimOperatorName();
                        if (name != null && !name.isEmpty()) carrier = name;
                    } catch (Exception ignored) {}
                }
                if ("UNKNOWN_CARRIER".equals(carrier)) {
                    String name = tm.getNetworkOperatorName();
                    if (name != null && !name.isEmpty()) carrier = name;
                }

                // Get network type (works on all APIs, though getNetworkType() deprecated from 30)
                int netType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        netType = tm.getDataNetworkType(); // API 30+
                    } catch (Exception ignored) {}
                }
                if (netType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                    netType = tm.getNetworkType(); // deprecated but works for older
                }
                networkType = getNetworkTypeString(netType);
            }
        } catch (Exception e) {
            onErrorSave("CellularCarrier", e);
        }

        // Get mobile data IP address (works on all APIs)
        dataIp = getMobileDataIp();

        // If not found, try hotspot IP (device acting as hotspot)
        if ("NO_IP".equals(dataIp)) {
            dataIp = getHotspotIp();
        }

        setCellularIdAndNotify(carrier, networkType, dataIp);
    }

    private static String getMobileDataIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                String name = iface.getName().toLowerCase();
                // Mobile data interfaces: rmnet (Qualcomm), ccmni (Spreadtrum), wwan, etc.
                if (name.contains("rmnet") || name.contains("ccmni") || name.contains("wwan") || name.contains("mobile")) {
                    Enumeration<InetAddress> addrs = iface.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception e) {
            onErrorSave("GetMobileDataIp", e);
        }
        return "NO_IP";
    }

    private static String getHotspotIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                String name = iface.getName().toLowerCase();
                // Hotspot interfaces often start with wlan or ap, or have IP 192.168.42/43.1
                if (name.startsWith("wlan") || name.startsWith("ap") || name.contains("hotspot")) {
                    Enumeration<InetAddress> addrs = iface.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                            String ip = addr.getHostAddress();
                            if (ip.startsWith("192.168.")) {
                                return ip;
                            }
                        }
                    }
                }
            }
            // Fallback: try any non-loopback IPv4 that isn't WiFi/Ethernet typical ranges
            interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") && !ip.startsWith("192.168.1.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            onErrorSave("GetHotspotIp", e);
        }
        return "NO_IP";
    }

    private static String getNetworkTypeString(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_GPRS: return "GPRS";
            case TelephonyManager.NETWORK_TYPE_EDGE: return "EDGE";
            case TelephonyManager.NETWORK_TYPE_UMTS: return "UMTS";
            case TelephonyManager.NETWORK_TYPE_HSDPA: return "HSDPA";
            case TelephonyManager.NETWORK_TYPE_HSUPA: return "HSUPA";
            case TelephonyManager.NETWORK_TYPE_HSPA: return "HSPA";
            case TelephonyManager.NETWORK_TYPE_LTE: return "LTE";
            case TelephonyManager.NETWORK_TYPE_HSPAP: return "HSPA+";
            case TelephonyManager.NETWORK_TYPE_NR: return "5G";
            default: return "CELLULAR";
        }
    }

    private static void onHaveConnectionGetWifiRouterMacAddress(final Context context) {
        try {
            final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) { onErrorSave("WifiManagerNull", new NullPointerException("wifiManager")); return; }

            final WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo == null) { onErrorSave("WifiInfoNull", new NullPointerException("wifiInfo")); return; }

            final String ssid = normalize(wifiInfo.getSSID(), "NO_SSID");
            final String bssid = normalize(wifiInfo.getBSSID(), "NO_BSSID");

            String gateway = "NO_GW";
            try {
                DhcpInfo dhcp = wifiManager.getDhcpInfo();
                if (dhcp != null) gateway = formatGateway(dhcp.gateway);
            } catch (Exception e) {
                onErrorSave("DhcpRead", e);
            }

            // If gateway not ready, retry once after short delay to allow DHCP to settle
            if ("NO_GW".equals(gateway)) {
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String gw2 = "NO_GW";
                            try {
                                DhcpInfo dh2 = wifiManager.getDhcpInfo();
                                if (dh2 != null) gw2 = formatGateway(dh2.gateway);
                            } catch (Exception ignored) {}
                            String hostnameFallback = null; // optionally get from BaseServer if available
                            setWifiIdAndNotify(ssid, bssid, gw2, hostnameFallback);
                        } catch (Exception e) {
                            onErrorSave("DhcpRetry", e);
                            setWifiIdAndNotify(ssid, bssid, "NO_GW", null);
                        }
                    }
                }, 350);
            } else {
                String hostnameFallback = null;
                setWifiIdAndNotify(ssid, bssid, gateway, hostnameFallback);
            }
        } catch (Exception e) {
            onErrorSave("WifiMAC", e);
        }
    }

    // ---------- Ethernet handling (API 14 compatible) ----------
    private static void setEthernetIdAndNotify(String ifName, String macStr, String gateway, String localIp) {
        connectedToRouter = true;
        currentConnectionType = ConnectionTypes.ETHERNET;

        if (!"NO_MAC".equals(macStr)) {
            currentRouterMac = ConnectionTypes.ETHERNET + "|" + ifName + "|" + macStr + "|" + gateway + "|" + localIp;
        } else {
            if (!"NO_GW".equals(gateway)) {
                currentRouterMac = ConnectionTypes.ETHERNET + "|IP|" + gateway;
            } else if (!"NO_IP".equals(localIp)) {
                currentRouterMac = ConnectionTypes.ETHERNET + "|IP|" + localIp;
            } else {
                currentRouterMac = ConnectionTypes.ETHERNET + "|UNKNOWN";
            }
        }

        logD("ConnectionInfo", "Ethernet id set: " + currentRouterMac);
        onIsHaveConnection();
    }

    private static void onHaveConnectionGetEthernetMacAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return;

            for (NetworkInterface nif : Collections.list(interfaces)) {
                if (!isEthernetInterface(nif)) continue;

                byte[] mac = null;
                try { mac = nif.getHardwareAddress(); } catch (SocketException e) { onErrorSave("GetHwAddr", e); }

                String ifName = normalize(nif.getName(), "NO_IF");
                String macStr = (mac == null) ? "NO_MAC" : formatMacAddress(mac);

                // Try to find a non-loopback IPv4 address on this interface (fallback for gateway)
                String localIp = "NO_IP";
                try {
                    Enumeration<InetAddress> addrs = nif.getInetAddresses();
                    if (addrs != null) {
                        while (addrs.hasMoreElements()) {
                            InetAddress ia = addrs.nextElement();
                            if (ia != null && !ia.isLoopbackAddress()) {
                                String addr = ia.getHostAddress();
                                if (addr != null && addr.indexOf(':') < 0) { // IPv4
                                    localIp = addr;
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    onErrorSave("EthernetInet", e);
                }

                // No reliable gateway available via API14; use localIp as fallback. If localIp unknown, retry once.
                if ("NO_IP".equals(localIp)) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                onHaveConnectionGetEthernetMacAddress(); // single retry
                            } catch (Exception e) {
                                onErrorSave("EthernetRetry", e);
                            }
                        }
                    }, 350);
                    return;
                }

                setEthernetIdAndNotify(ifName, macStr, "NO_GW", localIp);
                return;
            }
        } catch (SocketException e) {
            onErrorSave("EthernetMAC", e);
        } catch (Exception e) {
            onErrorSave("EthernetGeneral", e);
        }
    }

    private static void onLostConnection()
    {
        connectedToRouter = false;
        currentRouterMac = null;
        currentConnectionType = null;
    }

    private static void modernUpdateNetwork(Network network, Context context)
    {
        if (network == null)
            return;

        NetworkCapabilities caps = cmB.getNetworkCapabilities(network);

        if (caps == null)
            return;


        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){
            onHaveConnectionGetWifiRouterMacAddress(context);
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            onHaveConnectionGetCellularId(context);
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)){
            onHaveConnectionGetEthernetMacAddress();
        }
    }

    private static void oldUpdateNetwork(Context context)
    {
        onLostConnection();

        NetworkInfo activeNetwork = cmB.getActiveNetworkInfo();

        if(activeNetwork == null || !NetworkInfo.State.CONNECTED.equals(activeNetwork.getState()))
            return;

        // Get MAC address based on connection type
        if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
            onHaveConnectionGetWifiRouterMacAddress(context);
        } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
            onHaveConnectionGetCellularId(context);
        } else if (activeNetwork.getType() == ConnectivityManager.TYPE_ETHERNET) {
            onHaveConnectionGetEthernetMacAddress();
        }
    }

    private static void onIsHaveConnection()
    {
        starter.run();
    }

    private static boolean isEthernetInterface(NetworkInterface networkInterface) {
        String name = networkInterface.getName().toLowerCase();
        String displayName = networkInterface.getDisplayName().toLowerCase();

        return name.startsWith("eth") ||
                name.contains("ethernet") ||
                displayName.contains("ethernet") ||
                name.equals("eth0");
    }

    private static String formatMacAddress(byte[] mac) {
        if (mac == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? ":" : ""));
        }
        return sb.toString();
    }

    private static boolean ipIsChanged() {
        try {
            String currentComposite = getCurrentRouterMac();
            String savedComposite = SData.getString(SData.Data.SavedIPorMac);

            if (savedComposite == null) {
                SData.setString(SData.Data.SavedIPorMac, currentComposite);
                return true;
            }
            if (currentComposite == null) return false;

            // Read the combined flag (default false)
            boolean checkMacAndSsid = SData.get(SData.Data.CheckMacAndSsid, false);

            // Parse both composites
            ConnectionIdentity currentId = ConnectionIdentity.parse(currentComposite);
            ConnectionIdentity savedId = ConnectionIdentity.parse(savedComposite);

            boolean changed = false;

            // ---- MAC + SSID combined check (if enabled) ----
            if (checkMacAndSsid) {
                // MAC check
                if (!equalsSafe(currentId.mac, savedId.mac)) changed = true;

                // SSID / interface name check
                if (currentId.type.equals("WIFI")) {
                    if (!equalsSafe(currentId.ssid, savedId.ssid)) changed = true;
                } else if (currentId.type.equals("ETHERNET")) {
                    if (!equalsSafe(currentId.ifName, savedId.ifName)) changed = true;
                }
            }

            // ---- IP / hostname check (ALWAYS) ----
            String currentIdentifier = getIpOrHostIdentifier(currentId);
            String savedIdentifier = getIpOrHostIdentifier(savedId);
            boolean currentHasHost = currentId.isHostFallback;
            boolean savedHasHost = savedId.isHostFallback;
            boolean identifierTypeChanged = (currentHasHost != savedHasHost);

            if (!equalsSafe(currentIdentifier, savedIdentifier) || identifierTypeChanged) {
                changed = true;
            }

            // If any change detected (always includes IP/hostname, plus optional MAC+SSID)
            if (changed) {
                triggerUpdate(currentComposite);
                return true;
            }

            return false;
        } catch (Exception e) {
            onErrorSave("IPIsChanged", e);
            return false;
        }
    }

    private static String getIpOrHostIdentifier(ConnectionIdentity id) {
        if (id == null) return null;
        if (id.isHostFallback && id.host != null) return id.host;
        if (id.ip != null && !"NO_GW".equals(id.ip) && !"NO_IP".equals(id.ip)) return id.ip;
        return null;
    }

    private static void triggerUpdate(String newComposite) {
        app().sendURL();
        SData.setString(SData.Data.SavedIPorMac, newComposite);
        BaseServer.ipAddressLoad();
        Main.loadUI();
    }

    public static class ConnectivityManagerM extends ConnectivityManager.NetworkCallback {
        private static Consumer<Network> onCon = (network)->{};
        private static Runnable onLos = ()->{};

        @Override
        public void onAvailable(Network network) {
            onCon.accept(network);
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {

        }

        @Override
        public void onLost(Network network) {
            onLos.run();
        }
    }

    public static class ConnectionTypes
    {
        public static final String WIFI = "WIFI";
        public static final String ETHERNET = "ETHERNET";
        public static final String MOBILE = "Mobile";
        public static final String UNKNOWN = "Unknown";
    }

    private static class ConnectionIdentity {
        String type;
        String ssid;
        String ifName;
        String mac;
        String ip;
        String host;
        boolean isIpFallback;
        boolean isHostFallback;

        static ConnectionIdentity parse(String composite) {
            ConnectionIdentity id = new ConnectionIdentity();
            if (composite == null) return id;

            String[] parts = composite.split("\\|");
            if (parts.length < 2) return id;

            id.type = parts[0];

            if (parts[1].equals("IP")) {
                id.isIpFallback = true;
                id.ip = (parts.length > 2) ? parts[2] : null;
            } else if (parts[1].equals("HOST")) {
                id.isHostFallback = true;
                id.host = (parts.length > 2) ? parts[2] : null;
            } else {
                if (id.type.equals("WIFI")) {
                    id.ssid = parts[1];
                    id.mac = (parts.length > 2) ? parts[2] : null;
                    id.ip = (parts.length > 3) ? parts[3] : null;
                } else if (id.type.equals("ETHERNET")) {
                    id.ifName = parts[1];
                    id.mac = (parts.length > 2) ? parts[2] : null;
                    // For Ethernet with MAC, parts[3] is gateway (often NO_GW), parts[4] is local IP
                    if (parts.length > 4 && !"NO_GW".equals(parts[3])) {
                        id.ip = parts[3];  // use gateway if available
                    } else if (parts.length > 4) {
                        id.ip = parts[4];  // otherwise use local IP
                    } else if (parts.length > 3) {
                        id.ip = parts[3];
                    }
                }
            }
            return id;
        }
    }
}
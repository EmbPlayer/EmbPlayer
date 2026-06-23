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

package ssl;

import android.util.Log;

import org.conscrypt.Conscrypt;

import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;
import ssl.SslConfigManager;

public class MediaProxyClientFactory {
    public OkHttpClient buildClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        // 1. Conscrypt for Modern TLS on old devices
        if (SslConfigManager.isUseConscrypt()) {
            try {
                Security.insertProviderAt(Conscrypt.newProvider(), 1);
                Log.d("ProxyClient", "Conscrypt enabled successfully.");
            } catch (Exception e) {
                Log.e("ProxyClient", "Failed to initialize Conscrypt.", e);
            }
        }

        // 2. Insecure SSL (Trust All Certificates)
        if (SslConfigManager.isEnableInsecureSSL()) {
            try {
                final TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                            @Override
                            public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[]{};
                            }
                        }
                };

                SSLContext sslContext = SslConfigManager.isUseConscrypt() ?
                        SSLContext.getInstance("TLS", "Conscrypt") :
                        SSLContext.getInstance("TLS");

                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);

                builder.hostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                });
                Log.d("ProxyClient", "Insecure SSL enabled. Trusting all certs.");

            } catch (Exception e) {
                Log.e("ProxyClient", "Failed to set up insecure SSL.", e);
            }
        }

        // 3. Legacy TLS Fallback
        if (SslConfigManager.isLegacyTlsForOldAndroid()) {
            ConnectionSpec legacySpec = new ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
                    .allEnabledCipherSuites()
                    .build();

            builder.connectionSpecs(Arrays.asList(legacySpec, ConnectionSpec.CLEARTEXT));
            Log.d("ProxyClient", "Legacy TLS connection specs enabled.");
        }

        return builder.build();
    }
}
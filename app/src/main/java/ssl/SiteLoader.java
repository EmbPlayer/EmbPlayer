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

import android.os.Build;

import org.jetbrains.annotations.NotNull;

import app.App.AppBack;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import okhttp3.*;
import javax.net.ssl.*;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.Socket;

public class SiteLoader {
    protected final String mainSiteUrl;
    protected Listeners listener;
    private SiteLoaderTypes type;
    private Loader base;

    public SiteLoader(String mainSiteUrl, Listeners listener) {
        this.mainSiteUrl = mainSiteUrl;
        this.listener = listener;

        this.type = SiteLoaderTypes.New;
        base = new NewLoader();
    }

    // Public API methods
    public void startCapture() {
        base.startCapture();
    }

    public void stop() {
        base.stop();
    }

    public void clearCapturedUrls() {
        base.clearCapturedUrls();
    }

    // Common getters and setters
    public void setListener(Listeners listener) {
        this.listener = listener;
        if (base.siteInterceptor != null && listener != null) {
            base.siteInterceptor.addListener(listener);
        }
    }

    // Common utility methods (static)
    public static String extractString(String patternTxt, String text) {
        Pattern pattern = Pattern.compile(patternTxt);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String extractLastPart(String url) {
        Matcher matcher = Pattern.compile("^(https?)://([^/]+)/([^/?]+)(\\?.*)?$").matcher(url);

        if (matcher.matches()) {
            return matcher.group(3);
        }
        return null;
    }

    private enum SiteLoaderTypes {
        New, Old
    }

    public interface Listeners {
        void onRequestIntercepted(String url, String method);
        void onError(String errorMessage);
        void onMainSiteLoaded(String htmlContent);
        void onLog(String logMessage);
    }

    // Inner classes and interfaces
    private static class LegacyTLSSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        public LegacyTLSSocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return enableTLSOnSocket(delegate.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return enableTLSOnSocket(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return enableTLSOnSocket(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return enableTLSOnSocket(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return enableTLSOnSocket(delegate.createSocket(address, port, localAddress, localPort));
        }

        private Socket enableTLSOnSocket(Socket socket) {
            if (socket instanceof SSLSocket) {
                try {
                    ((SSLSocket) socket).setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.1", "TLSv1"});
                } catch (Exception e) {
                    // Ignore - use default protocols
                }
            }
            return socket;
        }
    }

    private static class SiteInterceptor implements Interceptor {
        private Set<Listeners> listeners = new CopyOnWriteArraySet<>();
        private Set<String> capturedUrls = new HashSet<>();

        @NotNull
        @Override
        public Response intercept(@NotNull Chain chain) throws IOException {
            Request originalRequest = chain.request();
            String url = originalRequest.url().toString();
            String method = originalRequest.method();

            Request modifiedRequest = originalRequest.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Cache-Control", "no-cache")
                    .removeHeader("Accept-Encoding")
                    .build();

            for (Listeners listener : listeners) {
                listener.onRequestIntercepted(url, method);
            }

            return chain.proceed(modifiedRequest);
        }

        public void addListener(Listeners listener) {
            listeners.add(listener);
        }

        public void removeListener(Listeners listener) {
            listeners.remove(listener);
        }

        public void clearCapturedUrls() {
            capturedUrls.clear();
        }
    }

    public abstract class Loader {
        // Common fields
        protected OkHttpClient client;
        protected SiteInterceptor siteInterceptor;

        public Loader() {
            setupHttpClient();
        }

        // Abstract methods - to be implemented by subclasses
        protected abstract void setupHttpClient();
        protected abstract boolean isAndroid4_2OrBelow(); // For new implementation only

        // Common protected methods
        protected void log(String message) {
            if (listener != null) {
                listener.onLog(message);
            }
        }

        protected void notifyError(String errorMessage) {
            if (listener != null) {
                listener.onError(errorMessage);
            }
        }

        // Common SSL setup methods
        protected OkHttpClient.Builder setupConscryptSSL(OkHttpClient.Builder builder) {
            try {
                log("Setting up Conscrypt for TLS 1.2/1.3 support...");

                Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1);

                SSLContext sslContext = SSLContext.getInstance("TLSv1.2", "Conscrypt");

                if (SslConfigManager.isEnableInsecureSSL()) {
                    TrustManager[] trustAllCerts = new TrustManager[]{
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

                    sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                    builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
                    builder.hostnameVerifier((hostname, session) -> true);
                    log("Conscrypt with insecure SSL enabled");
                } else {
                    sslContext.init(null, null, null);
                    builder.sslSocketFactory(sslContext.getSocketFactory(), getSystemTrustManager());
                    log("Conscrypt with system trust store enabled");
                }

                ConnectionSpec conscryptSpec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
                        .cipherSuites(
                                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                                CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                                CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256
                        )
                        .build();

                builder.connectionSpecs(Arrays.asList(conscryptSpec, ConnectionSpec.CLEARTEXT));

            } catch (Exception e) {
                log("Failed to setup Conscrypt: " + e.getMessage());
                if (SslConfigManager.isEnableInsecureSSL()) {
                    builder = enableInsecureSSL(builder);
                }
            }
            return builder;
        }

        protected OkHttpClient.Builder setupCompatibleSSL(OkHttpClient.Builder builder) {
            try {
                ConnectionSpec legacySpec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
                        .build();

                builder.connectionSpecs(Arrays.asList(legacySpec, ConnectionSpec.CLEARTEXT));

            } catch (Exception e) {
                log("Error setting up compatible SSL: " + e.getMessage());
            }
            return builder;
        }

        protected OkHttpClient.Builder enableInsecureSSL(OkHttpClient.Builder builder) {
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

                SSLContext sslContext;
                try {
                    sslContext = SSLContext.getInstance("TLSv1.2");
                } catch (NoSuchAlgorithmException e) {
                    try {
                        sslContext = SSLContext.getInstance("TLSv1");
                    } catch (NoSuchAlgorithmException e2) {
                        sslContext = SSLContext.getInstance("TLS");
                    }
                }

                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
                builder.hostnameVerifier((hostname, session) -> true);

            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                log("Failed to enable insecure SSL: " + e.getMessage());
            }
            return builder;
        }

        protected X509TrustManager getSystemTrustManager() {
            try {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init((KeyStore) null);
                TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
                if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                    throw new IllegalStateException("Unexpected default trust managers: " + Arrays.toString(trustManagers));
                }
                return (X509TrustManager) trustManagers[0];
            } catch (Exception e) {
                log("Failed to get system trust manager: " + e.getMessage());
                return null;
            }
        }

        // Common HTTP methods
        public void startCapture() {
            if (mainSiteUrl == null || mainSiteUrl.isEmpty()) {
                notifyError("Main site URL is null or empty");
                return;
            }

            log("Starting capture for: " + mainSiteUrl);
            log("SSL Mode: " + (SslConfigManager.isUseConscrypt() ? "Conscrypt" : "System") +
                    (SslConfigManager.isEnableInsecureSSL() ? " (Insecure)" : " (Secure)"));

            attemptRequest(mainSiteUrl);
        }

        protected void attemptRequest(String url) {
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    String errorMsg = "Failed to load " + url + ": " + e.getMessage();
                    log("Request failed: " + errorMsg);

                    // Switch loader synchronously and retry
                    switchLoaderAndRetry(url, errorMsg);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        logResponseDetails(response);

                        ResponseBody body = response.body();
                        if (body == null) {
                            notifyError("Empty response body received with status: " + response.code());
                            return;
                        }

                        MediaType contentType = body.contentType();
                        String contentEncoding = response.header("Content-Encoding");
                        long contentLength = body.contentLength();

                        log("Content-Type: " + contentType);
                        log("Content-Encoding: " + contentEncoding);
                        log("Content-Length: " + contentLength);

                        String htmlContent;
                        byte[] responseBytes = body.bytes();

                        log("First 20 bytes (hex): " + bytesToHex(responseBytes, 20));

                        if (contentEncoding != null && contentEncoding.contains("gzip")) {
                            log("Decompressing gzip content...");
                            htmlContent = decompressGzip(responseBytes);
                        } else {
                            htmlContent = decodeResponseBytes(responseBytes, contentType);
                        }

                        log("Site loaded with status: " + response.code() +
                                ". HTML length: " + htmlContent.length() + " characters");

                        if (htmlContent == null || htmlContent.trim().isEmpty() || htmlContent.length() < 10) {
                            notifyError("Empty response body received with status: " + response.code());
                        } else {
                            if (listener != null) {
                                listener.onMainSiteLoaded(htmlContent);
                            }
                        }
                    } catch (IOException e) {
                        notifyError("Failed to read response body: " + e.getMessage());
                    } finally {
                        response.close();
                    }
                }
            });
        }

        private void switchLoaderAndRetry(String failedUrl, String errorMsg) {
            // Stop current loader
            stop();

            // Create new loader of the opposite type
            Loader newLoader;
            if (type == SiteLoaderTypes.New) {
                log("Switching from NewLoader to OldLoader due to request failure");
                newLoader = new OldLoader();
                type = SiteLoaderTypes.Old;
            } else {
                log("Switching from OldLoader to NewLoader due to request failure");
                newLoader = new NewLoader();
                type = SiteLoaderTypes.New;
            }

            // Replace the base reference
            base = newLoader;

            // Retry with HTTP if it was HTTPS
            if (failedUrl.startsWith("https://")) {
                String httpUrl = failedUrl.replace("https://", "http://");
                log("Trying HTTP instead with new loader: " + httpUrl);
                base.attemptRequest(httpUrl);
            } else {
                notifyError(errorMsg);
            }
        }

        protected void logResponseDetails(Response response) {
            if (listener != null) {
                Headers headers = response.headers();
                listener.onLog("Response Code: " + response.code());
                listener.onLog("Response Message: " + response.message());
                listener.onLog("Protocol: " + response.protocol());

                for (int i = 0; i < headers.size(); i++) {
                    listener.onLog("Header: " + headers.name(i) + ": " + headers.value(i));
                }
            }
        }

        protected String decompressGzip(byte[] compressedBytes) throws IOException {
            try (ByteArrayInputStream bis = new ByteArrayInputStream(compressedBytes);
                 GZIPInputStream gis = new GZIPInputStream(bis);
                 java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {

                byte[] buffer = new byte[1024];
                int len;
                while ((len = gis.read(buffer)) > 0) {
                    bos.write(buffer, 0, len);
                }
                return bos.toString("UTF-8");
            } catch (IOException e) {
                log("GZIP decompression failed, trying raw bytes: " + e.getMessage());
                return new String(compressedBytes, "UTF-8");
            }
        }

        protected String decodeResponseBytes(byte[] bytes, MediaType contentType) {
            String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

            if (isGarbled(content)) {
                log("Content appears garbled, trying alternative encodings...");

                String[] encodings = {
                        "ISO-8859-1",
                        "Windows-1252",
                        "UTF-16",
                        "US-ASCII"
                };

                for (String encoding : encodings) {
                    try {
                        String testContent = new String(bytes, encoding);
                        if (!isGarbled(testContent)) {
                            log("Successfully decoded with encoding: " + encoding);
                            return testContent;
                        }
                    } catch (Exception e) {
                        log("Failed to decode with " + encoding + ": " + e.getMessage());
                    }
                }

                log("All encoding attempts failed, returning raw UTF-8 interpretation");
            }

            return content;
        }

        protected boolean isGarbled(String text) {
            if (text == null || text.isEmpty()) return false;

            int nonPrintable = 0;
            int totalChecked = Math.min(text.length(), 500);

            for (int i = 0; i < totalChecked; i++) {
                char c = text.charAt(i);
                if (c < 32 && c != 9 && c != 10 && c != 13) {
                    nonPrintable++;
                }
            }

            boolean garbled = (nonPrintable * 100 / totalChecked) > 30;
            if (garbled) {
                log("Detected garbled text: " + nonPrintable + " non-printable chars in first " + totalChecked + " characters");
            }

            return garbled;
        }

        protected String bytesToHex(byte[] bytes, int length) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(bytes.length, length); i++) {
                sb.append(String.format("%02x ", bytes[i]));
            }
            return sb.toString().trim();
        }

        public void stop() {
            if (client != null) {
                client.dispatcher().executorService().shutdown();
                client.connectionPool().evictAll();
            }
        }

        public void clearCapturedUrls() {
            if (siteInterceptor != null) {
                siteInterceptor.clearCapturedUrls();
            }
        }
    }

    // Old implementation
    public class OldLoader extends Loader {

        public OldLoader() {
            super();
        }

        @Override
        protected void setupHttpClient() {
            siteInterceptor = new SiteInterceptor();

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .addInterceptor(siteInterceptor)
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .retryOnConnectionFailure(true);

            // Add cookie management
            builder.cookieJar(new CookieJar() {
                private final HashMap<HttpUrl, List<Cookie>> cookieStore = new HashMap<>();

                @Override
                public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                    cookieStore.put(url, cookies);
                }

                @Override
                public List<Cookie> loadForRequest(HttpUrl url) {
                    List<Cookie> cookies = cookieStore.get(url);
                    return cookies != null ? cookies : new ArrayList<Cookie>();
                }
            });

            // Setup SSL based on configuration
            if (SslConfigManager.isUseConscrypt()) {
                builder = setupConscryptSSL(builder);
            } else if (SslConfigManager.isEnableInsecureSSL()) {
                builder = enableInsecureSSL(builder);
            } else {
                builder = setupCompatibleSSL(builder);
            }

            client = builder.build();

            if (listener != null)
                siteInterceptor.addListener(listener);
        }

        @Override
        protected boolean isAndroid4_2OrBelow() {
            // Old implementation doesn't need this, return false
            return false;
        }
    }

    // New implementation
    public class NewLoader extends Loader {

        public NewLoader() {
            super();
        }

        @Override
        protected boolean isAndroid4_2OrBelow() {
            return Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1;
        }

        @Override
        protected void setupHttpClient() {
            siteInterceptor = new SiteInterceptor();

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .addInterceptor(siteInterceptor)
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .retryOnConnectionFailure(true);

            // Add cookie management
            builder.cookieJar(new CookieJar() {
                private final HashMap<HttpUrl, List<Cookie>> cookieStore = new HashMap<>();

                @Override
                public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                    cookieStore.put(url, cookies);
                }

                @Override
                public List<Cookie> loadForRequest(HttpUrl url) {
                    List<Cookie> cookies = cookieStore.get(url);
                    return cookies != null ? cookies : new ArrayList<Cookie>();
                }
            });

            // Auto-detect Android 4.2 and use legacy SSL instead of Conscrypt
            boolean isAndroid4_2OrBelow = isAndroid4_2OrBelow();

            if (isAndroid4_2OrBelow) {
                log("Android 4.2 detected - using legacy SSL support instead of Conscrypt");
                builder = setupLegacyAndroidSupport(builder);
            } else {
                if (SslConfigManager.isUseConscrypt()) {
                    builder = setupConscryptSSL(builder);
                } else if (SslConfigManager.isEnableInsecureSSL()) {
                    builder = enableInsecureSSL(builder);
                } else {
                    builder = setupCompatibleSSL(builder);
                }
            }

            client = builder.build();

            if (listener != null)
                siteInterceptor.addListener(listener);
        }

        private OkHttpClient.Builder setupLegacyAndroidSupport(OkHttpClient.Builder builder) {
            try {
                log("Adding legacy Android SSL support for Android 4.2 compatibility...");

                TrustManager[] trustAllCerts = new TrustManager[]{
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

                SSLContext sslContext;
                try {
                    sslContext = SSLContext.getInstance("TLSv1.2");
                } catch (NoSuchAlgorithmException e) {
                    try {
                        sslContext = SSLContext.getInstance("TLSv1");
                    } catch (NoSuchAlgorithmException e2) {
                        sslContext = SSLContext.getInstance("TLS");
                    }
                }

                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

                builder.sslSocketFactory(new LegacyTLSSocketFactory(sslContext.getSocketFactory()),
                        (X509TrustManager) trustAllCerts[0]);

                ConnectionSpec legacySpec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_0, TlsVersion.TLS_1_1, TlsVersion.TLS_1_2)
                        .supportsTlsExtensions(true)
                        .build();

                builder.connectionSpecs(Arrays.asList(legacySpec, ConnectionSpec.CLEARTEXT));
                log("Legacy Android support added");

            } catch (Exception e) {
                log("Failed to add legacy Android support: " + e.getMessage());
            }
            return builder;
        }
    }
}
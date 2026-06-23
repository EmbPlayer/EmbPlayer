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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import app.services.BaseServer;
import app.tools.Generators.Requirements.Piped.VideoResolution;
import app.tools.Players.all.Players;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * MediaProxyServlet
 *
 * - Preserves Range semantics so seekable media (mp4, etc.) work.
 * - Adds per-request timeouts, retries with exponential backoff + jitter.
 * - Streams using a configurable buffer size.
 * - Copies only relevant headers back to the client and skips hop-by-hop headers.
 */
public class MediaProxyServlet extends HttpServletAdvanced {

    public static String directory_;
    public static class MediaElements{
        private final int videoOnly;
        private BufferSize bufferSize = BufferSize.KB_4;
        private String source;

        public MediaElements(boolean videoOnly){
            if(videoOnly)
                this.videoOnly = 1;
            else
                this.videoOnly = 0;
        }

        public void bufferSize(BufferSize bufferSize){
            this.bufferSize = bufferSize;
        }

        private String correctData(){
            StringBuilder sb = new StringBuilder();
            sb.append("http://")
                    .append(BaseServer.getIP())
                    .append(":")
                    .append(BaseServer.getPort())
                    .append(directory_)
                    .append("?type=")
                    .append(videoOnly);

            return sb.toString();
        }
    }

    public final static MediaElements videoOnly = new MediaElements(true);
    public final static MediaElements combinedOrAudio = new MediaElements(false);

    private final OkHttpClient httpClient;
    private final int timeoutSeconds = 6;
    private final int maxRetries = 3;

    public MediaProxyServlet(OkHttpClient httpClient,String directory) {
        this.httpClient = httpClient;
        directory_ = directory;
    }

    // Helper: build MediaProxy URL with optional bufferSize enum (stores only enum ordinal)
    // Build MediaProxy URL with a videoOnly flag (no bufferSize parameter)
    public static String getPure(String url, boolean videoOnly) {
        if(videoOnly) {
            MediaProxyServlet.videoOnly.source = url;
            return MediaProxyServlet.videoOnly.correctData();
        }

        MediaProxyServlet.combinedOrAudio.source = url;
        return MediaProxyServlet.combinedOrAudio.correctData();
    }


    public static BufferSize bufferSizeForAudio(){
        return Players.isLive() ? BufferSize.KB_4 : BufferSize.KB_8;
    }

    public static BufferSize bufferSizeForVideo(){
        if(Players.isLive())
            return bufferSizeOrdinalForResolution(Players.resolutionLive(),true);

        return bufferSizeOrdinalForResolution(Players.resolution(),false);
    }

    // Map VideoResolution to BufferSize ordinal; live uses smaller buffers to reduce latency
    public static BufferSize bufferSizeOrdinalForResolution(VideoResolution res, boolean isLive) {
        if (res == null) return BufferSize.KB_16; // safe fallback

        switch (res) {
            case _1080P:
                return isLive ? BufferSize.KB_32 : BufferSize.KB_64;
            case _720P:
                return isLive ? BufferSize.KB_16 : BufferSize.KB_32;
            case _480P:
                return isLive ? BufferSize.KB_8  : BufferSize.KB_16;
            case _360P:
                return isLive ? BufferSize.KB_4  : BufferSize.KB_8;
            case _240P:
                return isLive ? BufferSize.KB_2  : BufferSize.KB_4;
            case _144P:
                return isLive ? BufferSize.KB_1  : BufferSize.KB_2;
            case Audio:
            default:
                // Audio: smaller for live, slightly larger for on-demand
                return bufferSizeForAudio();
        }
    }

    @Override
    protected void doGetAdvanced(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        int realMediaUrl;
        MediaElements selectedMedia;

        try{
            realMediaUrl = Integer.parseInt(request.getParameter("type"));
        }
        catch (Exception e){
            realMediaUrl = 0;
        }

        if(realMediaUrl == 1)
            selectedMedia = videoOnly;
        else
            selectedMedia = combinedOrAudio;

        // Build a client with the specific timeout for this request
        OkHttpClient retryClient = httpClient.newBuilder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();

        // Build OkHttp Request, only include Range if the client sent one
        Request.Builder reqBuilder = new Request.Builder()
                .url(selectedMedia.source)
                .header("User-Agent", "Mozilla/5.0");

        String clientRange = request.getHeader("Range");
        if (clientRange != null && !clientRange.isEmpty()) {
            reqBuilder.header("Range", clientRange);
        }

        // If the incoming request is HEAD, forward as HEAD so clients can probe metadata
        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            reqBuilder.head();
        }

        Request okHttpRequest = reqBuilder.build();

        Response okHttpResponse = null;
        int attempt = 0;

        // Retry loop with exponential backoff
        while (attempt <= maxRetries) {
            try {
                okHttpResponse = retryClient.newCall(okHttpRequest).execute();

                // Accept 200 (OK) or 206 (Partial Content) as valid
                if (okHttpResponse.isSuccessful() || okHttpResponse.code() == 206) {
                    break;
                } else {
                    // Non-success code (e.g., 404, 500) — close and retry
                    okHttpResponse.close();
                    throw new IOException("Origin returned code: " + okHttpResponse.code());
                }
            } catch (IOException e) {
                attempt++;
                if (attempt > maxRetries) {
                    response.sendError(HttpServletResponse.SC_GATEWAY_TIMEOUT, "Retries exhausted: " + e.getMessage());
                    return;
                }

                long backoffSec = (long) Math.pow(2.0, attempt);
                long jitterMs = (long) (Math.random() * 500.0);
                long waitMs = (backoffSec * 1000L) + jitterMs;

                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        // Stream response back to client, preserving seeking headers
        try (Response finalResponse = okHttpResponse) {
            if (finalResponse == null) {
                response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "No response from origin");
                return;
            }

            int originCode = finalResponse.code();
            response.setStatus(originCode);

            // Copy only relevant headers and skip hop-by-hop headers
            Headers responseHeaders = finalResponse.headers();
            boolean acceptRangesPresent = false;
            for (int i = 0; i < responseHeaders.size(); i++) {
                String name = responseHeaders.name(i);
                String value = responseHeaders.value(i);

                // Skip hop-by-hop headers
                if ("Connection".equalsIgnoreCase(name)
                        || "Keep-Alive".equalsIgnoreCase(name)
                        || "Proxy-Authenticate".equalsIgnoreCase(name)
                        || "Proxy-Authorization".equalsIgnoreCase(name)
                        || "TE".equalsIgnoreCase(name)
                        || "Trailer".equalsIgnoreCase(name)
                        || "Transfer-Encoding".equalsIgnoreCase(name)
                        || "Upgrade".equalsIgnoreCase(name)) {
                    continue;
                }

                // Only copy headers that matter for media seeking and content
                if ("Content-Type".equalsIgnoreCase(name)
                        || "Content-Length".equalsIgnoreCase(name)
                        || "Content-Range".equalsIgnoreCase(name)
                        || "Accept-Ranges".equalsIgnoreCase(name)
                        || "Content-Disposition".equalsIgnoreCase(name)
                        || "ETag".equalsIgnoreCase(name)
                        || "Last-Modified".equalsIgnoreCase(name)) {

                    response.setHeader(name, value);

                    if ("Accept-Ranges".equalsIgnoreCase(name)) {
                        acceptRangesPresent = true;
                    }
                }
            }

            // If origin returned 200 OK and didn't advertise Accept-Ranges, add it
            if (originCode == HttpServletResponse.SC_OK && !acceptRangesPresent) {
                response.setHeader("Accept-Ranges", "bytes");
            }

            // If this was a HEAD request, do not stream body
            if ("HEAD".equalsIgnoreCase(request.getMethod())) {
                return;
            }

            if (finalResponse.body() != null) {
                try (InputStream inputStream = finalResponse.body().byteStream();
                     OutputStream outputStream = response.getOutputStream()) {

                    // later: allocate buffer using the effective size
                    byte[] buffer = new byte[selectedMedia.bufferSize.getBytes()];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.flush();
                } catch (IOException e) {
                    // Client disconnected or sought to a new position; ignore
                }
            }
        }
    }

    // BufferSize enum (compact, stable ordinals)
    public enum BufferSize {
        KB_1(1024),
        KB_2(2 * 1024),
        KB_4(4 * 1024),
        KB_8(8 * 1024),
        KB_16(16 * 1024),
        KB_32(32 * 1024),
        KB_64(64 * 1024);

        private final int bytes;

        BufferSize(int bytes) { this.bytes = bytes; }
        public int getBytes() { return bytes; }

        public static BufferSize fromIndex(Integer index) {
            if (index == null) return null;
            BufferSize[] vals = values();
            if (index < 0 || index >= vals.length) return null;
            return vals[index];
        }
    }
}

/*
public class MediaProxyServlet extends HttpServletAdvanced {

    private final OkHttpClient httpClient;

    public MediaProxyServlet(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public static String getPure(String url)
    {
        return "http://"+ BaseServer.getIP()+":"+
                BaseServer.getPort()+
                "/MediaProxy?url=" + android.net.Uri.encode(url);
    }

    @Override
    protected void doGetAdvanced(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String realMediaUrl = request.getParameter("url");
        if (realMediaUrl == null || realMediaUrl.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // 1. Build OkHttp Request, copying the Range header crucial for video seeking
        Request.Builder okHttpRequestBuilder = new Request.Builder().url(realMediaUrl);
        String rangeHeader = request.getHeader("Range");
        if (rangeHeader != null) {
            okHttpRequestBuilder.addHeader("Range", rangeHeader);
        }

        // 2. Execute the OkHttp request
        try (Response okHttpResponse = httpClient.newCall(okHttpRequestBuilder.build()).execute()) {

            response.setStatus(okHttpResponse.code());

            // 3. Copy response headers back to the MediaPlayer
            Headers responseHeaders = okHttpResponse.headers();
            for (int i = 0; i < responseHeaders.size(); i++) {
                response.addHeader(responseHeaders.name(i), responseHeaders.value(i));
            }

            // 4. Pipe the data stream
            if (okHttpResponse.body() != null) {
                try (InputStream inputStream = okHttpResponse.body().byteStream();
                     OutputStream outputStream = response.getOutputStream()) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        outputStream.flush();
                    }
                } catch (IOException e) {
                    // This catch is necessary. The MediaPlayer will frequently drop the
                    // connection when seeking or closing, causing a Broken Pipe exception.
                    // This is expected behavior and can be ignored.
                }
            }
        }
    }
}*/

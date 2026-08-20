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

import android.util.Log;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import ssl.SiteLoader;
import server.web.ErrorCodeApp;

import static app.tools.DisposableTools.waitMS;

public class HlsSelector {

    private final static int[] RESOLUTIONS = new int[] {1,144, 240, 360, 480, 720, 1080, 1440, 2160};

    private static String result;

    public static int getRes(int index) {
        return RESOLUTIONS[index];
    }

    /**
     * Overloaded method for backwards compatibility with code using String resolutions.
     * Parses the String (e.g., "1080p", "720", "1") into an int and calls the main method.
     *
     * @param playlistUrl The absolute URL of the master M3U8 playlist
     * @param resolution The target maximum resolution as a String (e.g., "1080p")
     * @return The entire modified M3U8 content with absolute URLs
     */
    public static String getCorrectUrl(String playlistUrl, String resolution) {
        int targetHeight;
        try {
            targetHeight = extractDigitsAsInt(resolution);
        } catch (Exception e) {
            // If string parsing fails, set to max so nothing gets filtered and playback continues safely
            targetHeight = Integer.MAX_VALUE;
        }
        return getCorrectUrl(playlistUrl, targetHeight);
    }

    /**
     * Fetches the M3U8 playlist and filters out streams to keep strictly the selected resolution.
     *
     * @param playlistUrl The absolute URL of the master M3U8 playlist
     * @param resolution The target maximum resolution height (e.g., 1080, or 1 for audio/lowest)
     * @return The entire modified M3U8 content with absolute URLs
     */
    public static String getCorrectUrl(String playlistUrl, int resolution) {
        result = null;

        SiteLoader loadM3 = new SiteLoader(playlistUrl, new SiteLoader.Listeners() {
            @Override
            public void onRequestIntercepted(String url, String method) {
            }

            @Override
            public void onError(String errorMsg) {
                StaticFunctions.getInfo(errorMsg);
            }

            @Override
            public void onMainSiteLoaded(String m3u8Content) {
                try {
                    // Filter the entire M3U8 playlist directly using the integer maxResolution
                    result = filterM3U8ByResolution(playlistUrl, resolution, m3u8Content);
                } catch (Exception e) {
                    StaticFunctions.onErrorSave("onMainSiteLoaded", e);
                }
            }

            @Override
            public void onLog(String logMessage) {
            }
        });

        try {
            loadM3.startCapture();

            int i = 0;
            while (i < 10) {
                waitMS(1000);
                if (result != null) break;
                i++;
            }

            if (i == 10) {
                return null;
            }

            return result;

        } finally {
            loadM3.stop();
        }
    }

    /**
     * Extracts and returns only the digits from the input string.
     */
    public static String extractDigits(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return input.replaceAll("\\D", "");
    }

    /**
     * Extracts digits and returns them as an integer.
     */
    public static int extractDigitsAsInt(String input) {
        String digits = extractDigits(input);
        if (digits.isEmpty()) {
            throw new NumberFormatException("No digits found in the input string.");
        }
        return Integer.parseInt(digits);
    }

    /**
     * Processes an M3U8 string to strictly keep ONLY the requested resolution.
     * If there is only one stream available, it keeps it without filtering.
     */
    private static String filterM3U8ByResolution(String playlistUrl, int targetHeight, String m3u8Content) {
        try {
            String[] lines = m3u8Content.split("\n");

            // Pass 1: Find all heights and count streams
            List<Integer> heights = new ArrayList<>();
            int streamCount = 0;
            int videoStreamCount = 0;
            int minHeight = Integer.MAX_VALUE;

            for (String line : lines) {
                if (line.trim().startsWith("#EXT-X-STREAM-INF")) {
                    streamCount++;
                    int h = parseStreamInfHeight(line);
                    heights.add(h);

                    if (h < minHeight) {
                        minHeight = h;
                    }

                    // Count only streams that have a valid video resolution (h > 0)
                    if (h > 0) {
                        videoStreamCount++;
                    }
                }
            }

            boolean canFilter = false;
            int bestHeight = -1;

            // Safety Check: Filter ONLY if there is MORE than 1 video stream,
            // OR if the user explicitly wants the lowest/audio (targetHeight == 1) and there are multiple streams total.
            if ((videoStreamCount > 1 || (targetHeight == 1 && streamCount > 1)) && minHeight != Integer.MAX_VALUE) {
                canFilter = true;

                if (targetHeight == 1) {
                    // User requested absolute lowest (e.g., audio)
                    bestHeight = minHeight;
                } else {
                    // Find the exact target height or the closest one below it
                    int maxValid = -1;
                    for (int h : heights) {
                        if (h <= targetHeight && h > maxValid) {
                            maxValid = h;
                        }
                    }

                    if (maxValid != -1) {
                        bestHeight = maxValid;
                    } else {
                        // Edge case: All streams are higher than requested. Keep the lowest available to avoid breaking.
                        bestHeight = minHeight;
                    }
                }
            }

            URL baseUrl = new URL(playlistUrl);
            StringBuilder sb = new StringBuilder();
            boolean skipNextUrl = false;
            boolean resolveNextUrl = false;

            for (String originalLine : lines) {
                String line = originalLine;
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                String trimmed = line.trim();

                if (trimmed.isEmpty()) {
                    sb.append(originalLine).append("\n");
                    continue;
                }

                if (skipNextUrl) {
                    if (!trimmed.startsWith("#")) {
                        // This is the relative URL line matching the skipped stream variant.
                        skipNextUrl = false;
                    }
                    continue;
                }

                if (trimmed.startsWith("#EXT-X-STREAM-INF")) {
                    int h = parseStreamInfHeight(trimmed);
                    boolean shouldSkip = false;

                    if (canFilter) {
                        // Strictly keep ONLY streams that match the chosen optimal height
                        if (h != bestHeight) {
                            shouldSkip = true;
                        }
                    }

                    if (shouldSkip) {
                        skipNextUrl = true;
                        continue;
                    } else {
                        sb.append(originalLine).append("\n");
                        resolveNextUrl = true;
                        continue;
                    }
                }

                if (resolveNextUrl && !trimmed.startsWith("#")) {
                    // This is a stream variant URL we are keeping. Resolve to absolute URL.
                    try {
                        URL resolved = new URL(baseUrl, trimmed);
                        sb.append(resolved.toString()).append("\n");
                    } catch (Exception e) {
                        sb.append(originalLine).append("\n");
                    }
                    resolveNextUrl = false;
                    continue;
                }

                // Make relative URLs in master tags (e.g., Audio tracks) absolute
                if (trimmed.startsWith("#EXT-X-") && trimmed.contains("URI=\"")) {
                    int uriStart = trimmed.indexOf("URI=\"") + 5;
                    int uriEnd = trimmed.indexOf("\"", uriStart);
                    if (uriStart > 4 && uriEnd > uriStart) {
                        String relUri = trimmed.substring(uriStart, uriEnd);
                        try {
                            URL resolved = new URL(baseUrl, relUri);
                            String newTag = originalLine.substring(0, uriStart) + resolved.toString() + originalLine.substring(uriEnd);
                            sb.append(newTag).append("\n");
                            continue;
                        } catch (Exception e) {
                            // Silently ignore format issue and append the original
                        }
                    }
                }

                // Append any other line (like `#EXTM3U` header, comments, etc)
                sb.append(originalLine).append("\n");
            }

            return sb.toString();

        } catch (Exception e) {
            StaticFunctions.onErrorSave("filterM3U8ByResolution", e);
            return m3u8Content; // Safety fallback: return raw content so playback still works
        }
    }

    /**
     * Parses the height from an #EXT-X-STREAM-INF tag either using RESOLUTION= or NAME= attributes.
     */
    private static int parseStreamInfHeight(String line) {
        // Try RESOLUTION=1920x1080
        int resIndex = line.indexOf("RESOLUTION=");
        if (resIndex != -1) {
            int xIndex = line.indexOf('x', resIndex);
            if (xIndex != -1) {
                int commaIndex = line.indexOf(',', xIndex);
                if (commaIndex == -1) commaIndex = line.length();
                try {
                    return Integer.parseInt(line.substring(xIndex + 1, commaIndex).trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        // Try NAME="1080p"
        int nameIndex = line.indexOf("NAME=\"");
        if (nameIndex != -1) {
            int quoteIndex = line.indexOf('"', nameIndex + 6);
            if (quoteIndex != -1) {
                String nameVal = line.substring(nameIndex + 6, quoteIndex);
                try {
                    return extractDigitsAsInt(nameVal);
                } catch (Exception ignored) {}
            }
        }

        // Returns 0 if it's an audio stream with no RESOLUTION= or recognizable NAME=
        return 0;
    }
}
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ssl.SiteLoader;
import server.web.ErrorCodeApp;

import static app.tools.DisposableTools.waitMS;

public class HlsSelector {
    /**
     * Adds a live stream source with automatic quality selection.
     *
     * @param url                 Master stream URL (e.g., M3U8 with variants)
     * @param preferHigherQuality true = highest quality within constraints, false = lowest
     * @param minHeight           Minimum resolution height (0 = no minimum)
     * @param maxHeight           Maximum resolution height (0 = no maximum)
     */

    private final static int[] RESOLUTIONS = new int[] {1,144, 240, 360, 480, 720, 1080, 1440, 2160};

    private static String result;

    public static int getRes(int index)
    {
        return RESOLUTIONS[index];
    }

    public static String getCorrectUrl(String playlistUrl, String resolution) {
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
                    // Parse the M3U8 content to find the correct resolution
                    String correctedUrl = parseM3U8ForResolution(playlistUrl, resolution, m3u8Content);
                    result = correctedUrl;
                } catch (Exception e) {
                    StaticFunctions.onErrorSave("onMainSiteLoaded",e);
                }
            }

            @Override
            public void onLog(String logMessage) {
            }
        });

        try {
            // Start loading the M3U8 playlist
            loadM3.startCapture();

            int i = 0;
            while (i<10)
            {
                waitMS(1000);
                if(result!=null)
                    break;
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

    public static String addLiveSource(String url, boolean preferHigherQuality,
                                       int minHeight, int maxHeight) {
        Map<String, String> variants = getStreamMap(url);
        if (variants == null || variants.isEmpty()) {
            Log.e("StreamSelector", "No variants found in stream map");
            return null;
        }

        // Parse all available resolutions
        List<Integer> availableHeights = new ArrayList<>();
        Map<Integer, String> heightToUrlMap = new HashMap<>();

        for (Map.Entry<String, String> entry : variants.entrySet()) {
            int height = extractHeight(entry.getKey());
            if (height > 0) {
                availableHeights.add(height);
                heightToUrlMap.put(height, entry.getValue());
            }
        }

        if (availableHeights.isEmpty()) {
            Log.e("StreamSelector", "No valid resolutions found");
            return null;
        }

        // Sort resolutions ascending
        Collections.sort(availableHeights);

        // Filter by constraints
        List<Integer> validHeights = new ArrayList<>();
        for (int height : availableHeights) {
            boolean meetsMin = (minHeight <= 0) || (height >= minHeight);
            boolean meetsMax = (maxHeight <= 0) || (height <= maxHeight);
            if (meetsMin && meetsMax) {
                validHeights.add(height);
            }
        }

        // Select appropriate quality
        int selectedHeight;
        if (validHeights.isEmpty()) {
            Log.w("StreamSelector", "No streams match constraints - using fallback");
            selectedHeight = preferHigherQuality ?
                    availableHeights.get(availableHeights.size() - 1) : // Highest available
                    availableHeights.get(0);                            // Lowest available
        } else {
            selectedHeight = preferHigherQuality ?
                    validHeights.get(validHeights.size() - 1) :        // Highest in constraints
                    validHeights.get(0);                                // Lowest in constraints
        }

        // Add the selected stream
        String selectedStream = heightToUrlMap.get(selectedHeight);
        if (selectedStream != null) {/*
            Log.i("StreamSelector", "Selected " + selectedHeight + "p stream (" +
                    (preferHigherQuality ? "highest" : "lowest") + " quality)");*/
            return selectedStream;
        } else {
            Log.e("StreamSelector", "Selected stream not found in variants");
        }
        return null;
    }
    /**
     * Extracts and returns only the digits from the input string.
     * @param input The string from which digits are to be extracted.
     * @return A string containing only the digits from the input.
     */
    public static String extractDigits(String input) {
        if (input == null || input.isEmpty()) {
            return ""; // Handle null or empty input
        }
        return input.replaceAll("\\D", ""); // Remove all non-digit characters
    }

    /**
     * Extracts digits and returns them as an integer.
     * @param input The string from which digits are to be extracted.
     * @return The numeric value of the extracted digits.
     * @throws NumberFormatException If no digits are found (resulting in an empty string).
     */
    public static int extractDigitsAsInt(String input) {
        String digits = extractDigits(input);
        if (digits.isEmpty()) {
            throw new NumberFormatException("No digits found in the input string.");
        }
        return Integer.parseInt(digits);
    }

    private static String parseM3U8ForResolution(String playlistUrl, String resolution, String m3u8Content) {
        try {
            BufferedReader reader = new BufferedReader(new StringReader(m3u8Content));
            String line;
            int lineNumber = 0;
            int streamInfoCount = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                if (line.contains("#EXT-X-STREAM-INF")) {
                    streamInfoCount++;

                    // Check multiple possible resolution formats
                    boolean nameWithP = line.contains("NAME=\"" + resolution + "p\"") ||
                            line.contains("NAME=" + resolution + "p");
                    boolean nameWithoutP = line.contains("NAME=\"" + resolution + "\"") ||
                            line.contains("NAME=" + resolution);
                    boolean resolutionFormat = line.contains("RESOLUTION=") && line.contains("x" + resolution);

                    boolean matchesResolution = nameWithP || nameWithoutP || resolutionFormat;

                    if (matchesResolution) {

                        // Read the next line which should contain the URL
                        String urlLine = reader.readLine();
                        lineNumber++;

                        if (urlLine != null) {
                            urlLine = urlLine.trim();

                            // Skip any comment lines and get the actual URL
                            while (urlLine != null &&
                                    (urlLine.isEmpty() || urlLine.startsWith("#"))) {
                                urlLine = reader.readLine();
                                lineNumber++;
                                if (urlLine != null) {
                                    urlLine = urlLine.trim();
                                }
                            }

                            if (urlLine != null && !urlLine.isEmpty()) {
                                // Resolve relative URL to absolute URL
                                URL baseUrl = new URL(playlistUrl);
                                URL resolvedUrl = new URL(baseUrl, urlLine);
                                String resolvedUrlString = resolvedUrl.toString();

                                return resolvedUrlString;
                            }
                        }
                    }
                }
            }

            return null;

        } catch (Exception e) {
            StaticFunctions.onErrorSave("parseM3U8ForResolution",e);
            return null;
        }
    }

    private static Map<String, String> getStreamMap(String m3u8Url) {
        Map<String, String> streamMap = new LinkedHashMap<>();
        try {
            URL url = new URL(m3u8Url);
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));

            String baseUrl = m3u8Url.substring(0, m3u8Url.lastIndexOf("/") + 1);
            String line;
            String lastResolution = null;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    // Extract resolution
                    int resIndex = line.indexOf("RESOLUTION=");
                    if (resIndex != -1) {
                        int commaIndex = line.indexOf(",", resIndex);
                        if (commaIndex == -1) commaIndex = line.length();
                        lastResolution = line.substring(resIndex + 11, commaIndex).trim();
                    } else {
                        lastResolution = "unknown";
                    }
                } else if (!line.startsWith("#") && line.endsWith(".m3u8")) {
                    String fullUrl = line.startsWith("http") ? line : baseUrl + line;
                    if (lastResolution != null) {
                        streamMap.put(lastResolution, fullUrl);
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            StaticFunctions.onErrorSave("getStreamMap",e);
        }

        return streamMap;
    }

    // Helper method to extract height from resolution string
    private static int extractHeight(String resolution) {
        try {
            if (resolution.contains("x")) {
                return Integer.parseInt(resolution.split("x")[1]);  // "1920x1080" → 1080
            } else if (resolution.endsWith("p")) {
                return Integer.parseInt(resolution.substring(0, resolution.indexOf("p"))); // "720p" → 720
            } else if (resolution.endsWith("kbps")) {
                return Integer.parseInt(resolution.substring(0, resolution.indexOf("kbps"))); // "3000kbps" → 3000
            }
        } catch (Exception e) {
            StaticFunctions.onErrorSave("extractHeight",e);
        }
        return 0;
    }
}

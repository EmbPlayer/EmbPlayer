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

package app.tools.Generators.Requirements;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static app.tools.StaticFunctions.onErrorSave;

/**
 * Extracts the `expire` timestamp from YouTube video URLs.
 * Works with:
 * - Query-based URLs (?expire=123456789)
 * - Path-based URLs (/expire/123456789/)
 * - Hybrid cases
 */
public class YouTubeExpireExtractor {
    // Regex to match both query and path formats
    private static final Pattern EXPIRE_PATTERN =
            Pattern.compile("(?:/expire/|\\?expire=)(\\d+)");

    /**
     * Extracts the `expire` value from a YouTube URL.
     * @param url The YouTube video URL
     * @return The expire timestamp as a String, or null if not found
     */
    public static String extractExpire(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        // First try regex (fast for most cases)
        Matcher matcher = EXPIRE_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Fallback to URI parsing (more robust for edge cases)
        try {
            URI uri = new URI(url);

            // Check query params (?expire=...)
            String query = uri.getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("expire=")) {
                        return param.substring(7); // "expire=".length()
                    }
                }
            }

            // Check path segments (/expire/.../)
            String path = uri.getPath();
            if (path != null) {
                String[] segments = path.split("/");
                for (int i = 0; i < segments.length - 1; i++) {
                    if ("expire".equals(segments[i])) {
                        return segments[i + 1];
                    }
                }
            }
        } catch (URISyntaxException e) {
            onErrorSave("YoutubeExpireExtractor-URISyntaxException",e);
            //System.err.println("Invalid URL: " + e.getMessage());
        }

        return null; // Not found
    }
}
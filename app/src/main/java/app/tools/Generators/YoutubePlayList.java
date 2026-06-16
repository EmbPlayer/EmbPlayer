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

package app.tools.Generators;

import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.tools.DisposableTools;
import app.tools.Generators.Requirements.Piped.ContentId;
import app.tools.Generators.Requirements.Piped.NewPipeEx;
import app.tools.Recyclable;
import app.tools.StaticFunctions;
import server.tools.VideoSettings;

import app.tools.Connection;
import app.tools.Players.all.Listeners;
import server.web.ErrorCodeApp;
import static app.tools.DisposableTools.killAll;
import static app.tools.DisposableTools.ioThreadPoolScheduler;

public class YoutubePlayList {

    private static final Recyclable.ListDisposable adder = new Recyclable.ListDisposable(YoutubePlayList.class);
    // Thread safety and state management
    private static final Object lock = new Object();
    private static final AtomicBoolean isLoading = new AtomicBoolean(false);
    private static final AtomicBoolean isDisposed = new AtomicBoolean(false);

    public static VideoSettings videoSettings;
    public static Listeners listeners;
    public static boolean hardware;
    public static List<StreamInfoItem> streamInfoItem;
    public static boolean changed;
    public static int current;
    public static List<YoutubeGenerator> youtubeGenerators;

    private static String cleanUrl;
    private static ContentId savedId;
    private static int countLoaded;
    private static PlaylistExtractor playlistExtractor;
    private static boolean loaded;
    private static List<Integer> isAdded;
    private static Set<String> loadedVideoUrls; // Track loaded videos by URL

    // Add this field to your class to store the next page
    private static Page secondPage;
    private static int changeCount = 0;

    public static YoutubeGenerator createCollection(String videoURL, VideoSettings VideoSettings, Listeners Listeners, boolean Loop, boolean Hardware) throws ExtractionException, IOException {
        synchronized (lock) {
            isDisposed.set(false);
            isAdded = new ArrayList<>();
            loadedVideoUrls = new HashSet<>(); // Initialize URL tracker
            // Extract index BEFORE cleaning the URL
            int urlIndex = extractIndexFromUrl(videoURL);

            // Use clean URL for extractor
            cleanUrl = cleanUrlForExtractor(videoURL);
            playlistExtractor = ServiceList.YouTube.getPlaylistExtractor(cleanUrl);
            playlistExtractor.fetchPage();

            youtubeGenerators = new ArrayList<>();
            streamInfoItem = playlistExtractor.getInitialPage().getItems();

            listeners = Listeners;
            videoSettings = VideoSettings;
            hardware = Hardware;

            // Set current index (convert 1-based URL index to 0-based list index)
            current = Math.max(0, urlIndex - 1); // Subtract 1, but ensure not negative

            if (current >= streamInfoItem.size()) {
                current = 0; // Reset to 0 if index is out of bounds
            }

            // Load the video at the specified index
            if (!streamInfoItem.isEmpty()) {
                String targetUrl = streamInfoItem.get(current).getUrl();
                YoutubeGenerator targetGenerator = new YoutubeGenerator(
                        targetUrl, videoSettings, listeners, hardware
                );

                // Try to generate link for the target index
                if (targetGenerator.generateLink(1)) {
                    youtubeGenerators.add(targetGenerator);
                    targetGenerator.reloadContent();
                    updateToDefault(targetGenerator);
                    isAdded.add(current);
                    loadedVideoUrls.add(targetUrl); // Track the URL
                    return targetGenerator;
                } else {
                    // Fallback: try other items if target fails
                    for (int i = 0; i < streamInfoItem.size(); i++) {
                        if (i == current) continue; // Skip the one that failed

                        String fallbackUrl = streamInfoItem.get(i).getUrl();
                        YoutubeGenerator fallbackGenerator = new YoutubeGenerator(
                                fallbackUrl, videoSettings, listeners, hardware
                        );

                        if (fallbackGenerator.generateLink(1)) {
                            current = i; // Update current index
                            youtubeGenerators.add(fallbackGenerator);
                            fallbackGenerator.reloadContent();
                            updateToDefault(fallbackGenerator);
                            isAdded.add(current);
                            loadedVideoUrls.add(fallbackUrl); // Track the URL
                            return fallbackGenerator;
                        }
                    }
                }
            }
            return null;
        }
    }

    public static String youtubePlaylistId()
    {
        if(savedId==null)
            savedId = NewPipeEx.getContentId(cleanUrl);

        return savedId.getId();
    }

    // Improved index extraction with multiple patterns
    public static int extractIndexFromUrl(String url) {
        try {
            // Decode URL first
            String decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8.name());

            // Pattern 1: &index=XX or ?index=XX
            Pattern pattern1 = Pattern.compile("[&?]index=(\\d+)");
            Matcher matcher1 = pattern1.matcher(decodedUrl);
            if (matcher1.find()) {
                return Integer.parseInt(matcher1.group(1));
            }

            // Pattern 2: &t=XX (time parameter, sometimes used as index)
            Pattern pattern2 = Pattern.compile("[&?]t=(\\d+)");
            Matcher matcher2 = pattern2.matcher(decodedUrl);
            if (matcher2.find()) {
                return Integer.parseInt(matcher2.group(1));
            }

            // Pattern 3: watch?v=XXX&index=XX (common YouTube pattern)
            Pattern pattern3 = Pattern.compile("watch\\?v=[^&]+&index=(\\d+)");
            Matcher matcher3 = pattern3.matcher(decodedUrl);
            if (matcher3.find()) {
                return Integer.parseInt(matcher3.group(1));
            }

            return 1; // Default to first item (1-based) if no index found
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
    }
    // Fixed isAdded method
    public static boolean isAdded(int index) {
        synchronized (lock) {
            if (isAdded == null) return false;
            return isAdded.contains(index);
        }
    }

    // Improved background loading with index awareness
    public static void loadInitialVideosInBackground(String videoURL, int count) {
        synchronized (lock) {
            if (streamInfoItem == null || streamInfoItem.size() <= 1 ||
                    isDisposed.get() || isLoading.get()) {
                return;
            }

            isLoading.set(true);

            adder.add(()->{
                synchronized (lock) {
                    try {
                        // Pre-load items around the current index for better UX
                        int startIndex = Math.max(0, current - 1);
                        int endIndex = Math.min(current + count + 1, streamInfoItem.size());

                        for (int i = startIndex; i < endIndex; i++) {
                            if (isDisposed.get()) break;
                            if (isAdded(i)) continue; // Skip already added items

                            String videoUrl = streamInfoItem.get(i).getUrl();
                            if (loadedVideoUrls.contains(videoUrl)) continue; // Double-check by URL

                            YoutubeGenerator generator = new YoutubeGenerator(
                                    videoUrl, videoSettings, listeners, hardware
                            );

                            if (generator.generateLink(3)) {
                                youtubeGenerators.add(generator);
                                generator.reloadContent();
                                isAdded.add(i);
                                loadedVideoUrls.add(videoUrl);

                                // Prioritize loading of current item
                                if (i == current) {
                                    updateToDefault(generator);
                                }
                            }

                            try {
                                Thread.sleep(30);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    } catch (Exception e) {
                        // Silent handling
                    } finally {
                        isLoading.set(false);
                    }
                }
            },ioThreadPoolScheduler,"LoadInitialVideosInBackground");
        }
    }

    // Get current item based on URL index
    public static YoutubeGenerator getCurrentItem() {
        synchronized (lock) {
            if (isDisposed.get() || youtubeGenerators == null ||
                    current < 0 || current >= youtubeGenerators.size()) {
                return null;
            }
            return youtubeGenerators.get(current);
        }
    }

    // Get item by URL index (1-based from URL)
    public static YoutubeGenerator getItemByUrlIndex(int urlIndex) {
        synchronized (lock) {
            int listIndex = Math.max(0, urlIndex - 1);
            if (isDisposed.get() || youtubeGenerators == null ||
                    listIndex < 0 || listIndex >= youtubeGenerators.size()) {
                return null;
            }
            return youtubeGenerators.get(listIndex);
        }
    }

    // Navigate to specific index
    public static boolean navigateToIndex(int index) {
        synchronized (lock) {
            if (isDisposed.get() || streamInfoItem == null ||
                    index < 0 || index >= streamInfoItem.size()) {
                return false;
            }

            current = index;

            // Ensure the item at current index is loaded
            if (!isAdded(current)) {
                AddSingleElement(current);
            }

            return true;
        }
    }

    // Navigate to URL index (1-based)
    public static boolean navigateToUrlIndex(int urlIndex) {
        return navigateToIndex(Math.max(0, urlIndex - 1));
    }

    public static void addElements(int count) throws ExtractionException, IOException {
        synchronized (lock) {
            if (isDisposed.get() || isLoading.get() || streamInfoItem == null) {
                return;
            }

            isLoading.set(true);

            adder.add(()->{
                synchronized (lock) {
                    try {
                        // Find the first unloaded index
                        int startIndex = 0;
                        for (int i = 0; i < streamInfoItem.size(); i++) {
                            String videoUrl = streamInfoItem.get(i).getUrl();
                            if (!isAdded(i) && !loadedVideoUrls.contains(videoUrl)) {
                                startIndex = i;
                                break;
                            }
                        }

                        int itemsToLoad = Math.min(count, streamInfoItem.size() - startIndex);

                        for (int i = startIndex; i < startIndex + itemsToLoad; i++) {
                            if (i >= streamInfoItem.size() || isDisposed.get()) {
                                break;
                            }

                            String videoUrl = streamInfoItem.get(i).getUrl();

                            // Double-check for duplicates using URL
                            if (loadedVideoUrls.contains(videoUrl)) {
                                continue;
                            }

                            YoutubeGenerator generator = new YoutubeGenerator(
                                    videoUrl, videoSettings, listeners, hardware
                            );

                            if (generator.generateLink(10)) {
                                youtubeGenerators.add(generator);
                                generator.reloadContent();
                                loadedVideoUrls.add(videoUrl); // Track URL
                                isAdded.add(i); // Track index

                                // Only update and start session for current item
                                if (i == current) {
                                    updateToDefault(generator);
                                }

                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        }

                        countLoaded++;
                        loaded = true;
                    } catch (Exception e) {
                        // Handle exceptions
                    } finally {
                        isLoading.set(false);
                    }
                }
            },ioThreadPoolScheduler,"AddElements-YoutubePlayList");
        }
    }

    // Add this method for progressive loading
    public static void loadOnDemand(int index) {
        synchronized (lock) {
            if (isDisposed.get() || isLoading.get() || index >= youtubeGenerators.size()) {
                return;
            }

            YoutubeGenerator generator = youtubeGenerators.get(index);
            if (generator != null && !generator.IsLoaded()) {
                // Load this specific generator on demand
                isLoading.set(true);

                adder.add(()->{
                    try {
                        generator.reloadContent();
                        updateToDefault(generator);
                    } catch (Exception e) {
                        // Handle exception
                    } finally {
                        isLoading.set(false);
                    }
                },ioThreadPoolScheduler,"LoadOnDemand");
            }
        }
    }

    public static void reload() throws ExtractionException, IOException {
        synchronized (lock) {
            if (isDisposed.get() || isLoading.get()) return;

            Page nextPage = null;

            if (playlistExtractor.getInitialPage().getNextPage() != null) {
                nextPage = playlistExtractor.getInitialPage().getNextPage();
            } else if (secondPage != null) {
                nextPage = secondPage;
            } else {
                return; // No more pages to load
            }

            try {
                ListExtractor.InfoItemsPage<StreamInfoItem> nextPageItems = playlistExtractor.getPage(nextPage);
                if (nextPageItems != null && nextPageItems.getItems() != null) {

                    // CHECK FOR DUPLICATES BEFORE ADDING TO streamInfoItem
                    for (StreamInfoItem newItem : nextPageItems.getItems()) {
                        boolean isDuplicate = false;
                        for (StreamInfoItem existingItem : streamInfoItem) {
                            if (existingItem.getUrl().equals(newItem.getUrl())) {
                                isDuplicate = true;
                                break;
                            }
                        }
                        if (!isDuplicate) {
                            streamInfoItem.add(newItem);
                        }
                    }

                    secondPage = nextPageItems.getNextPage();

                    if (!isLoading.get()) {
                        addElements(10); // Load only 10 at a time to avoid overload
                    }
                }
            } catch (Exception e) {
                // Handle reload errors gracefully
            }
        }
    }

    // Update hasMoreVideos method
    public static boolean hasMoreVideos() throws ExtractionException, IOException {
        synchronized (lock) {
            if (isDisposed.get()) return false;

            // Check if initial page has next page
            if (playlistExtractor.getInitialPage().getNextPage() != null) {
                return true;
            }

            // Check if we have a stored next page
            if (secondPage != null) {
                return true;
            }

            // Check if there are more videos in streamInfoItem than loaded
            if (streamInfoItem != null && youtubeGenerators != null &&
                    streamInfoItem.size() > youtubeGenerators.size()) {
                return true;
            }

            return false;
        }
    }

    public static void loadNextVideo() throws ExtractionException, IOException {
        synchronized (lock) {
            if (isDisposed.get() || isLoading.get()) return;

            int currentSize = youtubeGenerators.size();
            if (currentSize < streamInfoItem.size()) {
                AddSingleElement(currentSize); // Load just one more video
            }
        }
    }

    public static void tryLoad() throws ExtractionException, IOException {
        synchronized (lock) {
            if (isDisposed.get() || loaded || isLoading.get()) return;

            int currentSize = youtubeGenerators.size();
            if (currentSize < streamInfoItem.size()) {
                addElements(streamInfoItem.size() - currentSize);
            }
        }
    }

    public static void updateToDefault(YoutubeGenerator generator) throws ExtractionException, IOException {
        if (generator == null) return;

        generator.onErrorUpdate(() -> {
            try {

                if(Connection.ifNotHaveConnectionWaitInfinityTime(()->isDisposed.get()) && generator.mediaIsExpired())
                {
                    generator.generateContent();
                    generator.reloadContent();
                }

                generator.mediaError.started = false;
            } catch (ExtractionException | IOException e) {
                if (!isDisposed.get()) {
                    generator.getOnError().call();
                }
            }
            return true;
        });

        // Reset any other necessary states
        generator.mediaError.started = false;
    }

    // Add a separate method for soft cleanup (without killing generators)
    public static Runnable softDispose() {
        synchronized (lock) {
            isDisposed.set(true);

            if (isAdded != null) {
                isAdded.clear();
                isAdded = null;
            }

            if (loadedVideoUrls != null) {
                loadedVideoUrls.clear();
                loadedVideoUrls = null;
            }

            current = 0;
            countLoaded = 0;
            loaded = false;
            isLoading.set(false);
            changeCount = 0;

            List<YoutubeGenerator> generatorsOld;
            boolean run = false;

            if (youtubeGenerators != null) {
                generatorsOld = youtubeGenerators;
                run = true;
                youtubeGenerators = null;
            } else {
                generatorsOld = null;
            }

            if(run)
                return ()->{

                    adder.clear();

                    if(generatorsOld != null){

                        killAll(generatorsOld.stream()
                                .map(YoutubeGenerator::makeKillAnotherWhere)
                                .toArray(DisposableTools.WaitDisposable[]::new));

                        generatorsOld.clear();
                    }
                };

            return null;
        }
    }

    // Add a method to check if disposed and reset if needed
    public static void ensureNotDisposed() {
        synchronized (lock) {
            if (isDisposed.get()) {
                // Reset disposal state but don't recreate resources
                isDisposed.set(false);
            }
        }
    }

    public static YoutubeGenerator getFirst(int tryCount, int mills) {
        for (int i = 0; i < tryCount; i++) {
            try {
                Thread.sleep(mills);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }

            synchronized (lock) {
                if (isDisposed.get() || youtubeGenerators == null || youtubeGenerators.isEmpty()) {
                    continue;
                }

                YoutubeGenerator y = youtubeGenerators.get(0);
                if (y != null && y.IsLoaded()) {
                    return y;
                }
            }
        }
        return null;
    }

    public static int getSize() {
        synchronized (lock) {
            return youtubeGenerators != null ? youtubeGenerators.size() : 0;
        }
    }

    public static YoutubeGenerator getGenerator(int index) {
        synchronized (lock) {
            if (isDisposed.get() || youtubeGenerators == null || index < 0 || index >= youtubeGenerators.size()) {
                return null;
            }
            return youtubeGenerators.get(index);
        }
    }

    public static boolean isLoaded() {
        synchronized (lock) {
            return countLoaded >= 2 && loaded;
        }
    }

    public static boolean isLoading() {
        return isLoading.get();
    }

    public static boolean isDisposed() {
        return isDisposed.get();
    }

    public static int getTotalVideosCount() {
        synchronized (lock) {
            return streamInfoItem != null ? streamInfoItem.size() : 0;
        }
    }

    public static boolean isLoadingMoreVideos() {
        return isLoading.get();
    }

    public static void incrementChangeCount() {
        changeCount++;
    }

    public static void resetChangeCount() {
        changeCount = 0;
    }

    public static int getChangeCount() {
        return changeCount;
    }

    public static void loadMultipleVideos(int startIndex, int count) {
        synchronized (lock) {
            if (isDisposed.get() || isLoading.get() || streamInfoItem == null) return;

            int endIndex = Math.min(startIndex + count, streamInfoItem.size());
            if (startIndex >= endIndex) return;

            isLoading.set(true);

            adder.add(()->{
                synchronized (lock) {
                    try {
                        for (int i = startIndex; i < endIndex; i++) {
                            if (isDisposed.get()) break;

                            String videoUrl = streamInfoItem.get(i).getUrl();
                            if (!loadedVideoUrls.contains(videoUrl)) {
                                YoutubeGenerator generator = new YoutubeGenerator(
                                        videoUrl, videoSettings, listeners, hardware
                                );

                                if (generator.generateLink(5)) {
                                    youtubeGenerators.add(generator);
                                    generator.reloadContent();
                                    loadedVideoUrls.add(videoUrl);
                                    isAdded.add(i);

                                    // Small delay to avoid overwhelming
                                    try {
                                        Thread.sleep(50);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Silent fail
                    } finally {
                        isLoading.set(false);
                    }
                }
            },ioThreadPoolScheduler,"LoadMultipleVideos");
        }
    }

    public static void preloadVideos(int count) {
        synchronized (lock) {
            int currentLoaded = getLoadedVideosCount();
            int totalAvailable = getTotalVideosCount();

            if (currentLoaded < totalAvailable && !isLoading.get()) {
                int videosToLoad = Math.min(count, totalAvailable - currentLoaded);
                loadMultipleVideos(currentLoaded, videosToLoad);
            }
        }
    }

    public static int getIndexByGenerator(YoutubeGenerator target) {
        synchronized (lock) {
            if (youtubeGenerators == null || target == null) return -1;

            for (int i = 0; i < youtubeGenerators.size(); i++) {
                if (youtubeGenerators.get(i) == target) {
                    return i;
                }
            }
            return -1;
        }
    }

    public static int getLoadedVideosCount() {
        synchronized (lock) {
            if (youtubeGenerators == null) return 0;

            int loadedCount = 0;
            for (YoutubeGenerator generator : youtubeGenerators) {
                if (generator != null && generator.IsLoaded()) {
                    loadedCount++;
                }
            }
            return loadedCount;
        }
    }public static void AddSingleElement(int index) {
        synchronized (lock) {
            if (isDisposed.get() || isLoading.get() || streamInfoItem == null ||
                    index >= streamInfoItem.size()) {
                return;
            }

            // Check if already exists at this index
            if (index < youtubeGenerators.size() && youtubeGenerators.get(index) != null) {
                YoutubeGenerator existing = youtubeGenerators.get(index);
                if (existing.IsLoaded()) {
                    return; // Already loaded
                }
            }

            isLoading.set(true);

            adder.add(()->{
                synchronized (lock) {
                    try {
                        if (index < streamInfoItem.size() && !isDisposed.get()) {
                            String videoUrl = streamInfoItem.get(index).getUrl();

                            // CHECK FOR DUPLICATE by URL
                            if (!loadedVideoUrls.contains(videoUrl)) {
                                YoutubeGenerator generator = new YoutubeGenerator(
                                        videoUrl, videoSettings, listeners, hardware
                                );

                                if (generator.generateLink(3)) {
                                    // Ensure the list is big enough
                                    while (youtubeGenerators.size() <= index) {
                                        youtubeGenerators.add(null);
                                    }

                                    // Add or replace at the correct index
                                    youtubeGenerators.set(index, generator);
                                    generator.reloadContent();
                                    loadedVideoUrls.add(videoUrl);
                                    isAdded.add(index);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Handle exceptions during loading
                    } finally {
                        isLoading.set(false);
                    }
                }
            },ioThreadPoolScheduler,"AddSingleElement");
        }
    }

    // Clean URL by removing index parameter for the extractor
    private static String cleanUrlForExtractor(String url) {
        try {
            String decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8.name());
            // Remove index parameter
            String cleanUrl = decodedUrl
                    .replaceAll("[&?]index=\\d+", "")
                    .replaceAll("&t=\\d+", "")
                    .replaceAll("\\?&", "?")
                    .replaceAll("&+", "&")
                    .replaceAll("\\?$", "");

            // Ensure URL ends properly
            if (cleanUrl.contains("?") && !cleanUrl.contains("=")) {
                cleanUrl = cleanUrl.replace("?", "");
            }

            return cleanUrl;
        } catch (Exception e) {
            return url; // Return original if cleaning fails
        }
    }

    private static boolean containsVideo(String videoUrl) {
        synchronized (lock) {
            return loadedVideoUrls != null && loadedVideoUrls.contains(videoUrl);
        }
    }

    private static void handleMediaErrors(YoutubeGenerator generator) {
        if (generator == null) return;

        generator.mediaError.started = true;
        generator.mediaErrorRun();

        // Wait for error handling to complete with timeout
        long startTime = StaticFunctions.getCurrentTimeAsSeconds();
        while (generator.mediaError.started &&
                (StaticFunctions.getCurrentTimeAsSeconds() - startTime) < 4500) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (isDisposed.get()) {
                break;
            }
        }
    }
}
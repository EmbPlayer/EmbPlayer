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
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
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
import java.util.concurrent.ConcurrentHashMap;
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

    /** Playlist indexes that can never play (deleted, private, blocked, age-restricted...). */
    private static final Set<Integer> unavailable = ConcurrentHashMap.newKeySet();
    /** Playlist indexes that a background load is working on right now. */
    private static final Set<Integer> inFlight = ConcurrentHashMap.newKeySet();

    public static YoutubeGenerator createCollection(String videoURL, VideoSettings VideoSettings, Listeners Listeners, boolean Loop, boolean Hardware) throws ExtractionException, IOException {
        synchronized (lock) {
            isDisposed.set(false);
            isAdded = new ArrayList<>();
            loadedVideoUrls = new HashSet<>();
            unavailable.clear();
            inFlight.clear();

            int urlIndex = extractIndexFromUrl(videoURL);

            cleanUrl = cleanUrlForExtractor(videoURL);
            playlistExtractor = ServiceList.YouTube.getPlaylistExtractor(cleanUrl);
            playlistExtractor.fetchPage();

            youtubeGenerators = new ArrayList<>();
            streamInfoItem = playlistExtractor.getInitialPage().getItems();

            listeners = Listeners;
            videoSettings = VideoSettings;
            hardware = Hardware;

            // 1-based URL index -> 0-based list index
            current = Math.max(0, urlIndex - 1);

            final int total = streamInfoItem.size();
            if (current >= total) {
                current = 0;
            }

            // Try the requested video first, then the following ones, until one plays.
            for (int hop = 0; hop < total; hop++) {
                int i = (current + hop) % total;
                String url = streamInfoItem.get(i).getUrl();

                YoutubeGenerator g = new YoutubeGenerator(url, videoSettings, listeners, hardware);
                try {
                    if (!g.generateLink(1)) continue;
                    g.reloadContent();
                } catch (ExtractionException e) {
                    if (isPermanentFailure(e)) {
                        unavailable.add(i);
                        continue;                    // dead: try the next one
                    }
                    throw e;                         // temporary: let the caller decide
                }

                current = i;
                putGenerator(i, g);                  // stored at its real index
                updateToDefault(g);
                return g;
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

    /**
     * True when retrying can never help. "Confirm you're not a bot" is also a
     * ContentNotAvailableException in some NewPipe versions, but it is temporary, so it is
     * excluded (checked by class name, so it compiles whether or not your version has it).
     */
    public static boolean isPermanentFailure(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof ContentNotAvailableException) {
                return !c.getClass().getSimpleName().contains("SignInConfirmNotBot");
            }
        }
        return false;
    }

    public static void markUnavailable(int index) {
        unavailable.add(index);
    }

    public static boolean isUnavailable(int index) {
        return unavailable.contains(index);
    }

    /** True while a background load for this playlist index is running. */
    public static boolean isLoadingIndex(int index) {
        return inFlight.contains(index);
    }

    /** Stores the generator at its real playlist index (pads with null). */
    public static void putGenerator(int index, YoutubeGenerator g) {
        synchronized (lock) {
            if (g == null || index < 0 || youtubeGenerators == null) return;

            while (youtubeGenerators.size() <= index) {
                youtubeGenerators.add(null);
            }
            youtubeGenerators.set(index, g);

            if (isAdded != null && !isAdded.contains(index)) isAdded.add(index);
            if (loadedVideoUrls != null) loadedVideoUrls.add(g.getVideoUrl());
        }
    }

    /** First playlist index that is neither loaded nor known dead, or -1. */
    private static int firstPendingIndex() {
        synchronized (lock) {
            if (streamInfoItem == null || isAdded == null) return -1;
            for (int i = 0; i < streamInfoItem.size(); i++) {
                if (!isAdded.contains(i) && !unavailable.contains(i)) return i;
            }
            return -1;
        }
    }

    /** How many playlist items are neither loaded nor known dead. */
    private static int pendingCount() {
        synchronized (lock) {
            if (streamInfoItem == null || isAdded == null) return 0;
            return Math.max(0, streamInfoItem.size() - isAdded.size() - unavailable.size());
        }
    }

    /**
     * Loads one playlist item. The network request runs OUTSIDE {@code lock}.
     *
     * @return true when the item is loaded afterwards.
     */
    private static boolean loadInto(int index, int linkTries) {
        final String url;
        synchronized (lock) {
            if (isDisposed.get() || streamInfoItem == null || youtubeGenerators == null
                    || index < 0 || index >= streamInfoItem.size()) {
                return false;
            }
            if (unavailable.contains(index)) return false;

            YoutubeGenerator existing = index < youtubeGenerators.size() ? youtubeGenerators.get(index) : null;
            if (existing != null && existing.IsLoaded()) return true;

            url = streamInfoItem.get(index).getUrl();
        }

        YoutubeGenerator g = new YoutubeGenerator(url, videoSettings, listeners, hardware);
        try {
            if (!g.generateLink(linkTries)) return false;
            g.reloadContent();                       // network, no lock held
        } catch (Exception e) {
            if (isPermanentFailure(e)) {
                unavailable.add(index);              // remember it, never ask again
            } else {
                StaticFunctions.onErrorSave("YoutubePlayList-LoadInto idx=" + index, e);
            }
            return false;
        }

        synchronized (lock) {
            if (isDisposed.get() || youtubeGenerators == null) {
                g.kill();                            // playlist was closed while we were loading
                return false;
            }

            YoutubeGenerator existing = index < youtubeGenerators.size() ? youtubeGenerators.get(index) : null;
            if (existing != null && existing.IsLoaded()) {
                g.kill();                            // someone else finished first
                return true;
            }

            putGenerator(index, g);
            if (index == current) updateToDefault(g);
            return true;
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
        }

        adder.add(() -> {
            try {
                int total = getTotalVideosCount();
                int startIndex = Math.max(0, current - 1);
                int endIndex = Math.min(current + count + 1, total);

                for (int i = startIndex; i < endIndex; i++) {
                    if (isDisposed.get()) break;
                    if (isAdded(i) || isUnavailable(i)) continue;

                    loadInto(i, 3);

                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                StaticFunctions.onErrorSave("YoutubePlayList-LoadInitial", e);
            } finally {
                isLoading.set(false);
            }
        }, () -> isLoading.set(false), () -> isLoading.set(false), ioThreadPoolScheduler, "LoadInitialVideosInBackground");
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
        }

        adder.add(() -> {
            try {
                int startIndex = firstPendingIndex();

                if (startIndex >= 0) {
                    int endIndex = Math.min(startIndex + count, getTotalVideosCount());

                    for (int i = startIndex; i < endIndex; i++) {
                        if (isDisposed.get()) break;
                        if (isAdded(i) || isUnavailable(i)) continue;

                        loadInto(i, 10);        // a dead video no longer aborts the whole batch

                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }

                synchronized (lock) {
                    countLoaded++;
                    loaded = true;
                }
            } catch (Exception e) {
                StaticFunctions.onErrorSave("YoutubePlayList-AddElements", e);
            } finally {
                isLoading.set(false);
            }
        }, () -> isLoading.set(false), () -> isLoading.set(false), ioThreadPoolScheduler, "AddElements-YoutubePlayList");
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

            if (playlistExtractor.getInitialPage().getNextPage() != null) {
                return true;
            }

            if (secondPage != null) {
                return true;
            }

            // items we know about that are neither loaded nor dead
            return pendingCount() > 0;
        }
    }

    public static void loadNextVideo() throws ExtractionException, IOException {
        synchronized (lock) {
            if (isDisposed.get() || isLoading.get()) return;

            int next = firstPendingIndex();
            if (next >= 0) {
                AddSingleElement(next);              // load just one more video
            }
        }
    }

    public static void tryLoad() throws ExtractionException, IOException {
        synchronized (lock) {
            if (isDisposed.get() || loaded || isLoading.get()) return;

            int pending = pendingCount();
            if (pending > 0) {
                addElements(pending);
            }
        }
    }

    public static void updateToDefault(YoutubeGenerator generator) {
        if (generator == null) return;

        generator.onErrorUpdate(() -> {

            if(isDisposed.get())
            {
                generator.mediaError.started = false;
                return true;
            }

            Connection.ifNotHaveConnectionWaitInfinityTime(adder,()->isDisposed.get(),()->{
                try {
                    if(!isDisposed.get()&&generator.mediaIsExpired())
                    {
                        generator.generateContent();
                        generator.reloadContent();
                    }

                    generator.mediaError.started = false;
                } catch (ExtractionException | IOException e) {
                    if (!isDisposed.get()) {
                        try {
                            generator.getOnError().call();
                        } catch (Exception ex) {}
                    }
                }
            });
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

            unavailable.clear();
            inFlight.clear();

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
                return () -> adder.clear(()->{
                    if(generatorsOld != null){

                        killAll(generatorsOld.stream()
                                .map(YoutubeGenerator::makeKillAnotherWhere)
                                .toArray(DisposableTools.WaitDisposable[]::new));

                        generatorsOld.clear();
                    }
                },DisposableTools.forCleaner);

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

    /** Returns the generator of the current item if loaded, otherwise the first loaded one. */
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

                if (current >= 0 && current < youtubeGenerators.size()) {
                    YoutubeGenerator c = youtubeGenerators.get(current);
                    if (c != null && c.IsLoaded()) return c;
                }

                for (YoutubeGenerator y : youtubeGenerators) {
                    if (y != null && y.IsLoaded()) return y;
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
        final int endIndex;
        synchronized (lock) {
            if (isDisposed.get() || isLoading.get() || streamInfoItem == null) return;

            endIndex = Math.min(startIndex + count, streamInfoItem.size());
            if (startIndex >= endIndex) return;

            isLoading.set(true);
        }

        adder.add(() -> {
            try {
                for (int i = startIndex; i < endIndex; i++) {
                    if (isDisposed.get()) break;
                    if (isAdded(i) || isUnavailable(i)) continue;

                    loadInto(i, 5);

                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                StaticFunctions.onErrorSave("YoutubePlayList-LoadMultiple", e);
            } finally {
                isLoading.set(false);
            }
        }, () -> isLoading.set(false), () -> isLoading.set(false), ioThreadPoolScheduler, "LoadMultipleVideos");
    }

    public static void preloadVideos(int count) {
        synchronized (lock) {
            int start = firstPendingIndex();
            int totalAvailable = getTotalVideosCount();

            if (start >= 0 && !isLoading.get()) {
                loadMultipleVideos(start, Math.min(count, totalAvailable - start));
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
    }

    public static void AddSingleElement(int index) {
        synchronized (lock) {
            if (isDisposed.get() || streamInfoItem == null || youtubeGenerators == null
                    || index < 0 || index >= streamInfoItem.size()) {
                return;
            }
            if (unavailable.contains(index)) return;

            YoutubeGenerator existing = index < youtubeGenerators.size() ? youtubeGenerators.get(index) : null;
            if (existing != null && existing.IsLoaded()) return;

            if (!inFlight.add(index)) return;       // already loading this index
        }

        adder.add(() -> {
            try {
                loadInto(index, 3);
            } finally {
                inFlight.remove(index);
            }
        }, () -> inFlight.remove(index), () -> inFlight.remove(index), ioThreadPoolScheduler, "AddSingleElement");
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
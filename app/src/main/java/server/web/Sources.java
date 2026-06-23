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

package server.web;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import app.services.BaseServer;
import app.tools.Generators.Requirements.Piped.VideoResolution;
import app.tools.JsonDownloader;
import app.tools.JsonFileLister;
import app.tools.SData;
import app.tools.StaticFunctions;
import server.JettyServer;
import server.tools.HttpServletAdvanced;

import static app.Main.getContext;
import static app.tools.DisposableTools.forServer;

public class Sources extends HttpServletAdvanced {
    private static Sources sourcesController;

    private final String directory;
    private List<String> jsonNames;
    private Source[] allSources;

    public Sources(ServletContextHandler handler, String directory, Resolutions resolutions) {
        sourcesController = this;
        this.directory = directory;

        // Load jsonNames immediately from persistent storage
        handler.addServlet(new JettyServer.CustomServletHolder(this), "/" + directory);
        loadJsonNames();

        // Only load URLs if we have saved JSON names
        String loadData = SData.getString(SData.Data.SavedJsonNames);

        if (loadData != null && !loadData.isEmpty()) {
            try {
                loadURLS(handler,resolutions);
                SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Sources initialized with: " + loadData + "]");
            } catch (Exception e) {
                SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[LoadURLS error: " + e.getMessage() + "]");
            }
        } else {
            SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[No saved JSON names found]");
        }
    }

    public static Sources getSourcesController(){
        return sourcesController;
    }

    public static Resolutions getAsResolution(VideoResolution resolution)
    {
        switch (resolution)
        {
            case _480P:
                return Resolutions.R480P;
            case _720P:
                return Resolutions.R720P;
            case _1080P:
                return Resolutions.R1080P;
        }
        return Resolutions.R360P;
    }

    public Source getSource(String sourceName)
    {
        for(Source source : allSources)
        {
            if(source.isCorrectFile(sourceName))
            {
                return source;
            }
        }
        return null;
    }

    public void updateSources(Resolutions resolutions) throws JSONException {
        if(allSources != null)
            for(Source source : allSources)
            {
                source.updateSelectedUrls(resolutions);
            }
    }

    private void clean(String ISOLanguages){
        SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Starting ReCreate with: " + ISOLanguages + "]");

        JsonFileLister fileLister = new JsonFileLister(getContext());
        fileLister.deleteAllFiles(directory);
    }

    private void restart(Runnable uiStopper){
        SData.setString(SData.Data.SavedJsonNames, null);
        uiStopper.run();
        BaseServer.restart();
    }

    public void recreate(Runnable uiStopper)
    {
        recreate(SData.getString(SData.Data.SavedJsonNames),uiStopper);
    }

    public void recreate(String ISOLanguages,Runnable uiStopper) {
        if(ISOLanguages == null || ISOLanguages.isEmpty())
        {
            jsonNames.clear();
            clean(ISOLanguages);
            restart(uiStopper);
            return;
        }

        loadJsonNames(ISOLanguages);
        clean(ISOLanguages);

        if(jsonNames == null || jsonNames.isEmpty())
        {
            restart(uiStopper);
            return;
        }
        else
        {
            SData.setString(SData.Data.SavedJsonNames, ISOLanguages);
        }

        JsonDownloader downloader = new JsonDownloader(getContext(), forServer);

        uiStopper.run();
        downloader.downloadJsonFilesMultipleAndRestartAppRx(jsonNames,"https://plaxir.github.io/",directory);
    }

    /**
     * Load jsonNames from persistent storage (SData)
     * This ensures jsonNames is always available even if servlet registration fails
     */
    private void loadJsonNames() {
        loadJsonNames(SData.getString(SData.Data.SavedJsonNames));
    }
    private void loadJsonNames(String loadData) {
        if (loadData != null && !loadData.isEmpty()) {
            jsonNames = correctlyLoadData(loadData.split(","));
            SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[jsonNames loaded: " + jsonNames.size() + " items: " + String.join(",", jsonNames) + "]");
        } else {
            jsonNames = new ArrayList<>();
            SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[jsonNames initialized as empty list]");
        }
    }

    private List<String> correctlyLoadData(String[] iso)
    {
        List<String> withRadio = new ArrayList<>();
        for(String lg : iso)
        {
            if(lg.length()!=2)
            {
                withRadio.clear();
                break;
            }
            withRadio.add(lg);
            withRadio.add(lg+"_Radio");
        }

        return withRadio;
    }

    private void loadURLS(ServletContextHandler handler, Resolutions resolutions) throws JSONException {
        // Ensure jsonNames is populated before registering servlets
        loadJsonNames();

        if (jsonNames == null || jsonNames.isEmpty()) {
            SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[No JSON names to load in LoadURLS]");
            return;
        }

        SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Loading " + jsonNames.size() + " JSON files]");

        allSources = new Source[jsonNames.size()];
        for (int i = 0; i < jsonNames.size(); i++) {
            String jsonName = jsonNames.get(i);
            SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[" + directory + "}{" + jsonName + "}{" + "/" + directory + "/" + jsonName + "]");
            try {
                allSources[i] = new Source(directory, jsonName,resolutions);
                handler.addServlet(new JettyServer.CustomServletHolder(allSources[i]), "/" + directory + "/" + jsonName);
            } catch (Exception e) {
                SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Error creating servlet for " + jsonName + ": " + e.getMessage() + "]");
            }
        }
    }

    @Override
    protected void doGetAdvanced(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.addHeader("Content-Type", "application/json");
        String output = "[]";

        // CRITICAL FIX: Always load fresh data from persistent storage
        // This ensures we don't rely on the potentially stale jsonNames field
        String loadData = SData.getString(SData.Data.SavedJsonNames);
        List<String> currentJsonNames;

        if (loadData != null && !loadData.isEmpty()) {

            //Load Data and with Radio
            currentJsonNames = correctlyLoadData(loadData.split(","));

            SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Main Sources.doGet: Loaded " + currentJsonNames.size() + " items from SData: " + String.join(",", currentJsonNames) + "]");
        } else {
            resp.getWriter().write(output);
            SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Main Sources.doGet: No data in SData]");
            return;
        }

        // Also update the instance variable for consistency
        this.jsonNames = currentJsonNames;

        if (!currentJsonNames.isEmpty()) {
            try {
                output = StaticFunctions.setData(StaticFunctions.getAllForJson(currentJsonNames.stream().toArray(String[]::new)));
                SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Main Sources.doGet: Generated output successfully]");
            } catch (Exception e) {
                SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Main Sources.doGet error: " + e.getMessage() + "]");
            }
        } else {
            SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Main Sources.doGet: No JSON names available for output]");
        }
        resp.getWriter().write(output);
    }

    public enum Resolutions
    {
        Default,R360P,R480P,R576P,R720P,R1080P
    }
    public static class CollectionSeletedItems
    {
        public final int collectionIndex;
        public final Resolutions[] selecteditems;

        public CollectionSeletedItems(int collectionIndex,Resolutions[] selecteditems)
        {
            this.collectionIndex = collectionIndex;
            this.selecteditems = selecteditems;
        }
    }
    public static class Source extends HttpServletAdvanced {
        public final JSONArray jsonFile;
        private final String ddirectory;
        private final String ffileName;
        private List<ItemOfJson> items;
        private JSONArray resolutionsObjects;
        private CollectionSeletedItems[] seletedItems = null;

        public Source(String directory, String fileName, Resolutions resolution) throws JSONException {
            SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[" + directory + "/" + fileName + ".json]");
            ffileName = fileName;
            ddirectory = directory;
            jsonFile = readJsonObjectFromFile(fileName);

            updateSelectedUrls(resolution);
        }

        private static class ItemOfJson {
            private String name;
            private int subCollectionIndex;
            private int itemIndexInSubCollection;

            public ItemOfJson(String name, int subCollectionIndex, int itemIndexInSubCollection) {
                this.name = name;
                this.subCollectionIndex = subCollectionIndex;
                this.itemIndexInSubCollection = itemIndexInSubCollection;
            }

            public String name() {
                return name;
            }

            public int subCollectionIndex() {
                return subCollectionIndex;
            }

            public int itemIndexInSubCollection() {
                return itemIndexInSubCollection;
            }
        }

        public CollectionSeletedItems[] getCorrectSelection()
        {
            return seletedItems;
        }

        public boolean isCorrectFile(String fileName)
        {
            return fileName.equals(ffileName);
        }

        public String directoryOfResolution(Resolutions resolution) throws JSONException {
            int index = resolution.ordinal()-1;
            if(resolutionsObjects==null||index==-1)
                return null;

            return resolutionsObjects.getJSONObject(index).getString("directory");
        }

        public String directoryOfResolution(int resIndexInJson) throws JSONException {
            if(resolutionsObjects==null&&(resIndexInJson<0||resIndexInJson>=resolutionsObjects.length()))
                return null;

            return resolutionsObjects.getJSONObject(resIndexInJson).getString("directory");
        }

        @Override
        protected void doGetAdvanced(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String output;

            try {
                // Check if we have items to serve
                if (items == null || items.isEmpty()) {
                    output = "[]";
                    SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Source.doGet: No items available]");
                } else {
                    List<String> savedItems = new ArrayList<>();
                    for (ItemOfJson currentItem : items) {
                        savedItems.add(StaticFunctions.setData(StaticFunctions.asJsonFormat(currentItem.name()), currentItem.subCollectionIndex() + "", currentItem.itemIndexInSubCollection() + ""));
                    }
                    output = StaticFunctions.setData(savedItems.stream().toArray(String[]::new));
                    SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Source.doGet: Generated " + savedItems.size() + " items]");
                }
            } catch (Exception e) {
                output = "[]";
                SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Source.doGet error: " + e.getMessage() + "]");
            }
            resp.addHeader("Content-Type", "application/json");
            resp.getWriter().write(output);
        }

        private void resolutionObject(JSONObject tempObj, int index, int objectFromArray, int itemIndex) throws JSONException {

            items.add(new ItemOfJson(tempObj.getString("name")+" - "+resolutionsObjects.getJSONObject(index).getString("quality"), objectFromArray, itemIndex));
        }

        // Helper method
        private int[] jsonArrayToIntArray(JSONArray jsonArray) {
            int[] result = new int[jsonArray.length()];
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    result[i] = jsonArray.getInt(i);
                } catch (JSONException ex) {
                    throw new RuntimeException(ex);
                }
            }
            return result;
        }

        private void updateSelectedUrls(Resolutions resolution) throws JSONException {
            resolutionsObjects = null;
            seletedItems = null;
            List<CollectionSeletedItems> collectionSeletedItems = new ArrayList<>();
            //jsonFile = readJsonObjectFromFile(fileName);

            if (jsonFile == null) {
                SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[JSON is null]");
                return;
            }

            SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[NotNull]");
            items = new ArrayList<>();

            if (jsonFile.length() > 0) {
                for (int objectFromArray = 0; objectFromArray < jsonFile.length(); objectFromArray++) {
                    JSONObject selectedSourceObject = jsonFile.getJSONObject(objectFromArray);
                    JSONArray array = selectedSourceObject.getJSONArray("sources");

                    try {
                        resolutionsObjects = selectedSourceObject.getJSONArray("resolutions");
                    } catch (Exception e) {
                        // If no resolutions array, add all items without resolution qualifier
                        for (int itemIndex = 0; itemIndex < array.length(); itemIndex++) {
                            JSONObject tempObj = array.getJSONObject(itemIndex);
                            items.add(new ItemOfJson(tempObj.getString("name"), objectFromArray, itemIndex));
                        }
                        continue; // Continue to next objectFromArray, don't return
                    }

                    Resolutions[] seletedItemsTemp = new Resolutions[array.length()];
                    for (int itemIndex = 0; itemIndex < array.length(); itemIndex++) {
                        JSONObject tempObj = array.getJSONObject(itemIndex);
                        int[] haveItemsIndex;

                        try {
                            // Direct HAVE case
                            JSONArray haveItems = tempObj.getJSONArray("resolutionsHaveIndex");
                            haveItemsIndex = jsonArrayToIntArray(haveItems);
                        } catch (JSONException ee) {

                            try {
                                // Calculate HAVE from NOT HAVE case
                                JSONArray notHaveItems = tempObj.getJSONArray("resolutionsNotHaveIndex");
                                int[] notHaveArray = jsonArrayToIntArray(notHaveItems);
                                int[] allResolutions = IntStream.range(0, resolutionsObjects.length()).toArray();

                                Set<Integer> notHaveSet = Arrays.stream(notHaveArray).boxed().collect(Collectors.toSet());
                                haveItemsIndex = Arrays.stream(allResolutions)
                                        .filter(item -> !notHaveSet.contains(item))
                                        .toArray();
                            }
                            catch (Exception ed)
                            {
                                haveItemsIndex = jsonArrayToIntArray(resolutionsObjects);
                            }
                        }

                        // Skip if no available resolutions, but continue with next item
                        if (haveItemsIndex == null || haveItemsIndex.length == 0) {
                            seletedItemsTemp[itemIndex] = Resolutions.Default;
                            items.add(new ItemOfJson(tempObj.getString("name"), objectFromArray, itemIndex));
                            continue;
                        }

                        // Find the best available resolution
                        Resolutions selectedResolution = Resolutions.Default;
                        int selectedIndex = haveItemsIndex[0]; // Default to first available

                        if (haveItemsIndex.length == 1) {
                            // Only one resolution available
                            selectedResolution = indexToResolution(haveItemsIndex[0]);
                            resolutionObject(tempObj, haveItemsIndex[0], objectFromArray, itemIndex);
                        } else {
                            // Multiple resolutions available - find best match for requested resolution
                            int requestedIndex = resolution.ordinal() - 1; // Convert to JSON index

                            // Try to find exact match or next best lower resolution
                            boolean found = false;
                            for (int k = haveItemsIndex.length - 1; k >= 0; k--) {
                                if (haveItemsIndex[k] <= requestedIndex) {
                                    selectedIndex = haveItemsIndex[k];
                                    selectedResolution = indexToResolution(selectedIndex);
                                    resolutionObject(tempObj, selectedIndex, objectFromArray, itemIndex);
                                    found = true;
                                    break;
                                }
                            }

                            // If no suitable resolution found, use the lowest available
                            if (!found) {
                                selectedIndex = haveItemsIndex[0];
                                selectedResolution = indexToResolution(selectedIndex);
                                resolutionObject(tempObj, selectedIndex, objectFromArray, itemIndex);
                            }
                        }

                        seletedItemsTemp[itemIndex] = selectedResolution;
                    }
                    collectionSeletedItems.add(new CollectionSeletedItems(objectFromArray, seletedItemsTemp));
                }
            }

            if(!collectionSeletedItems.isEmpty())
                seletedItems = collectionSeletedItems.toArray(new CollectionSeletedItems[0]);
        }

        // Helper method to convert JSON index to Resolution enum
        private Resolutions indexToResolution(int jsonIndex) {
            // JSON indices: 0=R360P, 1=R480P, 2=R576P, 3=R720P, 4=R1080P
            // Enum ordinals: 0=Default, 1=R360P, 2=R480P, 3=R576P, 4=R720P, 5=R1080P
            return Resolutions.values()[jsonIndex + 1];
        }
        private JSONArray readJsonObjectFromFile(String filename) {
            try {
                File directory = new File(getContext().getFilesDir(), ddirectory);
                File file = new File(directory, filename+".json");

                // Add file existence check
                if (!file.exists()) {
                    SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[File not exists: " + file.getAbsolutePath() + "]");
                    return null;
                }

                // Check file size
                long fileSize = file.length();
                if (fileSize == 0) {
                    SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[File is empty: " + file.getAbsolutePath() + "]");
                    return null;
                }

                SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Full Path: " + file.getAbsolutePath() + ", Size: " + fileSize + " bytes]");

                FileInputStream fis = new FileInputStream(file);
                InputStreamReader isr = new InputStreamReader(fis);
                BufferedReader bufferedReader = new BufferedReader(isr);
                StringBuilder stringBuilder = new StringBuilder();
                String line;

                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line);
                }

                fis.close();
                String jsonString = stringBuilder.toString();

                // Check if JSON string is empty
                if (jsonString.trim().isEmpty()) {
                    SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[JSON string is empty]");
                    return null;
                }

                return new JSONArray(jsonString);

            } catch (Exception e) {
                e.printStackTrace();
                SData.setString(SData.Data.SavedDataLoaderActions, SData.getString(SData.Data.SavedDataLoaderActions) + "[Error: " + e.getMessage() + "]");
                return null;
            }
        }

    }
}
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

import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.IOException;

import app.tools.Generators.Requirements.GeneratorWithExpire;
import ssl.SiteLoader;
import app.tools.HlsSelector;
import app.tools.Players.all.Listeners;
import app.tools.Players.all.PlayerControllerBase;
import server.web.ErrorCodeApp;

import static app.tools.DisposableTools.waitMS;

public class SiteGenerator extends GeneratorWithExpire {
    private final String urlPattern;
    private final String expireUnixTimePattern;
    private final String selectedRes;
    private final String nameOfMedia;
    private SiteLoader loader;

    public SiteGenerator(String selectedRes, String videoURL, Listeners listeners, boolean hardware, boolean isLive, String urlPattern, String expireUnixTimePattern,String nameOfMedia) {
        super(videoURL, listeners, hardware);
        this.urlPattern = urlPattern;
        this.expireUnixTimePattern = expireUnixTimePattern;
        this.selectedRes = selectedRes;
        this.isLive = isLive;
        this.nameOfMedia = nameOfMedia;
    }

    @Override
    public void modifyGenerateInfo() throws ExtractionException, IOException {
        loader =  loaderGenerate();
    }

    @Override
    public PlayerControllerBase onErrorLive(boolean DisplayOn, boolean loop, boolean playListLoop) {
        return null;
    }

    @Override
    public String nameOfMedia() {
        return nameOfMedia;
    }

    @Override
    protected void modifyGenerateLink() {}

    @Override
    protected boolean modifyGenerateContent()
    {
        try {
            loader = loaderGenerate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void modifyLoadContent() {
        expireSeconds = -1;
        loader.startCapture();
        for(int i = 0; i<50; i++)
        {
            waitMS(200);
            if(expireSeconds!=-1)
                break;
        }

        if(selectedRes!=null)
            audioOrBaseStream = HlsSelector.getCorrectUrl(audioOrBaseStream,selectedRes);

        loader.stop();
    }

    private SiteLoader loaderGenerate()
    {
        return new SiteLoader(videoURL, new SiteLoader.Listeners() {

            @Override
            public void onRequestIntercepted(String url, String method) {
            }

            @Override
            public void onError(String errorMessage) {
            }

            @Override
            public void onMainSiteLoaded(String htmlContent) {
                // Extract the main URL pattern
                audioOrBaseStream = SiteLoader.extractString(urlPattern, htmlContent);

                if (audioOrBaseStream != null) {
                    // Extract expire time if pattern exists
                    String expireStr = SiteLoader.extractString(expireUnixTimePattern, audioOrBaseStream);
                    if (expireStr != null) {
                        try {
                            expireSeconds = Integer.parseInt(expireStr);
                        } catch (NumberFormatException e) {
                            expireSeconds = -1;
                        }
                    }
                } else {
                    expireSeconds = -1;
                }
            }

            @Override
            public void onLog(String logMessage) {
            }
        });
    }
}
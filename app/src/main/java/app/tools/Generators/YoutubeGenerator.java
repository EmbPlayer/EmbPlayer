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

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;

import app.tools.Generators.Requirements.GeneratorWithExpire;
//import server.tools.HostedContents;
import app.tools.Players.all.PlayerControllerBase;
import app.tools.StaticFunctions;
import server.tools.VideoSettings;

import app.tools.Generators.Requirements.Piped.ContentId;
import app.tools.Generators.Requirements.Piped.NewPipeEx;
import app.tools.Generators.Requirements.Piped.StreamSelectionPolicy;
import app.tools.Generators.Requirements.Piped.VideoQuality;
import app.tools.Generators.Requirements.Piped.VideoResolution;
import app.tools.HlsSelector;
import app.tools.Players.all.Listeners;
import app.tools.Generators.Requirements.YouTubeExpireExtractor;

import server.web.ErrorCodeApp;

import static server.Home.app;

public class YoutubeGenerator extends GeneratorWithExpire {

    protected final VideoSettings videoSettings;
    private final Size size;

    private ContentId id;
    private boolean displayOn;
    private StreamInfo info;

    //private List<String> logs;
    //private List<VideoStream> videoStreamList;
    //private List<VideoStream> videoOnlyStreamList;
    //private List<AudioStream> audioStreams;
    //public String language;

    public YoutubeGenerator(String videoURL,VideoSettings videoSettings,Listeners listeners, boolean hardware)
    {
        super(videoURL,listeners,hardware);
        size = new Size();
        this.videoSettings = videoSettings;
    }

    public static void updateNewPipe()
    {
        NewPipeEx.init(Localization.DEFAULT, ContentCountry.DEFAULT);
    }

    @Override
    public String nameOfMedia() {
        return info.getName();
    }

    @Override
    public void modifyGenerateLink()
    {
        size.width = 0;
        size.height = 0;
    }

    @Override
    public boolean modifyGenerateContent(){
        try {
            id = NewPipeEx.getContentId(videoURL);
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public Size getSize()
    {
        return size;
    }

    public PlayerControllerBase onErrorLive(boolean DisplayOn, boolean loop, boolean playlistLoop)
    {
        notLive();
        return startPanel(DisplayOn,loop,playlistLoop);
    }

    public StreamingService.LinkType getType()
    {
        return id.getType();
    }

    @Override
    public void modifyGenerateInfo() throws ExtractionException, IOException {
        info = NewPipeEx.getStreamInfoById(id.getId());
    }
/*
    public void GenerateInfoOnlyAudio() throws ExtractionException, IOException {
        info = NewPipeEx.getCustomStreamInfoByVideoIdOnlyAudio(id.getId());
    }*/

    public StreamInfo getInfo() {
        return info;
    }

    public void generateInfoAuto() throws ExtractionException, IOException {
        generateInfo();/*
        if(!DisplayOn)
            GenerateInfoOnlyAudio();
        else
            GenerateInfo();*/
    }
    //Panel.AspectChange(width, height);
    //Panel.RefreshDisplay();

    @Override
    protected void modifyLoadContent(){

        //StreamingService.LinkType type = id.getType();
        //if(type== StreamingService.LinkType.PLAYLIST)

        if(!live())
            notLive();
    }

    private void notLive()
    {
        isLive = false;
        //contentType = ContentTypes.TYPE_OTHER;

        maxSeek.maxSeek=info.getDuration()*1000;

        StreamSelectionPolicy.StreamSelection selection = null;

        try {

            VideoResolution l = videoSettings.resolution();

            VideoQuality ll = videoSettings.quality();

            String lang = videoSettings.languageISO2();

            StreamSelectionPolicy streamS = new StreamSelectionPolicy(true,l, VideoResolution._144P, ll,lang);

            selection = streamS.select(info,!displayOn);
        }
        catch (Exception e){
            StaticFunctions.onErrorSave("Create_Stream_Selection_Policy",e);
        }

        try
        {
            if(app().legacyYoutubePlayer.make())
            {
                VideoStream videoStreamIn = selection.getVideoStream();
                audioOrBaseStream = videoStreamIn.getContent();
                if(displayOn){
                    updateSize(videoStreamIn);
                }
                return;
            }

            audioOrBaseStream = selection.getAudioStream().getContent();
        }
        catch (Exception e)
        {
            videoStream = null;
            return;
        }

        //HostedContents.GetAudioOrBase().UpdateContent(audioOrBaseStream);

        expireSeconds = Integer.parseInt(YouTubeExpireExtractor.extractExpire(audioOrBaseStream));

        if(displayOn)
        {
            VideoStream videoStreamIn = selection.getVideoStream();

            updateSize(videoStreamIn);

            videoStream = videoStreamIn.getContent();

            //HostedContents.GetVideo().UpdateContent(videoStream);

            int videoTime = Integer.parseInt(YouTubeExpireExtractor.extractExpire(videoStream));

            if(expireSeconds>videoTime)
                expireSeconds = videoTime;
        }

        //language = GetLanguages()[GetVideoLanguageID()];
        //audioStreams = info.getAudioStreams();
        //videoOnlyStreamList = info.getVideoOnlyStreams();
        //videoStreamList = info.getVideoStreams();
    }

    private boolean live()
    {
        StreamType streamType = info.getStreamType();
        if (streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM) {
            try {
                maxSeek.maxSeek = 0;

                if (!info.getHlsUrl().isEmpty()){
                    displayOn = videoSettings.resolutionLive() != VideoResolution.Audio;
                    return hlsAdd(info.getHlsUrl());
                }

                /*
                else if (!info.getDashMpdUrl().isEmpty()){
                    return false;
                }*/
            } catch (final Exception e) {}
        }

        displayOn = videoSettings.resolution() != VideoResolution.Audio;
        return false;
    }

    private boolean hlsAdd(String url)
    {
        boolean qualityOn = videoSettings.quality() == VideoQuality.BEST_QUALITY;
        try
        {
            audioOrBaseStream = HlsSelector.addLiveSource(url,qualityOn, HlsSelector.getRes(1), HlsSelector.getRes(videoSettings.resolutionLive().ordinal()));

            //HostedContents.GetAudioOrBase().UpdateContent(audioOrBaseStream);

            expireSeconds = Integer.parseInt(YouTubeExpireExtractor.extractExpire(audioOrBaseStream));

            isLive = true;
            //contentType = ContentTypes.TYPE_HLS;

            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private void updateSize(VideoStream videoStream)
    {
        size.width = videoStream.getWidth();
        size.height = videoStream.getHeight();
    }

    public static final class Size
    {
        private int width;
        private int height;

        public int getWidth(){
            return width;
        }

        public int getHeight(){
            return height;
        }
    }
}

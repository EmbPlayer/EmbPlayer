package app.tools.Generators.Requirements.Piped;

import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.utils.Utils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import androidx.annotation.NonNull;

public class NewPipeEx {

    public static boolean isUseDislikeApi;
    private static StreamingService streamingService;
    private static OkHttpDownloader downloader;

    public static void init(final Localization localization, final ContentCountry contentCountry)
    {
        downloader = OkHttpDownloader.getInstance();

        if (NewPipe.getDownloader() == null) {
            NewPipe.init(downloader, localization, contentCountry);
        }

        streamingService = ServiceList.YouTube;
    }

    public static ContentId getContentId(String url) {
        if (url == null) {
            return null;
        }
        ContentId id;
        id = parse(streamingService.getStreamLHFactory(), url, StreamingService.LinkType.STREAM);
        if (id != null) {
            return id;
        }
        id = parse(streamingService.getChannelLHFactory(), url, StreamingService.LinkType.CHANNEL);
        if (id != null) {
            return id;
        }
        id = parse(streamingService.getPlaylistLHFactory(), url, StreamingService.LinkType.PLAYLIST);
        return id;
    }

    private static ContentId parse(LinkHandlerFactory handlerFactory, String url, StreamingService.LinkType type) {
        if (handlerFactory != null) {
            try {
                String id = handlerFactory.getId(url);
                String canonicalUrl = handlerFactory.getUrl(id);
                if (type == StreamingService.LinkType.STREAM) {
                    return new VideoId(id, canonicalUrl, parseTimeStamp(url));
                } else {
                    return new ContentId(id, handlerFactory.getUrl(id), type);
                }
            } catch (ParsingException pe) {
                return null;
            }
        }
        return null;
    }

    private static String getVideoUrl(String videoId) throws ParsingException {
        return streamingService.getStreamLHFactory().getUrl(videoId);
    }

    public static StreamInfo getStreamInfoById(String videoId) throws ExtractionException, IOException {
        return StreamInfo.getInfo(streamingService, getVideoUrl(videoId));
    }

    private static Long getDislikeCount(StreamExtractor extractor, String id) {
        try {
            long dislikeCount = extractor.getDislikeCount();
            if (dislikeCount >= 0) {
                return dislikeCount;
            }
        } catch (ParsingException e) {
            //Logger.e(this, "Unable get dislike count for " + extractor.getLinkHandler().getUrl() + ", error:" + e.getMessage(), e);
        }
        return getDislikeCountFromApi(id);
    }

    public static Long getDislikeCountFromApi(String videoId)  {
        if (isUseDislikeApi) {
            // send the request
            String url = "https://returnyoutubedislikeapi.com/votes?videoId=" + videoId;
            try {
                //Logger.i(this, "fetching dislike count for "+ url);
                Response response = downloader.get(url);
                // get the response
                int responseCode = response.responseCode();
                if (responseCode != 200) {
                    //Logger.e(this, "ResponseCode " + responseCode + " for " + url);
                    return null;
                }

                JSONObject jsonObject = new JSONObject(response.responseBody());
                //Logger.i(this, "for "+ url +" -> "+jsonObject);
                return jsonObject.getLong("dislikes");
            } catch (IOException | JSONException | ReCaptchaException e) {
                //Logger.e(this, "getDislikeCount: error: " + e.getMessage() + " for url:" + url, e);
            }
        } else {
            //Logger.i(this, "Like fetching disabled for " + videoId);
        }
        return null;
    }
    public static class DateInfo {
        boolean exact;
        Instant instant;

        public DateInfo(DateWrapper uploadDate) {
            if (uploadDate != null) {
                instant = uploadDate.offsetDateTime().toInstant();
                exact = !uploadDate.isApproximation();
            } else {
                instant = null;
                exact = false;
            }
        }

        private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @NonNull
        @Override
        public String toString() {
            try {
                return "[time= " + dtf.format(instant) + ",exact=" + exact + ']';
            } catch (Exception e){
                return "[incorrect time= " + instant + " ,exact=" + exact + ']';
            }
        }
    }

    private static Integer parseTimeStamp(String url) {
        try {
            String time = Utils.getQueryValue(new URL(url), "t");
            if (time != null) {
                return Integer.parseInt(time);
            }
        } catch (MalformedURLException | NumberFormatException e) {
        }
        return null;
    }
}


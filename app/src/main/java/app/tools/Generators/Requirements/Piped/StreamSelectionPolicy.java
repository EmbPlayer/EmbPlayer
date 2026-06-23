package app.tools.Generators.Requirements.Piped;

import android.net.Uri;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static server.Home.app;

public class StreamSelectionPolicy {

    private static final Set<String> SUPPORTED_VIDEO_CODECS = Set.of("avc1.640028","avc1.4d401f","avc1.4d401e","avc1.4d4015","avc1.4d400c");

    private final static List<MediaFormat> VIDEO_FORMAT_QUALITY = Arrays.asList(MediaFormat.WEBM, MediaFormat.MPEG_4, MediaFormat.v3GPP);

    private final boolean allowVideoOnly;
    private final VideoResolution maxResolution;
    private final VideoResolution minResolution;
    private final VideoQuality videoQuality;
    private final String languageISO2;

    public StreamSelectionPolicy(boolean allowVideoOnly, VideoResolution maxResolution, VideoResolution minResolution, VideoQuality videoQuality,String languageISO2) {
        this.allowVideoOnly = allowVideoOnly;


        if(maxResolution==VideoResolution.Audio)
            this.maxResolution=VideoResolution._1080P;
        else
        {
            //this.maxResolution = maxResolution != VideoResolution.RES_UNKNOWN ? maxResolution : null;
            this.maxResolution = maxResolution;
        }

        //this.maxResolution = maxResolution != VideoResolution.RES_UNKNOWN ? maxResolution : null;
        this.minResolution = minResolution != VideoResolution.Audio ? minResolution : null;
        this.videoQuality = videoQuality;
        this.languageISO2 = languageISO2;
    }

    public StreamSelectionPolicy withAllowVideoOnly(boolean newValue) {
        return new StreamSelectionPolicy(newValue, maxResolution, minResolution, videoQuality,languageISO2);
    }

    /*public StreamSelection select(org.schabi.newpipe.extractor.stream.StreamInfo streamInfo) {
        VideoStreamWithResolution videoStreamWithResolution = pickVideo(streamInfo);
        if (videoStreamWithResolution != null) {
            if (videoStreamWithResolution.videoStream.isVideoOnly()) {
                AudioStream audioStream = pickAudio(streamInfo);
                if (audioStream != null) {
                    return new StreamSelection(videoStreamWithResolution.videoStream, videoStreamWithResolution.resolution, audioStream);
                }
            } else {
                return new StreamSelection(videoStreamWithResolution.videoStream, videoStreamWithResolution.resolution, null);
            }
        }
        return null;
    }*/

    public StreamSelection select(StreamInfo streamInfo, boolean audioOnly) {

        if(app().legacyYoutubePlayer.make()){
            VideoStreamWithResolution videoStreamWithResolution = new VideoStreamWithResolution(streamInfo.getVideoStreams().get(0));
            return new StreamSelection(videoStreamWithResolution.videoStream, videoStreamWithResolution.resolution,null);
        }
        else
        {
            if(audioOnly)
            {
                AudioStream audioStream = pickAudio(streamInfo);
                if (audioStream != null) {
                    return new StreamSelection(null,null,audioStream);
                }
            }

            VideoStreamWithResolution videoStreamWithResolution = pickVideo(streamInfo);

            if (videoStreamWithResolution != null) {
                if (videoStreamWithResolution.videoStream.isVideoOnly()) {
                    AudioStream audioStream = pickAudio(streamInfo);
                    if (audioStream != null) {
                        return new StreamSelection(videoStreamWithResolution.videoStream, videoStreamWithResolution.resolution, audioStream);
                    }
                } else {
                    return new StreamSelection(videoStreamWithResolution.videoStream, videoStreamWithResolution.resolution, null);
                }
            }
        }

        return null;
    }

    private VideoStreamWithResolution pickVideo(StreamInfo streamInfo) {
        List<VideoStream> streams = streamInfo.getVideoStreams();

        if (allowVideoOnly) {
            streams = new ArrayList<>(streams);
            streams.addAll(streamInfo.getVideoOnlyStreams());
        }

        return pick(streams);
    }

    private AudioStream pickAudio(StreamInfo streamInfo) {
        AudioStream best = null;
        for (AudioStream audioStream : getByLanguage(streamInfo.getAudioStreams())) {
            if (isBetter(best, audioStream)) {
                best = audioStream;
            }
        }
        return best;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("StreamSelectionPolicy{");
        sb.append("allowVideoOnly=").append(allowVideoOnly);
        if (maxResolution != null) {
            sb.append(", maxResolution=").append(maxResolution);
        }
        if (minResolution != null) {
            sb.append(", minResolution=").append(minResolution);
        }
        sb.append(", videoQuality=").append(videoQuality);
        sb.append('}');
        return sb.toString();
    }

    /*private AudioStream pickAudio(org.schabi.newpipe.extractor.stream.StreamInfo streamInfo) {
        AudioStream best = null;
        for (AudioStream audioStream : getByLanguage(streamInfo.getAudioStreams())) {
            if (isBetter(best, audioStream)) {
                best = audioStream;
            }
        }
        return best;
    }*/


    private Collection<AudioStream> getByLanguage(Collection<AudioStream> audioStreams)
    {
        List<AudioStream> selected = new ArrayList<>();

        for(AudioStream audio : audioStreams)
        {
            try
            {
                if(audio.getAudioLocale().getLanguage().equals(languageISO2))
                    selected.add(audio);
            }
            catch (Exception e)
            {
                return audioStreams;
            }
        }

        if(selected.isEmpty())
        {
            for(AudioStream audio : audioStreams)
            {
                if(audio.getAudioLocale().getLanguage().equals("en"))
                    selected.add(audio);
            }
        }

        return selected;
    }

    private static String toHumanReadable(AudioStream as) {
        return as != null ? "AudioStream(" + as.getAverageBitrate() + ", " + as.getFormat() + ", codec=" + as.getCodec() + ", q=" + as.getQuality() + ", isUrl=" + as.isUrl() + ",delivery=" + as.getDeliveryMethod() + ")" : "NULL";
    }

    private boolean isBetter(AudioStream best, AudioStream other) {
        if (best == null) {
            return true;
        }
        switch (videoQuality) {
            case LEAST_BANDWIDTH:
                return other.getAverageBitrate() < best.getAverageBitrate();
            case BEST_QUALITY:
                return best.getAverageBitrate() < other.getAverageBitrate();
        }
        throw new IllegalStateException("Unexpected videoQuality:" + videoQuality);
    }

    private static boolean isSecondBetterFormat(VideoStream stream1, VideoStream stream2) {
        final int format1Idx = VIDEO_FORMAT_QUALITY.indexOf(stream1.getFormat());
        final int format2Idx = VIDEO_FORMAT_QUALITY.indexOf(stream2.getFormat());
        if (format2Idx < 0) {
            return false;
        }
        if (format1Idx < 0) {
            return true;
        }
        return (format2Idx < format1Idx);
    }

    /*private VideoStreamWithResolution pickVideo(org.schabi.newpipe.extractor.stream.StreamInfo streamInfo) {
        List<VideoStream> streams = streamInfo.getVideoStreams();
        if (allowVideoOnly) {
            streams = new ArrayList<>(streams);
            streams.addAll(streamInfo.getVideoOnlyStreams());
        }
        return pick(streams);
    }*/

    private VideoStreamWithResolution pick(Collection<VideoStream> streams) {
        VideoStreamWithResolution best = null;

        List<VideoStreamWithResolution> savedStreams = new ArrayList<>();

        for (VideoStream stream : streams) {
            VideoStreamWithResolution videoStream = new VideoStreamWithResolution(stream);

            if(videoStream.videoStream.getFormat() == MediaFormat.MPEG_4 && stream.isUrl()){

                if (SUPPORTED_VIDEO_CODECS.contains(videoStream.videoStream.getCodec()))
                {
                    if(videoStream.resolution==maxResolution)
                    {
                        best = videoStream;
                        break;
                    }

                    savedStreams.add(videoStream);
                }
            }
        }

        if(best == null)
        {
            savedStreams.sort(Comparator.comparingInt(a -> a.resolution.ordinal()));

            for (int i = savedStreams.size() - 1; i > -1; i--) {
                VideoStreamWithResolution video = savedStreams.get(i);

                if(maxResolution.ordinal()>video.resolution.ordinal())
                {
                    best = video;
                    break;
                }
            }

            if(best == null)
            {
                best = savedStreams.get(0);
            }
        }

        return best;
    }

/*
    private VideoStreamWithResolution pick(Collection<VideoStream> streams) {
        VideoStreamWithResolution best = null;
        for (VideoStream stream : streams) {
            VideoStreamWithResolution videoStream = new VideoStreamWithResolution(stream);
            if (isAllowed(videoStream.resolution) && isAllowedVideoFormat(videoStream.videoStream.getFormat()) && stream.isUrl()) {
                switch (videoQuality) {
                    case BEST_QUALITY:
                        if (videoStream.isBetterQualityThan(best)) {
                            best = videoStream;
                        }
                        break;
                    case LEAST_BANDWITH:
                        if (videoStream.isLessNetworkUsageThan(best)) {
                            best = videoStream;
                        }
                        break;
                }
            }
        }
        return best;
    }*/

    private boolean isAllowed(VideoResolution resolution) {
        if (minResolution != null && minResolution.isBetterQualityThan(resolution)) {
            return false;
        }

        if (maxResolution != null && resolution.isBetterQualityThan(maxResolution)) {
            return false;
        }

        return true;
    }

    private boolean isAllowedVideoFormat(MediaFormat format) {
        return VIDEO_FORMAT_QUALITY.contains(format);
    }

    private static class VideoStreamWithResolution {
        final VideoStream videoStream;
        final VideoResolution resolution;

        VideoStreamWithResolution(VideoStream videoStream) {
            this.videoStream = videoStream;
            this.resolution = VideoResolution.resolutionToVideoResolution(videoStream.getResolution());
        }

        boolean isBetterQualityThan(VideoStreamWithResolution other) {
            return other == null || resolution.isBetterQualityThan(other.resolution) || (resolution == other.resolution && isSecondBetterFormat(other.videoStream, videoStream));
        }

        boolean isLessNetworkUsageThan(VideoStreamWithResolution other) {
            return other == null || resolution.isLessNetworkUsageThan(other.resolution) || (resolution == other.resolution && isSecondBetterFormat(other.videoStream, videoStream));
        }

        private String toHumanReadable() {
            return "VideoStream(" + resolution.name() +
                    ", format=" + videoStream.getFormat() +
                    ", codec=" + videoStream.getCodec() +
                    ", quality=" + videoStream.getQuality() +
                    ",videoOnly=" + videoStream.isVideoOnly() +
                    ",isUrl=" + videoStream.isUrl() +
                    ",delivery=" + videoStream.getDeliveryMethod() + ")";
        }

        private static String toHumanReadable(VideoStreamWithResolution v) {
            return v != null ? v.toHumanReadable() : "NULL";
        }

        private static String toHumanReadable(VideoStream v) {
            return v != null ? new VideoStreamWithResolution(v).toHumanReadable() : "NULL";
        }
    }

    public static class StreamSelection {
        final VideoStream videoStream;
        final VideoResolution resolution;
        final AudioStream audioStream;

        StreamSelection(VideoStream videoStream, VideoResolution resolution, AudioStream audioStream) {
            this.videoStream = videoStream;
            this.resolution = resolution;
            this.audioStream = audioStream;
        }

        public VideoStream getVideoStream() {
            return videoStream;
        }

        public Uri getVideoStreamUri() {
            return videoStream.isUrl() ? Uri.parse(videoStream.getContent()) : null;
        }

        public Uri getAudioStreamUri() {
            return audioStream != null && audioStream.isUrl() ? Uri.parse(audioStream.getContent()) : null;
        }

        public VideoResolution getResolution() {
            return resolution;
        }

        public AudioStream getAudioStream() {
            return audioStream;
        }
    }
}


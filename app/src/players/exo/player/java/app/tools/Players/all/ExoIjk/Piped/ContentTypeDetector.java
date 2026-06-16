package app.tools.Players.all.ExoIjk.Piped;

import android.net.Uri;
import android.text.TextUtils;

import androidx.media3.common.C;

/**
 * Utility class for detecting content types from URLs.
 */
public class ContentTypeDetector {
    
    /**
     * Detects the content type from a URL string.
     * 
     * @param urlString The URL string to analyze
     * @return One of the {@link C.ContentType} constants
     */
    @C.ContentType
    public static int detectContentType(String urlString) {
        if (TextUtils.isEmpty(urlString)) {
            return C.CONTENT_TYPE_OTHER;
        }
        
        Uri uri = Uri.parse(urlString);
        String path = uri.getPath();
        String scheme = uri.getScheme();
        
        if (path == null) {
            return C.CONTENT_TYPE_OTHER;
        }
        
        // Convert to lowercase for case-insensitive matching
        String lowerPath = path.toLowerCase();
        String lowerScheme = scheme != null ? scheme.toLowerCase() : "";
        
        // Check for server-side ad insertion
        if (C.SSAI_SCHEME.equals(lowerScheme)) {
            return C.CONTENT_TYPE_OTHER; // SSAI streams need further inspection
        }
        
        // Check file extensions and patterns
        if (lowerPath.endsWith(".mpd") || 
            lowerPath.contains(".mpd?") ||
            lowerPath.contains("/dash/") ||
            lowerPath.contains("/mpd")) {
            return C.CONTENT_TYPE_DASH;
        }
        
        if (lowerPath.endsWith(".m3u8") ||
            lowerPath.contains(".m3u8?") ||
            lowerPath.contains("/hls/") ||
            lowerPath.contains("/master.m3u8")) {
            return C.CONTENT_TYPE_HLS;
        }
        
        if (lowerPath.endsWith(".ism") ||
            lowerPath.contains(".ism/") ||
            lowerPath.endsWith(".isml") ||
            lowerPath.contains("/manifest") ||
            lowerPath.contains("/smoothstreaming/")) {
            return C.CONTENT_TYPE_SS;
        }
        
        if (lowerScheme.equals("rtsp") ||
            lowerPath.startsWith("rtsp://") ||
            lowerPath.contains("/rtsp/")) {
            return C.CONTENT_TYPE_RTSP;
        }
        
        // Check query parameters that might indicate content type
        String query = uri.getQuery();
        if (query != null) {
            String lowerQuery = query.toLowerCase();
            if (lowerQuery.contains("format=mpd") || lowerQuery.contains("type=dash")) {
                return C.CONTENT_TYPE_DASH;
            }
            if (lowerQuery.contains("format=m3u8") || lowerQuery.contains("type=hls")) {
                return C.CONTENT_TYPE_HLS;
            }
            if (lowerQuery.contains("format=smil") || lowerQuery.contains("type=smooth")) {
                return C.CONTENT_TYPE_SS;
            }
        }
        
        // Check for common streaming patterns in the path
        if (lowerPath.contains("/dash/") || lowerPath.contains("/mpd/")) {
            return C.CONTENT_TYPE_DASH;
        }
        if (lowerPath.contains("/hls/") || lowerPath.contains("/m3u8/")) {
            return C.CONTENT_TYPE_HLS;
        }
        if (lowerPath.contains("/smooth/") || lowerPath.contains("/ism/")) {
            return C.CONTENT_TYPE_SS;
        }
        
        return C.CONTENT_TYPE_OTHER;
    }
    
    /**
     * Detects the content type from a Uri object.
     * 
     * @param uri The Uri to analyze
     * @return One of the {@link C.ContentType} constants
     */
    @C.ContentType
    public static int detectContentType(Uri uri) {
        if (uri == null) {
            return C.CONTENT_TYPE_OTHER;
        }
        return detectContentType(uri.toString());
    }
    
    /**
     * Gets a descriptive string for a content type.
     * 
     * @param contentType One of the {@link C.ContentType} constants
     * @return A descriptive string
     */
    public static String getContentTypeString(@C.ContentType int contentType) {
        switch (contentType) {
            case C.CONTENT_TYPE_DASH:
                return "DASH";
            case C.CONTENT_TYPE_HLS:
                return "HLS";
            case C.CONTENT_TYPE_SS:
                return "SmoothStreaming";
            case C.CONTENT_TYPE_RTSP:
                return "RTSP";
            case C.CONTENT_TYPE_OTHER:
                return "Other";
            default:
                return "Unknown";
        }
    }
    
    /**
     * Checks if the content type is adaptive streaming (DASH, HLS, or SmoothStreaming).
     * 
     * @param contentType One of the {@link C.ContentType} constants
     * @return True if it's an adaptive streaming format
     */
    public static boolean isAdaptiveStreaming(@C.ContentType int contentType) {
        return contentType == C.CONTENT_TYPE_DASH ||
               contentType == C.CONTENT_TYPE_HLS ||
               contentType == C.CONTENT_TYPE_SS;
    }
    
    /**
     * Checks if the content type is live streaming capable.
     * 
     * @param contentType One of the {@link C.ContentType} constants
     * @return True if it supports live streaming
     */
    public static boolean isLiveStreamingCapable(@C.ContentType int contentType) {
        return contentType == C.CONTENT_TYPE_DASH ||
               contentType == C.CONTENT_TYPE_HLS ||
               contentType == C.CONTENT_TYPE_RTSP;
    }
}
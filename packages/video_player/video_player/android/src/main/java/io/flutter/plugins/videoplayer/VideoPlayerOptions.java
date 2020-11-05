package io.flutter.plugins.videoplayer;

import java.util.HashMap;
import java.util.Map;

class VideoPlayerOptions {
  public boolean mixWithOthers;

  public String drmScheme;

  public String drmLicenseUri;

  public boolean drmSessionForClearContent = false;

  public String userAgent = "FlutterExoPlayer";

  public static final String KEY_DRM_SCHEME = "drmScheme";

  public static final String KEY_DRM_LICENSE_URI = "drmLicenseUri";

  public static final String KEY_DRM_SESSION_FOR_CLEAR_CONTENT = "drmSessionForClearContent";

  public static final String KEY_USER_AGENT = "userAgent";

  public static VideoPlayerOptions fromMap(Map obj) {
    if (null == obj) {
      return null;
    }

    VideoPlayerOptions options = new VideoPlayerOptions();
    options.drmScheme = obj.get(KEY_DRM_SCHEME) == null ? null : (String) obj.get(KEY_DRM_SCHEME);
    options.drmLicenseUri = obj.get(KEY_DRM_LICENSE_URI) == null ? null : (String) obj.get(KEY_DRM_LICENSE_URI);
    options.drmSessionForClearContent = obj.get(KEY_DRM_SESSION_FOR_CLEAR_CONTENT) == null ? false : (Boolean) obj.get(KEY_DRM_SESSION_FOR_CLEAR_CONTENT);
    options.userAgent = obj.get(KEY_USER_AGENT) == null ? null : (String) obj.get(KEY_USER_AGENT);

    return options;
  }

  public static Map toMap(VideoPlayerOptions options) {
    Map<String, Object> result = new HashMap<>();

    if (null != options.drmLicenseUri) {
      result.put(KEY_DRM_LICENSE_URI, options.drmLicenseUri);
    }

    if (null != options.drmScheme) {
      result.put(KEY_DRM_SCHEME, options.drmScheme);
    }

    if (null != options.userAgent) {
      result.put(KEY_USER_AGENT, options.userAgent);
    }

    result.put(KEY_DRM_SESSION_FOR_CLEAR_CONTENT, options.drmSessionForClearContent);

    return result;
  }

  @Override
  public String toString() {
    return "VideoPlayerOptions{" +
            "mixWithOthers=" + mixWithOthers +
            ", drmScheme='" + drmScheme + '\'' +
            ", drmLicenseUri='" + drmLicenseUri + '\'' +
            ", drmSessionForClearContent=" + drmSessionForClearContent +
            ", userAgent='" + userAgent + '\'' +
            '}';
  }
}

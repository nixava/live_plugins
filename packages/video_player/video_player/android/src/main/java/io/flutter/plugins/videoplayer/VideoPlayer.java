package io.flutter.plugins.videoplayer;

import static com.google.android.exoplayer2.Player.REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_OFF;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.view.Surface;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DummyExoMediaDrm;
import com.google.android.exoplayer2.drm.ExoMediaDrm;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;

import io.flutter.plugin.common.EventChannel;
import io.flutter.view.TextureRegistry;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class VideoPlayer {
  private static final String FORMAT_SS = "ss";
  private static final String FORMAT_DASH = "dash";
  private static final String FORMAT_HLS = "hls";
  private static final String FORMAT_OTHER = "other";

  public static final String ACTION_VIEW = "com.google.android.exoplayer.demo.action.VIEW";
  public static final String ACTION_VIEW_LIST =
          "com.google.android.exoplayer.demo.action.VIEW_LIST";

  // Activity extras.

  public static final String PREFER_EXTENSION_DECODERS_EXTRA = "prefer_extension_decoders";

  // Media item configuration extras.

  public static final String URI_EXTRA = "uri";
  public static final String MIME_TYPE_EXTRA = "mime_type";
  public static final String CLIP_START_POSITION_MS_EXTRA = "clip_start_position_ms";
  public static final String CLIP_END_POSITION_MS_EXTRA = "clip_end_position_ms";

  public static final String AD_TAG_URI_EXTRA = "ad_tag_uri";

  public static final String DRM_SCHEME_EXTRA = "drm_scheme";
  public static final String DRM_LICENSE_URI_EXTRA = "drm_license_uri";
  public static final String DRM_KEY_REQUEST_PROPERTIES_EXTRA = "drm_key_request_properties";
  public static final String DRM_SESSION_FOR_CLEAR_CONTENT = "drm_session_for_clear_content";
  public static final String DRM_MULTI_SESSION_EXTRA = "drm_multi_session";
  public static final String DRM_FORCE_DEFAULT_LICENSE_URI_EXTRA = "drm_force_default_license_uri";

  public static final String SUBTITLE_URI_EXTRA = "subtitle_uri";
  public static final String SUBTITLE_MIME_TYPE_EXTRA = "subtitle_mime_type";
  public static final String SUBTITLE_LANGUAGE_EXTRA = "subtitle_language";

  private SimpleExoPlayer exoPlayer;

  private Surface surface;

  private final TextureRegistry.SurfaceTextureEntry textureEntry;

  private QueuingEventSink eventSink = new QueuingEventSink();

  private final EventChannel eventChannel;

  private boolean isInitialized = false;

  private final VideoPlayerOptions options;

  VideoPlayer(
      Context context,
      EventChannel eventChannel,
      TextureRegistry.SurfaceTextureEntry textureEntry,
      String dataSource,
      String formatHint,
      VideoPlayerOptions options) {
    this.eventChannel = eventChannel;
    this.textureEntry = textureEntry;
    this.options = options;

    Uri uri = Uri.parse(dataSource);

    DataSource.Factory dataSourceFactory;
    RenderersFactory renderersFactory = buildRenderersFactory(/* context= */ context);

    if (isHTTP(uri)) {
      dataSourceFactory =
          new DefaultHttpDataSourceFactory(
              null == options || options.userAgent == null ? "ExoPlayer" : options.userAgent,
              null,
              DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
              DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
              true);
    } else {
      dataSourceFactory = new DefaultDataSourceFactory(
              context,
              null == options || options.userAgent == null ? "ExoPlayer" : options.userAgent);
    }

    MediaSourceFactory mediaSourceFactory =
            new DefaultMediaSourceFactory(dataSourceFactory);

    TrackSelector trackSelector = new DefaultTrackSelector(context);
    exoPlayer = new SimpleExoPlayer.Builder(/* context= */ context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .build();

    MediaSource mediaSource = buildMediaSource(uri, dataSourceFactory, formatHint, context);
    exoPlayer.setMediaSource(mediaSource);
    exoPlayer.prepare();

    setupVideoPlayer(eventChannel, textureEntry);
  }

  public static RenderersFactory buildRenderersFactory(
          Context context) {
    return new DefaultRenderersFactory(context.getApplicationContext())
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
  }

  private static boolean isHTTP(Uri uri) {
    if (uri == null || uri.getScheme() == null) {
      return false;
    }
    String scheme = uri.getScheme();
    return scheme.equals("http") || scheme.equals("https");
  }

  private MediaSource buildMediaSource(
      Uri uri, DataSource.Factory mediaDataSourceFactory, String formatHint, Context context) {
    int type;
    if (formatHint == null) {
      type = Util.inferContentType(uri.getLastPathSegment());
    } else {
      switch (formatHint) {
        case FORMAT_SS:
          type = C.TYPE_SS;
          break;
        case FORMAT_DASH:
          type = C.TYPE_DASH;
          break;
        case FORMAT_HLS:
          type = C.TYPE_HLS;
          break;
        case FORMAT_OTHER:
          type = C.TYPE_OTHER;
          break;
        default:
          type = -1;
          break;
      }
    }

    switch (type) {
      case C.TYPE_SS:
        return new SsMediaSource.Factory(
                new DefaultSsChunkSource.Factory(mediaDataSourceFactory),
                new DefaultDataSourceFactory(context, null, mediaDataSourceFactory))
            .createMediaSource(createMediaItem(uri, options));
      case C.TYPE_DASH:
//        return new DashMediaSource.Factory(
//                new DefaultDashChunkSource.Factory(mediaDataSourceFactory),
//                new DefaultDataSourceFactory(context, null, mediaDataSourceFactory))
//            .createMediaSource(createMediaItem(uri, options));
        HttpDataSource.Factory licenseDataSourceFactory =
                new DefaultHttpDataSourceFactory(options.userAgent);
        HttpMediaDrmCallback drmCallback =
                new HttpMediaDrmCallback(options.drmLicenseUri, licenseDataSourceFactory);
        DashMediaSource.Factory dashMediaSource = new DashMediaSource.Factory(
                new DefaultDashChunkSource.Factory(mediaDataSourceFactory),
                new DefaultDataSourceFactory(context, null, mediaDataSourceFactory));
//            dashMediaSource.setDrmSessionManager(provider.acquireExoMediaDrm(C.WIDEVINE_UUID).)
        DefaultDrmSessionManager drmSessionManager = new DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID,
                        new ExoMediaDrm.Provider() {
                          @Override
                          public ExoMediaDrm acquireExoMediaDrm(UUID uuid) {
                            try {
                              FrameworkMediaDrm mediaDrm = FrameworkMediaDrm.newInstance(C.WIDEVINE_UUID);
                              // Force L3.
                              mediaDrm.setPropertyString("securityLevel", "L3");
                              return mediaDrm;
                            } catch (UnsupportedDrmException e) {
                              return new DummyExoMediaDrm();
                            }
                          }
                        })
                .setMultiSession(false)
                .build(drmCallback);
        dashMediaSource.setDrmSessionManager(drmSessionManager);
        return dashMediaSource.createMediaSource(createMediaItem(uri, options));
      case C.TYPE_HLS:
        return new HlsMediaSource.Factory(mediaDataSourceFactory)
            .createMediaSource(createMediaItem(uri, options));
      case C.TYPE_OTHER:
        return new ProgressiveMediaSource.Factory(mediaDataSourceFactory)
            .createMediaSource(createMediaItem(uri, options));
      default:
        {
          throw new IllegalStateException("Unsupported type: " + type);
        }
    }
  }

  private static MediaItem createMediaItem (
          Uri uri, VideoPlayerOptions options) {
    System.out.println("uri = " + uri);
    System.out.println("options = " + options);
    MediaItem.Builder builder =
            new MediaItem.Builder().setUri(uri);

    MediaItem mediaItem = populateDrmProperties(builder, options).build();

    MediaItem.DrmConfiguration drmConfiguration =
            checkNotNull(mediaItem.playbackProperties).drmConfiguration;

    if (drmConfiguration != null) {
      if (Util.SDK_INT < 18) {
        System.err.println("DRM content not supported on API levels below 18");
      } else if (!FrameworkMediaDrm.isCryptoSchemeSupported(drmConfiguration.uuid)) {
        System.err.println("This device does not support the required DRM scheme");
      }
    }

    return mediaItem;
  }

  private static MediaItem.Builder populateDrmProperties (
          MediaItem.Builder builder, VideoPlayerOptions options) {
    String drmSchemeExtra = options.drmScheme;
    if (drmSchemeExtra == null) {
      return builder;
    }
    Map<String, String> headers = new HashMap<>();
    builder
            .setDrmUuid(Util.getDrmUuid(Util.castNonNull(drmSchemeExtra)))
            .setDrmLicenseUri(options.drmLicenseUri)
            .setDrmLicenseRequestHeaders(headers);
    if (options.drmSessionForClearContent) {
      builder.setDrmSessionForClearTypes(ImmutableList.of(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO));
    }
    return builder;
  }

  private void setupVideoPlayer(
      EventChannel eventChannel, TextureRegistry.SurfaceTextureEntry textureEntry) {

    eventChannel.setStreamHandler(
        new EventChannel.StreamHandler() {
          @Override
          public void onListen(Object o, EventChannel.EventSink sink) {
            eventSink.setDelegate(sink);
          }

          @Override
          public void onCancel(Object o) {
            eventSink.setDelegate(null);
          }
        });

    surface = new Surface(textureEntry.surfaceTexture());
    exoPlayer.setVideoSurface(surface);
    setAudioAttributes(exoPlayer, options.mixWithOthers);

    exoPlayer.addListener(
        new EventListener() {

          @Override
          public void onPlaybackStateChanged(final int playbackState) {
            if (playbackState == Player.STATE_BUFFERING) {
              sendBufferingUpdate();
            } else if (playbackState == Player.STATE_READY) {
              if (!isInitialized) {
                isInitialized = true;
                sendInitialized();
              }
            } else if (playbackState == Player.STATE_ENDED) {
              Map<String, Object> event = new HashMap<>();
              event.put("event", "completed");
              eventSink.success(event);
            }
          }

          @Override
          public void onPlayerError(final ExoPlaybackException error) {
            if (eventSink != null) {
              eventSink.error("VideoError", "Video player had error " + error, null);
            }
          }
        });
  }

  void sendBufferingUpdate() {
    Map<String, Object> event = new HashMap<>();
    event.put("event", "bufferingUpdate");
    List<? extends Number> range = Arrays.asList(0, exoPlayer.getBufferedPosition());
    // iOS supports a list of buffered ranges, so here is a list with a single range.
    event.put("values", Collections.singletonList(range));
    eventSink.success(event);
  }

  @SuppressWarnings("deprecation")
  private static void setAudioAttributes(SimpleExoPlayer exoPlayer, boolean isMixMode) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      exoPlayer.setAudioAttributes(
          new AudioAttributes.Builder().setContentType(C.CONTENT_TYPE_MOVIE).build(), !isMixMode);
    } else {
      exoPlayer.setAudioStreamType(C.STREAM_TYPE_MUSIC);
    }
  }

  void play() {
    exoPlayer.setPlayWhenReady(true);
  }

  void pause() {
    exoPlayer.setPlayWhenReady(false);
  }

  void setLooping(boolean value) {
    exoPlayer.setRepeatMode(value ? REPEAT_MODE_ALL : REPEAT_MODE_OFF);
  }

  void setVolume(double value) {
    float bracketedValue = (float) Math.max(0.0, Math.min(1.0, value));
    exoPlayer.setVolume(bracketedValue);
  }

  void setPlaybackSpeed(double value) {
    // We do not need to consider pitch and skipSilence for now as we do not handle them and
    // therefore never diverge from the default values.
    final PlaybackParameters playbackParameters = new PlaybackParameters(((float) value));

    exoPlayer.setPlaybackParameters(playbackParameters);
  }

  void seekTo(int location) {
    exoPlayer.seekTo(location);
  }

  long getPosition() {
    return exoPlayer.getCurrentPosition();
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private void sendInitialized() {
    if (isInitialized) {
      Map<String, Object> event = new HashMap<>();
      event.put("event", "initialized");
      event.put("duration", exoPlayer.getDuration());

      if (exoPlayer.getVideoFormat() != null) {
        Format videoFormat = exoPlayer.getVideoFormat();
        int width = videoFormat.width;
        int height = videoFormat.height;
        int rotationDegrees = videoFormat.rotationDegrees;
        // Switch the width/height if video was taken in portrait mode
        if (rotationDegrees == 90 || rotationDegrees == 270) {
          width = exoPlayer.getVideoFormat().height;
          height = exoPlayer.getVideoFormat().width;
        }
        event.put("width", width);
        event.put("height", height);
      }
      eventSink.success(event);
    }
  }

  void dispose() {
    if (isInitialized) {
      exoPlayer.stop();
    }
    textureEntry.release();
    eventChannel.setStreamHandler(null);
    if (surface != null) {
      surface.release();
    }
    if (exoPlayer != null) {
      exoPlayer.release();
    }
  }
}

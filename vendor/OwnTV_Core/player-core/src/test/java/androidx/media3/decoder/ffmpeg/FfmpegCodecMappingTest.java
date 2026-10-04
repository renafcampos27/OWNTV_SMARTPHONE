package androidx.media3.decoder.ffmpeg;

import static org.junit.Assert.assertEquals;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.Log;
import androidx.media3.exoplayer.audio.AudioSink;
import java.lang.reflect.Proxy;
import androidx.media3.common.MimeTypes;
import org.junit.Test;

public class FfmpegCodecMappingTest {
  @Test public void mpegLayerTwoUsesBundledMpegAudioDecoder() {
    assertEquals("mp3", FfmpegLibrary.getCodecName(MimeTypes.AUDIO_MPEG_L2));
    assertEquals("aac", FfmpegLibrary.getCodecName(MimeTypes.AUDIO_AAC));
  }

  @Test public void videoIsNeverAnAudioFallback() {
    AudioSink sink = (AudioSink) Proxy.newProxyInstance(
        AudioSink.class.getClassLoader(), new Class<?>[] {AudioSink.class},
        (proxy, method, args) -> null);
    FfmpegAudioRenderer renderer = new FfmpegAudioRenderer(null, null, sink);
    assertEquals(C.TRACK_TYPE_AUDIO, renderer.getTrackType());
    // The host JVM has no Android native library or stack-trace formatter.
    Log.setLogStackTraces(false);
    try {
      assertEquals(C.FORMAT_UNSUPPORTED_TYPE, renderer.supportsFormatInternal(
          new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()));
    } finally {
      Log.setLogStackTraces(true);
    }
  }
}

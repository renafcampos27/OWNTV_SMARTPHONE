# Keep rules that :player-core imposes on anything consuming it. Empty for now: the engine's
# keep rules still live in app/proguard-rules.pro. Declared by consumerProguardFiles so moving a
# rule here later is a one-line change rather than a build-config change.

# Native JNI_OnLoad looks up the audio decoder and growOutputBuffer by their original names.
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder { *; }
-keep class androidx.media3.decoder.ffmpeg.FfmpegLibrary { *; }

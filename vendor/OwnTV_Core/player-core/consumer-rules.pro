# Keep rules required by the player library in every consuming application.

# Native JNI_OnLoad looks up the audio decoder and growOutputBuffer by their original names.
-keep class tv.own.owntv.player.ffmpeg.FfmpegAudioDecoder { *; }
-keep class tv.own.owntv.player.ffmpeg.FfmpegLibrary { *; }

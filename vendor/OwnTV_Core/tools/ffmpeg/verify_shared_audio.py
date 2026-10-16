"""Verify the pinned shared FFmpeg bridge, native ABIs and Java/JNI contract."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import struct
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = Path(__file__).with_name("audio_manifest.json")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--restore", action="store_true", help="Restore pinned Java sources only")
    parser.add_argument("--aar", type=Path, help="Verify an existing resolved Maven AAR")
    args = parser.parse_args()
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    data = args.aar.read_bytes() if args.aar else urllib.request.urlopen(
        manifest["native_artifact"], timeout=60).read()
    assert hashlib.sha256(data).hexdigest() == manifest["native_artifact_sha256"]
    for relative, spec in manifest["files"].items():
        target = (ROOT / relative).resolve()
        assert target.is_relative_to(ROOT), relative
        if args.restore:
            source = urllib.request.urlopen(spec["source"], timeout=60).read()
            assert hashlib.sha256(source).hexdigest() == spec["sha256"], relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source)
        assert hashlib.sha256(target.read_bytes()).hexdigest() == spec["sha256"], relative
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = {n for n in archive.namelist() if n.startswith("jni/") and n.endswith(".so")}
        assert names == set(manifest["native_files"])
        abis = set()
        for relative, spec in manifest["native_files"].items():
            native = archive.read(relative)
            assert hashlib.sha256(native).hexdigest() == spec["sha256"], relative
            assert native[:4] == b"\x7fELF" and native[5] == 1, relative
            is64 = native[4] == 2
            offset = struct.unpack_from("<Q" if is64 else "<I", native, 32 if is64 else 28)[0]
            size, count = struct.unpack_from("<HH", native, 54 if is64 else 42)
            for index in range(count):
                pos = offset + index * size
                if struct.unpack_from("<I", native, pos)[0] == 1:
                    alignment = struct.unpack_from("<Q" if is64 else "<I", native, pos + (48 if is64 else 28))[0]
                    assert alignment >= (16384 if is64 else 4096), (relative, alignment)
            if relative.endswith("libowntvffmpeg.so"):
                abis.add(relative.split("/")[1])
                for symbol in ["JNI_OnLoad", "tv/own/owntv/player/ffmpeg/FfmpegLibrary",
                               "tv/own/owntv/player/ffmpeg/FfmpegAudioDecoder", "ffmpegGetVersion",
                               "ffmpegGetInputBufferPaddingSize", "ffmpegHasDecoder", "ffmpegInitialize",
                               "ffmpegDecode", "ffmpegGetChannelCount", "ffmpegGetSampleRate", "ffmpegReset",
                               "ffmpegRelease", "growOutputBuffer", "libavcodec.so", "libswresample.so", "libavutil.so"]:
                    assert symbol.encode() + b"\0" in native, (relative, symbol)
        assert abis == {"arm64-v8a", "armeabi-v7a", "x86_64"}, abis
    assert not list((ROOT / "player-core/src/main/jniLibs").glob("*/libffmpegJNI.so"))
    assert not list((ROOT / "player-core/src/main/java/androidx/media3/decoder/ffmpeg").glob("*.java"))
    print("Verified shared FFmpeg: pinned AAR/Java hashes, three ABIs, JNI registration, dependencies and 16 KiB alignment on 64-bit ABIs.")


if __name__ == "__main__":
    main()

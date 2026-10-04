"""Verify pinned FFmpeg audio sources/binaries; --restore downloads only manifest files."""
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
    parser.add_argument("--restore", action="store_true")
    args = parser.parse_args()
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    archive = None
    if args.restore:
        data = urllib.request.urlopen(manifest["native_artifact"], timeout=60).read()
        assert hashlib.sha256(data).hexdigest() == manifest["native_artifact_sha256"]
        archive = zipfile.ZipFile(io.BytesIO(data))
    for relative, spec in manifest["files"].items():
        target = (ROOT / relative).resolve()
        assert target.is_relative_to(ROOT), relative
        if args.restore:
            source = spec["source"]
            data = archive.read(source.split("!/")[1]) if "!/" in source else urllib.request.urlopen(source, timeout=60).read()
            assert hashlib.sha256(data).hexdigest() == spec["sha256"], relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        data = target.read_bytes()
        assert hashlib.sha256(data).hexdigest() == spec["sha256"], relative
        if target.suffix == ".so":
            assert data[:4] == b"\x7fELF" and data[5] == 1, relative
            is64 = data[4] == 2
            offset = struct.unpack_from("<Q" if is64 else "<I", data, 32 if is64 else 28)[0]
            entry_size, count = struct.unpack_from("<HH", data, 54 if is64 else 42)
            for index in range(count):
                position = offset + index * entry_size
                if struct.unpack_from("<I", data, position)[0] == 1:
                    alignment = struct.unpack_from("<Q" if is64 else "<I", data, position + (48 if is64 else 28))[0]
                    assert alignment >= 16384, (relative, alignment)
            for owner, methods in {
                "FfmpegLibrary": ["ffmpegGetVersion", "ffmpegGetInputBufferPaddingSize", "ffmpegHasDecoder"],
                "FfmpegAudioDecoder": ["ffmpegInitialize", "ffmpegDecode", "ffmpegGetChannelCount", "ffmpegGetSampleRate", "ffmpegReset", "ffmpegRelease"],
            }.items():
                for method in methods:
                    symbol = f"Java_androidx_media3_decoder_ffmpeg_{owner}_{method}".encode()
                    assert symbol + b"\0" in data, (relative, method)
        print("OK", relative)
    print("Verified pinned Java sources, four native ABIs, JNI entry points and 16 KiB ELF alignment.")


if __name__ == "__main__":
    main()

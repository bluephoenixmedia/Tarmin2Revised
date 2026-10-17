"""Converts 24-bit PCM WAV files to the 16-bit PCM libGDX can decode.

libGDX's WAV decoder accepts only 8- and 16-bit PCM, so a 24-bit file throws when loaded (the game
logs and skips it; AudioFormatDecodabilityTest fails the build). Each original is copied to a backup
folder OUTSIDE the repository before it is overwritten, so no source audio is ever lost.

Usage, from the repository root:
    python tools/convert_wav_16bit.py assets/sounds/dimensional_shift.wav [more files ...]
    python tools/convert_wav_16bit.py --backup-dir D:/audio_originals assets/sounds/spells/*.wav

Files that are already 8- or 16-bit are left alone. The default backup folder is
<repo>/../Tarmin2_audio_originals, mirroring the path under assets/.
"""
import argparse
import os
import shutil
import sys
import wave

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def to_16bit(frames, channels):
    """Drops the low byte of every 24-bit little-endian sample (truncation: the top 16 bits)."""
    out = bytearray(len(frames) // 3 * 2)
    j = 0
    for i in range(0, len(frames) - 2, 3):
        out[j] = frames[i + 1]
        out[j + 1] = frames[i + 2]
        j += 2
    return bytes(out)


def convert(path, backup_root):
    with wave.open(path, 'rb') as w:
        params = w.getparams()
        if params.sampwidth != 3:
            return 'skipped (%d-bit)' % (params.sampwidth * 8)
        frames = w.readframes(params.nframes)

    rel = os.path.relpath(os.path.abspath(path), os.path.join(ROOT, 'assets'))
    backup = os.path.join(backup_root, rel)
    os.makedirs(os.path.dirname(backup), exist_ok=True)
    if not os.path.exists(backup):  # never overwrite an earlier backup with a converted file
        shutil.copy2(path, backup)

    converted = to_16bit(frames, params.nchannels)
    with wave.open(path, 'wb') as out:
        out.setnchannels(params.nchannels)
        out.setsampwidth(2)
        out.setframerate(params.framerate)
        out.writeframes(converted)
    return 'converted (%d -> %d bytes), original in %s' % (len(frames), len(converted), backup)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('files', nargs='+')
    parser.add_argument('--backup-dir', default=os.path.join(os.path.dirname(ROOT), 'Tarmin2_audio_originals'))
    args = parser.parse_args()

    failed = 0
    for f in args.files:
        try:
            print('%s: %s' % (f, convert(f, args.backup_dir)))
        except Exception as e:  # keep going: report every file
            failed += 1
            print('%s: FAILED %s' % (f, e))
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())

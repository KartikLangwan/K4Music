import yt_dlp
import json


# fmt: the itags of the chosen streaming quality, then any audio (same as the PC app).
def download(quickjs_bin: str, video_id: str, fmt: str = "bestaudio/best") -> str:
    opts = {
        "format": fmt,
        "js_runtimes": {"quickjs": {"path": quickjs_bin}},
        "socket_timeout": 12,
        "retries": 2,
        "extractor_retries": 2,
        "noplaylist": True,
        "quiet": True,
    }

    ydl = yt_dlp.YoutubeDL(opts)
    info = ydl.extract_info("https://www.youtube.com/watch?v=" + video_id, download=False)
    # sanitize_info drops objects json cannot write, whatever the yt-dlp version puts in the result.
    return json.dumps(ydl.sanitize_info(info))


def upgrade(package_name):
    try:
        import ensurepip

        ensurepip.bootstrap()
    except Exception as e:
        print(f"Error running ensurepip: {e}")

    try:
        import pip
        from pip._internal import main as pip_main

        # Only yt-dlp, wheels only (an sdist runs setup code at install time), no dependency swaps.
        if package_name != "yt-dlp":
            raise ValueError(f"Refusing to upgrade {package_name}")
        result = pip_main(
            ["install", "--upgrade", "--only-binary=:all:", "--no-deps", "yt-dlp>=2026.08.19"]
        )
        if result != 0:
            raise RuntimeError(f"Package update failed with exit code {result}")
        print(f"Successfully upgraded {package_name}")
    except Exception as e:
        print(f"Error upgrading package {package_name}: {e}")
        # Chaquopy must receive the failure so the UI cannot report false success.
        raise

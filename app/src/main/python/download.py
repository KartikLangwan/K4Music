import yt_dlp
import json


def download(quickjs_bin: str, video_id: str) -> str:
    opts = {
        "format": "bestaudio/best",
        "js_runtimes": {"quickjs": {"path": quickjs_bin}},
        "socket_timeout": 12,
        "retries": 2,
        "extractor_retries": 2,
        "noplaylist": True,
        "quiet": True,
    }

    return json.dumps(
        yt_dlp.YoutubeDL(opts).extract_info(
            "https://www.youtube.com/watch?v=" + video_id, download=False
        )
    )


def upgrade(package_name):
    try:
        import ensurepip

        ensurepip.bootstrap()
    except Exception as e:
        print(f"Error running ensurepip: {e}")

    try:
        import pip
        from pip._internal import main as pip_main

        result = pip_main(["install", "--upgrade", package_name])
        if result != 0:
            raise RuntimeError(f"Package update failed with exit code {result}")
        print(f"Successfully upgraded {package_name}")
    except Exception as e:
        print(f"Error upgrading package {package_name}: {e}")
        # Chaquopy must receive the failure so the UI cannot report false success.
        raise

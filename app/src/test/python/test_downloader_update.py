import importlib.util
import json
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import Mock, patch

sys.dont_write_bytecode = True
source = Path(__file__).resolve().parents[2] / "main" / "python" / "download.py"


class DownloaderUpdateTest(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location("k4_downloader", source)
        self.module = importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules, {"yt_dlp": types.ModuleType("yt_dlp")}):
            spec.loader.exec_module(self.module)

    def run_update(self, status=0, error=None):
        pip = types.ModuleType("pip")
        internal = types.ModuleType("pip._internal")
        internal.main = Mock(return_value=status, side_effect=error)
        ensurepip = types.ModuleType("ensurepip")
        ensurepip.bootstrap = Mock()
        with patch.dict(sys.modules, {"pip": pip, "pip._internal": internal, "ensurepip": ensurepip}):
            self.module.upgrade("yt-dlp")
        internal.main.assert_called_once_with(["install", "--upgrade", "yt-dlp"])

    def test_successful_install_completes(self):
        self.run_update()

    def test_nonzero_exit_reaches_android_error_handler(self):
        with self.assertRaisesRegex(RuntimeError, "exit code 1"):
            self.run_update(status=1)

    def test_installer_exception_reaches_android_error_handler(self):
        with self.assertRaisesRegex(OSError, "Disk full"):
            self.run_update(error=OSError("Disk full"))

    def test_extraction_uses_exact_video_and_bounded_network_options(self):
        instance = Mock()
        instance.extract_info.return_value = {"id": "abc", "url": "https://cdn.example/audio"}
        factory = Mock(return_value=instance)
        self.module.yt_dlp.YoutubeDL = factory
        result = json.loads(self.module.download("/native/libqjs.so", "abc"))
        self.assertEqual(result["id"], "abc")
        instance.extract_info.assert_called_once_with("https://www.youtube.com/watch?v=abc", download=False)
        options = factory.call_args.args[0]
        self.assertEqual(options["socket_timeout"], 12)
        self.assertEqual(options["retries"], 2)
        self.assertTrue(options["noplaylist"])


if __name__ == "__main__":
    unittest.main()

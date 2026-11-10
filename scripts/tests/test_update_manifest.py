"""Verify metadata describes the exact release artifact, including encoded URLs."""
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("manifest", Path(__file__).parents[1] / "create_update_manifest.py")
manifest = importlib.util.module_from_spec(spec)
spec.loader.exec_module(manifest)


class UpdateManifestTest(unittest.TestCase):
    def test_describes_exact_signed_bytes_and_encoded_filename(self):
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder) / "App preview.apk"
            data = b"signed APK fixture\x00\xff"
            apk.write_bytes(data)
            result = manifest.create(apk, "42", "0.1.2-pre.42", "v0.1.2-pre.42")
            self.assertEqual(result["versionCode"], 42)
            self.assertEqual(result["size"], len(data))
            self.assertEqual(result["sha256"], hashlib.sha256(data).hexdigest())
            self.assertTrue(result["url"].endswith("/v0.1.2-pre.42/App%20preview.apk"))

    def test_rejects_invalid_version_code(self):
        for code in ["0", "-1", "2147483648", "not-a-number"]:
            with self.assertRaises(ValueError):
                manifest.create("unused.apk", code, "v", "tag")

    def test_missing_artifact_cannot_produce_manifest(self):
        with self.assertRaises(FileNotFoundError):
            manifest.create("missing-update.apk", 1, "v", "tag")

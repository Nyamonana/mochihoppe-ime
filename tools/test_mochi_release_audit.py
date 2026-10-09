"""Offline regression tests for production artifact boundaries."""
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

import mochi_release_audit as audit


class ReleaseAuditTest(unittest.TestCase):
    def test_classification_keeps_test_source_but_rejects_local_output(self):
        for path in ("app/src/test/java/SafetyTest.kt", "mochi-input-lab/src/main/java/Notepad.kt", "tools/test_mochi_release_audit.py"):
            self.assertEqual("development-only", audit.category(path))
        for path in (".idea/misc.xml", "app/build/outputs/anything", "tools/enter_parity/results/capture.jsonl", "app.apk"):
            self.assertEqual("unnecessary-generated", audit.category(path))
        for path in ("local.properties", ".env.production", "release-private/key.jks", "release-private/signing.credentials.clixml"):
            self.assertEqual("sensitive-local", audit.category(path))
        self.assertEqual("production-required", audit.category("app/src/main/assets/system/token.dat.zip"))

    def check_artifact(self, extras=None, attrs='', instrumentation=''):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "app.apk"
            manifest = root / "AndroidManifest.xml"
            manifest.write_text(
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.mochihoppe.explorer.ime">'
                f'<application android:usesCleartextTraffic="false" {attrs}/>{instrumentation}</manifest>', encoding="utf8")
            with zipfile.ZipFile(apk, "w") as archive:
                for name in {"dictionary.dat"} | audit.GENERATED | audit.NOTICES:
                    archive.writestr("assets/" + name, b"runtime")
                archive.writestr("classes.dex", b"runtime bytecode")
                for name, contents in (extras or {}).items():
                    archive.writestr(name, contents)
            with patch.object(audit, "policy", return_value=({"dictionary.dat"}, set())):
                return audit.audit_apk(apk, manifest)

    def test_minimal_runtime_artifact_passes(self):
        self.assertEqual([], self.check_artifact()["findings"])

    def test_unapproved_assets_and_private_files_fail(self):
        findings = self.check_artifact({"assets/test.json": b"fixture", "key.jks": b"private"})["findings"]
        self.assertEqual({"unapproved-asset", "development-or-private-file"}, {item["reason"] for item in findings})

    def test_test_code_in_dex_fails(self):
        findings = self.check_artifact({"classes2.dex": b"Lorg/junit/Test;"})["findings"]
        self.assertIn("test-code-in-dex", {item["reason"] for item in findings})

    def test_debuggable_test_only_and_instrumentation_fail(self):
        for attrs in ('android:debuggable="true"', 'android:testOnly="true"'):
            self.assertIn("unsafe-release-manifest", {item["reason"] for item in self.check_artifact(attrs=attrs)["findings"]})
        self.assertIn("instrumentation-in-production", {item["reason"] for item in self.check_artifact(instrumentation='<instrumentation/>')["findings"]})

    def test_secret_patterns_detect_without_embedding_credentials(self):
        sample = ("gh" + "p_" + "a" * 36).encode()
        self.assertTrue(any(pattern.search(sample) for pattern in audit.SECRETS))


if __name__ == "__main__":
    unittest.main()

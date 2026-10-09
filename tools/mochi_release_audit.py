"""Read-only source/APK audit; generated reports stay under ignored build/.

No credentials or matched secret contents are printed. This is a supplementary
check, not proof that a repository is secret-free or that licenses are satisfied.
"""
import argparse
import collections
import hashlib
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / "release/runtime-assets.txt"
GENERATED = {
    "connection/connection.compact", "connection/connection.compact.index",
    "system/system.compact.kdict", "english/english.compact.kdict",
}
NOTICES = {"licenses/LICENSE", "licenses/ENGLISH_DICTIOANRY_LICENSE", "licenses/NOTICE-MOCHI.md"} | {
    "licenses/" + p.name for p in (ROOT / "release/licenses").glob("*") if p.is_file()
}
AGP = {"dexopt/baseline.prof", "dexopt/baseline.profm"}
SECRETS = [
    re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
    re.compile(rb"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{50,})\b"),
    re.compile(rb"\bAKIA[0-9A-Z]{16}\b"),
    re.compile(rb"\bAIza[0-9A-Za-z_-]{35}\b"),
]
PRIVATE_SUFFIX = {".jks", ".keystore", ".p12", ".pem", ".key"}
GENERATED_DIRS = {".git", ".gradle", "build", ".cxx", ".externalNativeBuild", "__pycache__"}
MODULES = {"app", "core", "custom_keyboard", "flexbox", "gojuon_keyboard", "qwerty_keyboard", "symbol_keyboard", "tenkey"}


def policy():
    lines = [s.strip() for s in POLICY.read_text(encoding="utf8").splitlines()]
    return ({s for s in lines if s and not s.startswith(("#", "!"))},
            {s[1:] for s in lines if s.startswith("!")})


def category(path):
    p = Path(path)
    parts = p.parts
    if (path.startswith("release-private/") or p.name == "local.properties"
            or p.name.startswith(".env") and p.name != ".env.example" or p.suffix in PRIVATE_SUFFIX):
        return "sensitive-local"
    if (any(s in GENERATED_DIRS or s == ".idea" for s in parts)
            or path.startswith("tools/enter_parity/results/") and p.name != "README.md"
            or p.suffix in {".apk", ".aab", ".log", ".tmp", ".pyc", ".hprof"}):
        return "unnecessary-generated"
    if parts[0] in MODULES and "src" in parts and "main" in parts or "src" in parts and any(s in parts for s in ("lite", "liteStandard", "release")):
        return "production-required"
    if p.name.endswith(".gradle") or path in {"gradle.properties", "gradlew", "gradlew.bat", "LICENSE", "ENGLISH_DICTIOANRY_LICENSE", "NOTICE-MOCHI.md"} or parts[0] in {"gradle", "release"}:
        return "production-required-build-or-notice"
    return "development-only"


def tracked_candidates():
    result = subprocess.run(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=ROOT, check=True, capture_output=True)
    return sorted(set(p.decode("utf8") for p in result.stdout.split(b"\0") if p))


def scan_sources():
    candidates = tracked_candidates()
    findings = []
    inventory = []
    # Also classify ignored local state, without traversing generated/cache trees.
    import os
    for folder, dirs, files in os.walk(ROOT):
        relative = Path(folder).relative_to(ROOT)
        for name in list(dirs):
            if name in GENERATED_DIRS:
                inventory.append({"path": (relative / name).as_posix() + "/**", "category": "unnecessary-generated"})
                dirs.remove(name)
        for name in files:
            rel = (relative / name).as_posix()
            inventory.append({"path": rel, "category": category(rel)})
    for rel in candidates:
        path = ROOT / rel
        if not path.is_file():
            continue
        kind = category(rel)
        if kind in {"sensitive-local", "unnecessary-generated"}:
            findings.append({"path": rel, "reason": kind})
        if path.stat().st_size <= 4 * 1024 * 1024:
            contents = path.read_bytes()
            if any(pattern.search(contents) for pattern in SECRETS):
                findings.append({"path": rel, "reason": "possible-secret-review-required"})
    runtime, excluded = policy()
    actual = {p.relative_to(ROOT / "app/src/main/assets").as_posix() for p in (ROOT / "app/src/main/assets").rglob("*") if p.is_file()}
    if actual - excluded != runtime:
        findings.append({"reason": "runtime-policy-mismatch", "paths": sorted(actual ^ (runtime | excluded))})
    assets = [{"path": rel, "sha256": hashlib.sha256((ROOT / "app/src/main/assets" / rel).read_bytes()).hexdigest(), "redistribution": "requires-review"} for rel in sorted(runtime)]
    review = ROOT / "release/runtime-sha256.json"
    if review.is_file():
        expected = json.loads(review.read_text(encoding="utf8"))["assets"]
        actual_hashes = {item["path"]: item["sha256"] for item in assets}
        if expected != actual_hashes:
            findings.append({"reason": "reviewed-asset-hash-mismatch"})
        else:
            for item in assets:
                item["redistribution"] = "provenance-and-notices-recorded"
    return {"categories": dict(collections.Counter(item["category"] for item in inventory)), "files": inventory, "runtime_assets": assets, "push_findings": findings}


def audit_apk(apk, manifest):
    runtime, _ = policy()
    allowed = runtime | GENERATED | NOTICES | AGP
    findings = []
    with zipfile.ZipFile(apk) as archive:
        names = {name for name in archive.namelist() if not name.endswith("/")}
        assets = {name[7:] for name in names if name.startswith("assets/")}
        for name in sorted(assets - allowed):
            findings.append({"path": name, "reason": "unapproved-asset"})
        for name in sorted((runtime | GENERATED | NOTICES) - assets):
            findings.append({"path": name, "reason": "missing-runtime-asset"})
        for name in sorted(runtime & assets):
            source = ROOT / "app/src/main/assets" / name
            if source.is_file() and hashlib.sha256(archive.read("assets/" + name)).digest() != hashlib.sha256(source.read_bytes()).digest():
                findings.append({"path": name, "reason": "stale-runtime-asset"})
        for name in sorted(names):
            parts = set(Path(name).parts)
            if (parts & {"test", "tests", "androidTest", "src", "tools", ".git", ".gradle", "cache", "logs"}
                    or Path(name).suffix in PRIVATE_SUFFIX | {".kt", ".java", ".py", ".log", ".tmp", ".hprof"}
                    or Path(name).name == "local.properties" or Path(name).name.startswith(".env")):
                findings.append({"path": name, "reason": "development-or-private-file"})
            if re.fullmatch(r"classes\d*\.dex", name):
                data = archive.read(name)
                if re.search(rb"L(?:org/junit/|org/robolectric/|org/mockito/|androidx/test/espresso/|com/mochihoppe/inputlab/)", data):
                    findings.append({"path": name, "reason": "test-code-in-dex"})
        size = sum(info.file_size for info in archive.infolist())
    namespace = "{http://schemas.android.com/apk/res/android}"
    root = ElementTree.parse(manifest).getroot()
    application = root.find("application")
    if root.get("package") != "com.mochihoppe.explorer.ime" or application is None:
        findings.append({"reason": "wrong-application"})
    elif application.get(namespace + "debuggable", "false") != "false" or application.get(namespace + "testOnly", "false") != "false" or application.get(namespace + "usesCleartextTraffic") != "false":
        findings.append({"reason": "unsafe-release-manifest"})
    if root.findall("instrumentation"):
        findings.append({"reason": "instrumentation-in-production"})
    return {"apk": Path(apk).name, "bytes": Path(apk).stat().st_size, "sha256": hashlib.sha256(Path(apk).read_bytes()).hexdigest(), "uncompressed_bytes": size, "asset_count": len(assets), "findings": findings}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--check-push", action="store_true")
    args = parser.parse_args()
    if bool(args.apk) != bool(args.manifest):
        parser.error("--apk and --manifest are required together")
    report = scan_sources()
    if args.apk:
        report["production_artifact"] = audit_apk(args.apk, args.manifest)
    output = ROOT / "build/reports/mochi-release-audit.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf8")
    print("Inventory:", report["categories"])
    print("Push findings (paths only):", json.dumps(report["push_findings"], ensure_ascii=False))
    if args.apk:
        print("APK audit:", json.dumps(report["production_artifact"], ensure_ascii=False))
    print("Detailed local report: build/reports/mochi-release-audit.json")
    failed = bool(args.apk and report["production_artifact"]["findings"]) or bool(args.check_push and report["push_findings"])
    return int(failed)


if __name__ == "__main__":
    sys.exit(main())

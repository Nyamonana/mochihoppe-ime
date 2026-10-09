# Production distribution boundary

The production artifact is ONE installable, independently signed Lite release
APK. Source code, unit/device tests, test data, build tooling and developer docs
remain in the source repository for reproducibility; they are not release assets.
Do not upload a workspace ZIP, build directory, debug APK, unsigned APK, mapping
file, log, local settings, signing key or test APK to a public Release.

## Repository classification

| Class | Contents | APK / public release |
| --- | --- | --- |
| Production required | `*/src/main`, Lite resources, compiled runtime dependencies, conversion/symbol data listed in `release/runtime-assets.txt` | Compiled/shrunk code and reviewed runtime assets only |
| Build / legal required | Gradle wrapper, build files, runtime asset policy, LICENSE and dictionary notices | Notices included; build tools stay in source only |
| Development only | `src/test`, `src/androidTest`, `src/debug`, labs, tools, investigation, documentation, CI, screenshots/store metadata | Excluded from APK; retain useful source/tests |
| Unnecessary/generated | `.gradle`, `.idea`, `**/build`, caches, temporary files, logs, test output, APK/AAB outputs | Ignored, never public release assets |
| Sensitive/local | `local.properties`, `.env*`, private keys/keystores, signing credentials | Never tracked, never APK, never public release |

`python tools/mochi_release_audit.py` inventories every non-cache file and marks
generated/cache trees as excluded groups. The local detailed file inventory and
runtime SHA-256 list go to ignored `build/reports/mochi-release-audit.json`.
`--check-push` blocks tracked/unignored local state, signing files and strong
credential-pattern matches. This supplement does not replace human diff review
or a full secret/license audit. It never prints matched secret values.

## Build boundary

- Only Lite/standard variants are enabled. AI models/native Zenz modules are not
  build dependencies. Labs are separate APK modules, not app dependencies.
- Gradle stages explicit asset paths, failing on unclassified additions. Two
  build-only Mozc JSON manifests remain locally but are omitted from the APK.
- Conversion data and serialized fallback dictionaries are runtime data, not
  test fixtures. Removing fallback files blindly breaks existing override/error
  recovery. Keep them until a separately tested format migration removes their
  runtime dependency; do not sacrifice working conversion just to shrink bytes.
- R8 code shrinking, resource shrinking and release log removal are enabled.
- No Docker deployment exists. `.dockerignore` is a defensive build-context
  boundary, not a substitute for Android/Gradle artifact checks.

## Release gate / procedure

1. Run focused unit tests and `assembleLiteStandardReleaseUnsigned` for local
   production-equivalent verification. The unsigned APK is NOT a public download.
2. Complete `NOTICE-MOCHI.md` asset/dependency redistribution review. Do not set
   `mochiRedistributionReviewed=true` just to bypass this condition.
3. Configure an independent release signing key outside Git; keep passwords in
   local protected configuration. Never use the Android debug key for production.
4. Build `assembleLiteStandardRelease -PmochiRedistributionReviewed=true`.
5. Audit the APK with `tools/mochi_release_audit.py --apk <APK> --manifest <merged
   release AndroidManifest.xml>`. Verify the final APK's manifest using Android
   SDK `aapt2 dump xmltree --file AndroidManifest.xml` (or `apkanalyzer manifest print`), verify signature using `apksigner verify`,
   and confirm the production search and image validation APIs are working.
6. Review `git diff`, staged changes, `git status` and audit findings before any
   push. Target only the user's fork/repository, never the upstream origin.
7. Publish only the audited signed APK with checksums in the release description.
   GitHub's source archives are source distributions, not Android runtime bundles.
   Keep signing keys and R8 mapping files private for future updates/diagnosis.

Public publication was authorized on 2026-10-10 at Nyamonana/mochihoppe-ime.
The reviewed assets are sealed in release/runtime-sha256.json. ATOK-derived rules
and added entries were excluded with owner approval; the system dictionary was
regenerated. See release/ASSET_PROVENANCE.md and the bundled release/licenses.
The public repository starts from a reviewed root snapshot, not upstream Git
history containing unreviewed asset versions and historical generated outputs.
The upstream commit remains documented for attribution and comparison.
Use tools/mochi_signing.ps1 -Mode Release to audit and build with the existing
independent production key. See SIGNING.md for protected key/password backups.
Signing or a successful build alone never constitutes redistribution review.

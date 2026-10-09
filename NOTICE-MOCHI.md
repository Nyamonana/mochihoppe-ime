# License / provenance inventory

Upstream: https://github.com/KazumaProject/JapaneseKeyboard
Tag v1.7.128; commit be9cccd7e6fef176667a12eb66fa6fe64380c9db.
MIT copyright Kazuma Naka: preserve root LICENSE. Mochi modifications are MIT under the same terms.
Do not imply endorsement by Sumire's author, Google, Mozc, or the Android project.

## Preserved upstream materials

- Root LICENSE and ENGLISH_DICTIOANRY_LICENSE.
- License screens/classes under `setting_activity` and license resource notices.
- Mozc/NAIST dictionary notices are separate from the Kotlin application license.
- Wikipedia/UT-derived dictionary material may have attribution/share-alike terms. The application MIT license does not relicense this material.
- Gradle dependencies retain their respective licenses; no additional third-party network/image SDK was introduced for Mochi.

## Runtime asset review

Review for the public snapshot was completed on 2026-10-10, bound to
release/runtime-sha256.json. Sources/changes: release/ASSET_PROVENANCE.md.
Owner approved excluding ATOK-derived optional priorities/extra entries. Basic
conversion was regenerated, not removed; old local binaries are not published.

Full notices in release/licenses are packaged as assets/licenses and readable
offline from 「派生元・ライセンスについて」. Preserve those notices, LICENSE,
ENGLISH_DICTIOANRY_LICENSE and applicable data terms when redistributing.
THIRD-PARTY-NOTICES-MOCHI.md maps attribution/modifications. Mochi modifications
Copyright 2026 Nyamonana, MIT under the same terms.
This record is not a warranty/legal opinion. New or changed assets require review.

## Review policy (continues to apply to future changes)

`app/src/main/assets` includes system, mozc, ngram, wiki, web, neologd, english,
english_reading, reading_correction, person_name, places, kotowaza, single_kanji,
emoji, emoticon, symbol, and connection/POS data. Except for the documented safe
regeneration/exclusions above, upstream data is retained; an MIT code license alone is NOT proof that each
asset can be redistributed under MIT.

Before a public binary release, record source/version/hash/license/attribution
for each included asset group and dependency. Preserve required notices and
share-alike obligations. Exclude optional assets lacking sufficient provenance;
if a required conversion dictionary is unresolved, BLOCK release rather than
remove it blindly and ship broken conversion. Local debug APKs are not a claim
of completed redistribution review. Public repository/APK publication was
requested on 2026-10-10. The selected destination is Nyamonana/mochihoppe-ime;
an independent production signing key has been generated locally and is never
distributed with source or binaries. The asset review remains a prerequisite
for public source/asset uploads and APK publication, not implied by authorization
or signing a private candidate.

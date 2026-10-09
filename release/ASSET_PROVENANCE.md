# Bundled asset provenance (2026-10-10)

## Completed review for the public snapshot

Historical investigation below is retained, not current release status. Review
covers 53 inputs sealed in runtime-sha256.json; changes invalidate that check.
This is a provenance/notice record, not a legal warranty. Data keeps its separate
licenses, not application MIT.

- Sumire base: be9cccd7e6fef176667a12eb66fa6fe64380c9db.
- Generator v1.7.256: 4080e406fa62650061c4e7d3f24daf73cec97dc7.
  https://github.com/KazumaProject/kotlin-kana-kanji-converter/releases/tag/v1.7.256
- CI https://github.com/KazumaProject/kotlin-kana-kanji-converter/actions/runs/35291148296
  pins Mozc cbbb6e1bd181cb9f3b409622d916a53a35400ec7 and JapaneseCorpus
  v2026.0914.15, correcting the older snapshot in the inherited notice.
- Initially 47/54 inherited files match generator ZIP by SHA-256. Four
  system/ngram files are regenerated and one unigram excluded, leaving 42
  unchanged connection/POS, supplemental Japanese, symbol, correction/proverb,
  English-reading and zero-query files from that exact release.
- Four English prediction/glide files match uncompressed English LOUDS v23
  exactly: https://github.com/KazumaProject/kotlin-louds-english/releases/tag/v23
  WikiText-103/Wikipedia CC BY-SA 4.0, original citation retained.
- Three remaining files (id.def and segmenter/node data) are pinned Sumire
  sources/build rules, retaining Google copyright/BSD source notices.
- Owner approved excluding ATOK-derived priorities/extra entries. The generator
  entry point in tools/dictionary never reads those sources. Original CI: 471
  pairs, 198 already in base, 273 extra. Extra additions are omitted, ordinary
  base retained. Safe system: 1,291,499 entries, identical POS bytes. Safe ngram:
  84 project-owned rules from an explicit four-file allowlist. Optional original
  unigram is ignored and never packaged/pushed.
- Full notices in release/licenses are packaged and readable offline. Mozc
  BSD/NAIST/ICOT/Okinawa, JMdict CC BY-SA 4.0, Wikipedia/UT historical CC BY-SA
  (including 3.0) and Neologd Apache 2.0/source credits are preserved. Detailed
  group mapping/modifications: THIRD-PARTY-NOTICES-MOCHI.md.
- Compact files are format conversions. Runtime Apache-2.0 library attribution
  screens/full license retained; test and AI/model modules are not APK dependencies.

Public Git starts from this reviewed snapshot, not historical binary revisions
containing excluded data, IDE state or test logs. Upstream attribution/base SHA
remain; original Git objects/local originals stay local. Regeneration:
tools/dictionary/README.md.

## Historical investigation (superseded by the review above)

This is a factual inventory, NOT completed legal clearance. Runtime source paths
are listed in `runtime-assets.txt`; exact SHA-256 values are generated locally by
`tools/mochi_release_audit.py`. Public redistribution remains blocked while
required asset provenance is unresolved.

At the start of review, bundled source data came from Sumire tag v1.7.128, commit
`be9cccd7e6fef176667a12eb66fa6fe64380c9db`. The fork retains the original data;
the compact connection/system/English files are generated from that data by
the inherited Gradle conversion tasks, not independent datasets.

| Asset groups | Evidence found | Remaining review |
| --- | --- | --- |
| system, connection, id/POS data | Upstream README credits Mozc; inherited license screen includes BSD conditions, NAIST/IPAdic and ICOT conditions | Pin original dataset/version, preserve complete copyright and license texts, confirm any combined sources |
| mozc segmenter/node attributes/zero-query | Build manifests identify Mozc rule paths; inherited Mozc license screen | Pin original Mozc revision and verify complete notices against those exact files |
| person_name, places, web, wiki, neologd | Upstream README credits Mozc UT (CC BY-SA); inherited Neologd and Wiki notices | Establish each dataset version, applicable license version, attribution/source and share-alike obligations; generic MIT is insufficient |
| english | `ENGLISH_DICTIOANRY_LICENSE` identifies WikiText/Salesforce, CC BY-SA 4.0 and the source publication | Pin generation input/version and retain full required license/attribution material |
| english_reading, kotowaza, reading_correction, single_kanji, emoji, emoticon, symbol | Bundled conversion/symbol assets; all runtime references retained | No per-group original source/version/license manifest found in this checkout |
| ngram | Bundled system ngram/unigram files and format/runtime checks | Original training/generation data and redistribution terms not recorded in this checkout |

Primary references checked:

- Sumire code and credits: https://github.com/KazumaProject/JapaneseKeyboard
- Mozc license: https://github.com/google/mozc/blob/master/LICENSE
- Mozc dictionary-specific conditions: https://github.com/google/mozc/blob/master/src/data/dictionary_oss/README.txt
- Neologd conditions referenced by the inherited screen: https://github.com/neologd/mecab-ipadic-neologd/blob/master/COPYING

These references describe upstream projects; they do not establish that every
binary asset in this checkout derives from the current revision of those
projects. Do not mark the release review complete based only on these links.
Do not remove mandatory conversion data merely to make a release gate pass.

Additional upstream research on 2026-10-10 located possible generation tools:
`KazumaProject/ngram-builder-jp`, `KazumaProject/mozc-ut-dictioanry-builder`, and
`KazumaProject/english_dictionary_builder`. The Japanese ngram builder accepts
arbitrary Hugging Face/local corpora (its README demonstrates Wikipedia), so the
tool's existence alone does not identify the input corpus/version used for the
bundled binary files. Their relationship to these exact asset hashes remains
unconfirmed. Do not infer redistribution clearance from a README example.

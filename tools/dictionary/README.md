# Reviewed dictionary regeneration

Generator-only source, never in APK. Shipped binaries are checked in: normal app
builds do not download dictionaries. Regeneration executes upstream Gradle code
and requires network access. Never silently upgrade fixed sources.

1. Check out KazumaProject/kotlin-kana-kanji-converter commit
   4080e406fa62650061c4e7d3f24daf73cec97dc7 in ignored build/provenance/converter.
2. Check out google/mozc commit cbbb6e1bd181cb9f3b409622d916a53a35400ec7 in ignored
   build/provenance/mozc. Copy dictionary00.txt through dictionary09.txt, suffix.txt
   and id.def from src/data/dictionary_oss into converter src/main/resources.
3. Inside converter, git apply this directory's generator.patch (JDK21/entry
   point only). Copy MochiDictionaryBuild.kt into converter src/main/kotlin.
4. From app root with JDK21, run the app's Gradle8.11.1 wrapper:

   ```powershell
   .\gradlew.bat -p build/provenance/converter installDist --no-configuration-cache
   $env:JAVA_OPTS = '-Xmx4g'
   Push-Location build/provenance/converter
   .\build\install\kana-kanji-converter\bin\kana-kanji-converter.bat <absolute-app-src-main-assets-path>
   Pop-Location
   ```

5. Outputs: converter build/mochi-safe. It aborts if POS bytes change, protecting
   all other dictionaries. Expect 1,291,499 system entries/84 ngram rules.
   ATOK word/ngram/unigram inputs are never read by this entry point.
6. Preserve originals privately if needed. Install only three generated ZIPs
   into app/src/main/assets/system and system_ngram.dat into app/src/main/assets/ngram.
   Never install a unigram or the generator's entire output directory.
7. Review notices before updating release/runtime-sha256.json. Run
   MochiDictionaryRegressionTest, SystemNgramRuntimeTest, PackedSystemNgramDictionaryTest,
   Mochi UI/safety/input tests, release build and APK audit. Common readings must
   resolve, compact/fallback candidates match, and optional unigram be absent.

Upstream developer tests mentioning ATOK are not a shipped dictionary. Generator
downloads/worktree stay under ignored build/, not Git or Release attachments.

// Compile inside kotlin-kana-kanji-converter 4080e406fa62650061c4e7d3f24daf73cec97dc7.
// Generator-only code. Never packaged in the APK.
package com.kazumaproject

import com.kazumaproject.dictionary.DicUtils
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.ngram.NgramSourceParser
import com.kazumaproject.ngram.SystemNgramBinaryBuilder
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun main(args: Array<String>) {
    val appAssets = File(args.single()).canonicalFile
    require(appAssets.resolve("system/token.dat.zip").isFile)
    val utils = DicUtils()
    val sources = (0..9).map { "/dictionary%02d.txt".format(it) } + "/suffix.txt"
    val base = with(Constants) { utils.getListDictionary(sources) + DIC_LIST + CUSTOM_LIST +
        NAME_LIST + FIXED_LIST + DIFFICULT_LIST + SYMBOL_LIST + NAME_MUSIC_LIST +
        NAME_IT_LIST + VERB_LIST + DOMAIN + ERA + PLACE + WORDS + ZENKANKU_LIST +
        ADDS_NEW_WORDS + PHISIC_NOUN_LIST + FIGHT_NAME + FOOD_NAME +
        ENTERTAIMENT_NAME + RESCORE_WORDS }
    // The upstream release adds ATOK-derived missing pairs here. Do not add them.
    val grouped = base.groupBy { it.yomi }.toSortedMap(compareBy({ it.length }, { it }))
    val output = File("build/mochi-safe").apply { mkdirs() }
    val pos = output.resolve("pos_table.dat")
    val posBuild = output.resolve("pos_table_for_build.dat")
    TokenArray().buildPOSTable(grouped, 1, pos.path)
    TokenArray().buildPOSTableWithIndex(grouped, 1, posBuild.path)
    require(pos.readBytes().contentEquals(appAssets.resolve("pos_table.dat").readBytes())) {
        "POS table changed; other dictionary assets would be incompatible. Do not publish."
    }
    buildAndWriteDictionaryArtifacts(
        grouped, output.resolve("yomi.dat").path, output.resolve("tango.dat").path,
        output.resolve("token.dat").path, mode = 1, skipKanaOnlyTango = true,
        posTableForBuildPath = posBuild.path,
    )
    for (name in listOf("yomi", "tango", "token")) {
        ZipOutputStream(output.resolve("$name.dat.zip").outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("$name.dat").apply { time = 0 })
            output.resolve("$name.dat").inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
    val files = listOf("general.ngram", "collocation.ngram", "conversion-preferences.ngram", "kaitai.ngram")
    val rules = files.flatMap { NgramSourceParser.parseFile(File("src/main/ngram", it)) }
    val report = SystemNgramBinaryBuilder.build(rules, appAssets.resolve("id.def"), output.resolve("system_ngram.dat"))
    println("Safe system entries=${base.size}; safe ngram rules=${report.ruleCount}; POS table unchanged")
    println("No ATOK dictionary, ngram or unigram input is read by this entry point.")
    for (name in listOf("yomi.dat.zip", "tango.dat.zip", "token.dat.zip", "system_ngram.dat")) {
        val hash = MessageDigest.getInstance("SHA-256").digest(output.resolve(name).readBytes())
            .joinToString("") { "%02x".format(it) }
        println("$hash  $name")
    }
}

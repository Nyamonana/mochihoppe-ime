package com.kazumaproject.markdownhelperkeyboard.converter.compact

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MochiDictionaryRegressionTest {
    @Test fun regeneratedSystemDictionaryPreservesCommonWordsAndCompactFallbackParity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun input(path: String) = DictionaryBinaryReader.openZipAwareObject(context.assets.open(path), path)
        val yomi = input("system/yomi.dat.zip").use { LOUDSWithTermId().readExternalNotCompress(it) }
        val tango = input("system/tango.dat.zip").use { LOUDS().readExternalNotCompress(it) }
        // The legacy reader mutates its receiver; its return value is a fresh empty instance.
        // Mirror DictionaryBinaryReader.loadTokenArrayInto instead of using that return value.
        val token = TokenArray().also { array -> input("system/token.dat.zip").use { array.readExternal(it) } }
        input("pos_table.dat").use { token.setPOSTable(it.readObject() as ShortArray, it.readObject() as ShortArray) }
        val fallback = CompactDictionaryTriple(tango, yomi, token, SuccinctBitVector(yomi.LBS),
            SuccinctBitVector(yomi.isLeaf), SuccinctBitVector(token.bitvector), SuccinctBitVector(tango.LBS))
        val packed = context.assets.open("system/system.compact.kdict").use {
            CompactSystemDictionaryReader.read(ByteBuffer.wrap(it.readBytes()))
        }.get(CompactDictionaryKind.SYSTEM)
        fun words(dictionary: CompactDictionaryTriple, reading: String): List<String> {
            val node = dictionary.yomiTrie.getNodeIndex(reading, dictionary.yomiLbsIndex)
            assertTrue("reading=$reading node=$node labels=${dictionary.yomiTrie.labels.size}", node > 0)
            val term = dictionary.yomiTrie.getTermId(node, dictionary.yomiLeafIndex)
            assertTrue("reading=$reading node=$node term=$term", term > 0)
            return dictionary.tokenArray.getListDictionaryByYomiTermId(term, dictionary.tokenIndex).map {
                when (it.nodeId) {
                    -2 -> reading
                    -1 -> reading.hiraToKata()
                    else -> dictionary.tangoTrie.getLetter(it.nodeId, dictionary.tangoLbsIndex)
                }
            }
        }
        for ((reading, expected) in mapOf("にほん" to "日本", "かんじ" to "漢字", "へんかん" to "変換", "とうきょう" to "東京")) {
            val raw = words(fallback, reading)
            assertTrue("$reading -> $expected; actual=$raw", expected in raw)
            assertEquals(reading, raw, words(packed, reading))
        }
        assertEquals(fallback.tokenArray.getWordCosts().size, packed.tokenArray.getWordCosts().size)
        assertArrayEquals(fallback.tokenArray.leftIds, packed.tokenArray.leftIds)
        assertArrayEquals(fallback.tokenArray.rightIds, packed.tokenArray.rightIds)
    }
}

package com.kazumaproject.markdownhelperkeyboard.mochi

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MochiSafetyTest {
    private val url = "https://img.mochi-hoppe-explorer.com/i/abcdefg.png"
    @Test fun insertsOnlyTextWithNoEnterSendOrClipboardOperation() {
        val connection = mock<InputConnection> { on { commitText(url, 1) } doReturn true }
        val inserter = MochiUrlInserter({ 1 }, { connection })
        assertTrue(inserter.insert(1, connection, url))
        verify(connection).commitText(url, 1)
        verifyNoMoreInteractions(connection)
    }
    @Test fun staleSessionsConnectionsAndInvalidUrlsCannotInsert() {
        val connection = mock<InputConnection>()
        val other = mock<InputConnection>()
        val inserter = MochiUrlInserter({ 2 }, { connection })
        assertFalse(inserter.insert(1, connection, url))
        assertFalse(inserter.insert(2, other, url))
        assertFalse(inserter.insert(2, connection, "$url\n"))
        assertFalse(inserter.insert(2, connection, "https://evil.example/i/abcdefg.png"))
        verifyNoInteractions(connection, other)
    }
    @Test fun connectionFailureDoesNotTryAnotherOperation() {
        val connection = mock<InputConnection> { on { commitText(url, 1) } doReturn false }
        assertFalse(MochiUrlInserter({ 1 }, { connection }).insert(1, connection, url))
        verify(connection).commitText(url, 1); verifyNoMoreInteractions(connection)
    }
    @Test fun passwordsPinsPrivateAndNonTextInputsAreBlocked() {
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        assertTrue(MochiInputPolicy.allowed(info, false))
        assertFalse(MochiInputPolicy.allowed(info, true))
        info.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        assertFalse(MochiInputPolicy.allowed(info, false))
        info.imeOptions = 0
        for (type in listOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_PHONE, InputType.TYPE_NULL)) {
            info.inputType = type; assertFalse("type=$type", MochiInputPolicy.allowed(info, false))
        }
        assertFalse(MochiInputPolicy.allowed(null, false))
    }
    @Test fun validatesHostsPortsPathsQueriesAndStaticThumbnails() {
        assertTrue(MochiUrlPolicy.allowed(url, false))
        assertTrue(MochiUrlPolicy.allowed("https://img.mochi-hoppe-explorer.com/t/abcdefg.webp?v=00000000-0000-4000-8000-000000000001", true))
        for (bad in listOf("http://img.mochi-hoppe-explorer.com/i/abcdefg.png", "$url?raw=1", "$url#fragment",
            "https://img.mochi-hoppe-explorer.com:444/i/abcdefg.png", "https://user@img.mochi-hoppe-explorer.com/i/abcdefg.png",
            "https://pub.mochi-hoppe-explorer.com/meme/id", "javascript:alert(1)", "https://img.mochi-hoppe-explorer.com/t/abcdefg.webp")) assertFalse(bad, MochiUrlPolicy.allowed(bad, false))
    }
}

package com.kazumaproject.markdownhelperkeyboard.mochi

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MochiLegalTest {
    @Test fun redistributionNoticesAreReadableOffline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val files = context.assets.list("licenses").orEmpty()
        assertTrue(files.contains("THIRD-PARTY-NOTICES-MOCHI.md"))
        for (name in files) {
            assertTrue(name, context.assets.open("licenses/$name").use { it.readBytes().isNotEmpty() })
        }
        val notice = context.assets.open("licenses/THIRD-PARTY-NOTICES-MOCHI.md").bufferedReader().use { it.readText() }
        assertTrue(notice.contains("CC BY-SA 4.0"))
        assertTrue(notice.contains("WITHOUT the upstream ATOK-derived"))
        assertTrue(notice.contains("Nyamonana/mochihoppe-ime"))
    }
}

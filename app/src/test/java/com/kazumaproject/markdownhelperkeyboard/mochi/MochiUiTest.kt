package com.kazumaproject.markdownhelperkeyboard.mochi

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.app.Activity
import android.os.Looper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class MochiUiTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private class Source : MochiImageSource {
        override val debugOrigin: String? = null
        var searches = 0; var imageReads = 0
        var results: List<MochiImage> = emptyList()
        var assets: List<MochiImage> = emptyList()
        var imageFailure: Exception? = null
        override suspend fun search(query: String, gif: Boolean, page: Int): MochiSearchPage { searches++; return MochiSearchPage(results, null) }
        override suspend fun images(postId: String): List<MochiImage> { imageReads++; imageFailure?.let { throw it }; return assets }
        override suspend fun thumbnail(url: String): Bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        override fun cancel() {}
        override fun clearMemory() {}
    }
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()

    @Test fun openingTypingAndSwitchingModesDoesNotSearchOrReadExternalText() {
        val source = Source()
        val connection = mock<android.view.inputmethod.InputConnection>()
        val targets = mutableListOf<EditText?>()
        val host = MochiKeyboardHost(context, View(context), { true }, { 1 }, { connection }, { targets.add(it) }, { _, _, _ -> }, source)
        views(host).filterIsInstance<Button>().first { it.text == "画像" }.performClick()
        val editor = views(host).filterIsInstance<EditText>().single()
        editor.performClick(); editor.setText("専用検索語")
        assertSame(editor, targets.last())
        views(host).filterIsInstance<Button>().first { it.text == "GIF" }.performClick()
        views(host).filterIsInstance<Button>().first { it.text == "最近" }.performClick()
        assertEquals(0, source.searches); assertEquals(0, source.imageReads)
        verifyNoInteractions(connection)
        host.dispose()
    }
    @Test fun savedImagesAreCappedDeduplicatedAndClearable() {
        MochiSavedImages.clear(context)
        val store = MochiSavedImages(context)
        for (n in 0..205) store.toggle(SavedImage("post", "$n", "", "", "png"))
        assertEquals(200, store.favorites().size)
        store.toggle(store.favorites().first()); assertEquals(199, store.favorites().size)
        for (n in 0..55) store.remember(SavedImage("post", "$n", "", "", "png"))
        assertEquals(50, store.recent().size)
        store.remember(store.recent().first()); assertEquals(50, store.recent().size)
        MochiSavedImages.clear(context)
        assertTrue(MochiSavedImages(context).favorites().isEmpty())
        assertTrue(MochiSavedImages(context).recent().isEmpty())
    }

    // Android's newer AtomicFile uses POSIX replace-on-rename, which Windows/JVM
    // cannot emulate. Oreo's backup strategy lets us verify actual reloads here.
    @Test @Config(sdk = [26], qualifiers = "w360dp-h800dp") fun favoriteNavigationIsVisibleInConstrainedWindowAndNeverInserts() {
        MochiSavedImages.clear(context)
        val id = "00000000-0000-4000-8000-000000000001"
        MochiSavedImages(context).toggle(SavedImage(id, id, "保存した長い画像タイトルです", "https://img.mochi-hoppe-explorer.com/t/abcdefg.webp", "png"))
        val source = Source()
        val connection = mock<android.view.inputmethod.InputConnection>()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = MochiKeyboardHost(activity, View(activity), { true }, { 1 }, { connection }, {}, { _, _, _ -> }, source)
        activity.setContentView(host)
        views(host).filterIsInstance<Button>().first { it.text == "画像" }.performClick()
        val favoriteTab = views(host).filterIsInstance<Button>().first { it.text == "お気に入り" }
        favoriteTab.performClick()
        val width = (360 * activity.resources.displayMetrics.density).toInt()
        val heightLimit = (300 * activity.resources.displayMetrics.density).toInt()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightLimit, View.MeasureSpec.AT_MOST))
        host.layout(0, 0, width, host.measuredHeight)
        val tabRect = android.graphics.Rect()
        assertTrue(favoriteTab.getLocalVisibleRect(tabRect))
        assertEquals(favoriteTab.height, tabRect.height())
        assertTrue(host.measuredHeight <= heightLimit)
        val recycler = views(host).filterIsInstance<RecyclerView>().single()
        // No reserved help/status strip between the tabs and thumbnails.
        val gridArea = recycler.parent as View
        assertEquals((favoriteTab.parent as View).bottom, gridArea.top)
        assertEquals(1, recycler.adapter!!.itemCount)
        val tile = requireNotNull(recycler.findViewHolderForAdapterPosition(0)).itemView
        val image = views(tile).filterIsInstance<ImageView>().single()
        val caption = views(tile).filterIsInstance<TextView>().first { it !is Button }
        val star = views(tile).filterIsInstance<Button>().single()
        assertEquals(2, caption.maxLines)
        assertTrue(caption.top >= 0)
        // Caption and favorite button are in a sibling row BELOW the image.
        assertSame(caption.parent, star.parent)
        assertTrue((star.parent as View).top >= image.bottom)
        val starRect = android.graphics.Rect()
        assertTrue(star.getLocalVisibleRect(starRect))
        assertEquals(star.height, starRect.height())
        star.performClick()
        assertFalse(MochiSavedImages(context).isFavorite(id, id))
        assertEquals(0, source.searches); assertEquals(0, source.imageReads)
        verifyNoInteractions(connection)
        assertEquals(3, MochiKeyboardHost.tileColumns(360))
        assertEquals(2, MochiKeyboardHost.tileColumns(320))
        assertEquals(4, MochiKeyboardHost.tileColumns(700))
        host.dispose(); activity.finish()
    }

    @Test fun toolbarHasNoLayoutButtonAndSearchTypingDoesNotSearch() {
        val source = Source()
        val targets = mutableListOf<EditText?>()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val connection = mock<android.view.inputmethod.InputConnection>()
        val host = MochiKeyboardHost(activity, View(activity), { true }, { 1 }, { connection }, { targets.add(it) }, { _, _, _ -> }, source)
        activity.setContentView(host)
        views(host).filterIsInstance<Button>().first { it.text == "画像" }.performClick()
        val editor = views(host).filterIsInstance<EditText>().single()
        editor.performClick(); editor.setText("検索用の日本語")
        assertFalse(views(host).filterIsInstance<Button>().any { it.text == "配列" })
        assertSame(editor, targets.last())
        assertEquals("検索用の日本語", editor.text.toString())
        assertEquals(0, source.searches)
        verifyNoInteractions(connection)
        host.dispose(); activity.finish()
    }

    @Test fun albumRequiresASecondSelectionAndFreshValidationThenInsertsOnlyOnce() {
        MochiSavedImages.clear(context)
        val id = "00000000-0000-4000-8000-000000000001"
        val child = "00000000-0000-4000-8000-000000000002"
        val rootUrl = "https://img.mochi-hoppe-explorer.com/i/abcdefg.png"
        val childUrl = "https://img.mochi-hoppe-explorer.com/i/hijklmn.gif"
        val root = MochiImage(id, "png", "https://img.mochi-hoppe-explorer.com/t/abcdefg.webp", rootUrl, id, "album", 2)
        val second = MochiImage(child, "gif", "https://img.mochi-hoppe-explorer.com/t/hijklmn.webp", childUrl, id)
        val source = Source().apply { results = listOf(root); assets = listOf(root.copy(imageCount = 1), second) }
        val connection = mock<android.view.inputmethod.InputConnection> { on { commitText(childUrl, 1) } doReturn true }
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = MochiKeyboardHost(activity, View(activity), { true }, { 1 }, { connection }, {}, { _, _, _ -> }, source)
        activity.setContentView(host)
        fun layout() {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            host.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1500, View.MeasureSpec.AT_MOST))
            host.layout(0, 0, 1080, host.measuredHeight)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
        views(host).filterIsInstance<Button>().first { it.text == "画像" }.performClick()
        views(host).filterIsInstance<Button>().first { it.text == "検索" }.performClick()
        layout()
        val recycler = views(host).filterIsInstance<RecyclerView>().single()
        val albumTile = requireNotNull(recycler.findViewHolderForAdapterPosition(0)).itemView
        albumTile.performClick(); layout()
        assertEquals(1, source.imageReads); verifyNoInteractions(connection)
        val childTile = requireNotNull(recycler.findViewHolderForAdapterPosition(1)).itemView
        childTile.performClick(); childTile.performClick(); layout()
        assertEquals(2, source.imageReads)
        verify(connection, times(1)).commitText(childUrl, 1); verifyNoMoreInteractions(connection)
        host.dispose(); activity.finish()
    }

    @Test fun httpFailureUsesTransientToastWithoutInsertingOrReservingGridSpace() {
        val id = "00000000-0000-4000-8000-000000000001"
        val image = MochiImage(id, "png", "https://img.mochi-hoppe-explorer.com/t/abcdefg.webp", "https://img.mochi-hoppe-explorer.com/i/abcdefg.png", id, "test")
        val source = Source().apply { results = listOf(image); imageFailure = MochiNetworkException(503) }
        val connection = mock<android.view.inputmethod.InputConnection>()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = MochiKeyboardHost(activity, View(activity), { true }, { 1 }, { connection }, {}, { _, _, _ -> }, source)
        activity.setContentView(host)
        views(host).filterIsInstance<Button>().first { it.text == "画像" }.performClick()
        views(host).filterIsInstance<Button>().first { it.text == "検索" }.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        host.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1500, View.MeasureSpec.AT_MOST))
        host.layout(0, 0, 1080, host.measuredHeight)
        val recycler = views(host).filterIsInstance<RecyclerView>().single()
        requireNotNull(recycler.findViewHolderForAdapterPosition(0)).itemView.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast().contains("HTTP 503"))
        assertFalse(views(host).filterIsInstance<TextView>().any { it.text.contains("タップでURL挿入") || it.text.contains("HTTP 503") })
        assertEquals(View.GONE, views(host).filterIsInstance<android.widget.ProgressBar>().single().visibility)
        verifyNoInteractions(connection)
        // A manual retry remains possible; no automatic retry or double insert.
        requireNotNull(recycler.findViewHolderForAdapterPosition(0)).itemView.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, source.imageReads)
        host.dispose(); activity.finish()
    }
}

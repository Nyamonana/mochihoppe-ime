package com.kazumaproject.markdownhelperkeyboard.mochi

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import java.net.URI

data class MochiImage(val id: String, val fileType: String, val thumbnailUrl: String, val imageUrl: String,
    val postId: String = "", val title: String = "", val imageCount: Int = 1)
data class MochiSearchPage(val items: List<MochiImage>, val nextPage: Int?)
// Gson does not apply Kotlin defaults to missing fields. Match the GET wire shape.
data class MochiAsset(val id: String, val fileType: String, val thumbnailUrl: String, val imageUrl: String)
data class MochiPostImages(val postId: String, val items: List<MochiAsset>)
enum class MochiKeyboardLayout { FLICK, JAPANESE_QWERTY, ENGLISH_QWERTY }
data class SavedImage(val postId: String, val assetId: String, val title: String,
    val thumbnailUrl: String, val fileType: String)

object MochiInputPolicy {
    fun allowed(info: EditorInfo?, privateMode: Boolean): Boolean {
        if (info == null || privateMode || info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return false
        val type = info.inputType
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        return type and InputType.TYPE_MASK_VARIATION !in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
    }
}

/** Only canonical original URLs; never a detail URL, query, fragment, or newline. */
object MochiUrlPolicy {
    private val imagePath = Regex("/i/[0-9A-Za-z]{7,16}\\.(jpg|png|webp|gif)")
    private val thumbnailPath = Regex("/t/[0-9A-Za-z]{7,16}\\.webp")
    private val fallbackThumbnailPath = Regex("/api/images/[0-9a-fA-F-]{36}/(?:album/[0-9a-fA-F-]{36}/)?file")
    fun allowed(url: String, thumbnail: Boolean, debugOrigin: String? = null): Boolean = runCatching {
        val uri = URI(url)
        val production = uri.scheme == "https" && uri.host in setOf("img.mochi-hoppe-explorer.com", "pub.mochi-hoppe-explorer.com") && (uri.port == -1 || uri.port == 443)
        val debug = debugOrigin?.let { origin ->
            val local = URI(origin)
            local.host in setOf("10.0.2.2", "10.0.3.2", "127.0.0.1", "localhost") && uri.scheme == local.scheme && uri.host == local.host && uri.port == local.port
        } == true
        (production || debug) && uri.userInfo == null && uri.fragment == null &&
            (if (thumbnail) (thumbnailPath.matches(uri.path) && (uri.query == null || Regex("v=[0-9a-fA-F-]{36}").matches(uri.query))) ||
                (fallbackThumbnailPath.matches(uri.path) && Regex("variant=thumbnail&v=[0-9a-fA-F-]{36}").matches(uri.query ?: ""))
            else imagePath.matches(uri.path) && uri.query == null)
    }.getOrDefault(false)
}

/** One validated operation, no key events/editor actions/clipboard fallback. */
class MochiUrlInserter(private val currentSession: () -> Long, private val connection: () -> InputConnection?) {
    fun insert(expectedSession: Long, expectedConnection: InputConnection, url: String, debugOrigin: String? = null): Boolean {
        if (currentSession() != expectedSession || connection() !== expectedConnection || !MochiUrlPolicy.allowed(url, false, debugOrigin)) return false
        return runCatching { expectedConnection.commitText(url, 1) }.getOrDefault(false)
    }
}

package com.kazumaproject.markdownhelperkeyboard.mochi

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import java.io.File

class MochiSavedImages(context: Context) {
    private data class State(val favorites: List<SavedImage> = emptyList(), val recent: List<SavedImage> = emptyList())
    private val file = AtomicFile(File(context.noBackupFilesDir, "mochi-images.json"))
    private val gson = Gson()
    private var state = runCatching { gson.fromJson(file.openRead().bufferedReader().use { it.readText() }, State::class.java) }.getOrNull() ?: State()
    @Synchronized fun favorites(): List<SavedImage> = state.favorites.take(200)
    @Synchronized fun recent(): List<SavedImage> = state.recent.take(50)
    @Synchronized fun isFavorite(post: String, id: String) = state.favorites.any { it.postId == post && it.assetId == id }
    @Synchronized fun toggle(image: SavedImage) {
        val old = state.favorites.filterNot { it.postId == image.postId && it.assetId == image.assetId }
        state = state.copy(favorites = if (old.size == state.favorites.size) (listOf(image) + old).take(200) else old)
        save()
    }
    @Synchronized fun remember(image: SavedImage) { state = state.copy(recent = (listOf(image) + state.recent.filterNot { it.postId == image.postId && it.assetId == image.assetId }).take(50)); save() }
    private fun save() {
        val stream = file.startWrite()
        try { stream.write(gson.toJson(state).toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    companion object {
        fun clear(context: Context) {
            AtomicFile(File(context.noBackupFilesDir, "mochi-images.json")).delete()
            File(context.cacheDir, "mochi-thumbnails").deleteRecursively()
        }
    }
}

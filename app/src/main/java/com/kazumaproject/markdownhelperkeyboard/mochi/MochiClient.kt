package com.kazumaproject.markdownhelperkeyboard.mochi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.AtomicFile
import com.google.gson.Gson
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

class MochiNetworkException(val status: Int, val waitSeconds: Int = 0) : Exception("Image request failed")

interface MochiImageSource {
    val debugOrigin: String?
    suspend fun search(query: String, gif: Boolean, page: Int): MochiSearchPage
    suspend fun images(postId: String): List<MochiImage>
    suspend fun thumbnail(url: String): Bitmap
    fun cancel()
    fun clearMemory()
}

class MochiClient(context: Context, apiBaseUrl: String = BuildConfig.MOCHI_API_BASE_URL) : MochiImageSource {
    private val gson = Gson()
    private val base = apiBaseUrl.trimEnd('/')
    override val debugOrigin: String? = if (BuildConfig.DEBUG) base else null
    private val generation = AtomicLong()
    private val connections = Collections.newSetFromMap(ConcurrentHashMap<HttpURLConnection, Boolean>())
    private val permits = Semaphore(4)
    private val disk = File(context.cacheDir, "mochi-thumbnails")
    private val diskLock = Any()
    private val bitmaps = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val searchCache = LinkedHashMap<String, Pair<Long, MochiSearchPage>>()

    init {
        val uri = URI(base)
        require(uri.rawUserInfo == null && uri.query == null && uri.fragment == null && uri.path.isNullOrEmpty())
        require(base == "https://pub.mochi-hoppe-explorer.com" || (BuildConfig.DEBUG && uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2", "10.0.3.2") && uri.scheme in setOf("http", "https")))
    }

    override suspend fun search(query: String, gif: Boolean, page: Int): MochiSearchPage {
        require(query.length <= 128 && page in 1..1000)
        val key = "$gif:$page:$query"
        synchronized(searchCache) { searchCache[key]?.takeIf { System.currentTimeMillis() - it.first < 60_000 }?.let { return it.second } }
        val result = gson.fromJson(String(request("$base/api/ime/v1/search", gson.toJson(mapOf("q" to query, "gifOnly" to gif, "page" to page))), Charsets.UTF_8), MochiSearchPage::class.java)
        require(result.items.size <= 24 && (result.nextPage == null || result.nextPage == page + 1))
        result.items.forEach { validate(it); require(UUID.matches(it.postId) && it.postId == it.id && it.imageCount in 1..10) }
        synchronized(searchCache) { if (searchCache.size >= 20) searchCache.remove(searchCache.keys.first()); searchCache[key] = System.currentTimeMillis() to result }
        return result
    }

    /** Deliberately not cached: parent visibility is checked immediately before insertion. */
    override suspend fun images(postId: String): List<MochiImage> {
        require(UUID.matches(postId))
        val result = gson.fromJson(String(request("$base/api/ime/v1/posts/$postId/images"), Charsets.UTF_8), MochiPostImages::class.java)
        require(result.postId == postId && result.items.size in 1..10)
        return result.items.map { MochiImage(it.id, it.fileType, it.thumbnailUrl, it.imageUrl, postId = postId) }.onEach(::validate)
    }

    private fun validate(image: MochiImage) {
        require(UUID.matches(image.id) && image.fileType in setOf("jpg", "png", "webp", "gif"))
        require(MochiUrlPolicy.allowed(image.imageUrl, false, debugOrigin) && MochiUrlPolicy.allowed(image.thumbnailUrl, true, debugOrigin))
    }

    override suspend fun thumbnail(url: String): Bitmap = permits.withPermit {
        require(MochiUrlPolicy.allowed(url, true, debugOrigin))
        bitmaps.get(url)?.let { return@withPermit it }
        withContext(Dispatchers.IO) {
            coroutineContext.ensureActive()
            val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            val file = File(disk, key)
            val cached = synchronized(diskLock) {
                runCatching {
                    if (file.exists() && file.length() in 1..1024 * 1024) AtomicFile(file).openRead().use { it.readBytes() }.also { file.setLastModified(System.currentTimeMillis()) }
                    else null
                }.getOrNull()
            }
            val bytes = cached ?: request(url, maximum = 1024 * 1024).also {
                coroutineContext.ensureActive()
                synchronized(diskLock) {
                    disk.mkdirs()
                    val atomic = AtomicFile(file)
                    val stream = atomic.startWrite()
                    try { stream.write(it); atomic.finishWrite(stream) }
                    catch (error: Exception) { atomic.failWrite(stream); throw error }
                    pruneDisk()
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096)
            val options = BitmapFactory.Options().apply { var sample = 1; while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2; inSampleSize = sample }
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
            coroutineContext.ensureActive()
            bitmaps.put(url, bitmap)
            bitmap
        }
    }

    @Synchronized private fun pruneDisk() {
        val files = disk.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        var size = 0L
        files.forEach { size += it.length(); if (size > 50L * 1024 * 1024) it.delete() }
    }

    private suspend fun request(url: String, json: String? = null, maximum: Int = 128 * 1024): ByteArray {
        val expectedGeneration = generation.get()
        return withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        val connection = URL(url).openConnection() as HttpURLConnection
        connections.add(connection)
        try {
            if (generation.get() != expectedGeneration) throw kotlinx.coroutines.CancellationException()
            connection.connectTimeout = 8000; connection.readTimeout = 8000
            connection.instanceFollowRedirects = false; connection.useCaches = false
            connection.setRequestProperty("Accept", if (maximum > 128 * 1024) "image/webp" else "application/json")
            connection.setRequestProperty("User-Agent", "MochiExplorerKeyboard/0.1.3")
            if (json != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                val bytes = json.toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            if (status != 200) throw MochiNetworkException(status, connection.getHeaderField("Retry-After")?.toIntOrNull()?.coerceIn(1, 3600) ?: 0)
            require(connection.contentLengthLong <= maximum)
            val output = ByteArrayOutputStream()
            connection.inputStream.use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= maximum)
                    output.write(buffer, 0, count)
                }
            }
            output.toByteArray()
        } finally { connections.remove(connection); connection.disconnect() }
        }
    }

    override fun cancel() { generation.incrementAndGet(); connections.toList().forEach { it.disconnect() }; synchronized(searchCache) { searchCache.clear() } }
    override fun clearMemory() { cancel(); bitmaps.evictAll() }
    companion object { private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}") }
}

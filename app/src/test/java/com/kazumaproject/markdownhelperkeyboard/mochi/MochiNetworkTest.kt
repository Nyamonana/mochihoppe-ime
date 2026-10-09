package com.kazumaproject.markdownhelperkeyboard.mochi

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket
import java.net.InetAddress
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.util.Collections

/** Local JVM HTTP fixture only. No emulator/device or production requests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MochiNetworkTest {
    @Test fun explicitSearchSendsOnlyDedicatedQueryCachesSearchAndNeverCachesVisibility() = runBlocking {
        val requests = Collections.synchronizedList(mutableListOf<Pair<String, String>>())
        val id = "00000000-0000-4000-8000-000000000001"
        val image = """{"id":"$id","postId":"$id","title":"画像","fileType":"gif","thumbnailUrl":"https://img.mochi-hoppe-explorer.com/t/abcdefg.webp?v=$id","imageUrl":"https://img.mochi-hoppe-explorer.com/i/abcdefg.gif","imageCount":1}"""
        // Match the real GET contract: no title, imageCount, or per-asset postId.
        val asset = """{"id":"$id","fileType":"gif","thumbnailUrl":"https://img.mochi-hoppe-explorer.com/t/abcdefg.webp?v=$id","imageUrl":"https://img.mochi-hoppe-explorer.com/i/abcdefg.gif"}"""
        val server = LoopbackFixture { methodPath, headers, body ->
            requests.add(methodPath to body)
            assertFalse(headers.containsKey("cookie")); assertFalse(headers.containsKey("authorization"))
            if (methodPath.endsWith("/search")) """{"items":[$image],"nextPage":null}""" else """{"postId":"$id","items":[$asset]}"""
        }
        val client = MochiClient(ApplicationProvider.getApplicationContext<Context>(), "http://127.0.0.1:${server.port}")
        try {
            assertTrue(requests.isEmpty())
            assertEquals("gif", client.search("専用の検索語", true, 1).items.single().fileType)
            client.search("専用の検索語", true, 1)
            assertEquals(1, requests.size)
            assertEquals("POST /api/ime/v1/search", requests[0].first)
            assertEquals(setOf("q", "gifOnly", "page"), com.google.gson.JsonParser.parseString(requests[0].second).asJsonObject.keySet())
            assertEquals("専用の検索語", com.google.gson.JsonParser.parseString(requests[0].second).asJsonObject["q"].asString)
            val selected = client.images(id).single()
            assertEquals(id, selected.postId)
            assertEquals("", selected.title)
            assertEquals(1, selected.imageCount)
            assertEquals("https://img.mochi-hoppe-explorer.com/i/abcdefg.gif", selected.imageUrl)
            client.images(id)
            assertEquals(3, requests.size)
            assertEquals("GET /api/ime/v1/posts/$id/images", requests.last().first)
        } finally { client.clearMemory(); server.close() }
    }
    @Test fun cannotConfigureArbitraryExfiltrationHost() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (url in listOf("https://evil.example", "http://pub.mochi-hoppe-explorer.com", "https://user@pub.mochi-hoppe-explorer.com", "http://192.168.0.1:3000")) {
            assertThrows(IllegalArgumentException::class.java) { MochiClient(context, url) }
        }
    }

    private class LoopbackFixture(private val respond: (String, Map<String, String>, String) -> String) {
        private val socket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val port = socket.localPort
        private val worker = Thread {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        client.soTimeout = 3000
                        val input = BufferedInputStream(client.getInputStream())
                        val first = line(input).split(' ').take(2).joinToString(" ")
                        val headers = mutableMapOf<String, String>()
                        while (true) { val next = line(input); if (next.isEmpty()) break; headers[next.substringBefore(':').lowercase()] = next.substringAfter(':').trim() }
                        val bytes = ByteArray(headers["content-length"]?.toInt() ?: 0)
                        var offset = 0
                        while (offset < bytes.size) { val n = input.read(bytes, offset, bytes.size - offset); check(n > 0); offset += n }
                        val body = respond(first, headers, String(bytes, Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
                        val output = client.getOutputStream()
                        output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                        output.write(body); output.flush()
                    }
                } catch (error: java.net.SocketException) { if (!socket.isClosed) throw error }
            }
        }.apply { isDaemon = true; start() }
        fun close() { socket.close(); worker.join(3000) }
        private fun line(input: BufferedInputStream): String {
            val bytes = ByteArrayOutputStream()
            while (true) { val next = input.read(); if (next < 0 || next == 10) break; if (next != 13) bytes.write(next); check(bytes.size() < 8192) }
            return bytes.toString("US-ASCII")
        }
    }
}

package com.sergioasenjo.vesperhome.music

import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicHttpTest {
    @Test
    fun successfulBodyIsReadAndClosed() = runBlocking {
        val closed = AtomicBoolean()
        assertEquals("music", responseClient(200, closed).executeBody(request))
        assertTrue(closed.get())
    }

    @Test
    fun httpErrorsCloseTheBody() {
        val closed = AtomicBoolean()
        assertThrows(IOException::class.java) { runBlocking { responseClient(503, closed).executeBody(request) } }
        assertTrue(closed.get())
    }

    @Test
    fun cancellationClosesTheSocketWhileWaitingForHeaders() = cancelRead(partialBody = false)

    @Test
    fun cancellationClosesTheSocketWhileReadingTheBody() = cancelRead(partialBody = true)

    private fun cancelRead(partialBody: Boolean) = runBlocking {
        val ready = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val bodyStarted = CountDownLatch(1)
        val observedCall = AtomicReference<Call>()
        val serverError = AtomicReference<Throwable>()
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val worker = thread(isDaemon = true, name = "music-http-test") {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val input = socket.getInputStream().bufferedReader()
                        while (!input.readLine().isNullOrEmpty()) { /* Consume request headers. */ }
                        if (partialBody) {
                            socket.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\nx".toByteArray())
                                flush()
                            }
                        }
                        ready.countDown()
                        if (input.read() == -1) disconnected.countDown()
                    }
                } catch (error: Throwable) {
                    serverError.set(error)
                    ready.countDown()
                }
            }
            val client = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun callStart(call: Call) {
                    observedCall.set(call)
                }
                override fun responseBodyStart(call: Call) {
                    bodyStarted.countDown()
                }
            }).build()
            val job = launch(Dispatchers.Default) {
                client.executeBody(Request.Builder().url("http://127.0.0.1:${server.localPort}/music").build())
            }
            try {
                assertTrue("Server did not receive the request", ready.await(5, TimeUnit.SECONDS))
                if (partialBody) assertTrue("Body read did not start", bodyStarted.await(5, TimeUnit.SECONDS))
                withTimeout(5000) { job.cancelAndJoin() }
                assertTrue(observedCall.get().isCanceled())
                assertTrue("Cancellation did not close the network socket", disconnected.await(5, TimeUnit.SECONDS))
                assertEquals(null, serverError.get())
            } finally {
                job.cancelAndJoin()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
                server.close()
                worker.join(5000)
            }
        }
    }

    private fun responseClient(code: Int, closed: AtomicBoolean): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val source = object : ForwardingSource(Buffer().writeUtf8("music")) {
                override fun close() {
                    closed.set(true)
                    super.close()
                }
            }.buffer()
            val body = object : ResponseBody() {
                override fun contentType(): MediaType? = null
                override fun contentLength() = 5L
                override fun source(): BufferedSource = source
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("Test").body(body).build()
        }.build()

    private val request = Request.Builder().url("http://music.test/track").build()
}

package com.sergioasenjo.vesperhome.music

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal data class MusicHttpResponse(val body: String, val headers: Headers)

internal suspend fun OkHttpClient.executeBody(request: Request): String = executeResponse(request).body

internal suspend fun OkHttpClient.executeResponse(request: Request): MusicHttpResponse =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    // Keep cancellation connected until the entire body has been read and closed.
                    val body = response.use {
                        if (!it.isSuccessful) throw IOException("HTTP request failed with ${it.code}")
                        MusicHttpResponse(it.body.string(), it.headers)
                    }
                    continuation.resume(body)
                } catch (error: Exception) {
                    continuation.resumeWithException(error)
                }
            }
        })
    }

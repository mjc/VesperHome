package com.sergioasenjo.vesperhome.music

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal suspend fun OkHttpClient.executeBody(request: Request): String = suspendCancellableCoroutine { continuation ->
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
                    it.body.string()
                }
                continuation.resume(body)
            } catch (error: IOException) {
                continuation.resumeWithException(error)
            }
        }
    })
}

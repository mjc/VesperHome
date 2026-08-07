package com.sergioasenjo.ltvlauncher.music

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class JellyfinDiscoveryRepository(private val json: Json) {
    suspend fun discover(): List<JellyfinServer> = withContext(Dispatchers.IO) {
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = RESPONSE_TIMEOUT_MS
            val request = DISCOVERY_MESSAGE.toByteArray()
            val networkInterfaces = NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
            val broadcastAddresses = networkInterfaces
                .flatMap { networkInterface ->
                    networkInterface.interfaceAddresses.mapNotNull { it.broadcast }
                }
                .toSet() + InetAddress.getByName(BROADCAST_ADDRESS)
            broadcastAddresses.forEach { address ->
                socket.send(
                    DatagramPacket(
                        request,
                        request.size,
                        address,
                        DISCOVERY_PORT
                    )
                )
            }

            val servers = linkedMapOf<String, JellyfinServer>()
            val deadline = System.currentTimeMillis() + DISCOVERY_WINDOW_MS
            while (System.currentTimeMillis() < deadline) {
                val response = DatagramPacket(ByteArray(RESPONSE_BUFFER_SIZE), RESPONSE_BUFFER_SIZE)
                try {
                    socket.receive(response)
                    val body = response.data.decodeToString(0, response.length)
                    val server = json.decodeFromString<JellyfinServer>(body)
                    servers[server.Id] = server
                } catch (_: SocketTimeoutException) {
                    break
                } catch (_: IllegalArgumentException) {
                    // Ignore malformed responses from unrelated UDP services.
                }
            }
            servers.values.toList()
        }
    }

    private companion object {
        const val DISCOVERY_MESSAGE = "Who is JellyfinServer?"
        const val BROADCAST_ADDRESS = "255.255.255.255"
        const val DISCOVERY_PORT = 7359
        const val DISCOVERY_WINDOW_MS = 2_500L
        const val RESPONSE_TIMEOUT_MS = 700
        const val RESPONSE_BUFFER_SIZE = 8_192
    }
}

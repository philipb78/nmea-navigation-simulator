package com.nauticontrol.nmeanavigationsimulator.network

import com.nauticontrol.nmeanavigationsimulator.model.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TcpNmeaClientTest {
    @Test
    fun `sendSentences writes every sentence to connected TCP server`() {
        val received = Collections.synchronizedList(mutableListOf<String>())
        val server = ServerSocket(0)
        val accepted = CountDownLatch(1)
        val readComplete = CountDownLatch(1)
        val serverThread = Thread {
            server.use { activeServer ->
                activeServer.accept().use { socket ->
                    accepted.countDown()
                    socket.getInputStream().bufferedReader().use { reader ->
                        while (received.size < 15) {
                            val line = reader.readLine() ?: break
                            received += line
                        }
                    }
                    readComplete.countDown()
                }
            }
        }
        serverThread.start()

        val client = TcpNmeaClient()
        try {
            client.connect("127.0.0.1", server.localPort)
            assertTrue(accepted.await(3, TimeUnit.SECONDS))
            assertTrue(waitForConnection(client))

            val sentences = (1..15).map { "\$GPTST,$it*00" }
            client.sendSentences(sentences)

            assertTrue(readComplete.await(3, TimeUnit.SECONDS))
            assertEquals(sentences, received.toList())
        } finally {
            client.close()
            server.close()
            serverThread.join(1_000L)
        }
    }

    @Test
    fun `client drains server data and keeps outbound path working`() {
        val received = Collections.synchronizedList(mutableListOf<String>())
        val server = ServerSocket(0)
        val accepted = CountDownLatch(1)
        val outboundComplete = CountDownLatch(1)
        // 256 KB easily exceeds a default socket receive buffer, so if the client
        // never drained its input stream the server's write() would block and this
        // latch would never count down within the timeout.
        val floodLineCount = 4_000
        val serverThread = Thread {
            server.use { activeServer ->
                activeServer.accept().use { socket ->
                    accepted.countDown()
                    val outbound = socket.getOutputStream()
                    val inbound = socket.getInputStream().bufferedReader()
                    repeat(floodLineCount) {
                        outbound.write("\$GPN2K,$it,${"X".repeat(50)}*00\r\n".toByteArray())
                    }
                    outbound.flush()
                    while (received.size < 5) {
                        val line = inbound.readLine() ?: break
                        received += line
                    }
                    outboundComplete.countDown()
                }
            }
        }
        serverThread.start()

        val client = TcpNmeaClient()
        try {
            client.connect("127.0.0.1", server.localPort)
            assertTrue(accepted.await(3, TimeUnit.SECONDS))
            assertTrue(waitForConnection(client))

            val sentences = (1..5).map { "\$GPTST,$it*00" }
            client.sendSentences(sentences)

            assertTrue(outboundComplete.await(10, TimeUnit.SECONDS))
            assertEquals(sentences, received.toList())
        } finally {
            client.close()
            server.close()
            serverThread.join(1_000L)
        }
    }

    private fun waitForConnection(client: TcpNmeaClient): Boolean {
        repeat(30) {
            if (client.connectionState.value == ConnectionState.CONNECTED) {
                return true
            }
            Thread.sleep(100L)
        }
        return false
    }
}

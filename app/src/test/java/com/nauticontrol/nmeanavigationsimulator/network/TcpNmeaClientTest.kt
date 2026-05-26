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

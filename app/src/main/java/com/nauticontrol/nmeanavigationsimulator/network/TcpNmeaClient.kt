package com.nauticontrol.nmeanavigationsimulator.network

import com.nauticontrol.nmeanavigationsimulator.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

class TcpNmeaClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var host: String = ""
    private var port: Int = 0
    private var keepConnected = false
    private var reconnectJob: Job? = null

    var onLog: (String) -> Unit = {}

    fun connect(host: String, port: Int) {
        this.host = host
        this.port = port
        keepConnected = true
        if (_connectionState.value == ConnectionState.CONNECTED || _connectionState.value == ConnectionState.CONNECTING) {
            return
        }
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            maintainConnection()
        }
    }

    fun disconnect() {
        keepConnected = false
        reconnectJob?.cancel()
        reconnectJob = null
        closeSocket()
        updateState(ConnectionState.DISCONNECTED)
        onLog("Disconnected")
    }

    fun sendSentences(sentences: List<String>) {
        scope.launch {
            if (!ensureConnected()) {
                onLog("Send skipped: socket unavailable")
                return@launch
            }
            try {
                sentences.forEach { sentence ->
                    writer?.write(sentence)
                    writer?.write("\r\n")
                    onLog("TX $sentence")
                }
                writer?.flush()
            } catch (error: Exception) {
                onLog("Send failed: ${error.message}")
                closeSocket()
                updateState(ConnectionState.DISCONNECTED)
                if (keepConnected) {
                    reconnectJob?.cancel()
                    reconnectJob = launch { maintainConnection() }
                }
            }
        }
    }

    private suspend fun maintainConnection() {
        while (keepConnected) {
            try {
                updateState(ConnectionState.CONNECTING)
                onLog("Connecting to $host:$port")
                val newSocket = Socket()
                newSocket.connect(InetSocketAddress(host, port), 3_000)
                socket = newSocket
                writer = BufferedWriter(OutputStreamWriter(newSocket.getOutputStream()))
                updateState(ConnectionState.CONNECTED)
                onLog("Connected to $host:$port")
                while (keepConnected && newSocket.isConnected && !newSocket.isClosed) {
                    delay(1_000)
                    if (newSocket.isOutputShutdown) {
                        throw IllegalStateException("output stream closed")
                    }
                }
            } catch (error: Exception) {
                closeSocket()
                updateState(ConnectionState.DISCONNECTED)
                onLog("Connection error: ${error.message}. Retrying...")
                delay(2_000)
            }
        }
    }

    private fun ensureConnected(): Boolean = _connectionState.value == ConnectionState.CONNECTED

    private fun closeSocket() {
        try {
            writer?.close()
        } catch (_: Exception) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        writer = null
        socket = null
    }

    private fun updateState(state: ConnectionState) {
        _connectionState.value = state
    }
}

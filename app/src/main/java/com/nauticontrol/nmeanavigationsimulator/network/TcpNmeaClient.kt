package com.nauticontrol.nmeanavigationsimulator.network

import com.nauticontrol.nmeanavigationsimulator.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

class TcpNmeaClient {
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.IO)
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val socketMutex = Mutex()

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    @Volatile
    private var host: String = ""
    @Volatile
    private var port: Int = 0
    @Volatile
    private var keepConnected = false
    private var reconnectJob: Job? = null

    var onLog: (String) -> Unit = {}

    fun connect(host: String, port: Int) {
        this.host = host
        this.port = port
        keepConnected = true
        if (reconnectJob?.isActive == true) {
            return
        }
        reconnectJob = scope.launch {
            maintainConnection()
        }
    }

    fun disconnect() {
        keepConnected = false
        reconnectJob?.cancel()
        reconnectJob = null
        scope.launch {
            socketMutex.withLock {
                closeSocketLocked()
            }
            updateState(ConnectionState.DISCONNECTED)
            onLog("Disconnected")
        }
    }

    fun close() {
        keepConnected = false
        reconnectJob?.cancel()
        reconnectJob = null
        scope.launch {
            socketMutex.withLock {
                closeSocketLocked()
            }
            updateState(ConnectionState.DISCONNECTED)
        }.invokeOnCompletion {
            scope.cancel()
        }
    }

    fun sendSentences(sentences: List<String>) {
        if (sentences.isEmpty()) {
            return
        }
        scope.launch {
            try {
                socketMutex.withLock {
                    val activeWriter = writer ?: throw IllegalStateException("socket unavailable")
                    sentences.forEach { sentence ->
                        activeWriter.write(sentence)
                        activeWriter.write("\r\n")
                        onLog("TX $sentence")
                    }
                    activeWriter.flush()
                }
            } catch (error: Exception) {
                handleConnectionLoss("Send failed", error)
            }
        }
    }

    private suspend fun maintainConnection() {
        while (keepConnected && currentCoroutineContext().isActive) {
            val connected = socketMutex.withLock {
                if (writer != null && socket?.isConnected == true && socket?.isClosed == false) {
                    true
                } else {
                    try {
                        updateState(ConnectionState.CONNECTING)
                        onLog("Connecting to $host:$port")
                        val newSocket = Socket()
                        newSocket.connect(InetSocketAddress(host, port), 3_000)
                        if (!keepConnected || !currentCoroutineContext().isActive) {
                            try {
                                newSocket.close()
                            } catch (_: Exception) {
                            }
                            updateState(ConnectionState.DISCONNECTED)
                            false
                        } else {
                            socket = newSocket
                            writer = BufferedWriter(OutputStreamWriter(newSocket.getOutputStream()))
                            updateState(ConnectionState.CONNECTED)
                            onLog("Connected to $host:$port")
                            true
                        }
                    } catch (error: Exception) {
                        closeSocketLocked()
                        updateState(ConnectionState.DISCONNECTED)
                        onLog("Connection error: ${error.message}. Retrying...")
                        false
                    }
                }
            }
            delay(if (connected) 750L else 2_000L)
        }
    }

    private suspend fun handleConnectionLoss(prefix: String, error: Exception) {
        socketMutex.withLock {
            closeSocketLocked()
        }
        updateState(ConnectionState.DISCONNECTED)
        val suffix = if (keepConnected) " Reconnecting..." else ""
        onLog("$prefix: ${error.message ?: error::class.java.simpleName}.$suffix")
    }

    private fun closeSocketLocked() {
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

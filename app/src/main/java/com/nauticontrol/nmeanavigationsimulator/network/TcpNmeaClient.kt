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
import java.util.concurrent.atomic.AtomicInteger

class TcpNmeaClient {
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.IO)
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val socketMutex = Mutex()

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private val connectionGeneration = AtomicInteger(0)
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
        connectionGeneration.incrementAndGet()
        scope.launch {
            socketMutex.withLock {
                closeSocketLocked()
            }
        }
        if (reconnectJob?.isActive != true) {
            reconnectJob = scope.launch {
                maintainConnection()
            }
        }
    }

    fun disconnect() {
        keepConnected = false
        connectionGeneration.incrementAndGet()
        reconnectJob?.cancel()
        reconnectJob = null
        updateState(ConnectionState.DISCONNECTED)
        scope.launch {
            socketMutex.withLock {
                closeSocketLocked()
            }
            onLog("Disconnected")
        }
    }

    fun close() {
        keepConnected = false
        connectionGeneration.incrementAndGet()
        reconnectJob?.cancel()
        reconnectJob = null
        updateState(ConnectionState.DISCONNECTED)
        scope.launch {
            socketMutex.withLock {
                closeSocketLocked()
            }
        }.invokeOnCompletion {
            scope.cancel()
        }
    }

    fun sendSentences(sentences: List<String>) {
        if (sentences.isEmpty()) {
            return
        }
        val currentState = _connectionState.value
        if (currentState != ConnectionState.CONNECTED) {
            return
        }
        scope.launch {
            try {
                socketMutex.withLock {
                    val activeWriter = writer ?: run {
                        onLog("Send skipped: writer unavailable")
                        return@withLock
                    }
                    sentences.forEach { sentence ->
                        activeWriter.write(sentence)
                        activeWriter.write("\r\n")
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
                hasOpenSocketLocked()
            }
            if (connected) {
                delay(750L)
                continue
            }

            val attemptGeneration = connectionGeneration.get()
            val attemptHost = host
            val attemptPort = port
            updateState(ConnectionState.CONNECTING)
            onLog("Connecting to $attemptHost:$attemptPort")

            val newSocket = try {
                Socket().apply {
                    tcpNoDelay = true
                    soTimeout = 5_000
                    connect(InetSocketAddress(attemptHost, attemptPort), 3_000)
                }
            } catch (error: Exception) {
                socketMutex.withLock {
                    closeSocketLocked()
                }
                if (keepConnected && attemptGeneration == connectionGeneration.get()) {
                    updateState(ConnectionState.DISCONNECTED)
                    onLog("Connection error: ${error.message ?: error::class.java.simpleName}. Retrying...")
                    delay(2_000L)
                }
                continue
            }

            val attemptStillActive = currentCoroutineContext().isActive
            val accepted = socketMutex.withLock {
                if (keepConnected &&
                    attemptStillActive &&
                    attemptGeneration == connectionGeneration.get()
                ) {
                    closeSocketLocked()
                    socket = newSocket
                    writer = BufferedWriter(OutputStreamWriter(newSocket.getOutputStream()))
                    true
                } else {
                    false
                }
            }

            if (accepted) {
                updateState(ConnectionState.CONNECTED)
                onLog("Connected to $attemptHost:$attemptPort")
                delay(750L)
            } else {
                try {
                    newSocket.close()
                } catch (_: Exception) {
                }
                updateState(ConnectionState.DISCONNECTED)
            }
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

    private fun hasOpenSocketLocked(): Boolean {
        val activeSocket = socket ?: return false
        return writer != null &&
            activeSocket.isConnected &&
            !activeSocket.isClosed &&
            !activeSocket.isOutputShutdown
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

package com.rootpad.bridge.service

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * root デーモンとの abstract Unix ドメインソケット通信。
 *
 * 読み取り専用スレッドが行を受け取り listener へ渡す。
 * 書き込みはスレッドセーフ (synchronized)。
 */
class DaemonConnection(private val listener: Listener) {

    interface Listener {
        fun onDaemonLine(line: String)
        fun onConnectionLost(reason: String)
    }

    private var socket: LocalSocket? = null
    private var out: OutputStream? = null
    private var readerThread: Thread? = null
    private val running = AtomicBoolean(false)
    private val writeLock = Any()

    val isConnected: Boolean
        get() = running.get() && socket != null

    /**
     * デーモンへ接続する (ブロッキング)。成功時 true。
     */
    fun connect(name: String, timeoutMs: Long = 3000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastError: Exception? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                val s = LocalSocket()
                s.connect(LocalSocketAddress(name, LocalSocketAddress.Namespace.ABSTRACT))
                s.soTimeout = 0
                socket = s
                out = s.outputStream
                running.set(true)
                startReader(s)
                return true
            } catch (e: Exception) {
                lastError = e
                try {
                    Thread.sleep(150)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
        listener.onConnectionLost("socket_connect_failed: ${lastError?.message ?: "timeout"}")
        return false
    }

    private fun startReader(s: LocalSocket) {
        readerThread = Thread({
            try {
                val reader = BufferedReader(InputStreamReader(s.inputStream), 16 * 1024)
                var line: String?
                while (running.get()) {
                    line = reader.readLine() ?: break
                    if (line.isNotEmpty()) listener.onDaemonLine(line)
                }
                if (running.getAndSet(false)) {
                    listener.onConnectionLost("daemon_closed")
                }
            } catch (e: Exception) {
                if (running.getAndSet(false)) {
                    listener.onConnectionLost("read_error: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }, "rootpad-reader").apply {
            isDaemon = true
            start()
        }
    }

    /** 1 行送信する (改行は自動付与)。失敗は黙って無視。 */
    fun send(line: String) {
        val o = out ?: return
        try {
            synchronized(writeLock) {
                val data = (line + "\n").toByteArray(Charsets.US_ASCII)
                o.write(data)
                o.flush()
            }
        } catch (e: Exception) {
            if (running.getAndSet(false)) {
                listener.onConnectionLost("write_error: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun close() {
        running.set(false)
        try {
            socket?.close()
        } catch (e: Exception) {
            // 無視
        }
        socket = null
        out = null
    }
}

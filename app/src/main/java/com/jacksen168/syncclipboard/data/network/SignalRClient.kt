package com.jacksen168.syncclipboard.data.network

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.jacksen168.syncclipboard.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * SignalR 客户端
 * 用于连接 SyncClipboard 服务端的 /SyncClipboardHub 实时推送
 */
class SignalRClient(
    private val serverUrl: String,
    private val username: String,
    private val password: String,
    private val trustUnsafeSSL: Boolean = false
) {
    companion object {
        private const val TAG = "SignalRClient"
        private const val HUB_PATH = "/SyncClipboardHub"
        private const val RECONNECT_DELAY = 5000L // 5秒重连延迟
        private const val PING_INTERVAL = 15000L // 15秒心跳间隔
    }

    private val gson = Gson()
    
    // WebSocket 客户端
    private var webSocket: WebSocket? = null
    private var okHttpClient: OkHttpClient
    
    // 连接状态
    private val _connectionState = Channel<ConnectionState>(Channel.CONFLATED)
    val connectionState: Flow<ConnectionState> = _connectionState.receiveAsFlow()
    
    // 事件通道
    private val _clipboardChangedEvent = Channel<ClipboardChangedEvent>(Channel.CONFLATED)
    val clipboardChangedEvent: Flow<ClipboardChangedEvent> = _clipboardChangedEvent.receiveAsFlow()
    
    private val _historyChangedEvent = Channel<HistoryChangedEvent>(Channel.CONFLATED)
    val historyChangedEvent: Flow<HistoryChangedEvent> = _historyChangedEvent.receiveAsFlow()
    
    // 心跳任务
    private var pingJob: Job? = null
    
    // 重连任务
    private var reconnectJob: Job? = null
    
    // 是否手动断开
    private var manuallyDisconnected = false
    
    // 服务协程作用域
    private val scope = CoroutineScope(
        Dispatchers.IO + 
        SupervisorJob()
    )

    init {
        // 配置 OkHttp 客户端
        val builder = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        
        // 添加认证头
        builder.addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("Authorization", createBasicAuthHeader())
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
        
        // 信任不安全的 SSL
        if (trustUnsafeSSL) {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<X509Certificate>,
                        authType: String
                    ) {}
                    
                    override fun checkServerTrusted(
                        chain: Array<X509Certificate>,
                        authType: String
                    ) {}
                    
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            val sslSocketFactory = sslContext.socketFactory
            
            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        
        okHttpClient = builder.build()
    }

    /**
     * 连接到 SignalR Hub
     */
    fun connect() {
        manuallyDisconnected = false
        val wsUrl = if (serverUrl.startsWith("http://")) {
            serverUrl.replace("http://", "ws://")
        } else {
            serverUrl.replace("https://", "wss://")
        } + HUB_PATH
        
        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", createBasicAuthHeader())
            .build()
        
        webSocket = okHttpClient.newWebSocket(request, SignalRWebSocketListener())
        _connectionState.trySend(ConnectionState.CONNECTING)
        Logger.d(TAG, "正在连接到 SignalR Hub: $wsUrl")
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        manuallyDisconnected = true
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        stopPing()
        stopReconnect()
        _connectionState.trySend(ConnectionState.DISCONNECTED)
        Logger.d(TAG, "已断开 SignalR 连接")
    }

    /**
     * 发送握手消息
     */
    private fun sendHandshake() {
        val handshake = SignalRHandshake()
        val handshakeJson = gson.toJson(handshake)
        val handshakeMessage = "$handshakeJson\u001E"
        webSocket?.send(handshakeMessage)
        Logger.d(TAG, "已发送握手消息")
    }

    /**
     * 创建 Basic Auth 头
     */
    private fun createBasicAuthHeader(): String {
        val credentials = "$username:$password"
        val encoded = Base64.encodeToString(
            credentials.toByteArray(),
            Base64.NO_WRAP
        )
        return "Basic $encoded"
    }

    /**
     * 启动心跳
     */
    private fun startPing() {
        stopPing()
        pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL)
                if (webSocket != null) {
                    sendPing()
                } else {
                    break
                }
            }
        }
    }

    /**
     * 停止心跳
     */
    private fun stopPing() {
        pingJob?.cancel()
        pingJob = null
    }

    /**
     * 发送 Ping
     */
    private fun sendPing() {
        val ping = PingMessage()
        val message = gson.toJson(ping)
        webSocket?.send("$message\u001E")
    }

    /**
     * 启动重连
     */
    private fun startReconnect() {
        stopReconnect()
        if (manuallyDisconnected) return
        
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY)
            if (!manuallyDisconnected) {
                _connectionState.trySend(ConnectionState.RECONNECTING)
                Logger.d(TAG, "正在重新连接到 SignalR Hub...")
                connect()
            }
        }
    }

    /**
     * 停止重连
     */
    private fun stopReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    /**
     * WebSocket 监听器
     */
    private inner class SignalRWebSocketListener : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            Logger.d(TAG, "WebSocket 连接已建立")
            // 发送握手
            sendHandshake()
            _connectionState.trySend(ConnectionState.CONNECTED)
        }

        override fun onMessage(ws: WebSocket, text: String) {
            try {
                // SignalR 消息以 \u001E 分隔
                val messages = text.split("\u001E")
                for (message in messages) {
                    if (message.isBlank()) continue
                    processMessage(message)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "处理消息时出错", e)
            }
        }

        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
            Logger.d(TAG, "WebSocket 连接正在关闭: code=$code, reason=$reason")
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            Logger.d(TAG, "WebSocket 连接已关闭: code=$code, reason=$reason")
            _connectionState.trySend(ConnectionState.DISCONNECTED)
            stopPing()
            startReconnect()
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            Logger.e(TAG, "WebSocket 连接失败", t)
            _connectionState.trySend(ConnectionState.ERROR(t.message ?: "Unknown error"))
            stopPing()
            startReconnect()
        }
    }

    /**
     * 处理接收到的消息
     */
    private fun processMessage(message: String) {
        try {
            val json = JsonParser.parseString(message).asJsonObject
            val type = json.get("type")?.asInt ?: return
            
            when (type) {
                SignalRMessageType.INVOCATION.value -> {
                    // 处理调用消息（服务器推送的事件）
                    val target = json.get("target")?.asString ?: return
                    val arguments = json.getAsJsonArray("arguments")
                    
                    when (target) {
                        "RemoteProfileChanged" -> {
                            if (arguments.size() > 0) {
                                val profileJson = arguments[0].asJsonObject
                                val event = gson.fromJson(profileJson, ClipboardChangedEvent::class.java)
                                _clipboardChangedEvent.trySend(event)
                                Logger.d(TAG, "收到剪贴板变更事件: type=${event.type}, hash=${event.hash}")
                            }
                        }
                        "RemoteHistoryChanged" -> {
                            // 处理历史记录变更事件
                            if (arguments.size() > 0) {
                                val historyJson = arguments[0].asJsonObject
                                val event = gson.fromJson(historyJson, HistoryChangedEvent::class.java)
                                _historyChangedEvent.trySend(event)
                                Logger.d(TAG, "收到历史记录变更事件: action=${event.action}, hash=${event.hash}")
                            }
                        }
                        else -> {
                            Logger.d(TAG, "收到未知的事件: $target")
                        }
                    }
                }
                SignalRMessageType.PING.value -> {
                    // Ping 消息，忽略
                }
                SignalRMessageType.CLOSE.value -> {
                    // 服务器关闭连接
                    Logger.d(TAG, "服务器要求关闭连接")
                    disconnect()
                }
                SignalRMessageType.COMPLETION.value -> {
                    // 完成消息，记录日志
                    val invocationId = json.get("invocationId")?.asString
                    val error = json.get("error")?.asString
                    if (error != null) {
                        Logger.w(TAG, "服务端返回错误: $error (invocationId=$invocationId)")
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "解析消息时出错: $message", e)
        }
    }

    /**
     * 调用服务器方法
     */
    fun invokeMethod(methodName: String, vararg args: Any) {
        val invocation = InvocationMessage(
            invocationId = UUID.randomUUID().toString(),
            target = methodName,
            arguments = args.toList()
        )
        val message = gson.toJson(invocation)
        webSocket?.send("$message\u001E")
        Logger.d(TAG, "调用服务器方法: $methodName")
    }

    /**
     * 连接状态
     */
    sealed class ConnectionState {
        object CONNECTING : ConnectionState()
        object CONNECTED : ConnectionState()
        object DISCONNECTED : ConnectionState()
        object RECONNECTING : ConnectionState()
        data class ERROR(val message: String) : ConnectionState()
    }
}
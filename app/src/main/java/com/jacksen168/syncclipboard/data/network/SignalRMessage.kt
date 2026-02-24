package com.jacksen168.syncclipboard.data.network

import com.google.gson.annotations.SerializedName

/**
 * SignalR 消息类型枚举
 */
enum class SignalRMessageType(val value: Int) {
    @SerializedName("1")
    INVOCATION(1),      // 调用消息
    
    @SerializedName("2")
    STREAM_ITEM(2),     // 流项
    
    @SerializedName("3")
    COMPLETION(3),      // 完成消息
    
    @SerializedName("4")
    STREAM_INVOCATION(4), // 流调用
    
    @SerializedName("5")
    CANCEL_INVOCATION(5),  // 取消调用
    
    @SerializedName("6")
    PING(6),            // Ping 消息
    
    @SerializedName("7")
    CLOSE(7)            // 关闭消息
}

/**
 * SignalR 握手协议
 */
data class SignalRHandshake(
    @SerializedName("protocol")
    val protocol: String = "json",
    
    @SerializedName("version")
    val version: Int = 1
)

/**
 * SignalR 消息基类
 */
sealed class SignalRMessage {
    abstract val type: Int
}

/**
 * 调用消息（服务器推送事件）
 */
data class InvocationMessage(
    @SerializedName("type")
    override val type: Int = SignalRMessageType.INVOCATION.value,
    
    @SerializedName("invocationId")
    val invocationId: String? = null,
    
    @SerializedName("target")
    val target: String,
    
    @SerializedName("arguments")
    val arguments: List<Any> = emptyList()
) : SignalRMessage()

/**
 * 完成消息
 */
data class CompletionMessage(
    @SerializedName("type")
    override val type: Int = SignalRMessageType.COMPLETION.value,
    
    @SerializedName("invocationId")
    val invocationId: String,
    
    @SerializedName("result")
    val result: Any? = null,
    
    @SerializedName("error")
    val error: String? = null
) : SignalRMessage()

/**
 * Ping 消息
 */
data class PingMessage(
    @SerializedName("type")
    override val type: Int = SignalRMessageType.PING.value
) : SignalRMessage()

/**
 * 关闭消息
 */
data class CloseMessage(
    @SerializedName("type")
    override val type: Int = SignalRMessageType.CLOSE.value,
    
    @SerializedName("error")
    val error: String? = null,
    
    @SerializedName("allowReconnect")
    val allowReconnect: Boolean? = null
) : SignalRMessage()

/**
 * 剪贴板变更事件（来自服务端）
 */
data class ClipboardChangedEvent(
    @SerializedName("type")
    val type: String, // "Text", "Image", "File"
    
    @SerializedName("hash")
    val hash: String,
    
    @SerializedName("text")
    val text: String?,
    
    @SerializedName("dataName")
    val dataName: String?,
    
    @SerializedName("size")
    val size: Long,
    
    @SerializedName("hasData")
    val hasData: Boolean,
    
    @SerializedName("timestamp")
    val timestamp: String? = null
)

/**
 * 历史记录变更事件（来自服务端）
 */
data class HistoryChangedEvent(
    @SerializedName("hash")
    val hash: String,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("text")
    val text: String?,
    
    @SerializedName("action")
    val action: String, // "created", "updated", "deleted"
    
    @SerializedName("timestamp")
    val timestamp: String? = null
)
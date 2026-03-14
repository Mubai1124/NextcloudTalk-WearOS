package com.example.wearmaterial.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

/**
 * Nextcloud Talk Signaling API 接口
 * 用于获取 Signaling Server 配置和 ticket
 */
interface SignalingApi {
    /**
     * 获取 Signaling 设置
     * 返回 signaling server URL 和 ticket
     * 
     * API 端点: /ocs/v2.php/apps/spreed/api/v3/signaling/settings
     * 方法: GET
     * 认证: Basic Auth
     * Header: OCS-APIRequest: true
     */
    @GET("ocs/v2.php/apps/spreed/api/v3/signaling/settings")
    suspend fun getSettings(): retrofit2.Response<SignalingSettingsResponse>
    
    /**
     * 获取房间 Signaling ticket
     */
    @POST("ocs/v2.php/apps/spreed/api/v3/signaling/{token}")
    suspend fun getTicket(
        @Path("token") token: String
    ): retrofit2.Response<SignalingTicketResponse>
}

/**
 * Signaling 设置响应
 */
data class SignalingSettingsResponse(
    val ocs: OcsWrapper<SignalingSettings>
)

/**
 * Signaling 设置
 * 从 Nextcloud Talk API 获取
 */
data class SignalingSettings(
    val server: String?,           // Signaling server URL (e.g., "http://yuanscrew.cn:8080")
    val ticket: String?,           // Authentication ticket
    @SerializedName("signalingMode")
    val signalingMode: Any?,       // "external" or 1 = external, "internal" or 0 = internal
    val federation: List<String>? = null,  // Federation servers
    @SerializedName("helloAuthParams")
    val helloAuthParams: Map<String, Any>? = null  // Hello auth params (contains ticket)
) {
    /**
     * 是否配置了外部 Signaling Server
     */
    fun hasExternalSignaling(): Boolean {
        if (server.isNullOrBlank()) return false
        
        // signalingMode 可能是字符串 "external" 或整数 1
        return when (signalingMode) {
            is String -> signalingMode == "external"
            is Int -> signalingMode != 0
            else -> true  // 如果有 server 但没有 mode，假设是 external
        }
    }
    
    /**
     * 获取认证 ticket（优先从 helloAuthParams 获取）
     */
    fun getAuthTicket(): String? {
        // 尝试从 helloAuthParams.1.0.ticket 获取
        helloAuthParams?.let { params ->
            (params["1.0"] as? Map<*, *>)?.let { v1 ->
                return v1["ticket"] as? String
            }
        }
        // 否则使用顶层的 ticket
        return ticket
    }
}

/**
 * 房间 Ticket 响应
 */
data class SignalingTicketResponse(
    val ocs: OcsWrapper<SignalingTicket>
)

data class SignalingTicket(
    val ticket: String
)

data class OcsWrapper<T>(
    val data: T
)

/**
 * 缓存的 Signaling 配置
 */
data class CachedSignalingConfig(
    val serverUrl: String,
    val ticket: String?,
    val fetchedAt: Long = System.currentTimeMillis()
) {
    /**
     * 配置是否过期（超过 1 小时）
     */
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - fetchedAt > 3600_000
    }
}

/**
 * Nextcloud Talk Signaling Server WebSocket 客户端
 * 
 * 连接流程：
 * 1. 调用 Nextcloud API /ocs/v2.php/apps/spreed/api/v3/signaling/settings 获取服务器配置
 * 2. 解析返回的 server URL 和 ticket
 * 3. 连接到 Signaling Server WebSocket: {server}/ws?ticket={ticket}
 * 4. 发送 hello 消息进行认证
 * 5. 加入房间接收消息
 * 
 * 特性：
 * - 自动从 API 获取 Signaling Server 地址（不硬编码）
 * - 缓存配置到内存，减少 API 调用
 * - 配置过期自动刷新
 * - 优雅降级：API 失败时显示离线状态
 */
class SignalingClient(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {
    private val TAG = "SignalingClient"
    
    // 缓存的 Signaling 配置
    private var cachedConfig: CachedSignalingConfig? = null
    
    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    // Nextcloud Talk Signaling API
    private val signalingApi: SignalingApi by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", Credentials.basic(username, password))
                    .header("OCS-APIRequest", "true")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
        
        Retrofit.Builder()
            .baseUrl(serverUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SignalingApi::class.java)
    }
    
    // 连接状态
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    // 接收到的消息
    private val _messages = MutableSharedFlow<SignalingMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<SignalingMessage> = _messages
    
    // Signaling 配置状态（供 UI 显示）
    private val _signalingConfig = MutableStateFlow<SignalingConfigState>(SignalingConfigState.NotFetched)
    val signalingConfig: StateFlow<SignalingConfigState> = _signalingConfig
    
    // 当前会话 ID（用于 Hello 消息）
    private var currentSessionId: String? = null
    private var currentRoomId: String? = null
    private var reconnectJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 当前 ticket
    private var currentTicket: String? = null
    
    // 回复处理器
    private val pendingResponses = mutableMapOf<String, CompletableDeferred<JsonObject>>()
    private var messageIdCounter = 0
    
    // 连接是否活跃
    private var isActive = true
    
    /**
     * 获取 Signaling 配置
     * 优先使用缓存，过期或不存在时从 API 获取
     */
    private suspend fun fetchSignalingSettings(): SignalingSettings? {
        // 检查缓存是否有效
        cachedConfig?.let { config ->
            if (!config.isExpired()) {
                Log.d(TAG, "Using cached signaling config: ${config.serverUrl}")
                return SignalingSettings(
                    server = config.serverUrl,
                    ticket = config.ticket ?: currentTicket,
                    signalingMode = 1,
                    federation = null
                )
            }
        }
        
        // 从 API 获取配置
        Log.d(TAG, "Fetching signaling settings from API...")
        _signalingConfig.value = SignalingConfigState.Fetching
        
        return try {
            val response = signalingApi.getSettings()
            if (response.isSuccessful) {
                val settings = response.body()?.ocs?.data
                Log.d(TAG, "API returned: server=${settings?.server}, mode=${settings?.signalingMode}")
                
                // 缓存配置
                if (settings != null && !settings.server.isNullOrBlank()) {
                    cachedConfig = CachedSignalingConfig(
                        serverUrl = settings.server,
                        ticket = settings.ticket
                    )
                    _signalingConfig.value = SignalingConfigState.Fetched(settings.server)
                }
                
                settings
            } else {
                Log.e(TAG, "Failed to fetch signaling settings: ${response.code()}")
                _signalingConfig.value = SignalingConfigState.Error("API error: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching signaling settings: ${e.message}", e)
            _signalingConfig.value = SignalingConfigState.Error(e.message ?: "Unknown error")
            null
        }
    }
    
    /**
     * 连接到 Signaling Server
     */
    fun connect() {
        if (_connectionState.value == ConnectionState.Connecting || 
            _connectionState.value == ConnectionState.Connected) {
            Log.d(TAG, "Already connecting or connected, skipping")
            return
        }
        
        isActive = true
        _connectionState.value = ConnectionState.Connecting
        
        scope.launch {
            // 步骤 1: 从 Nextcloud Talk API 获取 signaling 设置
            Log.d(TAG, "Step 1: Getting signaling settings from Nextcloud API...")
            val settings = fetchSignalingSettings()
            
            if (settings == null) {
                Log.e(TAG, "Failed to get signaling settings, staying disconnected")
                _connectionState.value = ConnectionState.Error("无法获取 Signaling 配置")
                return@launch
            }
            
            // 保存 ticket（优先从 helloAuthParams 获取）
            currentTicket = settings.getAuthTicket()
            Log.d(TAG, "Got ticket: ${currentTicket?.take(20)}...")
            
            // 检查是否有外部 Signaling Server
            if (!settings.hasExternalSignaling()) {
                Log.d(TAG, "No external signaling server configured (mode=${settings.signalingMode})")
                _connectionState.value = ConnectionState.Error("未配置外部 Signaling Server")
                return@launch
            }
            
            // 步骤 2: 连接到 Signaling Server
            val serverUrl = settings.server!!
            Log.d(TAG, "Step 2: Connecting to signaling server: $serverUrl")
            connectToWebSocket(serverUrl, currentTicket)
        }
    }
    
    /**
     * 刷新 Signaling 配置（强制从 API 获取）
     */
    suspend fun refreshConfig(): SignalingConfigState {
        cachedConfig = null  // 清除缓存
        val settings = fetchSignalingSettings()
        return when {
            settings == null -> _signalingConfig.value
            settings.hasExternalSignaling() -> SignalingConfigState.Fetched(settings.server!!)
            else -> SignalingConfigState.Error("未配置外部 Signaling Server")
        }
    }
    
    /**
     * 连接到 WebSocket
     */
    private fun connectToWebSocket(signalingServerUrl: String, ticket: String?) {
        try {
            // 构建 WebSocket URL
            val wsUrl = buildWebSocketUrl(signalingServerUrl, ticket)
            Log.d(TAG, "Step 3: Connecting to WebSocket: $wsUrl")
            Log.d(TAG, "Server URL: $signalingServerUrl, Ticket: ${ticket?.take(20)}...")
            
            val request = Request.Builder()
                .url(wsUrl)
                .header("User-Agent", "NextcloudTalk-WearOS/1.0")
                .build()
            
            Log.d(TAG, "Request headers: ${request.headers}")
            
            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket opened successfully, response: ${response.code}")
                    Log.d(TAG, "Response headers: ${response.headers}")
                    // 步骤 4: 发送 Hello 消息
                    sendHello()
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "Received message: $text")
                    handleMessage(text)
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closing: code=$code, reason=$reason")
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: code=$code, reason=$reason")
                    _connectionState.value = ConnectionState.Disconnected
                    if (isActive) {
                        scheduleReconnect()
                    }
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure: ${t.message}", t)
                    Log.e(TAG, "Response: ${response?.code}, ${response?.message}")
                    Log.e(TAG, "Response headers: ${response?.headers}")
                    _connectionState.value = ConnectionState.Error(t.message ?: "WebSocket 连接失败")
                    if (isActive) {
                        scheduleReconnect()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Connection error: ${e.message}", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * 构建 WebSocket URL
     * 按照官方 Nextcloud Talk Android 实现
     * 
     * 官方格式：
     * String generatedURL = url.replace("https://", "wss://").replace("http://", "ws://");
     * if (generatedURL.endsWith("/")) {
     *     generatedURL += "spreed";
     * } else {
     *     generatedURL += "/spreed";
     * }
     * 
     * 例如: ws://yuanscrew.cn:8080/spreed
     */
    private fun buildWebSocketUrl(server: String, ticket: String?): String {
        // 按照官方实现：替换协议并添加 /spreed 后缀
        var wsUrl = server
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        
        // 添加 /spreed 后缀
        wsUrl = if (wsUrl.endsWith("/")) {
            wsUrl + "spreed"
        } else {
            wsUrl + "/spreed"
        }
        
        return wsUrl
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        isActive = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "Goodbye")
        webSocket = null
        currentRoomId = null
        currentSessionId = null
        _connectionState.value = ConnectionState.Disconnected
    }
    
    /**
     * 加入会话房间 - 必须调用此方法才能接收消息！
     * 
     * 按照官方 Nextcloud Talk Android 实现：
     * {
     *   "type": "room",
     *   "room": {
     *     "roomid": "j2b9ckxf"
     *   }
     * }
     */
    suspend fun joinRoom(token: String, sessionId: String? = null): Boolean {
        if (_connectionState.value != ConnectionState.Connected) {
            Log.w(TAG, "Cannot join room: not connected")
            return false
        }
        
        currentRoomId = token
        
        // 获取房间 ticket（可选，某些配置可能需要）
        try {
            val ticketResponse = signalingApi.getTicket(token)
            if (ticketResponse.isSuccessful) {
                currentTicket = ticketResponse.body()?.ocs?.data?.ticket
                Log.d(TAG, "Got ticket for room $token: ${currentTicket?.take(20)}...")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to get room ticket (may not be needed): ${e.message}")
        }
        
        Log.d(TAG, "Joining room: $token")
        
        return try {
            // 按照官方格式发送 room 消息
            val roomMessage = mapOf(
                "type" to "room",
                "room" to mapOf(
                    "roomid" to token
                )
            )
            sendJson(roomMessage)
            
            // 等待响应
            delay(1000)
            
            Log.d(TAG, "Room join message sent for: $token")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join room: ${e.message}", e)
            false
        }
    }
    
    /**
     * 离开会话房间
     */
    fun leaveRoom() {
        currentRoomId?.let { roomId ->
            Log.d(TAG, "Leaving room: $roomId")
            sendCommand("leave", mapOf("roomid" to roomId))
        }
        currentRoomId = null
    }
    
    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean = _connectionState.value == ConnectionState.Connected
    
    /**
     * 获取当前房间 ID
     */
    fun getCurrentRoomId(): String? = currentRoomId
    
    /**
     * 获取缓存的 Signaling Server URL
     */
    fun getSignalingServerUrl(): String? = cachedConfig?.serverUrl
    
    private fun generateSessionId(): String {
        return "wear-${System.currentTimeMillis()}-${(1000..9999).random()}"
    }
    
    /**
     * 发送 Hello 消息进行认证
     * 
     * 按照官方 Nextcloud Talk Android 实现：
     * {
     *   "type": "hello",
     *   "hello": {
     *     "version": "1.0",
     *     "auth": {
     *       "url": "https://nextcloud/ocs/v2.php/apps/spreed/api/v3/signaling/backend",
     *       "params": {
     *         "ticket": "xxx",
     *         "userid": "admin"
     *       }
     *     }
     *   }
     * }
     */
    private fun sendHello() {
        // 构建认证后端 URL
        val backendUrl = serverUrl.trimEnd('/') + "/ocs/v2.php/apps/spreed/api/v3/signaling/backend"
        
        // 按照官方格式构建 Hello 消息
        val hello = mapOf(
            "type" to "hello",
            "hello" to mapOf(
                "version" to "1.0",
                "auth" to mapOf(
                    "url" to backendUrl,
                    "params" to mapOf(
                        "ticket" to (currentTicket ?: ""),
                        "userid" to username
                    )
                )
            )
        )
        
        Log.d(TAG, "Sending hello with backend URL: $backendUrl, userid: $username")
        sendJson(hello)
    }
    
    private fun handleMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type")?.asString?.lowercase()
            
            Log.d(TAG, "Processing message type: $type")
            
            when (type) {
                "hello" -> {
                    // Hello 响应，提取 session ID
                    val helloData = json.getAsJsonObject("hello") ?: json
                    currentSessionId = helloData?.get("sessionid")?.asString 
                        ?: helloData?.get("sessionId")?.asString
                    Log.d(TAG, "Hello received, Session ID: $currentSessionId")
                    
                    // 连接成功
                    _connectionState.value = ConnectionState.Connected
                    
                    // 如果之前有待加入的房间，重新加入
                    currentRoomId?.let { roomId ->
                        scope.launch {
                            joinRoom(roomId, currentSessionId)
                        }
                    }
                }
                
                "room" -> {
                    // 房间事件
                    val roomData = json.getAsJsonObject("room") ?: json
                    val roomEvent = roomData?.get("event")?.asString
                    Log.d(TAG, "Room event: $roomEvent")
                    
                    if (roomEvent == "join" || roomEvent == "joined") {
                        Log.d(TAG, "Successfully joined room")
                    }
                    
                    // 房间内的消息
                    if (roomEvent == "message") {
                        parseAndEmitMessage(roomData)
                    }
                }
                
                "event" -> {
                    // 消息事件
                    val eventData = json.getAsJsonObject("event") ?: json
                    val eventType = eventData?.get("type")?.asString
                    
                    Log.d(TAG, "Event type: $eventType")
                    
                    when (eventType) {
                        "message", "chat" -> {
                            parseAndEmitMessage(eventData)
                        }
                        
                        "room" -> {
                            // 房间事件（用户加入/离开等）
                            val roomEventData = eventData.getAsJsonObject("room")
                            val roomEvent = roomEventData?.get("event")?.asString
                            if (roomEvent == "message") {
                                parseAndEmitMessage(roomEventData)
                            }
                        }
                    }
                }
                
                "message" -> {
                    // 直接消息格式
                    parseAndEmitMessage(json)
                }
                
                "error" -> {
                    val errorData = json.getAsJsonObject("error")
                    val errorMsg = errorData?.get("message")?.asString ?: "Unknown error"
                    Log.e(TAG, "Signaling error: $errorMsg")
                }
                
                "response" -> {
                    // 响应消息
                    val id = json.get("id")?.asString
                    if (id != null) {
                        pendingResponses[id]?.complete(json)
                        pendingResponses.remove(id)
                    }
                }
                
                else -> {
                    // 尝试解析未知格式的消息
                    Log.d(TAG, "Unknown message type: $type, attempting to parse")
                    tryParseUnknownFormat(json)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: ${e.message}", e)
        }
    }
    
    /**
     * 解析并发出消息
     */
    private fun parseAndEmitMessage(data: JsonObject?) {
        if (data == null) return
        
        try {
            // 尝试多种消息格式
            val messageData = data.getAsJsonObject("message") 
                ?: data.getAsJsonObject("data")
                ?: data
            
            val roomId = messageData?.get("roomid")?.asString
                ?: messageData?.get("roomId")?.asString
                ?: messageData?.get("token")?.asString
                ?: currentRoomId
                ?: ""
            
            val messageId = messageData?.get("id")?.asLong
                ?: messageData?.get("messageId")?.asLong
                ?: System.currentTimeMillis()
            
            val content = messageData?.get("content")?.asString
                ?: messageData?.get("message")?.asString
                ?: messageData?.get("text")?.asString
                ?: ""
            
            val senderId = messageData?.get("sender")?.asString
                ?: messageData?.get("senderId")?.asString
                ?: messageData?.get("actorId")?.asString
                ?: ""
            
            val senderName = messageData?.get("sendername")?.asString
                ?: messageData?.get("senderName")?.asString
                ?: messageData?.get("actorDisplayName")?.asString
                ?: ""
            
            val timestamp = messageData?.get("timestamp")?.asLong
                ?: System.currentTimeMillis()
            
            if (content.isNotEmpty()) {
                val signalingMsg = SignalingMessage.ChatMessage(
                    roomId = roomId,
                    messageId = messageId,
                    content = content,
                    senderId = senderId,
                    senderName = senderName,
                    timestamp = timestamp
                )
                
                Log.d(TAG, "Emitting chat message: from=$senderName, content=$content")
                _messages.tryEmit(signalingMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message data: ${e.message}", e)
        }
    }
    
    /**
     * 尝试解析未知格式的消息
     */
    private fun tryParseUnknownFormat(json: JsonObject) {
        // 检查是否包含典型的消息字段
        val hasContent = json.has("message") || json.has("content") || json.has("text")
        val hasSender = json.has("sender") || json.has("senderId") || json.has("actorId")
        
        if (hasContent || hasSender) {
            parseAndEmitMessage(json)
        }
    }
    
    private suspend fun sendRequest(type: String, params: Map<String, Any>): JsonObject {
        val id = (++messageIdCounter).toString()
        val request = mutableMapOf<String, Any?>(
            "id" to id,
            "type" to type
        )
        request.putAll(params)
        
        val deferred = CompletableDeferred<JsonObject>()
        pendingResponses[id] = deferred
        
        sendJson(request)
        
        // 等待响应，超时 10 秒
        return withTimeoutOrNull(10000) {
            deferred.await()
        } ?: JsonObject()
    }
    
    private fun sendCommand(type: String, params: Map<String, Any>) {
        val command = mutableMapOf<String, Any?>("type" to type)
        command.putAll(params)
        sendJson(command)
    }
    
    private fun sendJson(data: Map<String, Any?>) {
        val json = gson.toJson(data)
        Log.d(TAG, "Sending: $json")
        webSocket?.send(json)
    }
    
    private fun scheduleReconnect() {
        if (!isActive) {
            Log.d(TAG, "Not scheduling reconnect: client is not active")
            return
        }
        
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5000) // 5秒后重连
            if (isActive && _connectionState.value != ConnectionState.Connected) {
                Log.d(TAG, "Attempting reconnect...")
                // 清除缓存，重新获取配置
                cachedConfig = null
                connect()
            }
        }
    }
}

/**
 * 连接状态
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * Signaling 配置状态
 */
sealed class SignalingConfigState {
    object NotFetched : SignalingConfigState()
    object Fetching : SignalingConfigState()
    data class Fetched(val serverUrl: String) : SignalingConfigState()
    data class Error(val message: String) : SignalingConfigState()
}

/**
 * Signaling 消息类型
 */
sealed class SignalingMessage {
    data class ChatMessage(
        val roomId: String,
        val messageId: Long,
        val content: String,
        val senderId: String,
        val senderName: String,
        val timestamp: Long
    ) : SignalingMessage()
    
    data class RoomEvent(
        val roomId: String,
        val event: String,
        val data: Map<String, Any>
    ) : SignalingMessage()
}

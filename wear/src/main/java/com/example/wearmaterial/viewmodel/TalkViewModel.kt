package com.example.wearmaterial.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wearmaterial.data.SettingsRepository
import com.example.wearmaterial.data.local.*
import com.example.wearmaterial.models.*
import com.example.wearmaterial.network.NetworkModule
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TalkViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "TalkViewModel"
    private val repository = SettingsRepository(application)
    
    // Database migration
    private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE conversations ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
        }
    }
    
    // Database instance
    private val database = androidx.room.Room.databaseBuilder(
        application,
        TalkDatabase::class.java,
        "talk_database"
    ).addMigrations(MIGRATION_1_2).build()
    
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    
    // 长轮询配置
    private val POLLING_INTERVAL = 3000L // 3秒轮询间隔
    private val LONG_POLL_TIMEOUT = 30 // 长轮询超时（秒）
    
    // 长轮询 Job
    private var pollingJob: Job? = null
    
    // UI 状态
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState
    
    // 会话列表
    private val _conversations = MutableStateFlow<List<Room>>(emptyList())
    val conversations: StateFlow<List<Room>> = _conversations
    
    private val _isLoadingConversations = MutableStateFlow(false)
    val isLoadingConversations: StateFlow<Boolean> = _isLoadingConversations
    
    private val _conversationsError = MutableStateFlow<String?>(null)
    val conversationsError: StateFlow<String?> = _conversationsError
    
    // 会话列表刷新状态
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing
    
    // 消息列表
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    
    private val _isLoadingMessages = MutableStateFlow(false)
    val isLoadingMessages: StateFlow<Boolean> = _isLoadingMessages
    
    private val _messagesError = MutableStateFlow<String?>(null)
    val messagesError: StateFlow<String?> = _messagesError
    
    // 当前打开的会话 Token（用于 WebSocket 监听）
    private val _currentConversationToken = MutableStateFlow<String?>(null)
    val currentConversationToken: StateFlow<String?> = _currentConversationToken
    
    // 最后查看的消息 ID（用于定位新消息）
    private val _lastViewedMessageId = MutableStateFlow<Map<String, Int>>(emptyMap())
    val lastViewedMessageId: StateFlow<Map<String, Int>> = _lastViewedMessageId
    
    // 新消息计数（用于定位）
    private val _newMessageCount = MutableStateFlow<Map<String, Int>>(emptyMap())
    val newMessageCount: StateFlow<Map<String, Int>> = _newMessageCount
    
    // 轮询状态
    private val _pollingState = MutableStateFlow<PollingState>(PollingState.Idle)
    val pollingState: StateFlow<PollingState> = _pollingState
    
    // 最后消息 ID（用于长轮询）
    private val _lastMessageId = MutableStateFlow<Map<String, Int>>(emptyMap())
    
    // 输入字段（用于登录）
    private val _inputServerUrl = MutableStateFlow("")
    val inputServerUrl: StateFlow<String> = _inputServerUrl
    
    private val _inputUsername = MutableStateFlow("")
    val inputUsername: StateFlow<String> = _inputUsername
    
    private val _inputPassword = MutableStateFlow("")
    val inputPassword: StateFlow<String> = _inputPassword
    
    // 登录状态（已初始化 NetworkModule）
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized
    
    // 创建会话状态
    private val _createConversationState = MutableStateFlow<CreateConversationState>(CreateConversationState.Idle)
    val createConversationState: StateFlow<CreateConversationState> = _createConversationState
    
    // Bot 管理状态
    private val _availableBots = MutableStateFlow<List<Bot>>(emptyList())
    val availableBots: StateFlow<List<Bot>> = _availableBots
    
    private val _isLoadingBots = MutableStateFlow(false)
    val isLoadingBots: StateFlow<Boolean> = _isLoadingBots
    
    val isLoggedIn = repository.isLoggedIn
    val notificationsEnabled = repository.notificationsEnabled
    val vibrationEnabled = repository.vibrationEnabled
    
    init {
        // 初始化时加载保存的凭证
        viewModelScope.launch {
            repository.serverUrl.first()?.let { url ->
                repository.credentials.first()?.let { (user, pass) ->
                    try {
                        // 配置 NetworkModule
                        NetworkModule.configure(url, user, pass)
                        _inputServerUrl.value = url
                        _inputUsername.value = user
                        _inputPassword.value = pass
                        _isInitialized.value = true
                        
                        // 加载缓存的会话
                        val cachedConversations = conversationDao.getAll()
                        _conversations.value = cachedConversations.map { it.toModel() }
                        
                        // 后台刷新会话列表（不阻塞启动）
                        loadConversations()
                        
                        // 预加载所有消息
                        preloadAllMessages()
                        
                        // 标记轮询状态为就绪
                        _pollingState.value = PollingState.Ready
                    } catch (e: Exception) {
                        // 初始化失败，但不清除登录状态
                        // 用户可以稍后重试
                        _isInitialized.value = true
                        android.util.Log.e("TalkViewModel", "Init failed: ${e.message}")
                    }
                }
            }
        }
    }
    
    /**
     * 启动长轮询获取新消息
     */
    fun startPolling(token: String) {
        // 如果已经在轮询同一个会话，不做任何事
        if (pollingJob?.isActive == true && _currentConversationToken.value == token) {
            return
        }
        
        // 取消之前的轮询
        pollingJob?.cancel()
        
        _currentConversationToken.value = token
        _pollingState.value = PollingState.Polling
        
        pollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val lastId = _lastMessageId.value[token] ?: 0
                    
                    // 使用 lookIntoFuture=1 进行长轮询
                    val response = NetworkModule.api.getMessages(
                        token = token,
                        lookIntoFuture = 1,
                        lastKnownMessageId = lastId,
                        limit = 100
                    )
                    
                    if (response.isSuccessful) {
                        val newMessages = response.body()?.ocs?.data ?: emptyList()
                        
                        if (newMessages.isNotEmpty()) {
                            // 更新最后消息 ID
                            val newLastId = newMessages.maxOf { it.id }
                            val ids = _lastMessageId.value.toMutableMap()
                            ids[token] = newLastId
                            _lastMessageId.value = ids
                            
                            // 更新消息列表
                            val currentMessages = _messages.value.toMutableList()
                            newMessages.forEach { msg ->
                                if (currentMessages.none { it.id == msg.id }) {
                                    currentMessages.add(msg)
                                }
                            }
                            _messages.value = currentMessages
                            
                            // 保存到数据库
                            messageDao.insertAll(newMessages.map { it.toEntity(token) })
                            
                            // 更新会话列表中的最后消息
                            newMessages.lastOrNull()?.let { lastMsg ->
                                updateConversationLastMessageFromPolling(token, lastMsg)
                            }
                            
                            Log.d(TAG, "Polling: received ${newMessages.size} new messages")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}")
                    // 网络错误时短暂等待后重试
                    delay(5000)
                }
                
                // 轮询间隔
                delay(POLLING_INTERVAL)
            }
        }
    }
    
    /**
     * 停止长轮询
     */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _pollingState.value = PollingState.Idle
    }
    
    /**
     * 从轮询消息更新会话列表
     */
    private fun updateConversationLastMessageFromPolling(token: String, message: Message) {
        val conversations = _conversations.value.toMutableList()
        val index = conversations.indexOfFirst { it.token == token }
        if (index >= 0) {
            val room = conversations[index]
            val updatedRoom = room.copy(
                lastMessage = message,
                lastActivity = message.timestamp
            )
            // 移动到顶部
            conversations.removeAt(index)
            conversations.add(0, updatedRoom)
            _conversations.value = conversations
        }
    }
    
    fun setInputServerUrl(value: String) { _inputServerUrl.value = value }
    fun setInputUsername(value: String) { _inputUsername.value = value }
    fun setInputPassword(value: String) { _inputPassword.value = value }
    
    fun login(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                // 验证 URL 格式
                val cleanUrl = serverUrl.trim().trimEnd('/')
                if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                    _uiState.value = UiState.Error("地址需以 http:// 或 https:// 开头")
                    return@launch
                }
                
                android.util.Log.d("TalkViewModel", "Attempting login to: $cleanUrl")
                
                // 配置网络模块
                NetworkModule.configure(cleanUrl, username, password)
                
                // 测试连接
                android.util.Log.d("TalkViewModel", "Testing connection...")
                val response = NetworkModule.api.getCurrentUser()
                android.util.Log.d("TalkViewModel", "Response: ${response.code()}")
                
                if (response.isSuccessful && response.body()?.ocs?.data != null) {
                    // 保存登录信息
                    repository.saveLogin(cleanUrl, username, password)
                    
                    // 更新输入字段
                    _inputServerUrl.value = cleanUrl
                    _inputUsername.value = username
                    _inputPassword.value = password
                    
                    _isInitialized.value = true
                    _uiState.value = UiState.Success
                    
                    // 标记轮询状态为就绪
                    _pollingState.value = PollingState.Ready
                    
                    android.util.Log.d("TalkViewModel", "Login successful")
                } else {
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("TalkViewModel", "Login failed: ${response.code()} - $errorBody")
                    _uiState.value = UiState.Error("登录失败：${response.code()}")
                }
            } catch (e: java.net.UnknownHostException) {
                android.util.Log.e("TalkViewModel", "Unknown host", e)
                _uiState.value = UiState.Error("无法解析服务器地址")
            } catch (e: java.net.SocketTimeoutException) {
                android.util.Log.e("TalkViewModel", "Timeout", e)
                _uiState.value = UiState.Error("连接超时")
            } catch (e: java.net.ConnectException) {
                android.util.Log.e("TalkViewModel", "Connection refused", e)
                _uiState.value = UiState.Error("连接被拒绝")
            } catch (e: javax.net.ssl.SSLHandshakeException) {
                android.util.Log.e("TalkViewModel", "SSL error", e)
                _uiState.value = UiState.Error("SSL证书错误")
            } catch (e: IllegalArgumentException) {
                android.util.Log.e("TalkViewModel", "Invalid URL", e)
                _uiState.value = UiState.Error("无效的服务器地址")
            } catch (e: Exception) {
                android.util.Log.e("TalkViewModel", "Login error: ${e.javaClass.simpleName}: ${e.message}", e)
                val msg = e.message?.takeIf { it.isNotEmpty() } ?: e.javaClass.simpleName
                _uiState.value = UiState.Error("错误：$msg")
            }
        }
    }
    
    fun resetUiState() {
        _uiState.value = UiState.Idle
    }
    
    fun loadConversations() {
        // 检查 NetworkModule 是否已配置
        if (!NetworkModule.isConfigured()) return
        
        viewModelScope.launch {
            // 静默加载，不显示加载状态
            try {
                val response = NetworkModule.api.getRooms()
                if (response.isSuccessful) {
                    val rooms = response.body()?.ocs?.data ?: emptyList()
                    
                    // 保存到数据库
                    conversationDao.deleteAll()
                    conversationDao.insertAll(rooms.map { it.toEntity() })
                    
                    // 更新 UI
                    _conversations.value = rooms
                }
            } catch (e: Exception) {
                // 只在缓存为空时显示错误
                if (_conversations.value.isEmpty()) {
                    _conversationsError.value = e.message ?: "网络错误"
                }
            }
        }
    }
    
    /**
     * 下拉刷新会话列表
     */
    fun refreshConversations() {
        if (!NetworkModule.isConfigured()) return
        
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val response = NetworkModule.api.getRooms()
                if (response.isSuccessful) {
                    val rooms = response.body()?.ocs?.data ?: emptyList()
                    
                    // 保存到数据库
                    conversationDao.deleteAll()
                    conversationDao.insertAll(rooms.map { it.toEntity() })
                    
                    // 更新 UI
                    _conversations.value = rooms
                }
            } catch (e: Exception) {
                _conversationsError.value = e.message ?: "网络错误"
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    
    /**
     * 加载消息 - 带消息定位支持和启动长轮询
     */
    fun loadMessages(token: String, initialLoad: Boolean = true) {
        if (!NetworkModule.isConfigured()) return
        
        viewModelScope.launch {
            // 设置当前会话
            _currentConversationToken.value = token
            
            // 先显示缓存
            val cached = messageDao.getByConversation(token)
            _messages.value = cached.map { it.toModel() }
            
            // 记录最后查看的消息 ID
            if (cached.isNotEmpty() && initialLoad) {
                val lastId = cached.maxOf { it.id }
                val viewed = _lastViewedMessageId.value.toMutableMap()
                viewed[token] = lastId
                _lastViewedMessageId.value = viewed
                
                // 更新最后消息 ID（用于长轮询）
                val msgIds = _lastMessageId.value.toMutableMap()
                msgIds[token] = lastId
                _lastMessageId.value = msgIds
                
                // 清除新消息计数
                val counts = _newMessageCount.value.toMutableMap()
                counts.remove(token)
                _newMessageCount.value = counts
            }
            
            // 静默增量更新
            try {
                val lastId = cached.maxOfOrNull { it.id } ?: 0
                val response = NetworkModule.api.getMessages(
                    token = token,
                    lastKnownMessageId = lastId
                )
                if (response.isSuccessful) {
                    val newMessages = response.body()?.ocs?.data ?: emptyList()
                    
                    if (newMessages.isNotEmpty()) {
                        // 保存新消息到数据库
                        messageDao.insertAll(newMessages.map { it.toEntity(token) })
                        
                        // 重新加载所有消息
                        val allMessages = messageDao.getByConversation(token)
                        _messages.value = allMessages.map { it.toModel() }
                        
                        // 更新最后消息 ID（用于长轮询）
                        val latestId = newMessages.maxOf { it.id }
                        val msgIds = _lastMessageId.value.toMutableMap()
                        msgIds[token] = latestId
                        _lastMessageId.value = msgIds
                        
                        // 更新新消息计数
                        if (!initialLoad) {
                            val counts = _newMessageCount.value.toMutableMap()
                            counts[token] = (counts[token] ?: 0) + newMessages.size
                            _newMessageCount.value = counts
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load messages: ${e.message}")
            }
            
            // 启动长轮询获取新消息
            startPolling(token)
        }
    }
    
    /**
     * 刷新当前会话消息 - 用于手动刷新或轮询
     */
    fun refreshCurrentMessages() {
        val token = _currentConversationToken.value ?: return
        loadMessages(token, initialLoad = false)
    }
    
    /**
     * 发送消息 - 发送后自动刷新
     */
    fun sendMessage(token: String, message: String) {
        viewModelScope.launch {
            try {
                val response = NetworkModule.api.sendMessage(token, message)
                if (response.isSuccessful) {
                    val sentMessage = response.body()?.ocs?.data
                    if (sentMessage != null) {
                        // 直接添加到当前列表
                        val currentList = _messages.value.toMutableList()
                        currentList.add(sentMessage)
                        _messages.value = currentList
                        
                        // 保存到数据库
                        messageDao.insertAll(listOf(sentMessage.toEntity(token)))
                        
                        // 更新最后查看的消息 ID
                        val viewed = _lastViewedMessageId.value.toMutableMap()
                        viewed[token] = sentMessage.id
                        _lastViewedMessageId.value = viewed
                    }
                }
            } catch (e: Exception) {
                // 错误处理
                Log.e(TAG, "Failed to send message", e)
            }
        }
    }
    
    // ==================== 新功能：创建会话 ====================
    
    /**
     * 创建新会话
     */
    fun createConversation(name: String, type: Int = 3) {
        viewModelScope.launch {
            _createConversationState.value = CreateConversationState.Loading
            try {
                val response = NetworkModule.api.createRoom(
                    roomType = type,
                    roomName = name
                )
                if (response.isSuccessful) {
                    val room = response.body()?.ocs?.data
                    if (room != null) {
                        // 添加到会话列表
                        val currentList = _conversations.value.toMutableList()
                        currentList.add(0, room)
                        _conversations.value = currentList
                        
                        // 保存到数据库
                        conversationDao.insertAll(listOf(room.toEntity()))
                        
                        _createConversationState.value = CreateConversationState.Success(room)
                    } else {
                        _createConversationState.value = CreateConversationState.Error("创建失败")
                    }
                } else {
                    _createConversationState.value = CreateConversationState.Error("创建失败: ${response.code()}")
                }
            } catch (e: Exception) {
                _createConversationState.value = CreateConversationState.Error(e.message ?: "网络错误")
            }
        }
    }
    
    fun resetCreateConversationState() {
        _createConversationState.value = CreateConversationState.Idle
    }
    
    // ==================== 新功能：Bot 管理 ====================
    
    /**
     * 加载可用的 Bot 列表
     */
    fun loadAvailableBots() {
        if (!NetworkModule.isConfigured()) return
        
        viewModelScope.launch {
            _isLoadingBots.value = true
            try {
                val response = NetworkModule.api.getBots()
                if (response.isSuccessful) {
                    val bots = response.body()?.ocs?.data ?: emptyList()
                    _availableBots.value = bots
                }
            } catch (e: Exception) {
                // 错误处理
            } finally {
                _isLoadingBots.value = false
            }
        }
    }
    
    /**
     * 加载会话中的 Bot 列表
     */
    fun loadConversationBots(token: String): Flow<List<Bot>> = flow {
        if (!NetworkModule.isConfigured()) {
            emit(emptyList())
            return@flow
        }
        try {
            val response = NetworkModule.api.getConversationBots(token)
            if (response.isSuccessful) {
                emit(response.body()?.ocs?.data ?: emptyList())
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            emit(emptyList())
        }
    }
    
    /**
     * 添加 Bot 到会话
     */
    fun addBotToConversation(token: String, botId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val botIdInt = botId.toIntOrNull() ?: 0
                Log.d(TAG, "Adding bot $botIdInt to conversation $token")
                val response = NetworkModule.api.addBotToConversation(token, botIdInt)
                Log.d(TAG, "Add bot response: ${response.code()} success=${response.isSuccessful}")
                if (!response.isSuccessful) {
                    Log.e(TAG, "Add bot failed: ${response.errorBody()?.string()}")
                }
                onComplete(response.isSuccessful)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add bot: ${e.message}", e)
                onComplete(false)
            }
        }
    }
    
    /**
     * 从会话移除 Bot
     */
    fun removeBotFromConversation(token: String, botId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val botIdInt = botId.toIntOrNull() ?: 0
                Log.d(TAG, "Removing bot $botIdInt from conversation $token")
                val response = NetworkModule.api.removeBotFromConversation(token, botIdInt)
                Log.d(TAG, "Remove bot response: ${response.code()} success=${response.isSuccessful}")
                if (!response.isSuccessful) {
                    Log.e(TAG, "Remove bot failed: ${response.errorBody()?.string()}")
                }
                onComplete(response.isSuccessful)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove bot: ${e.message}", e)
                onComplete(false)
            }
        }
    }
    
    // ==================== 会话管理功能 ====================
    
    /**
     * 切换会话置顶状态
     */
    fun toggleConversationFavorite(token: String, isFavorite: Boolean, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val response = if (isFavorite) {
                    NetworkModule.api.unfavoriteConversation(token)
                } else {
                    NetworkModule.api.favoriteConversation(token)
                }
                if (response.isSuccessful) {
                    // 更新本地列表
                    val conversations = _conversations.value.toMutableList()
                    val index = conversations.indexOfFirst { it.token == token }
                    if (index >= 0) {
                        val room = conversations[index]
                        conversations[index] = room.copy(isFavorite = !isFavorite)
                        _conversations.value = conversations
                    }
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle favorite: ${e.message}")
                onComplete(false)
            }
        }
    }
    
    /**
     * 删除会话
     */
    fun deleteConversation(token: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val response = NetworkModule.api.deleteConversation(token)
                if (response.isSuccessful) {
                    // 从本地列表移除
                    val conversations = _conversations.value.toMutableList()
                    conversations.removeAll { it.token == token }
                    _conversations.value = conversations
                    
                    // 从数据库移除
                    conversationDao.deleteByToken(token)
                    messageDao.deleteByConversation(token)
                    
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete conversation: ${e.message}")
                onComplete(false)
            }
        }
    }
    
    /**
     * 标记会话为已读
     */
    fun markAsRead(token: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val response = NetworkModule.api.markConversationRead(token)
                if (response.isSuccessful) {
                    // 更新本地列表
                    val conversations = _conversations.value.toMutableList()
                    val index = conversations.indexOfFirst { it.token == token }
                    if (index >= 0) {
                        val room = conversations[index]
                        conversations[index] = room.copy(unreadMessages = 0)
                        _conversations.value = conversations
                    }
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark as read: ${e.message}")
                onComplete(false)
            }
        }
    }
    
    /**
     * 标记会话为未读
     */
    fun markAsUnread(token: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val response = NetworkModule.api.markConversationUnread(token)
                if (response.isSuccessful) {
                    // 更新本地列表（设置未读数为 1，因为 Nextcloud 不会返回具体数量）
                    val conversations = _conversations.value.toMutableList()
                    val index = conversations.indexOfFirst { it.token == token }
                    if (index >= 0) {
                        val room = conversations[index]
                        conversations[index] = room.copy(unreadMessages = 1)
                        _conversations.value = conversations
                    }
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark as unread: ${e.message}")
                onComplete(false)
            }
        }
    }
    
    /**
     * 离开当前会话（关闭聊天界面时调用）
     */
    fun leaveCurrentConversation() {
        stopPolling()
        _currentConversationToken.value = null
    }
    
    /**
     * 获取应该定位到的消息索引
     * - 首次打开：返回最后一条消息的索引
     * - 有新消息：返回第一条新消息的索引
     * - 没有新消息：返回最后一条消息的索引
     */
    fun getScrollToMessageIndex(token: String, messages: List<Message>): Int {
        if (messages.isEmpty()) return 0
        
        val lastViewedId = _lastViewedMessageId.value[token]
        val newCount = _newMessageCount.value[token] ?: 0
        
        return when {
            // 首次打开或没有新消息：定位到最后
            lastViewedId == null || newCount == 0 -> messages.size - 1
            
            // 有新消息：找到第一条新消息
            else -> {
                val firstNewIndex = messages.indexOfFirst { it.id > lastViewedId }
                if (firstNewIndex >= 0) firstNewIndex else messages.size - 1
            }
        }
    }
    
    /**
     * 标记当前会话的所有消息为已读
     */
    fun markConversationAsRead(token: String) {
        val messages = _messages.value
        if (messages.isNotEmpty()) {
            val lastId = messages.maxOf { it.id }
            val viewed = _lastViewedMessageId.value.toMutableMap()
            viewed[token] = lastId
            _lastViewedMessageId.value = viewed
            
            // 清除新消息计数
            val counts = _newMessageCount.value.toMutableMap()
            counts.remove(token)
            _newMessageCount.value = counts
        }
    }
    
    // 启动时预加载所有会话的消息
    fun preloadAllMessages() {
        if (!NetworkModule.isConfigured()) return
        
        viewModelScope.launch {
            val conversations = conversationDao.getAll()
            conversations.forEach { conv ->
                try {
                    val cached = messageDao.getByConversation(conv.token)
                    val lastId = cached.maxOfOrNull { it.id } ?: 0
                    
                    val response = NetworkModule.api.getMessages(conv.token, lastKnownMessageId = lastId)
                    if (response.isSuccessful) {
                        val newMessages = response.body()?.ocs?.data ?: emptyList()
                        if (newMessages.isNotEmpty()) {
                            messageDao.insertAll(newMessages.map { it.toEntity(conv.token) })
                        }
                    }
                } catch (e: Exception) {
                    // 静默失败
                }
            }
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            // 停止轮询
            stopPolling()
            
            repository.clearLogin()
            conversationDao.deleteAll()
            _conversations.value = emptyList()
            _messages.value = emptyList()
            _uiState.value = UiState.Idle
            _isInitialized.value = false
            _inputServerUrl.value = ""
            _inputUsername.value = ""
            _inputPassword.value = ""
            _pollingState.value = PollingState.Idle
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}

/**
 * 轮询状态
 */
sealed class PollingState {
    object Idle : PollingState()
    object Ready : PollingState()
    object Polling : PollingState()
}

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    object Success : UiState()
    data class Error(val message: String) : UiState()
}

sealed class CreateConversationState {
    object Idle : CreateConversationState()
    object Loading : CreateConversationState()
    data class Success(val room: Room) : CreateConversationState()
    data class Error(val message: String) : CreateConversationState()
}

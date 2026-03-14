package com.example.wearmaterial.models

data class User(
    val id: String,
    val userId: String?,
    val displayName: String?,
    val email: String? = null
)

data class UserResponse(val ocs: OcsResponse<User>)

data class Room(
    val id: Int,
    val token: String,
    val name: String,
    val displayName: String?,
    val type: Int,
    val lastMessage: Message?,
    val unreadMessages: Int = 0,
    val lastActivity: Long = 0,
    val isFavorite: Boolean = false
)

data class RoomsResponse(val ocs: OcsResponse<List<Room>>)

data class RoomResponse(val ocs: OcsResponse<Room>)

data class Message(
    val id: Int,
    val token: String?,
    val actorId: String?,
    val actorDisplayName: String?,
    val actorType: String? = null,
    val timestamp: Long,
    val message: String,
    val messageType: String? = "comment",
    val systemMessage: String? = null
)

data class MessagesResponse(val ocs: OcsResponse<List<Message>>)

data class MessageResponse(val ocs: OcsResponse<Message>)

data class OcsResponse<T>(
    val meta: MetaResponse,
    val data: T
)

data class MetaResponse(
    val status: String,
    val statuscode: Int
)

// ==================== Bot 数据模型 ====================

/**
 * Bot 信息
 */
data class Bot(
    val id: Int,
    val name: String,
    val description: String?,
    val state: Int = 0,  // 0=禁用, 1=启用
    val conversationId: Int? = null
)

data class BotsResponse(val ocs: OcsResponse<List<Bot>>)

// ==================== 创建会话请求模型 ====================

/**
 * 创建会话请求
 */
data class CreateRoomRequest(
    val roomType: Int,      // 1=一对一, 2=群组, 3=公开群组
    val roomName: String? = null
)

// ==================== UI 数据模型 ====================

/**
 * 会话列表项（用于 UI 显示）
 */
data class ConversationItem(
    val token: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int,
    val isFavorite: Boolean,
    val isGroup: Boolean
)

/**
 * Room 转换为 ConversationItem
 */
fun Room.toConversationItem(): ConversationItem {
    return ConversationItem(
        token = this.token,
        name = this.displayName ?: this.name,
        lastMessage = this.lastMessage?.message ?: "暂无消息",
        time = formatTime(this.lastActivity),
        unreadCount = this.unreadMessages,
        isFavorite = this.isFavorite,
        isGroup = this.type == 2 || this.type == 3
    )
}

/**
 * 格式化时间
 */
private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis() / 1000
    val diff = now - timestamp
    
    return when {
        diff < 60 -> "刚刚"
        diff < 3600 -> "${diff / 60}分钟前"
        diff < 86400 -> "${diff / 3600}小时前"
        diff < 604800 -> "${diff / 86400}天前"
        else -> {
            val date = java.util.Date(timestamp * 1000)
            java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault()).format(date)
        }
    }
}

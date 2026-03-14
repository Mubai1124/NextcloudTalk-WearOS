package com.example.wearmaterial.api

import retrofit2.http.*
import retrofit2.Response
import com.example.wearmaterial.models.*

interface NcApi {
    // 获取当前用户
    @GET("ocs/v2.php/cloud/user")
    suspend fun getCurrentUser(): Response<UserResponse>
    
    // 获取会话列表
    @GET("ocs/v2.php/apps/spreed/api/v4/room")
    suspend fun getRooms(): Response<RoomsResponse>
    
    // 获取消息 - 使用正确的 API 路径
    @GET("ocs/v2.php/apps/spreed/api/v1/chat/{token}")
    suspend fun getMessages(
        @Path("token") token: String,
        @Query("lookIntoFuture") lookIntoFuture: Int = 0,
        @Query("limit") limit: Int = 50,
        @Query("lastKnownMessageId") lastKnownMessageId: Int = 0
    ): Response<MessagesResponse>
    
    // 发送消息
    @FormUrlEncoded
    @POST("ocs/v2.php/apps/spreed/api/v1/chat/{token}")
    suspend fun sendMessage(
        @Path("token") token: String,
        @Field("message") message: String
    ): Response<MessageResponse>
    
    // ==================== 新功能：创建会话 ====================
    
    /**
     * 创建新会话
     * @param roomType 会话类型: 1=一对一, 2=群组, 3=公开群组
     * @param roomName 会话名称
     */
    @FormUrlEncoded
    @POST("ocs/v2.php/apps/spreed/api/v4/room")
    suspend fun createRoom(
        @Field("roomType") roomType: Int,
        @Field("roomName") roomName: String
    ): Response<RoomResponse>
    
    // ==================== 新功能：Bot 管理 ====================
    
    /**
     * 获取可用的 Bot 列表
     */
    @GET("ocs/v2.php/apps/spreed/api/v1/bot/admin")
    suspend fun getBots(): Response<BotsResponse>
    
    /**
     * 获取会话中的 Bot 列表
     */
    @GET("ocs/v2.php/apps/spreed/api/v1/bot/{token}")
    suspend fun getConversationBots(
        @Path("token") token: String
    ): Response<BotsResponse>
    
    /**
     * 启用 Bot 到会话
     * POST /ocs/v2.php/apps/spreed/api/v1/bot/{token}/{botId}
     */
    @POST("ocs/v2.php/apps/spreed/api/v1/bot/{token}/{botId}")
    suspend fun addBotToConversation(
        @Path("token") token: String,
        @Path("botId") botId: Int
    ): Response<Unit>
    
    /**
     * 从会话禁用 Bot
     * DELETE /ocs/v2.php/apps/spreed/api/v1/bot/{token}/{botId}
     */
    @DELETE("ocs/v2.php/apps/spreed/api/v1/bot/{token}/{botId}")
    suspend fun removeBotFromConversation(
        @Path("token") token: String,
        @Path("botId") botId: Int
    ): Response<Unit>
    
    // ==================== 会话管理功能 ====================
    
    /**
     * 置顶会话
     */
    @POST("ocs/v2.php/apps/spreed/api/v4/room/{token}/favorite")
    suspend fun favoriteConversation(
        @Path("token") token: String
    ): Response<Unit>
    
    /**
     * 取消置顶会话
     */
    @DELETE("ocs/v2.php/apps/spreed/api/v4/room/{token}/favorite")
    suspend fun unfavoriteConversation(
        @Path("token") token: String
    ): Response<Unit>
    
    /**
     * 删除/离开会话
     */
    @DELETE("ocs/v2.php/apps/spreed/api/v4/room/{token}")
    suspend fun deleteConversation(
        @Path("token") token: String
    ): Response<Unit>
    
    /**
     * 标记会话为已读
     */
    @POST("ocs/v2.php/apps/spreed/api/v1/chat/{token}/read")
    suspend fun markConversationRead(
        @Path("token") token: String
    ): Response<Unit>
    
    /**
     * 标记会话为未读
     */
    @DELETE("ocs/v2.php/apps/spreed/api/v1/chat/{token}/read")
    suspend fun markConversationUnread(
        @Path("token") token: String
    ): Response<Unit>
}

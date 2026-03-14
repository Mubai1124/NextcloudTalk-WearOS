package com.example.wearmaterial.service

import android.content.Context
import android.util.Log
import com.example.wearmaterial.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

/**
 * 语音消息上传服务
 * 参考 Nextcloud Talk Android 实现
 */
object VoiceMessageUploader {
    
    const val TAG = "VoiceMessageUploader"
    
    /**
     * 上传并发送语音消息
     * @param context 上下文
     * @param conversationToken 会话 Token
     * @param audioFile 音频文件
     * @param serverUrl 服务器地址
     * @param username 用户名
     * @param password 密码
     * @return 是否成功
     */
    suspend fun uploadAndSend(
        context: Context,
        conversationToken: String,
        audioFile: File,
        serverUrl: String,
        username: String,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val credentials = java.util.Base64.getEncoder()
                        .encodeToString("$username:$password".toByteArray())
                    val request = chain.request().newBuilder()
                        .addHeader("Authorization", "Basic $credentials")
                        .addHeader("OCS-APIRequest", "true")
                        .build()
                    chain.proceed(request)
                }
                .build()
            
            // 1. 上传文件到 Nextcloud
            val fileName = "Talk/voice_${System.currentTimeMillis()}.m4a"
            val uploadUrl = "$serverUrl/remote.php/dav/files/$username/$fileName"
            
            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .put(audioFile.asRequestBody("audio/mp4".toMediaType()))
                .build()
            
            val uploadResponse = client.newCall(uploadRequest).execute()
            if (!uploadResponse.isSuccessful) {
                Log.e(TAG, "Upload failed: ${uploadResponse.code}")
                return@withContext false
            }
            
            Log.d(TAG, "File uploaded: $fileName")
            
            // 2. 分享文件到会话
            val metaData = JSONObject().apply {
                put("messageType", "voice-message")
            }.toString()
            
            val shareUrl = "$serverUrl/ocs/v2.php/apps/files_sharing/api/v1/shares"
            val shareBody = "path=/$fileName&shareType=10&shareWith=$conversationToken&talkMetaData=$metaData"
            
            val shareRequest = Request.Builder()
                .url(shareUrl)
                .post(okhttp3.RequestBody.create(
                    "application/x-www-form-urlencoded".toMediaType(),
                    shareBody
                ))
                .build()
            
            val shareResponse = client.newCall(shareRequest).execute()
            if (!shareResponse.isSuccessful) {
                Log.e(TAG, "Share failed: ${shareResponse.code}")
                return@withContext false
            }
            
            Log.d(TAG, "Voice message sent successfully")
            
            // 3. 删除临时文件
            audioFile.delete()
            
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload voice message: ${e.message}")
            return@withContext false
        }
    }
}

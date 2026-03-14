package com.example.wearmaterial.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.wearmaterial.MainActivity
import com.example.wearmaterial.R
import com.example.wearmaterial.network.NetworkModule
import kotlinx.coroutines.*
import retrofit2.HttpException

/**
 * 前台服务 - 持续轮询新消息并发送通知
 */
class MessagePollingService : Service() {

    companion object {
        const val TAG = "MessagePollingService"
        const val CHANNEL_ID = "message_polling_channel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START = "com.example.wearmaterial.START_POLLING"
        const val ACTION_STOP = "com.example.wearmaterial.STOP_POLLING"
        const val ACTION_REPLY = "com.example.wearmaterial.QUICK_REPLY"
        const val EXTRA_TOKEN = "conversation_token"
        const val EXTRA_MESSAGE = "reply_message"
        
        fun start(context: Context) {
            val intent = Intent(context, MessagePollingService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, MessagePollingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startPolling()
            ACTION_STOP -> stopPolling()
            ACTION_REPLY -> handleQuickReply(intent)
        }
        return START_STICKY
    }
    
    private fun startPolling() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createForegroundNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    pollForNewMessages()
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}")
                }
                delay(5000) // 5秒轮询间隔
            }
        }
        Log.d(TAG, "Polling service started")
    }
    
    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Polling service stopped")
    }
    
    private suspend fun pollForNewMessages() {
        if (!NetworkModule.isConfigured()) return
        
        try {
            val response = NetworkModule.api.getRooms()
            if (response.isSuccessful) {
                val rooms = response.body()?.ocs?.data ?: emptyList()
                rooms.forEach { room ->
                    if (room.unreadMessages > 0) {
                        checkForNewMessages(room.token, room.name, room.unreadMessages)
                    }
                }
            }
        } catch (e: HttpException) {
            Log.e(TAG, "HTTP error: ${e.code()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error polling messages: ${e.message}")
        }
    }
    
    private suspend fun checkForNewMessages(token: String, roomName: String, unreadCount: Int) {
        try {
            val response = NetworkModule.api.getMessages(token, limit = 5)
            if (response.isSuccessful) {
                val messages = response.body()?.ocs?.data ?: emptyList()
                messages.lastOrNull()?.let { msg ->
                    if (msg.actorType != "users" || msg.actorId != null) {
                        sendMessageNotification(token, roomName, msg.message, msg.actorDisplayName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking messages: ${e.message}")
        }
    }
    
    private fun sendMessageNotification(token: String, roomName: String, message: String, sender: String?) {
        // 快速回复 RemoteInput
        val remoteInput = RemoteInput.Builder(EXTRA_MESSAGE)
            .setLabel("快速回复")
            .build()
        
        // 回复 Intent
        val replyIntent = Intent(this, MessagePollingService::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_TOKEN, token)
        }
        val replyPendingIntent = PendingIntent.getService(
            this, token.hashCode(), replyIntent, PendingIntent.FLAG_MUTABLE
        )
        
        // 回复 Action
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_reply, "回复", replyPendingIntent
        ).addRemoteInput(remoteInput).build()
        
        // 点击打开聊天
        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_chat", true)
            putExtra("token", token)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, token.hashCode() + 1, openIntent, PendingIntent.FLAG_MUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(sender ?: roomName)
            .setContentText(message)
            .setContentIntent(openPendingIntent)
            .addAction(replyAction)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        
        notificationManager.notify(token.hashCode(), notification)
    }
    
    private fun handleQuickReply(intent: Intent) {
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val message = remoteInput.getCharSequence(EXTRA_MESSAGE)?.toString() ?: return
        
        serviceScope.launch {
            try {
                NetworkModule.api.sendMessage(token, message)
                // 取消通知
                notificationManager.cancel(token.hashCode())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send quick reply: ${e.message}")
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "消息通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "接收新消息通知"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nextcloud Talk")
            .setContentText("正在监听新消息...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        serviceScope.cancel()
    }
}

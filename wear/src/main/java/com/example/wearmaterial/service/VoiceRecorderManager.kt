package com.example.wearmaterial.service

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 语音消息录音管理器
 * 参考 Nextcloud Talk Android 实现
 */
class VoiceRecorderManager(private val context: Context) {
    
    companion object {
        const val TAG = "VoiceRecorderManager"
        const val SAMPLE_RATE = 22050
        const val BIT_RATE = 32000
        const val CHANNELS = 1
        const val FILE_SUFFIX = ".m4a"
    }
    
    private var recorder: MediaRecorder? = null
    private var currentState: State = State.IDLE
    private var currentFile: File? = null
    
    enum class State {
        IDLE, RECORDING, STOPPED, ERROR
    }
    
    val state: State get() = currentState
    val outputFile: File? get() = currentFile
    
    /**
     * 开始录音
     * @return 录音文件路径，失败返回 null
     */
    fun start(): String? {
        if (currentState == State.RECORDING) {
            Log.w(TAG, "Already recording")
            return null
        }
        
        try {
            // 创建录音文件
            val fileName = generateFileName()
            currentFile = File(context.cacheDir, fileName)
            
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(currentFile!!.absolutePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(SAMPLE_RATE)
                setAudioEncodingBitRate(BIT_RATE)
                setAudioChannels(CHANNELS)
                
                prepare()
                start()
            }
            
            currentState = State.RECORDING
            Log.d(TAG, "Recording started: ${currentFile!!.absolutePath}")
            return currentFile!!.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}")
            currentState = State.ERROR
            return null
        }
    }
    
    /**
     * 停止录音
     * @return 录音文件，失败返回 null
     */
    fun stop(): File? {
        if (currentState != State.RECORDING) {
            Log.w(TAG, "Not recording")
            return null
        }
        
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            currentState = State.STOPPED
            Log.d(TAG, "Recording stopped: ${currentFile!!.absolutePath}")
            return currentFile
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording: ${e.message}")
            currentState = State.ERROR
            recorder?.release()
            recorder = null
            return null
        }
    }
    
    /**
     * 取消录音
     */
    fun cancel() {
        if (currentState != State.RECORDING) return
        
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            
            // 删除录音文件
            currentFile?.delete()
            currentFile = null
            
            currentState = State.IDLE
            Log.d(TAG, "Recording cancelled")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel recording: ${e.message}")
            currentState = State.ERROR
        }
    }
    
    /**
     * 获取录音时长（秒）
     */
    fun getDuration(): Int {
        if (currentState != State.STOPPED || currentFile == null) return 0
        
        return try {
            // 使用 MediaMetadataRetriever 获取时长
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(currentFile!!.absolutePath)
            val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            (duration?.toIntOrNull() ?: 0) / 1000
        } catch (e: Exception) {
            0
        }
    }
    
    private fun generateFileName(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "voice_message_$timestamp$FILE_SUFFIX"
    }
    
    fun release() {
        if (currentState == State.RECORDING) {
            cancel()
        }
        recorder?.release()
        recorder = null
        currentState = State.IDLE
    }
}

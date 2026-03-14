package com.example.wearmaterial.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.*
import com.example.wearmaterial.service.VoiceRecorderManager
import com.example.wearmaterial.service.VoiceMessageUploader
import com.example.wearmaterial.network.NetworkModule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * 语音消息录制界面
 */
@Composable
fun VoiceRecordScreen(
    conversationId: String,
    onBack: () -> Unit,
    onSend: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()
    
    // 录音管理器
    val recorder = remember { VoiceRecorderManager(context) }
    
    // 录音状态
    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var hasPermission by remember { mutableStateOf(false) }
    
    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }
    
    // 检查权限
    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
    // 录音计时器
    LaunchedEffect(isRecording) {
        while (isRecording && !isPaused) {
            delay(1000)
            recordingDuration++
        }
    }
    
    // 清理
    DisposableEffect(Unit) {
        onDispose {
            recorder.release()
        }
    }
    
    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题
            item {
                Text(
                    text = "语音消息",
                    style = MaterialTheme.typography.title2
                )
            }
            
            // 权限提示
            if (!hasPermission) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.MicOff,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colors.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "需要麦克风权限",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Chip(
                            label = { Text("请求权限") },
                            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            colors = ChipDefaults.primaryChipColors()
                        )
                    }
                }
            } else {
                // 录音时长显示
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formatDuration(recordingDuration),
                            style = MaterialTheme.typography.display2,
                            color = if (isRecording) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isRecording) "正在录音..." else "点击下方按钮开始录音",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                
                // 录音按钮
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                    ) {
                        // 取消/删除按钮
                        if (isRecording || audioFile != null) {
                            Chip(
                                modifier = Modifier.size(56.dp),
                                label = { },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                onClick = {
                                    recorder.cancel()
                                    audioFile = null
                                    isRecording = false
                                    recordingDuration = 0
                                },
                                colors = ChipDefaults.secondaryChipColors()
                            )
                        }
                        
                        // 录音/停止按钮
                        Chip(
                            modifier = Modifier.size(72.dp),
                            label = { },
                            icon = {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = if (isRecording) "停止" else "录音",
                                    modifier = Modifier.size(32.dp)
                                )
                            },
                            onClick = {
                                if (isRecording) {
                                    // 停止录音
                                    audioFile = recorder.stop()
                                    isRecording = false
                                } else {
                                    // 开始录音
                                    recorder.start()
                                    isRecording = true
                                    recordingDuration = 0
                                    audioFile = null
                                }
                            },
                            colors = if (isRecording) {
                                ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.error)
                            } else {
                                ChipDefaults.primaryChipColors()
                            }
                        )
                    }
                }
                
                // 发送按钮（录音完成后显示）
                if (audioFile != null && !isRecording) {
                    item {
                        Chip(
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("发送语音消息") },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                scope.launch {
                                    // 获取登录信息
                                    val serverUrl = NetworkModule.baseUrl ?: ""
                                    val username = NetworkModule.username ?: ""
                                    val password = NetworkModule.password ?: ""
                                    
                                    val success = VoiceMessageUploader.uploadAndSend(
                                        context = context,
                                        conversationToken = conversationId,
                                        audioFile = audioFile!!,
                                        serverUrl = serverUrl,
                                        username = username,
                                        password = password
                                    )
                                    
                                    if (success) {
                                        onSend(audioFile!!)
                                    }
                                }
                            },
                            colors = ChipDefaults.primaryChipColors()
                        )
                    }
                }
            }
            
            // 返回按钮
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("返回") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        recorder.cancel()
                        onBack()
                    },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", minutes, secs)
}

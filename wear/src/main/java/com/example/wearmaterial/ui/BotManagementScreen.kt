package com.example.wearmaterial.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.*
import com.example.wearmaterial.models.Bot
import com.example.wearmaterial.viewmodel.TalkViewModel
import com.example.wearmaterial.network.NetworkModule
import kotlinx.coroutines.launch

/**
 * Bot 管理界面
 */
@Composable
fun BotManagementScreen(
    conversationId: String,
    onBack: () -> Unit
) {
    val viewModel: TalkViewModel = viewModel()
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    
    val availableBots by viewModel.availableBots.collectAsState()
    val isLoadingBots by viewModel.isLoadingBots.collectAsState()
    
    // 当前会话中的 Bots - 使用 State 而不是直接收集 Flow
    var conversationBots by remember { mutableStateOf<List<Bot>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // 操作状态
    var isTogglingBot by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // 加载 Bot 列表的函数
    fun reloadBots() {
        scope.launch {
            try {
                val response = NetworkModule.api.getConversationBots(conversationId)
                if (response.isSuccessful) {
                    conversationBots = response.body()?.ocs?.data ?: emptyList()
                } else {
                    Log.e("BotManagement", "Failed to load bots: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("BotManagement", "Error loading bots: ${e.message}")
            }
        }
    }
    
    // 加载 Bot 列表
    LaunchedEffect(conversationId) {
        viewModel.loadAvailableBots()
        isLoading = true
        reloadBots()
        isLoading = false
    }
    
    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 标题
            item {
                Text(
                    text = "Bot 管理",
                    style = MaterialTheme.typography.title2
                )
            }
            
            // 错误提示
            if (errorMessage != null) {
                item {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.caption2,
                        color = Color.Red,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
            
            // 加载中
            if (isLoading || isLoadingBots) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            indicatorColor = MaterialTheme.colors.primary
                        )
                    }
                }
            }
            
            // 当前会话中的 Bots
            if (!isLoading && conversationBots.isNotEmpty()) {
                item {
                    Text(
                        text = "已启用 (${conversationBots.size})",
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.primary
                    )
                }
                
                items(conversationBots.size) { index ->
                    val bot = conversationBots[index]
                    BotItem(
                        bot = bot,
                        isInConversation = true,
                        isToggling = isTogglingBot == bot.id.toString(),
                        onToggle = {
                            if (isTogglingBot == null) {
                                isTogglingBot = bot.id.toString()
                                errorMessage = null
                                scope.launch {
                                    viewModel.removeBotFromConversation(conversationId, bot.id.toString()) { success ->
                                        isTogglingBot = null
                                        if (success) {
                                            reloadBots()
                                        } else {
                                            errorMessage = "禁用失败"
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
            
            // 可添加的 Bots
            if (!isLoadingBots && availableBots.isNotEmpty()) {
                // 过滤掉已添加的
                val availableToAdd = availableBots.filter { bot ->
                    conversationBots.none { it.id == bot.id }
                }
                
                if (availableToAdd.isNotEmpty()) {
                    item {
                        Text(
                            text = if (conversationBots.isEmpty()) "可用 Bot" else "未启用",
                            style = MaterialTheme.typography.caption1,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    
                    items(availableToAdd.size) { index ->
                        val bot = availableToAdd[index]
                        BotItem(
                            bot = bot,
                            isInConversation = false,
                            isToggling = isTogglingBot == bot.id.toString(),
                            onToggle = {
                                if (isTogglingBot == null) {
                                    isTogglingBot = bot.id.toString()
                                    errorMessage = null
                                    scope.launch {
                                        viewModel.addBotToConversation(conversationId, bot.id.toString()) { success ->
                                            isTogglingBot = null
                                            if (success) {
                                                reloadBots()
                                            } else {
                                                errorMessage = "启用失败"
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
            
            // 空状态
            if (!isLoading && !isLoadingBots && availableBots.isEmpty() && conversationBots.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "暂无可用 Bot",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            
            // 说明
            if (!isLoading && !isLoadingBots && availableBots.isNotEmpty()) {
                item {
                    Text(
                        text = "点击 Bot 切换启用状态",
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
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
                    onClick = onBack,
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}

@Composable
private fun BotItem(
    bot: Bot,
    isInConversation: Boolean,
    isToggling: Boolean,
    onToggle: () -> Unit
) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        label = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isInConversation) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bot.name,
                            style = MaterialTheme.typography.body1,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    
                    // 状态指示器
                    if (isToggling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            indicatorColor = MaterialTheme.colors.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        // 开关图标
                        Icon(
                            imageVector = if (isInConversation) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                            contentDescription = if (isInConversation) "已启用" else "已禁用",
                            modifier = Modifier.size(20.dp),
                            tint = if (isInConversation) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
                if (!bot.description.isNullOrEmpty()) {
                    Text(
                        text = bot.description,
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        maxLines = 2
                    )
                }
            }
        },
        onClick = onToggle,
        colors = if (isInConversation) {
            ChipDefaults.primaryChipColors()
        } else {
            ChipDefaults.secondaryChipColors()
        },
        enabled = !isToggling
    )
}

package com.example.wearmaterial.ui

import android.content.Context
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.wearmaterial.models.Message
import com.example.wearmaterial.viewmodel.TalkViewModel
import com.example.wearmaterial.viewmodel.PollingState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    conversationId: String,
    conversationName: String,
    onTextInput: () -> Unit,
    onQuickReply: () -> Unit,
    onBack: () -> Unit,
    onManageBots: () -> Unit = {},
    onVoiceRecord: () -> Unit = {}
) {
    val viewModel: TalkViewModel = viewModel()
    val messages by viewModel.messages.collectAsState()
    val listState = rememberScalingLazyListState()
    val pollingState by viewModel.pollingState.collectAsState()
    
    // Pager 状态：页面 0 = 菜单，页面 1 = 聊天
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    
    // 追踪最后查看的消息 ID
    var lastViewedId by remember { mutableStateOf(-1L) }
    var isFirstLoad by remember { mutableStateOf(true) }
    
    // 滚动状态检测 - 用于导航栏显示/隐藏
    var prevCenterIndex by remember { mutableStateOf(0) }
    var prevCenterOffset by remember { mutableStateOf(0) }
    var isNavBarVisible by remember { mutableStateOf(true) }
    
    // 检测滚动方向（结合 centerItemIndex 和 centerItemScrollOffset）
    LaunchedEffect(listState.centerItemIndex, listState.centerItemScrollOffset) {
        val currentIndex = listState.centerItemIndex
        val currentOffset = listState.centerItemScrollOffset
        
        // 计算总滚动位置（粗略估计）
        val totalPosition = currentIndex * 100 + currentOffset
        val prevTotalPosition = prevCenterIndex * 100 + prevCenterOffset
        
        val diff = totalPosition - prevTotalPosition
        when {
            diff > 30 -> {
                // 向下滚动 - 隐藏导航栏
                isNavBarVisible = false
            }
            diff < -30 -> {
                // 向上滚动 - 显示导航栏
                isNavBarVisible = true
            }
        }
        
        prevCenterIndex = currentIndex
        prevCenterOffset = currentOffset
    }
    
    // 加载消息
    LaunchedEffect(conversationId) {
        viewModel.loadMessages(conversationId, initialLoad = true)
    }
    
    // 消息定位逻辑
    LaunchedEffect(messages.size, isFirstLoad) {
        if (messages.isNotEmpty()) {
            val currentLastId = messages.maxOf { it.id.toLong() }
            
            if (isFirstLoad) {
                val targetIndex = viewModel.getScrollToMessageIndex(conversationId, messages)
                listState.scrollToItem(targetIndex)
                viewModel.markConversationAsRead(conversationId)
                lastViewedId = currentLastId
                isFirstLoad = false
            } else if (currentLastId > lastViewedId) {
                listState.animateScrollToItem(messages.size - 1)
                lastViewedId = currentLastId
            }
        }
    }
    
    DisposableEffect(conversationId) {
        onDispose {
            viewModel.leaveCurrentConversation()
        }
    }
    
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false
    ) { page ->
        when (page) {
            0 -> ChatMenuContent(
                conversationId = conversationId,
                viewModel = viewModel,
                onManageBots = {
                    scope.launch {
                        pagerState.animateScrollToPage(1)
                    }
                    onManageBots()
                },
                onDismiss = {
                    scope.launch {
                        pagerState.animateScrollToPage(1)
                    }
                }
            )
            1 -> ChatContent(
                conversationId = conversationId,
                conversationName = conversationName,
                messages = messages,
                listState = listState,
                pollingState = pollingState,
                viewModel = viewModel,
                onTextInput = onTextInput,
                onQuickReply = onQuickReply,
                onOpenMenu = {
                    scope.launch {
                        pagerState.animateScrollToPage(0)
                    }
                },
                isNavBarVisible = isNavBarVisible,
                onVoiceRecord = onVoiceRecord
            )
        }
    }
}

/**
 * 聊天内容页面
 */
@Composable
private fun ChatContent(
    conversationId: String,
    conversationName: String,
    messages: List<Message>,
    listState: ScalingLazyListState,
    pollingState: PollingState,
    viewModel: TalkViewModel,
    onTextInput: () -> Unit,
    onQuickReply: () -> Unit,
    onOpenMenu: () -> Unit,
    isNavBarVisible: Boolean,
    onVoiceRecord: () -> Unit
) {
    val context = LocalContext.current
    
    // 获取电量
    val batteryLevel = remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        batteryLevel.value = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    
    // 轮询状态
    val statusColor = when (pollingState) {
        is PollingState.Polling -> Color(0xFF4CAF50)
        is PollingState.Ready -> Color(0xFF2196F3)
        else -> Color.Gray
    }
    val statusText = when (pollingState) {
        is PollingState.Polling -> "在线"
        is PollingState.Ready -> "就绪"
        else -> ""
    }
    
    // 获取文本样式（必须在 Composable 上下文中）
    val timeTextStyle = TimeTextDefaults.timeTextStyle()
    val batteryStyle = CurvedTextStyle(timeTextStyle.copy(color = Color(0xFF4CAF50)))
    val statusStyle = CurvedTextStyle(timeTextStyle.copy(color = statusColor))
    
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
            timeText = {
                TimeText(
                    startCurvedContent = {
                        curvedText(
                            text = "${batteryLevel.value}%",
                            style = batteryStyle
                        )
                    },
                    endCurvedContent = {
                        curvedText(
                            text = "● $statusText",
                            style = statusStyle
                        )
                    }
                )
            }
        ) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 顶部占位（为导航栏留空间）
                item {
                    Spacer(modifier = Modifier.height(48.dp))
                }
                
                // 空消息提示
                if (messages.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "暂无消息",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                
                // 消息列表
                items(messages.size) { index ->
                    val msg = messages[index]
                    MessageBubble(
                        content = msg.message,
                        time = formatTime(msg.timestamp),
                        isOwn = msg.actorType == "users" && msg.actorId != null,
                        senderName = msg.actorDisplayName
                    )
                }
                
                // 操作按钮
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Chip(
                            modifier = Modifier.weight(1f),
                            label = { Text("输入", style = MaterialTheme.typography.caption1) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            onClick = onTextInput,
                            colors = ChipDefaults.primaryChipColors()
                        )
                        
                        Chip(
                            modifier = Modifier.weight(1f),
                            label = { Text("语音", style = MaterialTheme.typography.caption1) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            onClick = onVoiceRecord,
                            colors = ChipDefaults.secondaryChipColors()
                        )
                        
                        Chip(
                            modifier = Modifier.weight(1f),
                            label = { Text("快捷", style = MaterialTheme.typography.caption1) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Reply,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            onClick = onQuickReply,
                            colors = ChipDefaults.secondaryChipColors()
                        )
                    }
                }
            }
        }
        
        // 顶部导航栏 - 滑动隐藏/显示
        AnimatedVisibility(
            visible = isNavBarVisible,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
                .padding(top = 16.dp)  // 圆形屏幕：顶部边距
        ) {
            NavigationBar(
                conversationName = conversationName,
                onOpenMenu = onOpenMenu
            )
        }
    }
}

/**
 * 顶部导航栏 - 适配圆形屏幕
 * 圆形屏幕特点：左右两边被切掉，需要限制宽度并居中
 */
@Composable
private fun NavigationBar(
    conversationName: String,
    onOpenMenu: () -> Unit
) {
    // 圆形屏幕适配：限制宽度为 75%，居中显示，整体可点击
    Row(
        modifier = Modifier
            .fillMaxWidth(0.75f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colors.surface.copy(alpha = 0.95f))
            .clickable { onOpenMenu() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween  // 两端对齐
    ) {
        // 会话名称（左侧）
        Text(
            text = conversationName,
            style = MaterialTheme.typography.caption1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
            color = MaterialTheme.colors.onSurface
        )
        
        // 菜单图标（最右边）
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "菜单",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colors.primary
        )
    }
}

/**
 * 聊天菜单内容页面
 */
@Composable
private fun ChatMenuContent(
    conversationId: String,
    viewModel: TalkViewModel,
    onManageBots: () -> Unit,
    onDismiss: () -> Unit
) {
    val menuListState = rememberScalingLazyListState()
    val isRefreshing = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val primaryColor = MaterialTheme.colors.primary
    
    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = menuListState) },
        timeText = { 
            TimeText(
                startCurvedContent = {
                    curvedText(
                        text = "右滑返回 ←",
                        style = CurvedTextStyle(color = primaryColor)
                    )
                }
            )
        }
    ) {
        ScalingLazyColumn(
            state = menuListState,
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    text = "菜单",
                    style = MaterialTheme.typography.title3
                )
            }
            
            // 返回聊天按钮
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss,
                    label = { Text("返回聊天") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = ChipDefaults.primaryChipColors()
                )
            }
            
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        isRefreshing.value = true
                        viewModel.refreshCurrentMessages()
                        scope.launch {
                            delay(500)
                            isRefreshing.value = false
                            onDismiss()
                        }
                    },
                    label = { Text(if (isRefreshing.value) "刷新中..." else "刷新消息") },
                    icon = {
                        if (isRefreshing.value) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    colors = ChipDefaults.primaryChipColors(),
                    enabled = !isRefreshing.value
                )
            }
            
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onManageBots,
                    label = { Text("机器人管理") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
            
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        viewModel.markConversationAsRead(conversationId)
                        viewModel.markAsRead(conversationId)
                        onDismiss()
                    },
                    label = { Text("标记已读") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    content: String,
    time: String,
    isOwn: Boolean,
    senderName: String?
) {
    val backgroundColor = if (isOwn) {
        MaterialTheme.colors.primary
    } else {
        MaterialTheme.colors.surface
    }
    
    val contentColor = if (isOwn) {
        MaterialTheme.colors.onPrimary
    } else {
        MaterialTheme.colors.onSurface
    }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
    ) {
        // 发送者名称
        if (!isOwn && senderName != null) {
            Text(
                text = senderName,
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.secondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }
        
        // 气泡（宽度随内容变化，最大不超过屏幕 85%）
        Box(
            modifier = Modifier
                .widthIn(max = 140.dp)  // 最大宽度限制
                .wrapContentWidth(if (isOwn) Alignment.End else Alignment.Start)
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column {
                Text(
                    text = content,
                    style = MaterialTheme.typography.body2,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = time,
                    style = MaterialTheme.typography.caption3,
                    color = contentColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp * 1000))
}

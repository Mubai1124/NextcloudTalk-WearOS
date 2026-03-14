package com.example.wearmaterial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.*
import com.example.wearmaterial.models.ConversationItem
import com.example.wearmaterial.models.toConversationItem
import com.example.wearmaterial.viewmodel.TalkViewModel

/**
 * 会话列表屏幕
 */
@Composable
fun ConversationsScreen(
    onConversationClick: (String, String) -> Unit,
    onSettingsClick: () -> Unit,
    onCreateConversation: () -> Unit = {},
    onManageBots: (String) -> Unit = {}
) {
    val viewModel: TalkViewModel = viewModel()
    val conversations by viewModel.conversations.collectAsState()
    val error by viewModel.conversationsError.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState(initial = false)
    val isInitialized by viewModel.isInitialized.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val listState = rememberScalingLazyListState()
    
    // 加载会话列表
    LaunchedEffect(isLoggedIn, isInitialized) {
        if (isLoggedIn && isInitialized) {
            viewModel.loadConversations()
        }
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
            // 标题和操作按钮
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "消息",
                        style = MaterialTheme.typography.title3
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        CompactChip(
                            label = { 
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                                    contentDescription = "刷新",
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = { viewModel.refreshConversations() },
                            modifier = Modifier.size(40.dp),
                            enabled = !isRefreshing
                        )
                        CompactChip(
                            label = { 
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Add,
                                    contentDescription = "新建",
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = onCreateConversation,
                            modifier = Modifier.size(40.dp)
                        )
                        CompactChip(
                            label = {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                                    contentDescription = "设置",
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = onSettingsClick,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
            
            // 错误提示
            if (error != null) {
                item {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.error
                    )
                }
            }
            
            // 会话列表
            if (conversations.isEmpty()) {
                item {
                    Text(
                        text = if (isRefreshing) "加载中..." else "暂无会话",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                items(conversations.size) { index ->
                    val room = conversations[index]
                    ConversationItemRow(
                        item = room.toConversationItem(),
                        onClick = { 
                            onConversationClick(
                                room.token,
                                room.displayName ?: room.name
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * 会话行项目
 */
@Composable
private fun ConversationItemRow(
    item: ConversationItem,
    onClick: () -> Unit
) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = ChipDefaults.secondaryChipColors(),
        label = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (item.isFavorite) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "置顶",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colors.primary
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                        if (item.isGroup) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colors.secondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.body1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Text(
                        text = item.time,
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.lastMessage,
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.unreadCount > 0) {
                        Text(
                            text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                            style = MaterialTheme.typography.caption3,
                            color = MaterialTheme.colors.onPrimary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colors.primary,
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    )
}
